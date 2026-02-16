// MainActivity.kt
package com.antibet.presentation

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import com.antibet.service.vpn.AntiBetVpnService
import com.antibet.presentation.theme.AntibetTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val detectedDomain = intent.getStringExtra(AntiBetVpnService.EXTRA_DETECTED_DOMAIN)

        setContent {
            AntibetTheme {
                // Aqui você chamaria seu NavHost (Navigation)
                // Se detectedDomain != null, navegue direto para a tela de "AddSavedBet"
                // com o valor do domínio pré-preenchido.
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun handleVpnAction(enable: Boolean) {
        val intent = Intent(this, AntiBetVpnService::class.java).apply {
            action = if (enable) AntiBetVpnService.ACTION_START else AntiBetVpnService.ACTION_STOP
        }
        if (enable) startForegroundService(intent) else startService(intent)
    }
}