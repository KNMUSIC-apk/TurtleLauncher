package com.endiq.turtlelauncher.feature.ai

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.feature.turtle.FpsMonitor
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.setting.AllStaticSettings
import com.google.gson.JsonParser
import net.endiq.launcher.Tools
import java.io.File
import kotlin.concurrent.thread

/**
 * Asks the AI for a set of optimization switches and a resolution, based on this device and the FPS
 * the game recently reached. Without a key, offline, or with an unusable reply, it falls back to
 * hardware rules. Nothing changes until the player taps Apply on the plan.
 */
object AiOptimizer {
    private const val TAG = "AiOptimizer"
    private val mainHandler = Handler(Looper.getMainLooper())

    data class Plan(
        val resolutionRatio: Int,
        val optRenderDistance: Boolean,
        val optEntityDetail: Boolean,
        val optEffects: Boolean,
        val unlimitedFps: Boolean,
        val reason: String,
        val fromAi: Boolean
    )

    private val systemPrompt = """
        Bạn là bộ tối ưu Minecraft Java trên điện thoại Android.
        Chỉ trả về MỘT đối tượng JSON, không thêm chữ nào khác, với đúng các khóa:
        - resolutionRatio: số nguyên từ 50 đến 100, bội số của 5 (độ phân giải cửa sổ game, %)
        - optRenderDistance: true hoặc false (giảm khoảng cách hiển thị xuống 6 chunk; tăng FPS nhiều nhất)
        - optEntityDetail: true hoặc false (giảm khoảng cách và bóng của vật thể)
        - optEffects: true hoặc false (ít hạt, đồ họa nhanh, pha màu thấp)
        - unlimitedFps: true hoặc false (bỏ giới hạn FPS; chỉ bật khi máy mát và RAM cao)
        - reason: giải thích bằng tiếng Việt, tối đa 2 câu, nêu lý do chính.
        Nguyên tắc: nếu FPS gần đây thấp, máy nóng hoặc RAM thấp thì giảm độ phân giải và bật các tối ưu.
        Nếu FPS gần đây trên 55 và máy mát, có thể giữ hoặc tăng độ phân giải và tắt các tối ưu không cần.
    """.trimIndent()

    /** Entry point. Explains what is sent, analyses in the background, then shows the plan. */
    @JvmStatic
    fun start(context: Context, onApplied: Runnable) {
        AlertDialog.Builder(context, R.style.CustomAlertDialogTheme)
            .setTitle(R.string.ai_optimize_title)
            .setMessage(R.string.ai_optimize_consent)
            .setPositiveButton(R.string.ai_optimize_analyze) { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(context, R.string.ai_optimize_busy, Toast.LENGTH_SHORT).show()
                thread(name = "TurtleAiOptimizer", isDaemon = true) {
                    val plan = runCatching { recommend(context) }.getOrElse { rulesPlan(context) }
                    mainHandler.post { showPlan(context, plan, onApplied) }
                }
            }
            .setNegativeButton(R.string.ai_optimize_cancel, null)
            .show()
    }

    /** Applies a plan to the settings. The game reads the optimization switches at its next launch. */
    @JvmStatic
    fun apply(plan: Plan) {
        AllSettings.resolutionRatio.put(plan.resolutionRatio).save()
        AllStaticSettings.scaleFactor = plan.resolutionRatio / 100f
        AllSettings.optRenderDistance.put(plan.optRenderDistance).save()
        AllSettings.optEntityDetail.put(plan.optEntityDetail).save()
        AllSettings.optEffects.put(plan.optEffects).save()
        AllSettings.unlimitedFps.put(plan.unlimitedFps).save()
        Logging.i(TAG, "Applied plan (fromAi=${plan.fromAi}): ${plan.reason}")
    }

    private fun recommend(context: Context): Plan {
        if (TurtleAiGemini.apiKey().isEmpty()) return rulesPlan(context)
        val ram = totalRamMb()
        val thermal = thermalStatus(context)
        // Unlimited FPS heats the phone, so the AI may only suggest it on a cool phone with plenty of RAM.
        val allowUnlimited = thermal in 0..1 && ram > 8500
        val reply = runCatching {
            TurtleAiGemini.complete(systemPrompt, deviceProfile(context), temperature = 0.2, maxOutputTokens = 400)
        }.getOrNull()
        val plan = reply?.let { parsePlan(it, allowUnlimited) }
        if (plan == null) Logging.w(TAG, "AI reply unusable or unreachable, using hardware rules")
        return plan ?: rulesPlan(context)
    }

    private fun parsePlan(reply: String, allowUnlimited: Boolean): Plan? {
        return runCatching {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = JsonParser.parseString(reply.substring(start, end + 1)).asJsonObject
        fun flag(key: String, default: Boolean): Boolean = obj.get(key)?.asBoolean ?: default
        val ratio = obj.get("resolutionRatio")?.asInt ?: 100
        Plan(
            resolutionRatio = (ratio.coerceIn(50, 100) / 5) * 5,
            optRenderDistance = flag("optRenderDistance", false),
            optEntityDetail = flag("optEntityDetail", false),
            optEffects = flag("optEffects", false),
            unlimitedFps = allowUnlimited && flag("unlimitedFps", false),
            reason = obj.get("reason")?.asString?.take(300).orEmpty(),
            fromAi = true
        )
        }.getOrNull()
    }

    private fun rulesPlan(context: Context): Plan {
        val ram = totalRamMb()
        val thermal = thermalStatus(context)
        val fps = FpsMonitor.lastAverage
        return when {
            (ram in 1..4500) || thermal >= 3 || fps in 1..14 -> Plan(
                60, true, true, true, false,
                "Máy RAM thấp, đang nóng hoặc FPS gần đây thấp: giảm độ phân giải và bật các tối ưu.", false
            )
            ram in 4501..8500 -> Plan(
                80, true, true, false, false,
                "Máy tầm trung: giảm khoảng cách hiển thị và chi tiết vật thể, giữ hiệu ứng.", false
            )
            else -> Plan(
                100, false, false, false, false,
                "Máy mạnh: giữ độ phân giải tối đa, không cần tối ưu thêm.", false
            )
        }
    }

    private fun deviceProfile(context: Context): String {
        val metrics = Tools.currentDisplayMetrics
        return buildString {
            appendLine("Thiết bị: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
            appendLine("RAM: ${totalRamMb()} MB; số nhân CPU: ${Runtime.getRuntime().availableProcessors()}")
            appendLine("Màn hình: ${metrics.widthPixels}x${metrics.heightPixels}")
            appendLine("Nhiệt độ (0 mát, 3 nóng, 4 rất nóng, -1 không rõ): ${thermalStatus(context)}")
            appendLine("Tiết kiệm pin: ${powerManager(context)?.isPowerSaveMode == true}")
            appendLine("FPS trung bình 20 s gần nhất (-1 = chưa có): ${FpsMonitor.lastAverage}")
            append("Cài đặt hiện tại: độ phân giải ${AllSettings.resolutionRatio.getValue()}%, ")
            append("khoảng cách hiển thị ${onOff(AllSettings.optRenderDistance.getValue())}, ")
            append("chi tiết vật thể ${onOff(AllSettings.optEntityDetail.getValue())}, ")
            append("giảm hiệu ứng ${onOff(AllSettings.optEffects.getValue())}, ")
            append("FPS không giới hạn ${onOff(AllSettings.unlimitedFps.getValue())}")
        }
    }

    private fun showPlan(context: Context, plan: Plan, onApplied: Runnable) {
        val source = context.getString(
            if (plan.fromAi) R.string.ai_optimize_source_ai else R.string.ai_optimize_source_rules
        )
        val details = buildString {
            appendLine("Độ phân giải: ${plan.resolutionRatio}%")
            appendLine("Khoảng cách hiển thị: ${onOff(plan.optRenderDistance)}")
            appendLine("Chi tiết vật thể: ${onOff(plan.optEntityDetail)}")
            appendLine("Giảm hiệu ứng: ${onOff(plan.optEffects)}")
            append("FPS không giới hạn: ${onOff(plan.unlimitedFps)}")
        }
        AlertDialog.Builder(context, R.style.CustomAlertDialogTheme)
            .setTitle(R.string.ai_optimize_title)
            .setMessage("$source\n\n${plan.reason}\n\n$details")
            .setPositiveButton(R.string.ai_optimize_apply) { dialog, _ ->
                apply(plan)
                dialog.dismiss()
                onApplied.run()
                Toast.makeText(context, R.string.ai_optimize_applied, Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(R.string.ai_optimize_cancel, null)
            .show()
    }

    private fun onOff(on: Boolean): String = if (on) "Bật" else "Tắt"

    private fun powerManager(context: Context): PowerManager? =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    @SuppressLint("NewApi")
    private fun thermalStatus(context: Context): Int {
        val pm = powerManager(context) ?: return -1
        return if (Build.VERSION.SDK_INT >= 29) pm.currentThermalStatus else -1
    }

    private fun totalRamMb(): Long = runCatching {
        File("/proc/meminfo").readLines().first { it.startsWith("MemTotal:") }
            .trim().split(Regex("\\s+"))[1].toLong() / 1024
    }.getOrDefault(0L)
}
