package com.antibet.presentation.protection

import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.antibet.service.dns.BettingProtectionService

@Composable
fun ProtectionScreen(
    isVpnRunning: Boolean,
    onToggleVpn: (Boolean) -> Unit
) {
    val context = LocalContext.current
    
    var protectionActive by remember { mutableStateOf(isVpnRunning) }

    val vpnPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            startProtectionService(context)
            protectionActive = true
            onToggleVpn(true)
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Proteção contra Apostas", style = MaterialTheme.typography.titleLarge)
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Detectamos quando você tenta acessar sites de apostas e notificamos.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Switch(
            checked = protectionActive,
            onCheckedChange = { enabled ->
                if (enabled) {
                    val vpnIntent = VpnService.prepare(context)
                    if (vpnIntent != null) {
                        vpnPermissionLauncher.launch(vpnIntent)
                    } else {
                        startProtectionService(context)
                        protectionActive = true
                        onToggleVpn(true)
                    }
                } else {
                    stopProtectionService(context)
                    protectionActive = false
                    onToggleVpn(false)
                }
            }
        )

        if (protectionActive) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Proteção ativa! Monitorando acessos a sites de aposta.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            "Requisitos: Android 13+",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun startProtectionService(context: android.content.Context) {
    val intent = Intent(context, BettingProtectionService::class.java).apply {
        action = BettingProtectionService.ACTION_START
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun stopProtectionService(context: android.content.Context) {
    val intent = Intent(context, BettingProtectionService::class.java).apply {
        action = BettingProtectionService.ACTION_STOP
    }
    context.startService(intent)
}
