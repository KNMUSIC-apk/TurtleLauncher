package com.endiq.turtlelauncher.ui.subassembly.view

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.View
import android.widget.TextView
import com.getkeepsafe.taptargetview.TapTargetView
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.feature.inputstats.InputStatsTracker
import com.endiq.turtlelauncher.feature.inputstats.SessionStatsTracker
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.task.TaskExecutors
import com.endiq.turtlelauncher.utils.NewbieGuideUtils
import com.endiq.turtlelauncher.utils.file.FileTools.Companion.formatFileSize
import com.endiq.turtlelauncher.utils.platform.MemoryUtils
import com.petterp.floatingx.assist.FxGravity
import com.petterp.floatingx.assist.helper.FxScopeHelper
import com.petterp.floatingx.listener.IFxViewLifecycle
import com.petterp.floatingx.listener.control.IFxScopeControl
import com.petterp.floatingx.view.FxViewHolder
import net.endiq.launcher.LwjglGlfwKeycode
import org.lwjgl.glfw.CallbackBridge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

class GameMenuViewWrapper(
    private val activity: Activity,
    private val listener: View.OnClickListener,
    private val showInfo: Boolean
) {
    companion object {
        private const val TAG = "GameMenuViewWrapper"
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        private const val BYTES_PER_GB = 1024f * 1024f * 1024f
    }

    private var timer: Timer? = null

    private var showCpuRow: Boolean = false
    private var showGpuRow: Boolean = false
    private var showRamRow: Boolean = false
    private var showFpsRow: Boolean = false
    private var showPingRow: Boolean = false
    private var showScreenshotButton: Boolean = false
    private var showRecordButton: Boolean = false
    private var buttonVisible: Boolean = false

    private var buttonScopeFx: IFxScopeControl? = null
    private var hudScopeFx: IFxScopeControl? = null

    init {
        refreshState()
        thinkForButtonVisibility()
        if (showInfo) thinkForHudVisibility()
    }

    private fun getButtonWindow(): IFxScopeControl {
        return FxScopeHelper.Builder().apply {
            setLayout(R.layout.view_game_menu_window)
            setOnClickListener(0L, listener)
            setEnableEdgeAdsorption(false)
            addViewLifecycle(object : IFxViewLifecycle {
                override fun initView(holder: FxViewHolder) {
                    holder.view.alpha = AllSettings.gameMenuAlpha.getValue().toFloat() / 100f
                }

                override fun detached(view: View) {}
            })
            setGravity(getCurrentGravity())
        }.build().toControl(activity)
    }

    private fun getHudWindow(): IFxScopeControl {
        return FxScopeHelper.Builder().apply {
            setLayout(R.layout.view_game_info_hud)
            setEnableEdgeAdsorption(false)
            addViewLifecycle(object : IFxViewLifecycle {
                override fun initView(holder: FxViewHolder) {
                    holder.view.alpha = AllSettings.hudAlpha.getValue().toFloat() / 100f
                    updateInfoText(holder.view)
                }

                override fun detached(view: View) {
                    cancelInfoTimer()
                }
            })
            setGravity(getHudGravity())
        }.build().toControl(activity)
    }

    private fun startNewbieGuide(mainView: View) {
        if (NewbieGuideUtils.showOnlyOne(TAG)) return
        TapTargetView.showFor(
            activity,
            NewbieGuideUtils.getSimpleTarget(activity, mainView,
                activity.getString(R.string.setting_category_game_menu),
                activity.getString(R.string.newbie_guide_game_menu)
            )
        )
    }

    /** Controls only the menu button's visibility (e.g. hidden when the control layout already has its own menu button). */
    fun setVisibility(visible: Boolean) {
        this.buttonVisible = visible
        thinkForButtonVisibility()
    }

    /** Called when relevant settings (HUD module toggles, alpha, position, etc.) change. */
    fun refreshSettingsState() {
        refreshState()
        thinkForButtonVisibility()
        if (showInfo) thinkForHudVisibility()
    }

    private fun thinkForButtonVisibility() {
        if (buttonVisible) {
            if (buttonScopeFx == null) {
                buttonScopeFx = getButtonWindow().apply {
                    show()
                    getView()?.let { startNewbieGuide(it) }
                }
            }
        } else {
            buttonScopeFx?.cancel()
            buttonScopeFx = null
        }
    }

    private fun thinkForHudVisibility() {
        val shouldShow = showCpuRow || showGpuRow || showRamRow || showFpsRow || showPingRow ||
            showScreenshotButton || showRecordButton

        if (shouldShow) {
            if (hudScopeFx != null) {
                updateInfoText()
            } else {
                hudScopeFx = getHudWindow().apply {
                    updateInfoText()
                    show()
                }
            }
        } else {
            hudScopeFx?.cancel()
            hudScopeFx = null
            cancelInfoTimer()
        }
    }

    private fun refreshState() {
        showCpuRow = AllSettings.hudShowCpu.getValue()
        showGpuRow = AllSettings.hudShowGpu.getValue()
        showRamRow = AllSettings.hudShowRam.getValue()
        showFpsRow = AllSettings.hudShowFps.getValue()
        showPingRow = AllSettings.hudShowPing.getValue()
        showScreenshotButton = AllSettings.showScreenshotButtonHud.getValue()
        showRecordButton = AllSettings.showRecordButtonHud.getValue()
    }

    private fun updateInfoText() {
        hudScopeFx?.getView()?.apply {
            updateInfoText(this)
        }
    }

    private fun updateInfoText(view: View) {
        cancelInfoTimer()

        val card: com.endiq.turtlelauncher.feature.turtle.HudCardView = view.findViewById(R.id.hud_card)
        val screenshotButton: TextView = view.findViewById(R.id.screenshot_button)
        val recordButton: TextView = view.findViewById(R.id.record_button)
        val recordTimerText: TextView = view.findViewById(R.id.record_timer_text)

        card.setRows(showCpuRow, showGpuRow, showRamRow, showFpsRow, showPingRow)

        fun tick() {
            pushHudFrame(card)
            updateRecordButton(recordButton, recordTimerText)
        }

        if (showInfo) {
            card.visibility = if (card.hasVisibleRows()) View.VISIBLE else View.GONE
            screenshotButton.visibility = if (showScreenshotButton) View.VISIBLE else View.GONE
            screenshotButton.setOnClickListener {
                com.endiq.turtlelauncher.feature.turtle.ScreenshotHelper.takeScreenshot(activity)
            }
            if (!showRecordButton) {
                recordButton.visibility = View.GONE
                recordTimerText.visibility = View.GONE
            }
            recordButton.setOnClickListener {
                com.endiq.turtlelauncher.feature.turtle.ScreenRecorder.toggle(activity)
            }

            if (showPingRow) {
                com.endiq.turtlelauncher.feature.turtle.PingTracker.start()
            }

            tick()

            val needsTimer = showCpuRow || showGpuRow || showRamRow || showFpsRow || showPingRow || showRecordButton
            if (needsTimer) {
                timer = Timer().apply {
                    schedule(object : TimerTask() {
                        override fun run() {
                            tick()
                        }
                    }, 0, AllSettings.gameMenuInfoRefreshRate.getValue().toLong())
                }
            }
        }
    }

    /** Samples every enabled number once and pushes it to the card. Runs on the timer thread. */
    private fun pushHudFrame(card: com.endiq.turtlelauncher.feature.turtle.HudCardView) {
        val used = MemoryUtils.getUsedDeviceMemory(activity).toFloat()
        val total = MemoryUtils.getTotalDeviceMemory(activity).toFloat()
        val frame = com.endiq.turtlelauncher.feature.turtle.HudCardView.Snapshot(
            fps = CallbackBridge.getCurrentFps(),
            pingMs = com.endiq.turtlelauncher.feature.turtle.PingTracker.getPingMs().takeIf { it >= 0 },
            cpuPercent = com.endiq.turtlelauncher.feature.turtle.HudStatsReader.cpuUsagePercent(),
            cpuTempC = com.endiq.turtlelauncher.feature.turtle.HudStatsReader.cpuTempC(),
            gpuPercent = com.endiq.turtlelauncher.feature.turtle.HudStatsReader.gpuUsagePercent(),
            gpuTempC = com.endiq.turtlelauncher.feature.turtle.HudStatsReader.gpuTempC(),
            ramUsedGb = used / BYTES_PER_GB,
            ramTotalGb = total / BYTES_PER_GB
        )
        TaskExecutors.runInUIThread { card.update(frame) }
    }

    private fun updateRecordButton(recordButton: TextView, recordTimerText: TextView) {
        if (!showRecordButton) return
        val recording = com.endiq.turtlelauncher.feature.turtle.ScreenRecorder.isRecording()
        TaskExecutors.runInUIThread {
            recordButton.visibility = if (recording) View.VISIBLE else View.GONE
            recordButton.text = if (recording) "⏹" else "⏺"
            if (recording && AllSettings.recordingShowTimer.getValue()) {
                val elapsed = com.endiq.turtlelauncher.feature.turtle.ScreenRecorder.getElapsedSeconds()
                recordTimerText.text = String.format(Locale.US, "%02d:%02d", elapsed / 60, elapsed % 60)
                recordTimerText.visibility = View.VISIBLE
            } else {
                recordTimerText.visibility = View.GONE
            }
        }
    }

    private fun cancelInfoTimer() {
        timer?.cancel()
        timer = null
    }

    private fun getCurrentGravity(): FxGravity {
        return when(AllSettings.gameMenuLocation.getValue()) {
            "left_or_top" -> FxGravity.LEFT_OR_TOP
            "left_or_bottom" -> FxGravity.LEFT_OR_BOTTOM
            "right_or_top" -> FxGravity.RIGHT_OR_TOP
            "right_or_bottom" -> FxGravity.RIGHT_OR_BOTTOM
            "top_or_center" -> FxGravity.TOP_OR_CENTER
            "bottom_or_center" -> FxGravity.BOTTOM_OR_CENTER
            "center" -> FxGravity.CENTER
            else -> FxGravity.CENTER
        }
    }

    private fun getHudGravity(): FxGravity {
        return when(AllSettings.gameMenuLocation.getValue()) {
            "left_or_top", "left_or_bottom" -> FxGravity.RIGHT_OR_TOP
            else -> FxGravity.LEFT_OR_TOP
        }
    }
}
