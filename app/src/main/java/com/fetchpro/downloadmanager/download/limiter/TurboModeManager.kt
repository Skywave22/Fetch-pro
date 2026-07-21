package com.fetchpro.downloadmanager.download.limiter

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turbo mode + Autostop on low battery - ADM features
 * Turbo: boost download speed by increasing buffer, disabling limits temporarily
 * Battery: autostop when battery < threshold
 */
@Singleton
class TurboModeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val speedLimiterManager: SpeedLimiterManager
) {

    private val _isTurboEnabled = MutableStateFlow(false)
    val isTurboEnabled: StateFlow<Boolean> = _isTurboEnabled

    private val _isBatteryLow = MutableStateFlow(false)
    val isBatteryLow: StateFlow<Boolean> = _isBatteryLow

    private var originalGlobalLimit: Long = 0

    fun enableTurbo() {
        if (_isTurboEnabled.value) return
        _isTurboEnabled.value = true
        // Save original limit
        originalGlobalLimit = speedLimiterManager.getGlobalLimit()
        // Disable speed limit for turbo
        speedLimiterManager.setGlobalLimit(0)
        // In real implementation, also increase buffer size, more connections, etc.
    }

    fun disableTurbo() {
        if (!_isTurboEnabled.value) return
        _isTurboEnabled.value = false
        // Restore original limit
        speedLimiterManager.setGlobalLimit(originalGlobalLimit)
    }

    fun toggleTurbo() {
        if (_isTurboEnabled.value) disableTurbo() else enableTurbo()
    }

    fun checkBatteryLevel(): Int {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level != -1 && scale != -1) {
            (level.toFloat() / scale.toFloat() * 100).toInt()
        } else 100
    }

    fun shouldAutostopOnLowBattery(threshold: Int = 15): Boolean {
        val level = checkBatteryLevel()
        val isLow = level in 1..threshold
        _isBatteryLow.value = isLow
        return isLow
    }

    fun isCharging(): Boolean {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }
}
