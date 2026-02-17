package com.antibet.presentation.protection

import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.antibet.service.vpn.AntiBetVpnService

@Composable
fun ProtectionScreen(
    isVpnRunning: Boolean,
    onToggleVpn: (Boolean) -> Unit
) {
    val context = LocalContext.current

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            startVpnService(context)
            onToggleVpn(true)
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Proteção Ativa", style = MaterialTheme.typography.titleLarge)
        Text("Receba alertas ao acessar sites de apostas conhecidos.")

        Spacer(modifier = Modifier.height(16.dp))

        Switch(
            checked = isVpnRunning,
            onCheckedChange = { enabled ->
                if (enabled) {
                    val intent = VpnService.prepare(context)
                    if (intent != null) {
                        vpnLauncher.launch(intent)
                    } else {
                        startVpnService(context)
                        onToggleVpn(true)
                    }
                } else {
                    stopVpnService(context)
                    onToggleVpn(false)
                }
            }
        )

        if (isVpnRunning) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "VPN está ativo e monitorando acessos a sites de aposta.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun startVpnService(context: android.content.Context) {
    val intent = Intent(context, AntiBetVpnService::class.java).apply {
        action = AntiBetVpnService.ACTION_START
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun stopVpnService(context: android.content.Context) {
    val intent = Intent(context, AntiBetVpnService::class.java).apply {
        action = AntiBetVpnService.ACTION_STOP
    }
    context.startService(intent)
}