package com.antibet.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.antibet.R
import com.antibet.data.local.database.AntibetDatabase
import com.antibet.data.repository.AntibetRepository
import com.antibet.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.net.URI

class WebMonitorAccessibilityService : AccessibilityService() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "web_monitor_alerts"
        private const val NOTIFICATION_ID = 1001

        private val BETTING_DOMAINS = setOf(
            "bet365.com",
            "betano.com",
            "sportingbet.com",
            "betfair.com",
            "rivalo.com",
            "22bet.com",
            "1xbet.com",
            "pixbet.com",
            "betway.com",
            "pinnacle.com",
            "betwinner.com",
            "parimatch.com",
            "megapari.com",
            "esportes.da.sorte",
            "esportesdasorte.com",
            "blaze.com",
            "stake.com",
            "galera.bet",
            "galeranet.com",
            "apostaganha.bet",
            "betmotion.com",
            "f12.bet",
            "mrjack.bet",
            "luva.bet",
            "vaidebet.com",
            "pagbet.com",
            "estrela.bet",
            "superbet.com",
            "betnacional.com",
            "brazino777.com",
            "novibet.com.br",
            "betboo.com",
            "leovegas.com",
            ".bet.br"
        )

        // IDs de recursos comuns para barras de URL em diferentes navegadores
        private val URL_BAR_IDS = listOf(
            "url_bar",          // Chrome
            "location_bar",     // Chrome/Edge
            "mozac_browser_toolbar_url_view",  // Firefox
            "search",           // Alguns navegadores
            "addressbar",       // Opera
            "com.android.chrome:id/url_bar",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.opera.browser:id/url_field"
        )
    }

    private var lastCheckedUrl: String? = null
    private var lastNotificationTime: Long = 0L
    private val notificationCooldown = 5000L // 5s entre notificacoes

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var userBlockedDomains: Set<String> = emptySet()
    private lateinit var repository: AntibetRepository

    override fun onServiceConnected() {
        super.onServiceConnected()

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS

            notificationTimeout = 100
        }

        serviceInfo = info
        createNotificationChannel()

        val db = AntibetDatabase.getDatabase(this)
        repository = AntibetRepository(
            db.betDao(),
            db.savedBetDao(),
            db.siteTriggerDao(),
            db.settingDao(),
            db.blockedSiteDao()
        )

        // Keep user blacklist in memory, refreshed reactively
        serviceScope.launch {
            repository.getBlockedSites().collectLatest { sites ->
                userBlockedDomains = sites.map { it.domain }.toSet()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
       event ?: return

        val packageName = event.packageName?.toString() ?: return
        if (!isBrowserPackage(packageName)) return

        event.source?.let{ nodeInfo ->
            findAndCheckUrl(nodeInfo)
            nodeInfo.recycle()
        }


    }


    private fun isBrowserPackage(packageName: String): Boolean {
        return packageName.contains("chrome") ||
                packageName.contains("firefox") ||
                packageName.contains("opera") ||
                packageName.contains("edge") ||
                packageName.contains("browser") ||
                packageName.contains("brave") ||
                packageName.contains("duckduckgo") ||
                packageName.contains("samsung") ||
                packageName.contains("kiwi")
    }

    private fun findAndCheckUrl(node: AccessibilityNodeInfo) {
        val url = findUrlInNode(node)

        if (url != null && url != lastCheckedUrl) {
            lastCheckedUrl = url
            checkDomainAndNotify(url)
        }
    }

    private fun findUrlInNode(node: AccessibilityNodeInfo): String? {
        for (urlBarId in URL_BAR_IDS) {
            val urlNodes = node.findAccessibilityNodeInfosByViewId(urlBarId)
            if (urlNodes.isNotEmpty()) {
                val url = urlNodes[0]?.text?.toString()
                urlNodes.forEach { it?.recycle() }
                if (!url.isNullOrEmpty()) return url
            }

        }
        return null
    }

    private fun searchNodeRecursively(node: AccessibilityNodeInfo): String? {
        val text = node.text?.toString()

        if (!text.isNullOrEmpty() && isLikelyUrl(text)) {
            return text
        }

        val contentDesc = node.contentDescription?.toString()
        if (!contentDesc.isNullOrEmpty() && isLikelyUrl(contentDesc)) {
            return contentDesc
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val result = searchNodeRecursively(child)
                child.recycle()
                if (result != null) return result
            }
        }

        return null
    }

    private fun isLikelyUrl(text: String): Boolean {
        return text.contains("http://") ||
                text.contains("https://") ||
                text.contains(".com") ||
                text.contains(".br") ||
                text.contains(".net") ||
                text.contains("bet") ||
                text.contains(Regex(".*\\.[a-z]{2,6}.*"))
    }

    private fun checkDomainAndNotify(url: String) {
        try {
            val domain = extractDomain(url)

            val isBuiltInSite = BETTING_DOMAINS.any { bettingDomain ->
                domain.contains(bettingDomain, ignoreCase = true) ||
                        domain.endsWith(bettingDomain, ignoreCase = true)
            }

            val isUserBlocked = userBlockedDomains.any { blocked ->
                domain.contains(blocked, ignoreCase = true) ||
                        domain.endsWith(blocked, ignoreCase = true)
            }

            if (isBuiltInSite || isUserBlocked) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastNotificationTime > notificationCooldown) {
                    lastNotificationTime = currentTime
                    // Only offer the "Block" action for built-in sites not yet user-blocked
                    val offerBlockAction = isBuiltInSite && !isUserBlocked
                    showBettingAlert(domain, offerBlockAction)
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun extractDomain(url: String): String {
        return try {
            var cleanUrl = url.trim()

            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                cleanUrl = "https://$cleanUrl"
            }

            val uri = URI(cleanUrl)
            uri.host ?: cleanUrl
        } catch (_: Exception) {
            url.replace(Regex("^(https?://)?"), "")
                .replace(Regex("/.*$"), "")
                .lowercase()
        }
    }

    private fun showBettingAlert(domain: String, offerBlockAction: Boolean = false) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            action = "ACTION_ADD_SAVING"
            putExtra("EXTRA_DETECTED_DOMAIN", domain)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val blockIntent = Intent(this, BlockSiteReceiver::class.java).apply {
            action = BlockSiteReceiver.ACTION_BLOCK_SITE
            putExtra(BlockSiteReceiver.EXTRA_DOMAIN, domain)
            putExtra(BlockSiteReceiver.EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
        }
        val blockPendingIntent = PendingIntent.getBroadcast(
            this,
            domain.hashCode(),
            blockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⚠️ Site de Apostas Detectado")
            .setContentText("Você está acessando $domain. Lembre-se do seu objetivo!")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Você está acessando $domain.\n\nLembre-se: cada vez que você resiste, você economiza dinheiro e ganha mais controle sobre sua vida.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .addAction(
                R.drawable.ic_add,
                "Registrar Economia",
                openAppPendingIntent
            )

        if (offerBlockAction) {
            builder.addAction(
                R.drawable.ic_notification,
                "Bloquear este site",
                blockPendingIntent
            )
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Alertas de sites de apostas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações quando você acessa sites de apostas"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onInterrupt() {
        // Service interrupted — no action needed
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        lastCheckedUrl = null
    }

}