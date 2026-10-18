package com.cyplixlauncher.mc.ui.fragment.settings

import com.cyplixlauncher.mc.utils.anim.TurtleTransitions
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.endiq.anim.AnimPlayer
import com.endiq.anim.animations.Animations
import com.cyplixlauncher.mc.R
import com.cyplixlauncher.mc.databinding.SettingsFragmentPhoneBinding
import com.cyplixlauncher.mc.setting.AllSettings
import com.cyplixlauncher.mc.task.TaskExecutors
import com.cyplixlauncher.mc.ui.fragment.settings.wrapper.ListSettingsWrapper
import com.cyplixlauncher.mc.ui.fragment.settings.wrapper.SeekBarSettingsWrapper
import com.cyplixlauncher.mc.ui.fragment.settings.wrapper.SwitchSettingsWrapper
import com.cyplixlauncher.mc.utils.file.FileTools.Companion.formatFileSize
import com.cyplixlauncher.mc.utils.platform.MemoryUtils.Companion.getFreeDeviceMemory
import com.cyplixlauncher.mc.utils.platform.MemoryUtils.Companion.getTotalDeviceMemory
import com.cyplixlauncher.mc.utils.ZHTools
import net.endiq.launcher.prefs.LauncherPreferences


class PhoneSettingsFragment : AbstractSettingsFragment(R.layout.settings_fragment_phone, SettingCategory.PHONE) {
    companion object {
        const val TAG: String = "PhoneSettingsFragment"
    }

    private lateinit var binding: SettingsFragmentPhoneBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SettingsFragmentPhoneBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()
        binding.subSettingsBackButton.setOnClickListener { ZHTools.onBackPressed(requireActivity()) }

        // ── CPU ──────────────────────────────────────────────────────────────
        SwitchSettingsWrapper(context, AllSettings.autoDetectCores, binding.autoDetectCoresLayout, binding.autoDetectCores)

        SwitchSettingsWrapper(context, AllSettings.manualCoreOverride, binding.manualCoreOverrideLayout, binding.manualCoreOverride)
            .setOnCheckedChangeListener { _, isChecked, save ->
                save.onSave()
                computeVisibility()
            }

        SeekBarSettingsWrapper(
            context,
            AllSettings.manualCoreCount,
            binding.manualCoreCountLayout,
            binding.manualCoreCountTitle,
            binding.manualCoreCountSummary,
            binding.manualCoreCountValue,
            binding.manualCoreCount,
            ""
        )

        SwitchSettingsWrapper(context, AllSettings.perInstanceCpuOverride, binding.perInstanceCpuOverrideLayout, binding.perInstanceCpuOverride)

        SwitchSettingsWrapper(context, AllSettings.advancedCpuTuning, binding.advancedCpuTuningLayout, binding.advancedCpuTuning)
            .setOnCheckedChangeListener { _, isChecked, save ->
                save.onSave()
                computeVisibility()
            }

        // Thread Affinity reuses the existing bigCoreAffinity setting (previously only
        // surfaced in Experimental Settings) - single source of truth, just exposed here too.
        SwitchSettingsWrapper(context, AllSettings.bigCoreAffinity, binding.bigCoreAffinityLayout, binding.bigCoreAffinity)

        SwitchSettingsWrapper(context, AllSettings.schedulerTuning, binding.schedulerTuningLayout, binding.schedulerTuning)

        // ── Memory Optimizer ────────────────────────────────────────────────
        SwitchSettingsWrapper(context, AllSettings.autoRamCalculator, binding.autoRamCalculatorLayout, binding.autoRamCalculator)
            .setOnCheckedChangeListener { _, isChecked, save ->
                save.onSave()
                if (isChecked) {
                    // Pin the RAM slider to this device's best pick right away, rather than
                    // waiting for the next launch to notice the toggle flipped.
                    val best = LauncherPreferences.findBestRAMAllocation(context)
                    AllSettings.ramAllocation.value.put(best).save()
                }
            }

        SwitchSettingsWrapper(context, AllSettings.equalHeapSizes, binding.equalHeapSizesLayout, binding.equalHeapSizes)

        ListSettingsWrapper(
            context,
            AllSettings.ramPreset,
            binding.ramPresetLayout,
            binding.ramPresetTitle,
            binding.ramPresetValue,
            R.array.all_ram_preset, R.array.all_ram_preset_value
        )

        updateDeviceRamInfo(context)

        SwitchSettingsWrapper(context, AllSettings.memoryPressureMonitor, binding.memoryPressureMonitorLayout, binding.memoryPressureMonitor)
            .setOnCheckedChangeListener { _, isChecked, save ->
                save.onSave()
                if (isChecked) {
                    com.cyplixlauncher.mc.feature.turtle.MemoryPressureMonitor.start(context.applicationContext)
                } else {
                    com.cyplixlauncher.mc.feature.turtle.MemoryPressureMonitor.stop()
                }
            }

        SwitchSettingsWrapper(context, AllSettings.gcStatistics, binding.gcStatisticsLayout, binding.gcStatistics)

        // ── JNI Optimization ────────────────────────────────────────────────
        SwitchSettingsWrapper(context, AllSettings.jniBatching, binding.jniBatchingLayout, binding.jniBatching)
        SwitchSettingsWrapper(context, AllSettings.jniCachedReferences, binding.jniCachedReferencesLayout, binding.jniCachedReferences)
        SwitchSettingsWrapper(context, AllSettings.nativeObjectPooling, binding.nativeObjectPoolingLayout, binding.nativeObjectPooling)
        SwitchSettingsWrapper(context, AllSettings.reducedJniCalls, binding.reducedJniCallsLayout, binding.reducedJniCalls)

        computeVisibility()
    }

    override fun onChange() {
        super.onChange()
        computeVisibility()
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, TurtleTransitions.enter()))
    }

    private fun updateDeviceRamInfo(context: android.content.Context) {
        TaskExecutors.runInUIThread {
            binding.deviceRamDetectionValue.text = getString(
                R.string.setting_java_memory_info,
                formatFileSize(getTotalDeviceMemory(context) - getFreeDeviceMemory(context)),
                formatFileSize(getTotalDeviceMemory(context)),
                formatFileSize(getFreeDeviceMemory(context))
            )
        }
    }

    private fun computeVisibility() {
        binding.apply {
            setViewVisibility(manualCoreCountLayout, AllSettings.manualCoreOverride.getValue())
            val advanced = AllSettings.advancedCpuTuning.getValue()
            setViewVisibility(bigCoreAffinityLayout, advanced)
            setViewVisibility(schedulerTuningLayout, advanced)
        }
    }

    private fun setViewVisibility(view: View, visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }
}
