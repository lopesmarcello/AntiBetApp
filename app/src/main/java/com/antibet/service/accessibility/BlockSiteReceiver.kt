package com.antibet.service.accessibility

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antibet.data.local.database.AntibetDatabase
import com.antibet.data.repository.AntibetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BlockSiteReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_BLOCK_SITE = "com.antibet.ACTION_BLOCK_SITE"
        const val EXTRA_DOMAIN = "extra_domain"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_BLOCK_SITE) return

        val domain = intent.getStringExtra(EXTRA_DOMAIN) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        val db = AntibetDatabase.getDatabase(context)
        val repository = AntibetRepository(
            db.betDao(),
            db.savedBetDao(),
            db.siteTriggerDao(),
            db.settingDao(),
            db.blockedSiteDao()
        )

        val pendingResult = goAsync()
        scope.launch {
            try {
                repository.addBlockedSite(domain, "notification")
            } finally {
                pendingResult.finish()
            }
        }

        // Dismiss the notification immediately
        if (notificationId != -1) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notificationId)
        }
    }
}
