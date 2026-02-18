package com.antibet.service.vpn

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import com.antibet.data.local.database.AntibetDatabase
import com.antibet.data.repository.AntibetRepository
import com.antibet.domain.model.BettingDomainList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.pcap4j.packet.IpPacket
import org.pcap4j.packet.IpSelector
import org.pcap4j.packet.IpV4Packet
import org.pcap4j.packet.UdpPacket
import org.pcap4j.packet.UnknownPacket
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Rcode
import org.xbill.DNS.Section
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress

class AntiBetVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.antibet.vpn.START"
        const val ACTION_STOP  = "com.antibet.vpn.STOP"

        @Volatile var isRunning: Boolean = false
            private set

        private const val TAG             = "AntiBetVPN"
        private const val FAKE_DNS_IP     = "10.111.222.1"
        private val FALLBACK_DNS          = listOf("8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1")
        private const val DNS_PORT        = 53
        private const val MTU             = 32767
        private const val POLL_TIMEOUT_MS = 2_000
        private const val DNS_TIMEOUT_MS  = 5_000L
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private var tunFd: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var blockedDomains: Set<String> = emptySet()
    private lateinit var repository: AntibetRepository
    private lateinit var upstreamDns: InetAddress

    private val builtInDomains: Set<String> =
        BettingDomainList.domains.map { it.domain }.toSet()

    private data class PendingDns(
        val socket: DatagramSocket,
        val pfd: ParcelFileDescriptor,
        val requestPacket: IpPacket,
        val sentAt: Long = System.currentTimeMillis()
    )

    private val pending = mutableListOf<PendingDns>()

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        val db = AntibetDatabase.getDatabase(this)
        repository = AntibetRepository(
            db.betDao(), db.savedBetDao(), db.siteTriggerDao(),
            db.settingDao(), db.blockedSiteDao()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> { stopVpn(); START_NOT_STICKY }
            else        -> { startVpn(); START_STICKY }
        }
    }

    override fun onRevoke() { stopVpn() }

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // VPN setup
    // -------------------------------------------------------------------------

    private fun startVpn() {
        if (isRunning) return

        // Capture system DNS BEFORE VPN is up — after establish(), all DNS routes through TUN.
        val systemDns = getSystemDnsServers()
        Log.d(TAG, "System DNS: $systemDns")

        val upstreamIp = systemDns.firstOrNull { it.isNotEmpty() } ?: "8.8.8.8"
        upstreamDns = InetAddress.getByName(upstreamIp)
        Log.d(TAG, "Upstream DNS: $upstreamDns")

        val allDns = (FALLBACK_DNS + systemDns).distinct()

        val builder = Builder()
            .setSession("AntiBet DNS Filter")
            .addAddress(FAKE_DNS_IP, 32)
            .addDnsServer(FAKE_DNS_IP)

        allDns.forEach { dns ->
            if (dns.isNotEmpty()) {
                try { builder.addRoute(dns, 32) }
                catch (e: Exception) { Log.w(TAG, "addRoute $dns: ${e.message}") }
            }
        }

        // setBlocking(false) required — Os.poll() needs a non-blocking fd.
        tunFd = builder.setMtu(MTU).setBlocking(false).establish()

        if (tunFd == null) {
            Log.e(TAG, "establish() returned null")
            stopSelf(); return
        }

        isRunning = true
        Log.d(TAG, "VPN established")

        serviceScope.launch {
            repository.getBlockedSites().collectLatest { sites ->
                val userDomains = sites.map { it.domain }.toSet()
                blockedDomains = builtInDomains + userDomains
                Log.d(TAG, "Blocklist: ${blockedDomains.size} domains")
            }
        }

        serviceScope.launch(Dispatchers.IO) { runPollLoop(tunFd!!) }
    }

    private fun stopVpn() {
        if (!isRunning && tunFd == null) return
        isRunning = false
        pending.forEach { runCatching { it.pfd.close() } }
        pending.clear()
        try { tunFd?.close() } catch (_: Exception) {}
        tunFd = null
        stopSelf()
        Log.d(TAG, "VPN stopped")
    }

    private fun getSystemDnsServers(): List<String> = try {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.getLinkProperties(cm.activeNetwork)
            ?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    // -------------------------------------------------------------------------
    // Poll loop — single thread, no FileDescriptor lock contention
    // -------------------------------------------------------------------------

    private fun runPollLoop(pfd: ParcelFileDescriptor) {
        val rawFd  = pfd.fileDescriptor
        val input  = FileInputStream(rawFd)
        val output = FileOutputStream(rawFd)
        val readBuf    = ByteArray(MTU)
        val writeQueue = ArrayDeque<ByteArray>()

        Log.d(TAG, "Poll loop started")

        while (isRunning) {
            // --- Build poll set ---
            val tunPoll = StructPollfd().also {
                it.fd     = rawFd
                it.events = OsConstants.POLLIN.toShort()
                if (writeQueue.isNotEmpty())
                    it.events = (it.events.toInt() or OsConstants.POLLOUT).toShort()
            }

            val allPolls = ArrayList<StructPollfd>(1 + pending.size)
            allPolls.add(tunPoll)
            pending.forEach { p ->
                allPolls.add(StructPollfd().also {
                    it.fd     = p.pfd.fileDescriptor
                    it.events = OsConstants.POLLIN.toShort()
                })
            }

            try {
                Os.poll(allPolls.toTypedArray(), POLL_TIMEOUT_MS)
            } catch (e: Exception) {
                if (isRunning) Log.e(TAG, "poll() error: ${e.message}")
                break
            }

            // --- Write pending replies to TUN ---
            if ((tunPoll.revents.toInt() and OsConstants.POLLOUT) != 0) {
                while (writeQueue.isNotEmpty()) {
                    try { output.write(writeQueue.removeFirst()) }
                    catch (e: Exception) { Log.e(TAG, "TUN write: ${e.message}") }
                }
            }

            // --- Receive upstream DNS responses ---
            val snapshot = pending.toMutableList()
            pending.clear()
            snapshot.forEachIndexed { i, entry ->
                val pollFd = allPolls.getOrNull(i + 1)
                val ready  = (pollFd?.revents?.toInt() ?: 0) and OsConstants.POLLIN
                if (ready != 0) {
                    try {
                        val buf  = ByteArray(MTU)
                        val resp = DatagramPacket(buf, buf.size)
                        entry.socket.receive(resp)
                        val reply = buildReply(entry.requestPacket, buf.copyOf(resp.length))
                        if (reply != null) writeQueue.addLast(reply)
                    } catch (e: Exception) {
                        Log.w(TAG, "DNS recv: ${e.message}")
                    } finally {
                        runCatching { entry.pfd.close() }
                    }
                } else if (System.currentTimeMillis() - entry.sentAt < DNS_TIMEOUT_MS) {
                    pending.add(entry)
                } else {
                    Log.w(TAG, "DNS timeout — dropping")
                    runCatching { entry.pfd.close() }
                }
            }

            // --- Read new packet from TUN ---
            if ((tunPoll.revents.toInt() and OsConstants.POLLIN) != 0) {
                val len = try { input.read(readBuf) }
                catch (e: Exception) {
                    if (isRunning) Log.e(TAG, "TUN read: ${e.message}")
                    break
                }
                if (len > 0) handlePacket(readBuf, len, writeQueue)
            }
        }

        Log.d(TAG, "Poll loop exited")
    }

    // -------------------------------------------------------------------------
    // Packet handling
    // -------------------------------------------------------------------------

    private fun handlePacket(buf: ByteArray, len: Int, writeQueue: ArrayDeque<ByteArray>) {
        val ipPacket = try {
            IpSelector.newPacket(buf, 0, len) as? IpV4Packet
        } catch (e: Exception) { return } ?: return

        val udpPacket = ipPacket.payload as? UdpPacket ?: return
        if (udpPacket.header.dstPort.valueAsInt() != DNS_PORT) return

        val dnsBytes = udpPacket.payload?.rawData ?: return

        val query: Message = try { Message(dnsBytes) } catch (e: IOException) {
            Log.w(TAG, "DNS parse: ${e.message}"); return
        }

        val name = query.getQuestion()?.getName()?.toString()?.trimEnd('.')?.lowercase()
            ?: return

        if (isBlocked(name)) {
            Log.i(TAG, "BLOCK [$name]")
            val nxBytes = buildNxdomain(query) ?: return
            val reply   = buildReply(ipPacket, nxBytes) ?: return
            writeQueue.addLast(reply)
        } else {
            Log.d(TAG, "PASS  [$name]")
            try {
                val sock = DatagramSocket()
                protect(sock)
                sock.soTimeout = DNS_TIMEOUT_MS.toInt()
                sock.send(DatagramPacket(dnsBytes, dnsBytes.size, upstreamDns, DNS_PORT))
                val sockPfd = ParcelFileDescriptor.fromDatagramSocket(sock)
                pending.add(PendingDns(sock, sockPfd, ipPacket))
            } catch (e: Exception) {
                Log.w(TAG, "DNS forward: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Packet building — pcap4j handles checksums and lengths automatically
    // -------------------------------------------------------------------------

    private fun buildReply(request: IpPacket, dnsPayload: ByteArray): ByteArray? {
        return try {
            val ipv4 = request as IpV4Packet
            val udp  = ipv4.payload as UdpPacket

            val udpBuilder = UdpPacket.Builder(udp)
                .srcPort(udp.header.dstPort)
                .dstPort(udp.header.srcPort)
                .srcAddr(ipv4.header.dstAddr)
                .dstAddr(ipv4.header.srcAddr)
                .correctLengthAtBuild(true)
                .correctChecksumAtBuild(true)
                .payloadBuilder(UnknownPacket.Builder().rawData(dnsPayload))

            IpV4Packet.Builder(ipv4)
                .srcAddr(ipv4.header.dstAddr as Inet4Address)
                .dstAddr(ipv4.header.srcAddr as Inet4Address)
                .correctLengthAtBuild(true)
                .correctChecksumAtBuild(true)
                .payloadBuilder(udpBuilder)
                .build()
                .rawData
        } catch (e: Exception) {
            Log.w(TAG, "buildReply: ${e.message}"); null
        }
    }

    // -------------------------------------------------------------------------
    // NXDOMAIN response via dnsjava
    // -------------------------------------------------------------------------

    private fun buildNxdomain(query: Message): ByteArray? = try {
        val r   = Message(query.getHeader().id)
        val hdr = r.getHeader()
        hdr.setFlag(Flags.QR.toInt())
        hdr.setFlag(Flags.AA.toInt())
        hdr.setFlag(Flags.RA.toInt())
        if (query.getHeader().getFlag(Flags.RD.toInt())) hdr.setFlag(Flags.RD.toInt())
        hdr.setRcode(Rcode.NXDOMAIN)
        query.getQuestion()?.let { r.addRecord(it, Section.QUESTION) }
        r.toWire()
    } catch (e: Exception) { Log.w(TAG, "buildNxdomain: ${e.message}"); null }

    // -------------------------------------------------------------------------
    // Domain matching
    // -------------------------------------------------------------------------

    private fun isBlocked(domain: String): Boolean {
        val n = domain.removePrefix("www.")
        return blockedDomains.any { b -> n == b || n.endsWith(".$b") }
    }
}
