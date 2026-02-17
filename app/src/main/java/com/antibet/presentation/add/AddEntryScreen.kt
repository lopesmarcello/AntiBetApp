package com.antibet.presentation.add

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong

@Composable
fun AddEntryScreen(
    isSaved: Boolean,
    domain: String? = null,
    viewModel: AddEntryViewModel,
    onNavigateBack: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var blockDomain by remember { mutableStateOf(false) }

    val title = if (isSaved) "Registrar Economia" else "Registrar Aposta"
    val buttonText = if (isSaved) "Salvar Economia" else "Salvar Aposta"
    val sourceText = if (domain != null) "Fonte: $domain" else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)

        if (sourceText != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(sourceText, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            placeholder = { Text("R$0,00") },
            label = { Text("Valor (em Reais)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Observação (opcional)") },
            modifier = Modifier.fillMaxWidth()
        )

        if (!domain.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = blockDomain,
                    onCheckedChange = { blockDomain = it }
                )
                Text(
                    text = "Bloquear $domain permanentemente",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val clearAmount = amountText.replace(",", ".")
                val amountReais = clearAmount.toFloatOrNull() ?: 0.0f
                val amountCents = amountReais * 100.0f
                if (amountCents > 0) {
                    viewModel.saveEntry(amountCents.roundToLong(), note, isSaved, domain, blockDomain)
                    onNavigateBack()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(buttonText)
        }
    }
}
