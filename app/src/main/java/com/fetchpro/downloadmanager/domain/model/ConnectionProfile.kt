package com.fetchpro.downloadmanager.domain.model

enum class ConnectionType {
    WIFI,
    MOBILE,
    ROAMING,
    ETHERNET,
    ALL
}

data class ConnectionProfile(
    val type: ConnectionType,
    val maxConcurrent: Int = 3,
    val speedLimitBps: Long = 0, // 0 = unlimited
    val autoStart: Boolean = true,
    val wifiOnly: Boolean = false
) {
    companion object {
        val WIFI_DEFAULT = ConnectionProfile(ConnectionType.WIFI, maxConcurrent = 5, speedLimitBps = 0, autoStart = true)
        val MOBILE_DEFAULT = ConnectionProfile(ConnectionType.MOBILE, maxConcurrent = 2, speedLimitBps = 256 * 1024, autoStart = false, wifiOnly = false)
        val ROAMING_DEFAULT = ConnectionProfile(ConnectionType.ROAMING, maxConcurrent = 1, speedLimitBps = 128 * 1024, autoStart = false)
    }
}

enum class TrafficMode(val label: String, val speedLimitBps: Long) {
    LIGHT("Light (128 KB/s) – for browsing", 128 * 1024L),
    MEDIUM("Medium (512 KB/s)", 512 * 1024L),
    HEAVY("Heavy (Unlimited) – turbo", 0L);

    companion object {
        fun fromBps(bps: Long): TrafficMode {
            return when {
                bps == 0L -> HEAVY
                bps <= 128 * 1024L -> LIGHT
                bps <= 512 * 1024L -> MEDIUM
                else -> HEAVY
            }
        }
    }
}
