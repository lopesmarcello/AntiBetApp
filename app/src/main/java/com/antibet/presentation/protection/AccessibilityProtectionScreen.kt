package com.antibet.presentation.protection

import android.app.Activity
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

    // Verificar status inicial quando a tela aparecer
    LaunchedEffect(Unit) {
        viewModel.checkServiceStatus(context)
        viewModel.checkNotificationPermission(context)
    }

    // Observar lifecycle para detectar quando usuário volta das configurações
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Usuário voltou para o app (possivelmente das configurações)
                if (hasNavigatedToSettings) {
                    viewModel.checkServiceStatusWithDelay(context)
                    viewModel.checkNotificationPermission(context)
                    viewModel.resetNavigationFlag()
                } else {
                    viewModel.checkServiceStatusWithDelay(context)
                    viewModel.checkNotificationPermission(context)
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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
            // Card de status de acessibilidade
            AccessibilityStatusCard(isEnabled = isServiceEnabled)

            Spacer(modifier = Modifier.height(16.dp))

            // Card de status de notificações
            NotificationStatusCard(isEnabled = isNotificationPermissionGranted)

            Spacer(modifier = Modifier.height(24.dp))

            // Explicação
            ExplanationSection()

            Spacer(modifier = Modifier.height(24.dp))

            // Como funciona
            HowItWorksSection()

            Spacer(modifier = Modifier.height(24.dp))

            // Botões de ação
            PermissionButtons(
                isAccessibilityEnabled = isServiceEnabled,
                isNotificationEnabled = isNotificationPermissionGranted,
                onAccessibilityClick = {
                    viewModel.openAccessibilitySettings(context)
                },
                onNotificationClick = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        // Para Android 13+, tenta solicitar permissão
                        val activity = context as? Activity
                        if (activity != null) {
                            viewModel.requestNotificationPermission(activity)
                        } else {
                            // Fallback: abre configurações
                            viewModel.openNotificationSettings(context)
                        }
                    } else {
                        // Para Android < 13, abre configurações
                        viewModel.openNotificationSettings(context)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Instruções ou card de sucesso
            if (!isServiceEnabled || !isNotificationPermissionGranted) {
                InstructionCard(
                    needsAccessibility = !isServiceEnabled,
                    needsNotification = !isNotificationPermissionGranted
                )
            } else {
                SuccessCard()
            }
        }
    }
}

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
                tint = if (isEnabled)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = if (isEnabled) "🛡️ Monitoramento Ativo" else "🛡️ Monitoramento Inativo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isEnabled)
                        "Detectando sites de apostas"
                    else
                        "Ative para começar a monitorar",
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
                tint = if (isEnabled)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = if (isEnabled) "🔔 Notificações Permitidas" else "🔔 Notificações Bloqueadas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isEnabled)
                        "Você receberá alertas"
                    else
                        "Permita para receber alertas",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun PermissionButtons(
    isAccessibilityEnabled: Boolean,
    isNotificationEnabled: Boolean,
    onAccessibilityClick: () -> Unit,
    onNotificationClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Botão de Acessibilidade
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
                imageVector = if (isAccessibilityEnabled)
                    Icons.Default.CheckCircle
                else
                    Icons.Default.Warning,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isAccessibilityEnabled)
                    "Gerenciar Monitoramento"
                else
                    "Ativar Monitoramento",
                style = MaterialTheme.typography.titleMedium
            )
        }

        // Botão de Notificações (só aparece se não estiver permitido)
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
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Permitir Notificações",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun ExplanationSection() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🛡️ Proteção Inteligente",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Esta proteção monitora os sites que você visita e envia uma notificação amigável quando detecta que você está acessando um site de apostas.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "✅ Vantagens:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            val advantages = listOf(
                "Navegação rápida e sem lentidão",
                "Consumo mínimo de bateria",
                "Privacidade total - dados não saem do celular",
                "Funciona com todos os navegadores"
            )

            advantages.forEach { advantage ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("• ", color = MaterialTheme.colorScheme.primary)
                    Text(advantage, style = MaterialTheme.typography.bodySmall)
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
                text = "🔍 Como Funciona?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "O app usa um recurso do Android chamado 'Serviço de Acessibilidade' para observar discretamente o navegador. Quando você visita um site de apostas, você recebe uma notificação gentil para te lembrar do seu objetivo.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "⚠️ O app NÃO:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(4.dp))

            val notDoes = listOf(
                "Bloqueia ou interfere na navegação",
                "Deixa seu navegador lento",
                "Coleta ou armazena seu histórico",
                "Envia dados para servidores"
            )

            notDoes.forEach { item ->
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
                text = "📋 Siga estes passos:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            val steps = mutableListOf<String>()

            if (needsAccessibility) {
                steps.addAll(listOf(
                    "Toque em 'Ativar Monitoramento' acima",
                    "Você será levado às configurações de Acessibilidade",
                    "Procure por 'Anti-Bet' ou 'Anti-Bet Proteção Web'",
                    "Toque no serviço e ative o botão",
                    "Confirme tocando em 'OK' ou 'Permitir'"
                ))
            }

            if (needsNotification) {
                if (steps.isNotEmpty()) {
                    steps.add("Volte para este app usando o botão voltar ◀")
                }
                steps.addAll(listOf(
                    "Toque em 'Permitir Notificações' acima",
                    "Ative as notificações do Anti-Bet"
                ))
            }

            if (!needsAccessibility && !needsNotification) {
                steps.add("Pronto! 🎉")
            } else {
                steps.add("Volte para este app - tudo certo! 🎉")
            }

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
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "💡 Dica: Não se preocupe se demorar um pouco. Você pode fechar e voltar para este app quando quiser.",
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
private fun SuccessCard() {
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
                text = "🎉",
                style = MaterialTheme.typography.displayLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Proteção Ativada com Sucesso!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Agora você receberá notificações sempre que acessar um site de apostas. Continue focado no seu objetivo! 💪",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Você pode desativar ou reconfigurar a qualquer momento tocando em 'Gerenciar Monitoramento' acima.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}