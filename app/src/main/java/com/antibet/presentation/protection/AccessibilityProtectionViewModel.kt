package com.antibet.presentation.protection

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para gerenciar o AccessibilityService de proteção web
 */
class AccessibilityProtectionViewModel : ViewModel() {

    private val _isServiceEnabled = MutableStateFlow(false)
    val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

    private val _hasNavigatedToSettings = MutableStateFlow(false)
    val hasNavigatedToSettings: StateFlow<Boolean> = _hasNavigatedToSettings.asStateFlow()

    private val _isNotificationPermissionGranted = MutableStateFlow(true) // true por padrão para APIs antigas
    val isNotificationPermissionGranted: StateFlow<Boolean> = _isNotificationPermissionGranted.asStateFlow()


    fun checkNotificationPermission(context: Context) {
        viewModelScope.launch {
            _isNotificationPermissionGranted.value = hasNotificationPermission(context)
        }
    }

    fun checkServiceStatus(context: Context) {
        viewModelScope.launch {
            _isServiceEnabled.value = isAccessibilityServiceEnabled(context)
        }
    }
    fun checkServiceStatusWithDelay(context: Context) {
        viewModelScope.launch {
            // Aguardar um pouco antes de verificar (usuário pode estar voltando das configurações)
            delay(500)
            _isServiceEnabled.value = isAccessibilityServiceEnabled(context)
        }
    }

    fun openAccessibilitySettings(context: Context) {
        _hasNavigatedToSettings.value = true
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun openNotificationSettings(context: Context) {
        _hasNavigatedToSettings.value = true
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    fun resetNavigationFlag() {
        _hasNavigatedToSettings.value = false
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val service = "${context.packageName}/com.antibet.service.accessibility.WebMonitorAccessibilityService"

        try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            return enabledServices.contains(service)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Em versões antigas do Android, notificações estão sempre permitidas
            true
        }
    }

    companion object {
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }
}