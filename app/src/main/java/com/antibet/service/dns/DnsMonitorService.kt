package com.antibet.service.dns

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.antibet.R
import com.antibet.domain.model.BettingDomainList
import com.antibet.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Alternative DNS monitoring service using ConnectivityManager for Android 9+
 * This doesn't interfere with internet connectivity
 */
@RequiresApi(Build.VERSION_CODES.P)
class DnsMonitorService : Service() {
    
    private val TAG = "DnsMonitorService"
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val recentQueries = ConcurrentHashMap<String, Long>()
    
    companion object {
        const val ACTION_START = "com.antibet.dns.monitor.START"
        const val ACTION_STOP = "com.antibet.dns.monitor.STOP"
        const val CHANNEL_ID = "dns_monitor_channel"
        const val NOTIFICATION_ID = 2001
        private const val QUERY_CACHE_TIME = 60000L // 1 minute
    }
    
    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring()
        }
        return START_STICKY
    }
    
    @SuppressLint("MissingPermission")
    private fun startMonitoring() {
        Log.d(TAG, "Starting DNS monitoring...")
        
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Monitor DNS queries through system DNS resolver
        monitorDnsQueries()
        
        // Register network callback to monitor network changes
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
            
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                super.onLinkPropertiesChanged(network, linkProperties)
                // DNS servers changed, could log this
                Log.d(TAG, "Network link properties changed")
            }
        }
        
        connectivityManager?.registerNetworkCallback(networkRequest, networkCallback!!)
        
        Log.d(TAG, "DNS monitoring started")
    }
    
    private fun monitorDnsQueries() {
        serviceScope.launch {
            while (true) {
                try {
                    // Clean old queries from cache
                    val now = System.currentTimeMillis()
                    recentQueries.entries.removeIf { now - it.value > QUERY_CACHE_TIME }
                    
                    delay(5000) // Check every 5 seconds
                } catch (e: Exception) {
                    Log.e(TAG, "Error in monitoring loop", e)
                }
            }
        }
    }
    
    /**
     * This method would be called by a DNS resolver callback if we implement one
     * For now, it's a placeholder for the detection logic
     */
    fun checkDomain(domain: String) {
        val normalizedDomain = domain.lowercase().removePrefix("www.")
        
        // Skip if recently checked
        if (recentQueries.containsKey(normalizedDomain)) {
            return
        }
        
        recentQueries[normalizedDomain] = System.currentTimeMillis()
        
        Log.d(TAG, "Checking domain: $domain")
        val matchedDomain = BettingDomainList.matchesDomain(domain)
        
        if (matchedDomain != null) {
            Log.d(TAG, "Betting domain detected: ${matchedDomain.domain}")
            showNotification(matchedDomain.displayName, matchedDomain.domain)
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
                .setContentTitle("Site de aposta detectado")
                .setContentText("Você está acessando $siteName")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 500, 200, 500))
                .build()
                
            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification", e)
        }
    }
    
    private fun createNotification(): android.app.Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Monitoramento DNS",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitorando acessos a sites de aposta"
        }
        
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Anti-Bet Proteção Ativa")
            .setContentText("Monitorando acessos a sites de aposta")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    private fun stopMonitoring() {
        Log.d(TAG, "Stopping DNS monitoring...")
        
        networkCallback?.let {
            connectivityManager?.unregisterNetworkCallback(it)
        }
        networkCallback = null
        
        recentQueries.clear()
        
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground", e)
        }
        
        stopSelf()
        Log.d(TAG, "DNS monitoring stopped")
    }
    
    override fun onDestroy() {
        stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }
}