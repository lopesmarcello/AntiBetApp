# AntiBet VPN DNS Filter — Technical Documentation

## Overview

AntiBet uses a local VPN to intercept DNS queries and block access to gambling-related domains. This document describes the architecture, the bugs encountered during development, and the final working implementation.

## Architecture

### Goal
- Intercept DNS queries from all apps on the device
- Block queries to gambling domains (built-in list + user-added)
- Forward all other queries to a real upstream DNS server
- Zero impact on normal browsing speed

### Approach
- Android `VpnService` creates a local TUN interface
- DNS traffic is routed through this TUN
- A single-threaded `Os.poll()` event loop handles both reading packets from TUN and receiving responses from upstream DNS servers
- No external server — all processing is local on the device

## Key Files

```
app/src/main/java/com/antibet/
├── service/vpn/AntiBetVpnService.kt    # Main VPN DNS filter implementation
├── domain/model/BettingDomain.kt        # Built-in blocklist (~80 domains)
└── data/local/entity/BlockedSite.kt    # User-added blocklist (Room)

app/build.gradle.kts                    # Dependencies (dnsjava, pcap4j)
```

## Dependencies

```kotlin
// app/build.gradle.kts
implementation("dnsjava:dnsjava:3.6.2")           // DNS message parsing and NXDOMAIN building
implementation("org.pcap4j:pcap4j-core:1.8.2")   // IP/UDP packet parsing and building
```

## Implementation Details

### VPN Setup

```kotlin
private fun startVpn() {
    // Capture system DNS BEFORE VPN is established — after establish(), 
    // all DNS routes through TUN and we can't get the real server.
    val systemDns = getSystemDnsServers()
    val upstreamIp = systemDns.firstOrNull { it.isNotEmpty() } ?: "8.8.8.8"
    upstreamDns = InetAddress.getByName(upstreamIp)

    val allDns = (FALLBACK_DNS + systemDns).distinct()

    val builder = Builder()
        .setSession("AntiBet DNS Filter")
        .addAddress("10.111.222.1", 32)   // Fake TUN address
        .addDnsServer("10.111.222.1")      // Tell OS to send DNS here

    // Route all known DNS server IPs through the TUN
    allDns.forEach { dns ->
        try { builder.addRoute(dns, 32) }
        catch (e: Exception) { /* ignore */ }
    }

    // CRITICAL: setBlocking(false) — required for Os.poll() to work
    tunFd = builder.setMtu(32767).setBlocking(false).establish()
}
```

### Poll Loop (The Core Pattern)

The implementation uses `Os.poll()` in a single thread — exactly like the DNS66/AdBuster project:

```kotlin
private fun runPollLoop(pfd: ParcelFileDescriptor) {
    val rawFd = pfd.fileDescriptor
    val input = FileInputStream(rawFd)
    val output = FileOutputStream(rawFd)
    val writeQueue = ArrayDeque<ByteArray>()

    while (isRunning) {
        // Build poll set: TUN fd + all pending DNS sockets
        val polls = ArrayList<StructPollfd>()
        polls.add(StructPollfd().apply {
            fd = rawFd
            events = OsConstants.POLLIN
            if (writeQueue.isNotEmpty()) events = events or OsConstants.POLLOUT
        })
        pending.forEach { p ->
            polls.add(StructPollfd().apply {
                fd = p.pfd.fileDescriptor
                events = OsConstants.POLLIN
            })
        }

        Os.poll(polls.toTypedArray(), POLL_TIMEOUT_MS)

        // Write pending replies to TUN
        if (polls[0].revents has POLLOUT) {
            while (writeQueue.isNotEmpty()) {
                output.write(writeQueue.removeFirst())
            }
        }

        // Receive upstream DNS responses (polls[1..N])
        // ...

        // Read new packet from TUN (polls[0] has POLLIN)
        if (polls[0].revents has POLLIN) {
            handlePacket(readBuf, len, writeQueue)
        }
    }
}
```

### Why Poll Instead of Blocking Read?

**The critical bug that caused `DNS_PROBE_FINISHED_BAD_CONFIG`:**

- `FileInputStream.read()` with `setBlocking(true)` holds Java's `FileDescriptor.lock` indefinitely
- `FileOutputStream.write()` on the same fd tries to acquire the same lock
- Write blocks until the next read completes → DNS reply arrives too late
- Android's `netd` resolver times out

**Fix:** `setBlocking(false)` + `Os.poll()` — a single thread handles both reads and writes, no lock contention.

### Packet Handling with pcap4j

```kotlin
private fun handlePacket(buf: ByteArray, len: Int, writeQueue: ArrayDeque<ByteArray>) {
    // Parse with pcap4j — handles IPv4/IPv6 automatically
    val ipPacket = IpSelector.newPacket(buf, 0, len) as? IpV4Packet ?: return
    val udpPacket = ipPacket.payload as? UdpPacket ?: return
    
    // Only handle DNS (port 53)
    if (udpPacket.header.dstPort.valueAsInt() != 53) return

    val dnsBytes = udpPacket.payload.rawData

    // Parse DNS with dnsjava
    val query = Message(dnsBytes)
    val name = query.getQuestion().getName().toString().lowercase()

    if (isBlocked(name)) {
        // Build NXDOMAIN with dnsjava
        val nxBytes = buildNxdomain(query)
        // Build IP/UDP reply with pcap4j (correct checksums automatically)
        val reply = buildReply(ipPacket, nxBytes)
        writeQueue.addLast(reply)
    } else {
        // Forward to upstream DNS
        val sock = DatagramSocket()
        protect(sock)  // Route outside VPN
        sock.send(DatagramPacket(dnsBytes, dnsBytes.size, upstreamDns, 53))
        
        // Track for response
        pending.add(PendingDns(sock, pfd, ipPacket))
    }
}
```

### Building Reply Packets

```kotlin
private fun buildReply(request: IpPacket, dnsPayload: ByteArray): ByteArray {
    val ipv4 = request as IpV4Packet
    val udp = ipv4.payload as UdpPacket

    // pcap4j handles IP/UDP checksum and length automatically
    val udpBuilder = UdpPacket.Builder(udp)
        .srcPort(udp.header.dstPort)   // Swap: original dst → reply src
        .dstPort(udp.header.srcPort)   // Swap: original src → reply dst
        .srcAddr(ipv4.header.dstAddr)
        .dstAddr(ipv4.header.srcAddr)
        .correctLengthAtBuild(true)
        .correctChecksumAtBuild(true)
        .payloadBuilder(UnknownPacket.Builder().rawData(dnsPayload))

    return IpV4Packet.Builder(ipv4)
        .srcAddr(ipv4.header.dstAddr as Inet4Address)
        .dstAddr(ipv4.header.srcAddr as Inet4Address)
        .correctLengthAtBuild(true)
        .correctChecksumAtBuild(true)
        .payloadBuilder(udpBuilder)
        .build()
        .rawData
}
```

## Bugs Encountered

### 1. FileDescriptor Lock Contention
**Symptom:** Allowed sites show `DNS_PROBE_FINISHED_BAD_CONFIG`, blocked sites work.

**Cause:** `setBlocking(true)` + blocking `FileInputStream.read()` + separate writer thread. The write waits for the read lock.

**Fix:** `setBlocking(false)` + `Os.poll()` single-threaded loop.

### 2. Forwarding to Wrong DNS Server
**Symptom:** Forwarded DNS queries always sent to hardcoded `8.8.8.8`.

**Cause:** Code extracted destination IP from packet (which is always the TUN fake IP), not the original system DNS.

**Fix:** Capture real upstream DNS before `establish()`.

### 3. Hand-Rolled Packet Building
**Symptom:** Correct-looking hex dumps but responses rejected.

**Cause:** Manual IP checksum calculation had subtle bugs. pcap4j's `correctChecksumAtBuild(true)` is verified correct.

**Fix:** Use pcap4j for all IP/UDP packet building.

## Domain Matching

```kotlin
private fun isBlocked(domain: String): Boolean {
    val n = domain.removePrefix("www.")
    return blockedDomains.any { b -> n == b || n.endsWith(".$b") }
}

// blockedDomains = builtInDomains + userDomains
// builtInDomains = BettingDomainList.domains (~80 gambling domains)
```

## Testing

```bash
# Watch VPN logs
adb logcat -s AntiBetVPN

# Expected output:
# System DNS: [192.168.1.1, 8.8.8.8]
# Upstream DNS: /192.168.1.1
# VPN established
# Blocklist: 79 domains
# PASS [google.com]        # Allowed — page loads
# BLOCK [bet365.com]        # Blocked — ERR_NAME_NOT_RESOLVED
```

## Important Notes

1. **Private DNS must be OFF** — Chrome's DoH bypasses the VPN. Go to Android Settings → Network → Private DNS → Off.

2. **No IPv6 handling** — The code only handles IPv4 (IpV4Packet). IPv6 DNS queries are silently dropped. Android always falls back to IPv4, so this works.

3. **minSdk 33+** — The implementation uses modern Android APIs (`Os.poll()`, `StructPollfd`).

4. **Thread safety** — The entire poll loop runs on a single coroutine. All state (`pending`, `writeQueue`) is accessed from one thread — no synchronization needed.

## References

- DNS66 (AdVpnThread.java) — Original inspiration for the poll() pattern
- pcap4j — IP/UDP packet library
- dnsjava — DNS message library
