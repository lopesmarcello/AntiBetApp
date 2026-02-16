package com.antibet.presentation.protection

import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun ProtectionScreen(
    isVpnRunning: Boolean,
    onToggleVpn: (Boolean) -> Unit
) {
    val context = LocalContext.current

    // Launcher para a permissão de sistema do Android VPN
    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            onToggleVpn(true)
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Proteção Ativa", style = MaterialTheme.typography.titleLarge)
        Text("Receba alertas ao acessar sites de apostas conhecidos.")

        Switch(
            checked = isVpnRunning,
            onCheckedChange = { enabled ->
                if (enabled) {
                    val intent = VpnService.prepare(context)
                    if (intent != null) {
                        vpnLauncher.launch(intent)
                    } else {
                        onToggleVpn(true)
                    }
                } else {
                    onToggleVpn(false)
                }
            }
        )
    }
}