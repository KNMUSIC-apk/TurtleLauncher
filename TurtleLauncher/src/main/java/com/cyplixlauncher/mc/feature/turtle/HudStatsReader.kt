package com.cyplixlauncher.mc.feature.turtle

import java.io.File

/**
 * Reads the numbers shown on the in-game HUD card (CPU, GPU, RAM).
 *
 * Every source here is best-effort: Android restricts sysfs/procfs per device, so each reader
 * returns null (or -1 for CPU until two samples exist) instead of throwing. The HUD shows "--".
 */
object HudStatsReader {
    private var lastTotal = 0L
    private var lastIdle = 0L
    private var cpuPercent: Int? = null

    /** Overall CPU busy percentage since the previous call, or null if /proc/stat is unreadable. */
    @Synchronized
    fun cpuUsagePercent(): Int? {
        val line = runCatching { File("/proc/stat").useLines { it.firstOrNull() } }.getOrNull()
            ?: return cpuPercent
        val fields = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (fields.size < 5) return cpuPercent
        val total = fields.sum()
        val idle = fields[3] + fields[4] // idle + iowait
        val dTotal = total - lastTotal
        val dIdle = idle - lastIdle
        if (lastTotal != 0L && dTotal > 0) {
            cpuPercent = (100L * (dTotal - dIdle) / dTotal).toInt().coerceIn(0, 100)
        }
        lastTotal = total
        lastIdle = idle
        return cpuPercent
    }

    /** Hottest CPU-ish thermal zone in degrees Celsius, or null. */
    fun cpuTempC(): Int? = thermalTempC { it.contains("cpu") || it.contains("tsens") || it.contains("soc") }

    /** Battery temperature in degrees Celsius from the sticky battery broadcast, or null. */
    fun batteryTempC(context: android.content.Context): Int? {
        val intent = runCatching {
            context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return null
        val tenths = intent.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return if (tenths == Int.MIN_VALUE) null else tenths / 10
    }

    /** A sensor whose raw value has not moved for this long is treated as stuck (e.g. frozen at 75 °C). */
    private const val STUCK_MS = 60_000L
    private val zoneChangedAt = HashMap<String, Long>()
    private val zoneRawSeen = HashMap<String, Long>()

    /**
     * Device temperature in °C: the hottest plausible thermal zone that is still changing,
     * falling back to the battery sensor when no live zone is readable.
     * Zones frozen at one value (a stuck sensor) are ignored so they cannot pin the reading.
     */
    fun deviceTempC(context: android.content.Context): Int? {
        val now = System.currentTimeMillis()
        val zones = readAllThermalZones()
        val live = synchronized(this) {
            for (z in zones) {
                if (zoneRawSeen[z.name] != z.raw) {
                    zoneRawSeen[z.name] = z.raw
                    zoneChangedAt[z.name] = now
                }
                zoneChangedAt.putIfAbsent(z.name, now)
            }
            zones.filter { z ->
                z.tempC in 10..100 && now - (zoneChangedAt[z.name] ?: now) < STUCK_MS
            }
        }
        return live.maxOfOrNull { it.tempC } ?: batteryTempC(context)
    }

    private data class ThermalZone(val name: String, val raw: Long, val tempC: Int)

    private fun readAllThermalZones(): List<ThermalZone> {
        val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") } ?: return emptyList()
        return zones.mapNotNull { zone ->
            val raw = runCatching { File(zone, "temp").readText().trim().toLong() }.getOrNull()
                ?: return@mapNotNull null
            // Millidegrees on most kernels; some report whole degrees already.
            ThermalZone(zone.name, raw, (if (raw > 1000) raw / 1000 else raw).toInt())
        }
    }

    /** GPU thermal zone in degrees Celsius, or null. */
    fun gpuTempC(): Int? = thermalTempC { it.contains("gpu") }

    /** GPU busy percentage. Only Adreno (kgsl) exposes this in a common place; otherwise null. */
    fun gpuUsagePercent(): Int? {
        return runCatching {
            File("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage").readText().trim().removeSuffix("%").trim().toInt()
        }.getOrNull()?.coerceIn(0, 100)
    }

    private fun thermalTempC(match: (String) -> Boolean): Int? {
        val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") } ?: return null
        val temps = zones.mapNotNull { zone ->
            val type = runCatching { File(zone, "type").readText().trim().lowercase() }.getOrNull()
                ?: return@mapNotNull null
            if (!match(type)) return@mapNotNull null
            val raw = runCatching { File(zone, "temp").readText().trim().toLong() }.getOrNull()
                ?: return@mapNotNull null
            // Most kernels report millidegrees; a few report whole degrees already.
            if (raw > 1000) raw / 1000 else raw
        }
        return temps.maxOrNull()?.toInt()
    }
}
