package com.endiq.turtlelauncher.ui.fragment.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.endiq.anim.AnimPlayer
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.databinding.SettingsFragmentHudBinding
import com.endiq.turtlelauncher.feature.turtle.HudCardView
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.ui.fragment.settings.wrapper.ListSettingsWrapper
import com.endiq.turtlelauncher.ui.fragment.settings.wrapper.SeekBarSettingsWrapper
import com.endiq.turtlelauncher.ui.fragment.settings.wrapper.SwitchSettingsWrapper
import com.endiq.turtlelauncher.utils.ZHTools
import com.endiq.turtlelauncher.utils.anim.TurtleTransitions

/**
 * Settings > HUD. The in-game HUD is one card with five switchable rows: CPU, GPU, RAM, FPS and
 * ping. The preview under the title draws the same card with sample numbers.
 */
class HudSettingsFragment : AbstractSettingsFragment(R.layout.settings_fragment_hud, SettingCategory.HUD) {
    companion object {
        const val TAG: String = "HudSettingsFragment"
    }

    private lateinit var binding: SettingsFragmentHudBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SettingsFragmentHudBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()
        binding.subSettingsBackButton.setOnClickListener { ZHTools.onBackPressed(requireActivity()) }

        // The five HUD switches. Each one redraws the preview so the change is visible at once.
        listOf(
            Triple(AllSettings.hudShowCpu, binding.hudShowCpuLayout, binding.hudShowCpu),
            Triple(AllSettings.hudShowGpu, binding.hudShowGpuLayout, binding.hudShowGpu),
            Triple(AllSettings.hudShowRam, binding.hudShowRamLayout, binding.hudShowRam),
            Triple(AllSettings.hudShowFps, binding.hudShowFpsLayout, binding.hudShowFps),
            Triple(AllSettings.hudShowPing, binding.hudShowPingLayout, binding.hudShowPing),
        ).forEach { (setting, layout, switch) ->
            SwitchSettingsWrapper(context, setting, layout, switch).setOnCheckedChangeListener { _, _, listener ->
                listener.onSave()
                updatePreview()
            }
        }

        SwitchSettingsWrapper(
            context,
            AllSettings.showScreenshotButtonHud,
            binding.showScreenshotButtonHudLayout,
            binding.showScreenshotButtonHud
        )

        ListSettingsWrapper(
            context,
            AllSettings.gameMenuLocation,
            binding.gameMenuLocationLayout,
            binding.gameMenuLocationTitle,
            binding.gameMenuLocationValue,
            R.array.game_menu_location_names, R.array.game_menu_location_values
        )

        SeekBarSettingsWrapper(
            context,
            AllSettings.gameMenuInfoRefreshRate,
            binding.gameMenuInfoRefreshRateLayout,
            binding.gameMenuInfoRefreshRateTitle,
            binding.gameMenuInfoRefreshRateSummary,
            binding.gameMenuInfoRefreshRateValue,
            binding.gameMenuInfoRefreshRate,
            "ms"
        )

        SeekBarSettingsWrapper(
            context,
            AllSettings.gameMenuAlpha,
            binding.gameMenuAlphaLayout,
            binding.gameMenuAlphaTitle,
            binding.gameMenuAlphaSummary,
            binding.gameMenuAlphaValue,
            binding.gameMenuAlpha,
            "%"
        ).setOnSeekBarProgressChangeListener { progress ->
            setGameMenuAlpha(progress.toFloat() / 100F)
        }

        SeekBarSettingsWrapper(
            context,
            AllSettings.hudModuleScale,
            binding.hudModuleScaleLayout,
            binding.hudModuleScaleTitle,
            binding.hudModuleScaleSummary,
            binding.hudModuleScaleValue,
            binding.hudModuleScale,
            suffix = "%"
        )

        SeekBarSettingsWrapper(
            context,
            AllSettings.hudAlpha,
            binding.hudAlphaLayout,
            binding.hudAlphaTitle,
            binding.hudAlphaSummary,
            binding.hudAlphaValue,
            binding.hudAlpha,
            "%"
        )

        setGameMenuAlpha(AllSettings.gameMenuAlpha.getValue().toFloat() / 100F)
        updatePreview()
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, TurtleTransitions.enter()))
    }

    private fun setGameMenuAlpha(alpha: Float) {
        binding.gameMenuPreview.root.alpha = alpha
    }

    /** Draws the card with the current switch states and fixed sample numbers. */
    private fun updatePreview() {
        val card = binding.gameMenuPreview.hudCard
        card.setRows(
            AllSettings.hudShowCpu.getValue(),
            AllSettings.hudShowGpu.getValue(),
            AllSettings.hudShowRam.getValue(),
            AllSettings.hudShowFps.getValue(),
            AllSettings.hudShowPing.getValue()
        )
        card.update(
            HudCardView.Snapshot(
                fps = 120,
                pingMs = 24,
                cpuPercent = 54,
                cpuTempC = 85,
                gpuPercent = 60,
                gpuTempC = 75,
                ramUsedGb = 4.3f,
                ramTotalGb = 8f
            )
        )
        card.visibility = if (card.hasVisibleRows()) View.VISIBLE else View.GONE
    }
}
