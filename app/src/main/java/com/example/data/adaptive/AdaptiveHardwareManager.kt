package com.example.data.adaptive

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class NetworkTier {
    WIFI_BROADBAND,
    CELLULAR_MOBILE,
    OFFLINE
}

enum class HardwareTier {
    PERFORMANCE_HIGH,
    BALANCED,
    POWER_SAVER_LOW
}

data class HardwareContextState(
    val hardwareTier: HardwareTier = HardwareTier.BALANCED,
    val networkTier: NetworkTier = NetworkTier.WIFI_BROADBAND,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val isSystemPowerSave: Boolean = false,
    val availableRamMb: Long = 2048,
    val isLowMemory: Boolean = false,
    val recommendedModel: String = "gemini-3.5-flash",
    val dynamicContextBudget: Int = 8192,
    val enableLiveGraphPhysics: Boolean = true,
    val autoOfflineFallback: Boolean = true
)

class AdaptiveHardwareManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private val TAG = "AdaptiveHardwareManager"

    private val _hardwareState = MutableStateFlow(HardwareContextState())
    val hardwareState: StateFlow<HardwareContextState> = _hardwareState.asStateFlow()

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            evaluateHardwareState()
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            evaluateHardwareState()
        }
        override fun onLost(network: Network) {
            evaluateHardwareState()
        }
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            evaluateHardwareState()
        }
    }

    fun startMonitoring() {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(Intent.ACTION_DEVICE_STORAGE_LOW)
                addAction(Intent.ACTION_DEVICE_STORAGE_OK)
            }
            context.registerReceiver(batteryReceiver, filter)

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)

            evaluateHardwareState()
            Log.d(TAG, "Adaptive Hardware & Context Monitor started successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed initializing adaptive hardware monitor", e)
        }
    }

    fun stopMonitoring() {
        try {
            context.unregisterReceiver(batteryReceiver)
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping hardware monitor", e)
        }
    }

    fun evaluateHardwareState() {
        coroutineScope.launch(Dispatchers.Default) {
            try {
                // 1. Battery & Power State
                val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
                val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
                val batteryPercent = if (level >= 0 && scale > 0) (level * 100) / scale else 100
                val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                val isSystemPowerSave = powerManager?.isPowerSaveMode ?: false

                // 2. RAM & JVM Memory
                val memInfo = ActivityManager.MemoryInfo()
                activityManager?.getMemoryInfo(memInfo)
                val availableRamMb = memInfo.availMem / (1024 * 1024)
                val isLowMemory = memInfo.lowMemory

                // 3. Network Detection
                val networkTier = determineNetworkTier()

                // 4. Calculate Adaptive Hardware Tier
                val hardwareTier = when {
                    isSystemPowerSave || (batteryPercent <= 20 && !isCharging) || isLowMemory -> {
                        HardwareTier.POWER_SAVER_LOW
                    }
                    isCharging || (batteryPercent > 60 && availableRamMb > 1500) -> {
                        HardwareTier.PERFORMANCE_HIGH
                    }
                    else -> {
                        HardwareTier.BALANCED
                    }
                }

                val recommendedModel = when (hardwareTier) {
                    HardwareTier.POWER_SAVER_LOW -> "gemini-3.5-flash"
                    HardwareTier.BALANCED -> "gemini-3.5-flash"
                    HardwareTier.PERFORMANCE_HIGH -> "gemini-3.1-pro"
                }

                val contextBudget = when (hardwareTier) {
                    HardwareTier.POWER_SAVER_LOW -> 4096
                    HardwareTier.BALANCED -> 8192
                    HardwareTier.PERFORMANCE_HIGH -> 16384
                }

                val enableLivePhysics = hardwareTier != HardwareTier.POWER_SAVER_LOW

                val state = HardwareContextState(
                    hardwareTier = hardwareTier,
                    networkTier = networkTier,
                    batteryPercent = batteryPercent,
                    isCharging = isCharging,
                    isSystemPowerSave = isSystemPowerSave,
                    availableRamMb = availableRamMb,
                    isLowMemory = isLowMemory,
                    recommendedModel = recommendedModel,
                    dynamicContextBudget = contextBudget,
                    enableLiveGraphPhysics = enableLivePhysics,
                    autoOfflineFallback = true
                )

                _hardwareState.value = state
                Log.d(TAG, "Adaptive State: Tier=${hardwareTier.name}, Network=${networkTier.name}, Battery=$batteryPercent% (Charging=$isCharging), Budget=$contextBudget")
            } catch (e: Exception) {
                Log.e(TAG, "Error evaluating adaptive hardware state", e)
            }
        }
    }

    private fun determineNetworkTier(): NetworkTier {
        val cm = connectivityManager ?: return NetworkTier.OFFLINE
        val activeNet = cm.activeNetwork ?: return NetworkTier.OFFLINE
        val caps = cm.getNetworkCapabilities(activeNet) ?: return NetworkTier.OFFLINE

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                NetworkTier.WIFI_BROADBAND
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                NetworkTier.CELLULAR_MOBILE
            }
            else -> {
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) NetworkTier.CELLULAR_MOBILE else NetworkTier.OFFLINE
            }
        }
    }
}
