package com.cyplixlauncher.mc.feature.turtle

import com.cyplixlauncher.mc.feature.log.Logging
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

object PingTracker {
    private const val TAG = "PingTracker"
    private const val TIMEOUT_MS = 1500
    private const val INTERVAL_MS = 2000L
    private const val MINECRAFT_PORT = 25565
    /** Connections the launcher itself makes (HTTPS, DNS, ...) are never treated as a game server. */
    private val IGNORED_PORTS = setOf(53, 80, 443, 5353)

    @Volatile private var running = false
    private val lastPingMs = AtomicInteger(-1)
    private val currentTarget = AtomicReference<Pair<String, Int>?>(null)
    private var worker: Thread? = null

    /** Call when the player connects to / switches servers. host may be "host:port" or bare host (defaults 25565). */
    @JvmStatic
    fun setTarget(address: String?) {
        if (address.isNullOrBlank()) {
            currentTarget.set(null)
            lastPingMs.set(-1)
            return
        }
        val parts = address.trim().split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 25565
        currentTarget.set(host to port)
    }

    @JvmStatic
    fun start() {
        if (running) return
        running = true
        worker = thread(name = "TurtlePingTracker", isDaemon = true) {
            while (running) {
                val target = currentTarget.get() ?: detectConnectedServer()
                if (target == null) {
                    lastPingMs.set(-1)
                } else {
                    lastPingMs.set(measure(target.first, target.second))
                }
                try {
                    Thread.sleep(INTERVAL_MS)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
    }

    @JvmStatic
    fun stop() {
        running = false
        worker?.interrupt()
        worker = null
    }

    /** Returns last measured round-trip in ms, or -1 if unknown/unreachable. */
    @JvmStatic
    fun getPingMs(): Int = lastPingMs.get()

    /**
     * Finds the server the game is currently connected to by reading the established TCP
     * connections of this app from /proc/net/tcp(6). Prefers the Minecraft port.
     */
    private fun detectConnectedServer(): Pair<String, Int>? {
        var best: Pair<String, Int>? = null
        for (path in listOf("/proc/net/tcp", "/proc/net/tcp6")) {
            val file = File(path)
            if (!file.canRead()) continue
            runCatching {
                file.useLines { lines ->
                    lines.drop(1).forEach { line ->
                        val cols = line.trim().split(Regex("\\s+"))
                        // column 3 is the state; "01" means ESTABLISHED
                        if (cols.size < 4 || cols[3] != "01") return@forEach
                        val parts = cols[2].split(":")
                        if (parts.size != 2) return@forEach
                        val port = parts[1].toIntOrNull(16) ?: return@forEach
                        if (port in IGNORED_PORTS) return@forEach
                        val ip = parseProcAddress(parts[0]) ?: return@forEach
                        if (ip.isLoopbackAddress || ip.isAnyLocalAddress) return@forEach
                        val candidate = ip.hostAddress to port
                        if (port == MINECRAFT_PORT || best == null) best = candidate
                    }
                }
            }.onFailure { Logging.i(TAG, "Could not read ${file.path}: ${it.message}") }
        }
        return best
    }

    /** /proc stores each 32-bit word of an address in little-endian byte order. */
    private fun parseProcAddress(hex: String): InetAddress? = runCatching {
        val raw = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val fixed = ByteArray(raw.size)
        for (word in raw.indices step 4) {
            for (i in 0 until 4) fixed[word + i] = raw[word + 3 - i]
        }
        InetAddress.getByAddress(fixed)
    }.getOrNull()

    private fun measure(host: String, port: Int): Int {
        return try {
            val start = System.nanoTime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)
            }
            ((System.nanoTime() - start) / 1_000_000L).toInt()
        } catch (e: Exception) {
            Logging.i(TAG, "Ping failed for $host:$port — ${e.message}")
            -1
        }
    }
}
