package com.antibet.presentation

import android.app.ComponentCaller
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.antibet.presentation.navigation.AntiBetNavigation
import com.antibet.presentation.theme.AntibetTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()


        val shouldNavigateToAddSaving = intent?.action == "ACTION_ADD_SAVING"
        val detectedDomain = intent?.getStringExtra("EXTRA_DETECTED_DOMAIN")

        setContent {
            AntibetTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AntiBetNavigation(
                        navigateToAddSaving = shouldNavigateToAddSaving,
                        detectedDomain = detectedDomain
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent, caller: ComponentCaller) {
        super.onNewIntent(intent, caller)
        setIntent(intent)
        if (intent?.action == "ACTION_ADD_SAVING"){
            // posso navegar
        }
    }
}
