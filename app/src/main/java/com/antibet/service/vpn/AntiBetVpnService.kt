package com.antibet.service.vpn

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.antibet.presentation.MainActivity
import com.antibet.R
import com.antibet.data.local.database.AntibetDatabase
import com.antibet.data.local.entity.SiteTrigger
import com.antibet.data.repository.AntibetRepository
import com.antibet.domain.model.BettingDomainList
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class AntiBetVpnService: VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: AntibetRepository
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        val database = AntibetDatabase.getDatabase(applicationContext)
        repository = AntibetRepository(
            database.betDao(),
            database.savedBetDao(),
            database.siteTriggerDao(),
            database.settingDao()
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action){
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("ForegroundServiceType")
    private fun startVpn(){
        if (isRunning) return

        startForeground(NOTIFICATION_ID, createForegroundNotification())

        vpnInterface = Builder()
            .setSession("AntiBet Protection")
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("1.0.0.1")
            .setMtu(1500)
            .setBlocking(false)
            .establish()

        isRunning = true

        serviceScope.launch {
            processPackets()
        }
    }

    private fun stopVpn(){
        isRunning = false
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun processPackets(){
        val vpnInput = FileInputStream(vpnInterface?.fileDescriptor)
        val vpnOutput = FileOutputStream(vpnInterface?.fileDescriptor)
        val buffer = ByteBuffer.allocate(32767)

        try {
            while (isRunning){
                val length = vpnInput.read(buffer.array())
                if (length > 0){
                    val packet = buffer.array()
                    val ipVersion = (packet[0].toInt() shr 4) and 0x0F

                    if (ipVersion == 4){
                        val protocol = packet[9].toInt() and 0xFF
                        if (protocol == 17){ // UDP
                            val dnsQuery = parseDnsQuery(packet)
                            if (dnsQuery != null){
                                handleDnsQuery(dnsQuery)
                            }
                        }
                    }

                    buffer.position(0)
                    vpnOutput.write(buffer.array(), 0, length)

                    buffer.clear()
                }
            }

        } catch (e: Exception){
            e.printStackTrace()
        }
    }

    private fun parseDnsQuery(packet: ByteArray): String? {
        try{
            // Simple DNS query parser
            // IP header is typically 20 bytes
            // UDP header is 8 bytes

            // DNS query starts at byte 28

            val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4
            val dnsStart = ipHeaderLength + 8

            // Parse DNS question section
            var pos = dnsStart + 12 // skip DNS header
            val domain = StringBuilder()
            while (pos < packet.size && packet[pos] != 0.toByte()){
                val labelLength = packet[pos].toInt() and 0xFF
                if (labelLength == 0) break

                pos ++

                if (domain.isNotEmpty()) domain.append(".")

                repeat(labelLength) {
                    if (pos >= packet.size) return null

                    domain.append(packet[pos].toInt().toChar())
                    pos++
                }
            }
            return if (domain.isNotEmpty()) domain.toString() else null
        } catch (_: Exception){
            return null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun handleDnsQuery(domain: String){
        val matchedDomain = BettingDomainList.matchesDomain(domain)
        if (matchedDomain != null){
            repository.insertTrigger(
                SiteTrigger(
                    timestamp = System.currentTimeMillis(),
                    domain = matchedDomain.domain,
                    action = "warned"
                )
            )

            withContext(Dispatchers.Main){
                showBettingSiteDetectedNotification(matchedDomain.displayName, matchedDomain.domain)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showBettingSiteDetectedNotification(siteName: String, domain: String){
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            BETTING_ALERT_CHANNEL_ID,
            "Alertas de Sites de Apostas",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_DETECTED_DOMAIN, domain)
            putExtra(EXTRA_DETECTED_SITE_NAME, siteName)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, BETTING_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle("⚠️ Site de aposta detectado")
            .setContentText("Você está acessando $siteName. Quer registrar quanto NÃO vai apostar?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        notificationManager.notify(
            System.currentTimeMillis().toInt(),
            notification
        )
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createForegroundNotification() = NotificationCompat.Builder(this, VPN_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_shield)
        .setContentTitle("Anti-Bet Proteção Ativa")
        .setContentText("Monitorando acessos a sites de aposta")
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()
        .also {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                VPN_CHANNEL_ID,
                "Proteção VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

    companion object {
        const val ACTION_START = "com.antibet.vpn.START"
        const val ACTION_STOP = "com.antibet.vpn.STOP"
        const val VPN_CHANNEL_ID = "vpn_service_channel"
        const val BETTING_ALERT_CHANNEL_ID = "betting_alert_channel"
        const val NOTIFICATION_ID = 1000
        const val EXTRA_DETECTED_DOMAIN = "detected_domain"
        const val EXTRA_DETECTED_SITE_NAME = "detected_site_name"
    }
}
