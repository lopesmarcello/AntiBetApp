package com.antibet.presentation.protection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilityProtectionScreen(
    onNavigateBack: () -> Unit,
    viewModel: AccessibilityProtectionViewModel = viewModel()
) {
    val context = LocalContext.current
    val isServiceEnabled by viewModel.isServiceEnabled.collectAsState()

    // Verificar status quando a tela aparecer
    LaunchedEffect(Unit) {
        viewModel.checkServiceStatus(context)
    }

    // Verificar status periodicamente
    LaunchedEffect(key1 = Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000) // Verificar a cada 2 segundos
            viewModel.checkServiceStatus(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Proteção Web") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
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
            // Card de status
            StatusCard(isEnabled = isServiceEnabled)

            Spacer(modifier = Modifier.height(24.dp))

            // Explicação
            ExplanationSection()

            Spacer(modifier = Modifier.height(24.dp))

            // Como funciona
            HowItWorksSection()

            Spacer(modifier = Modifier.height(24.dp))

            // Botão de ação
            ActionButton(
                isEnabled = isServiceEnabled,
                onClick = { viewModel.openAccessibilitySettings(context) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Instruções passo a passo
            if (!isServiceEnabled) {
                StepByStepInstructions()
            }
        }
    }
}

@Composable
private fun StatusCard(isEnabled: Boolean) {
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
                modifier = Modifier.size(48.dp),
                tint = if (isEnabled)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = if (isEnabled) "Proteção Ativa" else "Proteção Desativada",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isEnabled)
                        "Você receberá alertas ao acessar sites de apostas"
                    else
                        "Ative a proteção para receber alertas",
                    style = MaterialTheme.typography.bodyMedium
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
private fun ActionButton(isEnabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isEnabled)
                MaterialTheme.colorScheme.secondary
            else
                MaterialTheme.colorScheme.primary
        )
    ) {
        Text(
            text = if (isEnabled) "Gerenciar Configurações" else "Ativar Proteção",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun StepByStepInstructions() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📋 Instruções",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            val steps = listOf(
                "Toque em 'Ativar Proteção' acima",
                "Encontre 'Anti-Bet' na lista de serviços",
                "Toque em 'Anti-Bet'",
                "Ative o botão 'Usar serviço'",
                "Confirme tocando em 'OK' ou 'Permitir'",
                "Volte para o app - pronto! 🎉"
            )

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
        }
    }
}
