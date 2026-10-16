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
