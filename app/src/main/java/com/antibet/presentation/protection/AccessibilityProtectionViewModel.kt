package com.antibet.presentation.protection

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antibet.service.vpn.AntiBetVpnService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para gerenciar o AccessibilityService de proteção web
 * e o filtro DNS local (VPN).
 */
class AccessibilityProtectionViewModel : ViewModel() {

    private val _isServiceEnabled = MutableStateFlow(false)
    val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

    private val _hasNavigatedToSettings = MutableStateFlow(false)
    val hasNavigatedToSettings: StateFlow<Boolean> = _hasNavigatedToSettings.asStateFlow()

    private val _isNotificationPermissionGranted = MutableStateFlow(true)
    val isNotificationPermissionGranted: StateFlow<Boolean> = _isNotificationPermissionGranted.asStateFlow()

    private val _isVpnActive = MutableStateFlow(false)
    val isVpnActive: StateFlow<Boolean> = _isVpnActive.asStateFlow()

    // -------------------------------------------------------------------------
    // Accessibility service
    // -------------------------------------------------------------------------

    fun checkNotificationPermission(context: Context) {
        viewModelScope.launch {
            _isNotificationPermissionGranted.value = hasNotificationPermission(context)
        }
    }

    fun checkServiceStatus(context: Context) {
        viewModelScope.launch {
            _isServiceEnabled.value = isAccessibilityServiceEnabled(context)
            _isVpnActive.value = AntiBetVpnService.isRunning
        }
    }

    fun checkServiceStatusWithDelay(context: Context) {
        viewModelScope.launch {
            delay(500)
            _isServiceEnabled.value = isAccessibilityServiceEnabled(context)
            _isVpnActive.value = AntiBetVpnService.isRunning
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

    // -------------------------------------------------------------------------
    // VPN / DNS filter
    // -------------------------------------------------------------------------

    /**
     * Returns a VpnService.prepare() Intent if the user still needs to grant
     * VPN consent, or null if consent was already given.
     * The caller must launch the intent with startActivityForResult and, on
     * RESULT_OK, call [startVpnFilter].
     */
    fun prepareVpn(context: Context): Intent? = VpnService.prepare(context)

    fun startVpnFilter(context: Context) {
        val intent = Intent(context, AntiBetVpnService::class.java).apply {
            action = AntiBetVpnService.ACTION_START
        }
        context.startService(intent)
        _isVpnActive.value = true
    }

    fun stopVpnFilter(context: Context) {
        val intent = Intent(context, AntiBetVpnService::class.java).apply {
            action = AntiBetVpnService.ACTION_STOP
        }
        context.startService(intent)
        _isVpnActive.value = false
    }

    fun refreshVpnState() {
        _isVpnActive.value = AntiBetVpnService.isRunning
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val service = "${context.packageName}/com.antibet.service.accessibility.WebMonitorAccessibilityService"
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            enabledServices.contains(service)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    companion object {
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }
}