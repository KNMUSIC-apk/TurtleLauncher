package com.cyplixlauncher.mc.ui.fragment.settings

import com.cyplixlauncher.mc.utils.anim.TurtleTransitions
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.endiq.anim.AnimPlayer
import com.endiq.anim.animations.Animations
import com.cyplixlauncher.mc.R
import com.cyplixlauncher.mc.databinding.SettingsFragmentRecordingBinding
import com.cyplixlauncher.mc.setting.AllSettings
import com.cyplixlauncher.mc.ui.fragment.settings.wrapper.SeekBarSettingsWrapper
import com.cyplixlauncher.mc.ui.fragment.settings.wrapper.SwitchSettingsWrapper
import com.cyplixlauncher.mc.utils.ZHTools


class RecordingSettingsFragment : AbstractSettingsFragment(R.layout.settings_fragment_recording, SettingCategory.RECORDING) {
    companion object {
        const val TAG: String = "RecordingSettingsFragment"
    }

    private lateinit var binding: SettingsFragmentRecordingBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SettingsFragmentRecordingBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()
        binding.subSettingsBackButton.setOnClickListener { ZHTools.onBackPressed(requireActivity()) }

        SwitchSettingsWrapper(context, AllSettings.showRecordButtonHud, binding.showRecordButtonHudLayout, binding.showRecordButtonHud)
        SwitchSettingsWrapper(context, AllSettings.recordingCaptureAudio, binding.recordingCaptureAudioLayout, binding.recordingCaptureAudio)
        SwitchSettingsWrapper(context, AllSettings.recordingShowTimer, binding.recordingShowTimerLayout, binding.recordingShowTimer)

        SeekBarSettingsWrapper(
            context, AllSettings.recordingFrameRate,
            binding.recordingFrameRateLayout, binding.recordingFrameRateTitle, binding.recordingFrameRateSummary,
            binding.recordingFrameRateValue, binding.recordingFrameRate, "fps"
        )

        SeekBarSettingsWrapper(
            context, AllSettings.recordingBitrateMbps,
            binding.recordingBitrateMbpsLayout, binding.recordingBitrateMbpsTitle, binding.recordingBitrateMbpsSummary,
            binding.recordingBitrateMbpsValue, binding.recordingBitrateMbps, "Mbps"
        )

        SeekBarSettingsWrapper(
            context, AllSettings.recordingResolutionScale,
            binding.recordingResolutionScaleLayout, binding.recordingResolutionScaleTitle, binding.recordingResolutionScaleSummary,
            binding.recordingResolutionScaleValue, binding.recordingResolutionScale, "%"
        )

        SeekBarSettingsWrapper(
            context, AllSettings.recordingMaxDurationMin,
            binding.recordingMaxDurationMinLayout, binding.recordingMaxDurationMinTitle, binding.recordingMaxDurationMinSummary,
            binding.recordingMaxDurationMinValue, binding.recordingMaxDurationMin, "min"
        )
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, TurtleTransitions.enter()))
    }
}
