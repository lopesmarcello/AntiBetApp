package com.antibet.presentation.protection

import android.app.Activity
import android.app.Activity.RESULT_OK
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilityProtectionScreen(
    onNavigateBack: () -> Unit,
    viewModel: AccessibilityProtectionViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isServiceEnabled by viewModel.isServiceEnabled.collectAsState()
    val hasNavigatedToSettings by viewModel.hasNavigatedToSettings.collectAsState()
    val isNotificationPermissionGranted by viewModel.isNotificationPermissionGranted.collectAsState()
    val isVpnActive by viewModel.isVpnActive.collectAsState()

    // Launcher for the Android VPN consent dialog
    val vpnConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // User granted consent — start the VPN service
            viewModel.startVpnFilter(context)
        }
    }

    // Initial status check
    LaunchedEffect(Unit) {
        viewModel.checkServiceStatus(context)
        viewModel.checkNotificationPermission(context)
    }

    // Re-check on resume (user may have returned from system settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (hasNavigatedToSettings) {
                    viewModel.checkServiceStatusWithDelay(context)
                    viewModel.checkNotificationPermission(context)
                    viewModel.resetNavigationFlag()
                } else {
                    viewModel.checkServiceStatusWithDelay(context)
                    viewModel.checkNotificationPermission(context)
                }
                viewModel.refreshVpnState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Proteção Web") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Accessibility status
            AccessibilityStatusCard(isEnabled = isServiceEnabled)

            Spacer(modifier = Modifier.height(12.dp))

            // Notification status
            NotificationStatusCard(isEnabled = isNotificationPermissionGranted)

            Spacer(modifier = Modifier.height(12.dp))

            // VPN / DNS filter status
            VpnStatusCard(isActive = isVpnActive)

            Spacer(modifier = Modifier.height(24.dp))

            ExplanationSection()

            Spacer(modifier = Modifier.height(24.dp))

            HowItWorksSection()

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            PermissionButtons(
                isAccessibilityEnabled = isServiceEnabled,
                isNotificationEnabled = isNotificationPermissionGranted,
                isVpnActive = isVpnActive,
                onAccessibilityClick = { viewModel.openAccessibilitySettings(context) },
                onNotificationClick = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        val activity = context as? Activity
                        if (activity != null) {
                            viewModel.requestNotificationPermission(activity)
                        } else {
                            viewModel.openNotificationSettings(context)
                        }
                    } else {
                        viewModel.openNotificationSettings(context)
                    }
                },
                onVpnToggle = {
                    if (isVpnActive) {
                        viewModel.stopVpnFilter(context)
                    } else {
                        val consentIntent = viewModel.prepareVpn(context)
                        if (consentIntent != null) {
                            // Android needs one-time user consent
                            vpnConsentLauncher.launch(consentIntent)
                        } else {
                            // Already consented — start directly
                            viewModel.startVpnFilter(context)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom card: instructions or success
            val fullyProtected = isServiceEnabled && isNotificationPermissionGranted && isVpnActive
            if (!isServiceEnabled || !isNotificationPermissionGranted) {
                InstructionCard(
                    needsAccessibility = !isServiceEnabled,
                    needsNotification = !isNotificationPermissionGranted
                )
            } else if (fullyProtected) {
                FullProtectionCard()
            } else {
                PartialProtectionCard(isVpnActive = isVpnActive)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Status cards
// ---------------------------------------------------------------------------

@Composable
private fun AccessibilityStatusCard(isEnabled: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (isEnabled) "Monitoramento Ativo" else "Monitoramento Inativo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isEnabled) "Detectando e notificando sites de apostas"
                           else "Ative para começar a monitorar",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun NotificationStatusCard(isEnabled: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isEnabled) Icons.Default.CheckCircle else Icons.Default.Notifications,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (isEnabled) "Notificações Permitidas" else "Notificações Bloqueadas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isEnabled) "Você receberá alertas ao visitar sites"
                           else "Permita para receber alertas",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun VpnStatusCard(isActive: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (isActive) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (isActive) "Bloqueio DNS Ativo" else "Bloqueio DNS Inativo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isActive)
                        "Sites bloqueados não carregam — 100% local, sem servidores externos"
                    else
                        "Ative para impedir que sites bloqueados carreguem",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Action buttons
// ---------------------------------------------------------------------------

@Composable
private fun PermissionButtons(
    isAccessibilityEnabled: Boolean,
    isNotificationEnabled: Boolean,
    isVpnActive: Boolean,
    onAccessibilityClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onVpnToggle: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Accessibility button
        Button(
            onClick = onAccessibilityClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isAccessibilityEnabled)
                    MaterialTheme.colorScheme.secondary
                else
                    MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = if (isAccessibilityEnabled) Icons.Default.CheckCircle
                              else Icons.Default.Warning,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isAccessibilityEnabled) "Gerenciar Monitoramento"
                       else "Ativar Monitoramento",
                style = MaterialTheme.typography.titleMedium
            )
        }

        // Notification button (only shown if needed)
        if (!isNotificationEnabled) {
            Button(
                onClick = onNotificationClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(imageVector = Icons.Default.Notifications, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Permitir Notificações",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        // VPN / DNS filter toggle — always visible so user can turn it on/off
        Button(
            onClick = onVpnToggle,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isVpnActive)
                    MaterialTheme.colorScheme.secondary
                else
                    MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = if (isVpnActive) Icons.Default.CheckCircle
                              else Icons.Default.Warning,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isVpnActive) "Desativar Bloqueio DNS"
                       else "Ativar Bloqueio DNS",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Info cards
// ---------------------------------------------------------------------------

@Composable
private fun ExplanationSection() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Proteção em duas camadas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "O app combina duas tecnologias complementares para uma proteção completa:",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "1. Monitoramento (Acessibilidade)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Detecta sites de apostas no navegador e envia alertas. Permite registrar economias diretamente da notificação.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "2. Bloqueio DNS (VPN local)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Impede que sites da sua lista de bloqueio carreguem. O bloqueio acontece no próprio celular — nenhum dado passa por servidores externos.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Vantagens:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            listOf(
                "Velocidade de navegação inalterada",
                "Consumo mínimo de bateria",
                "Privacidade total — dados não saem do celular",
                "Funciona com todos os navegadores e apps"
            ).forEach { item ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("• ", color = MaterialTheme.colorScheme.primary)
                    Text(item, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun HowItWorksSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Como funciona o Bloqueio DNS?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Quando você tenta acessar um site bloqueado, o app intercepta a consulta DNS no próprio celular e retorna 'domínio não encontrado'. O navegador mostra o erro padrão de 'site não encontrado' — sem flash de página, sem redirecionamento.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "O app NÃO:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(4.dp))
            listOf(
                "Lentifica sua navegação",
                "Envia tráfego para servidores externos",
                "Coleta ou armazena histórico de navegação",
                "Interfere em sites que não estão na lista"
            ).forEach { item ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("✗ ", color = MaterialTheme.colorScheme.error)
                    Text(item, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun InstructionCard(
    needsAccessibility: Boolean,
    needsNotification: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Siga estes passos:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            val steps = mutableListOf<String>()
            if (needsAccessibility) {
                steps.addAll(listOf(
                    "Toque em 'Ativar Monitoramento' acima",
                    "Nas configurações de Acessibilidade, procure 'AntiBet'",
                    "Ative o serviço e confirme com 'OK'"
                ))
            }
            if (needsNotification) {
                if (steps.isNotEmpty()) steps.add("Volte para o app")
                steps.addAll(listOf(
                    "Toque em 'Permitir Notificações'",
                    "Ative as notificações do AntiBet"
                ))
            }
            steps.add("Volte para este app — pronto!")

            steps.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "${index + 1}. ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(text = step, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Dica: Você pode fechar e voltar a qualquer momento.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun PartialProtectionCard(isVpnActive: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Monitoramento ativo!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Você receberá alertas ao visitar sites de apostas. Para também bloquear o acesso, ative o Bloqueio DNS acima.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FullProtectionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Proteção Completa Ativada!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Monitoramento ativo e bloqueio DNS habilitado. Sites da sua lista não carregarão. Continue focado no seu objetivo!",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Gerencie os sites bloqueados na aba 'Bloqueados'.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
