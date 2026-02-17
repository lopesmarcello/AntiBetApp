package com.antibet.service.dns

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.antibet.R
import com.antibet.domain.model.BettingDomainList
import com.antibet.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("VpnServicePolicy")
class BettingProtectionService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var vpnThread: Thread? = null
    
    private val checkedDomains = ConcurrentHashMap<String, Long>()
    private val CACHE_DURATION = 60000L
    
    private val TAG = "BettingProtection"
    
    companion object {
        const val ACTION_START = "com.antibet.dns.START"
        const val ACTION_STOP = "com.antibet.dns.STOP"
        const val CHANNEL_ID = "betting_protection_channel"
        const val NOTIFICATION_ID = 2000
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        when (intent?.action) {
            ACTION_START -> startProtection()
            ACTION_STOP -> stopProtection()
        }
        return START_STICKY
    }

    private fun startProtection() {
        if (isRunning) return

        Log.d(TAG, "Starting protection service...")

        try {
            startForeground(NOTIFICATION_ID, createNotification())
            
            isRunning = true
            
            vpnThread = Thread {
                runVpn()
            }.apply { start() }

            Log.d(TAG, "Protection service started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting protection", e)
            stopProtection()
        }
    }

    private fun runVpn() {
        var vpnFd: ParcelFileDescriptor? = null
        
        try {
            Log.d(TAG, "Establishing VPN...")
            
            // Key: Only route DNS server traffic through VPN
            // This allows other traffic to bypass the VPN entirely
            val builder = Builder()
                .setSession("AntiBet DNS")
                .addAddress("10.0.0.2", 32)
                // Only route traffic TO DNS servers - not all traffic!
                .addRoute("8.8.8.8", 32)
                .addRoute("8.8.4.4", 32)
                .addRoute("1.1.1.1", 32)
                .addRoute("1.0.0.1", 32)
                // Set DNS servers
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")
                .addDisallowedApplication(packageName)
                .setMtu(1500)
                .setBlocking(false)

            vpnFd = builder.establish()
            
            if (vpnFd == null) {
                Log.e(TAG, "VPN establish() returned null")
                stopProtection()
                return
            }
            
            vpnInterface = vpnFd
            Log.d(TAG, "VPN established")
            
            // Start packet processing
            processPackets(vpnFd)
            
        } catch (e: Exception) {
            Log.e(TAG, "VPN error", e)
            stopProtection()
        }
    }
    
    private fun processPackets(vpnFd: ParcelFileDescriptor) {
        val vpnInput = FileInputStream(vpnFd.fileDescriptor)
        val vpnOutput = FileOutputStream(vpnFd.fileDescriptor)
        val packet = ByteArray(32767)
        
        Log.d(TAG, "Starting packet forwarding")
        
        while (isRunning && vpnInterface != null) {
            try {
                val length = vpnInput.read(packet)
                
                if (length > 0) {
                    val packetData = packet.copyOf(length)
                    
                    // Check if DNS packet and inspect
                    val domain = extractDnsDomain(packetData, length)
                    if (domain != null) {
                        checkBettingDomain(domain)
                    }
                    
                    // Forward packet to internet
                    forwardPacket(packetData, length) { response ->
                        if (response != null) {
                            try {
                                vpnOutput.write(response)
                                vpnOutput.flush()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error writing response", e)
                            }
                        }
                    }
                }
                
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                if (isRunning) {
                    Thread.yield()
                }
            }
        }
        
        Log.d(TAG, "Packet forwarding ended")
    }
    
    private fun forwardPacket(packet: ByteArray, length: Int, callback: (ByteArray?) -> Unit) {
        try {
            if (length < 20) {
                callback(null)
                return
            }
            
            val ipHeaderLength = (packet[0].toInt() and 0xF) * 4
            if (ipHeaderLength < 20 || length < ipHeaderLength + 8) {
                callback(null)
                return
            }
            
            val protocol = packet[9].toInt() and 0xFF
            val destPort = ((packet[ipHeaderLength + 2].toInt() and 0xFF) shl 8) or 
                          (packet[ipHeaderLength + 3].toInt() and 0xFF)
            
            // For UDP DNS packets (port 53), forward with response
            if (protocol == 17 && destPort == 53) {
                forwardUdpDns(packet, length, ipHeaderLength, callback)
            } else {
                // For other packets, just return null (can't easily forward without routing)
                // But this would block internet - so let's try a different approach
                // Actually, we can't easily forward non-DNS packets
                // The solution is to make this a true VPN which is complex
                
                // For now, let's just pass through without forwarding
                // This won't work but is the best we can do without raw sockets
                callback(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error forwarding packet", e)
            callback(null)
        }
    }
    
    private fun forwardUdpDns(
        packet: ByteArray,
        length: Int,
        ipHeaderLength: Int,
        callback: (ByteArray?) -> Unit
    ) {
        Thread {
            try {
                // Get destination IP (DNS server)
                val destIp = ByteArray(4)
                System.arraycopy(packet, 16, destIp, 0, 4)
                val destAddress = InetAddress.getByAddress(destIp)
                
                // Get DNS payload
                val dnsStart = ipHeaderLength + 8
                val dnsPayload = packet.copyOfRange(dnsStart, length)
                
                // Create socket and protect it (bypass VPN)
                val socket = DatagramSocket()
                protect(socket)
                socket.soTimeout = 3000
                
                // Forward to DNS server
                val dnsPacket = DatagramPacket(dnsPayload, dnsPayload.size, destAddress, 53)
                socket.send(dnsPacket)
                
                // Receive response
                val responseBuffer = ByteArray(512)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(responsePacket)
                
                // Build response IP packet
                val responseIpPacket = buildResponsePacket(
                    packet,
                    ipHeaderLength,
                    responsePacket.data,
                    responsePacket.length
                )
                
                callback(responseIpPacket)
                socket.close()
                
            } catch (e: Exception) {
                Log.e(TAG, "DNS forward error", e)
                callback(null)
            }
        }.start()
    }
    
    private fun buildResponsePacket(
        originalPacket: ByteArray,
        ipHeaderLength: Int,
        dnsResponse: ByteArray,
        dnsResponseLength: Int
    ): ByteArray {
        val totalLength = ipHeaderLength + 8 + dnsResponseLength
        val buffer = ByteBuffer.allocate(totalLength)
        
        // Copy IP header
        buffer.put(originalPacket, 0, ipHeaderLength)
        
        // Update total length
        buffer.put(2, ((totalLength shr 8) and 0xFF).toByte())
        buffer.put(3, (totalLength and 0xFF).toByte())
        
        // Swap source and destination IPs
        for (i in 0..3) {
            val temp = buffer.get(12 + i)
            buffer.put(12 + i, buffer.get(16 + i))
            buffer.put(16 + i, temp)
        }
        
        // UDP header
        val udpStart = ipHeaderLength
        // Swap ports
        buffer.put(udpStart, originalPacket[udpStart + 2])
        buffer.put(udpStart + 1, originalPacket[udpStart + 3])
        buffer.put(udpStart + 2, originalPacket[udpStart])
        buffer.put(udpStart + 3, originalPacket[udpStart + 1])
        
        // UDP length
        val udpLength = 8 + dnsResponseLength
        buffer.put(udpStart + 4, ((udpLength shr 8) and 0xFF).toByte())
        buffer.put(udpStart + 5, (udpLength and 0xFF).toByte())
        
        // UDP checksum (0)
        buffer.put(udpStart + 6, 0)
        buffer.put(udpStart + 7, 0)
        
        // DNS response
        buffer.position(udpStart + 8)
        buffer.put(dnsResponse, 0, dnsResponseLength)
        
        return buffer.array()
    }
    
    private fun extractDnsDomain(packet: ByteArray, length: Int): String? {
        try {
            if (length < 20) return null
            
            val version = (packet[0].toInt() shr 4) and 0xF
            if (version != 4) return null
            
            val ipHeaderLength = (packet[0].toInt() and 0xF) * 4
            if (ipHeaderLength < 20 || ipHeaderLength + 8 > length) return null
            
            val protocol = packet[9].toInt() and 0xFF
            if (protocol != 17) return null
            
            val destPort = ((packet[ipHeaderLength + 2].toInt() and 0xFF) shl 8) or 
                          (packet[ipHeaderLength + 3].toInt() and 0xFF)
            
            if (destPort != 53) return null
            
            val dnsStart = ipHeaderLength + 8
            if (length <= dnsStart) return null
            
            val dnsData = packet.copyOfRange(dnsStart, length)
            return parseDnsQuery(dnsData)
            
        } catch (e: Exception) {
            return null
        }
    }
    
    private fun parseDnsQuery(data: ByteArray): String? {
        try {
            if (data.size < 12) return null
            
            var pos = 12
            val domain = StringBuilder()

            while (pos < data.size) {
                val labelLength = data[pos].toInt() and 0xFF
                if (labelLength == 0) break
                if (labelLength > 63) break
                
                pos++
                if (domain.isNotEmpty()) domain.append(".")

                for (i in 0 until labelLength) {
                    if (pos >= data.size) return null
                    val c = data[pos].toInt().toChar()
                    if (c.isLetterOrDigit() || c == '-') {
                        domain.append(c)
                    }
                    pos++
                }
            }

            return if (domain.isNotEmpty()) domain.toString().lowercase() else null
        } catch (e: Exception) {
            return null
        }
    }

    private fun checkBettingDomain(domain: String) {
        val normalizedDomain = domain.lowercase().removePrefix("www.")
        
        val now = System.currentTimeMillis()
        val lastCheck = checkedDomains[normalizedDomain]
        
        if (lastCheck != null && now - lastCheck < CACHE_DURATION) {
            return
        }
        
        checkedDomains[normalizedDomain] = now
        checkedDomains.entries.removeIf { now - it.value > CACHE_DURATION * 2 }
        
        Log.d(TAG, "DNS query for: $domain")
        
        val matchedDomain = BettingDomainList.matchesDomain(domain)
        if (matchedDomain != null) {
            Log.d(TAG, "⚠️ Betting domain detected: ${matchedDomain.domain}")
            
            val handler = android.os.Handler(mainLooper)
            handler.post {
                showNotification(matchedDomain.displayName, matchedDomain.domain)
            }
        }
    }

    private fun showNotification(siteName: String, domain: String) {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alertas de Apostas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações quando site de aposta é detectado"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle("⚠️ Site de aposta detectado")
                .setContentText("Você está tentando acessar $siteName")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 500, 200, 500))
                .build()

            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
            
            Log.d(TAG, "Notification shown for: $siteName")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification", e)
        }
    }

    private fun createNotification(): android.app.Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Proteção Ativa",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitorando acessos a sites de aposta"
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Anti-Bet Proteção Ativa")
            .setContentText("Monitorando acessos a sites de aposta")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun stopProtection() {
        Log.d(TAG, "Stopping protection service...")
        isRunning = false
        
        vpnThread?.interrupt()
        vpnThread = null
        
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN", e)
        }
        vpnInterface = null
        
        checkedDomains.clear()
        
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground", e)
        }
        
        stopSelf()
        Log.d(TAG, "Protection service stopped")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        stopProtection()
        serviceScope.cancel()
        super.onDestroy()
    }
}
