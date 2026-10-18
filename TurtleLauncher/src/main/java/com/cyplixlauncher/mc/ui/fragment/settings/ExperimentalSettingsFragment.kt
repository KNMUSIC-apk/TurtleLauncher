package com.cyplixlauncher.mc.ui.fragment.settings

import com.cyplixlauncher.mc.utils.anim.TurtleTransitions
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.endiq.anim.AnimPlayer
import com.endiq.anim.animations.Animations
import com.cyplixlauncher.mc.R
import com.cyplixlauncher.mc.databinding.SettingsFragmentExperimentalBinding
import com.cyplixlauncher.mc.feature.pluginupdate.PluginUpdateManager
import com.cyplixlauncher.mc.setting.AllSettings
import com.cyplixlauncher.mc.ui.fragment.settings.wrapper.BaseSettingsWrapper
import com.cyplixlauncher.mc.ui.fragment.settings.wrapper.ListSettingsWrapper
import com.cyplixlauncher.mc.ui.fragment.settings.wrapper.SeekBarSettingsWrapper
import com.cyplixlauncher.mc.ui.fragment.settings.wrapper.SwitchSettingsWrapper


class ExperimentalSettingsFragment :
    AbstractSettingsFragment(R.layout.settings_fragment_experimental, SettingCategory.EXPERIMENTAL) {
    companion object {
        const val TAG: String = "ExperimentalSettingsFragment"
    }

    private lateinit var binding: SettingsFragmentExperimentalBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SettingsFragmentExperimentalBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()
        binding.subSettingsBackButton.setOnClickListener { com.cyplixlauncher.mc.utils.ZHTools.onBackPressed(requireActivity()) }

        SwitchSettingsWrapper(
            context,
            AllSettings.dumpShaders,
            binding.dumpShadersLayout,
            binding.dumpShaders
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.turnipSysmem,
            binding.turnipSysmemLayout,
            binding.turnipSysmem
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.bigCoreAffinity,
            binding.bigCoreAffinityLayout,
            binding.bigCoreAffinity
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.fastBoot,
            binding.fastBootLayout,
            binding.fastBoot
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.modConflictDetection,
            binding.modConflictDetectionLayout,
            binding.modConflictDetection
        )

        ListSettingsWrapper(
            context, AllSettings.lwjglCompatMode,
            binding.lwjglCompatModeLayout, binding.lwjglCompatModeTitle, binding.lwjglCompatModeValue,
            R.array.lwjgl_compat_mode_names, R.array.lwjgl_compat_mode_values
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.backgroundServiceOptimization,
            binding.backgroundServiceOptimizationLayout,
            binding.backgroundServiceOptimization
        )

        SeekBarSettingsWrapper(
            context,
            AllSettings.tcVibrateDuration,
            binding.tcVibrateDurationLayout,
            binding.tcVibrateDurationTitle,
            binding.tcVibrateDurationSummary,
            binding.tcVibrateDurationValue,
            binding.tcVibrateDuration,
            "ms"
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.autoCheckPluginUpdates,
            binding.autoCheckPluginUpdatesLayout,
            binding.autoCheckPluginUpdates
        )

        BaseSettingsWrapper(
            context,
            binding.checkPluginUpdatesLayout
        ) {
            Toast.makeText(context, getString(R.string.setting_plugin_update_checking), Toast.LENGTH_SHORT).show()
            PluginUpdateManager.checkForUpdates(context, force = true) { updates, error ->
                when {
                    error != null -> Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    updates.isEmpty() -> Toast.makeText(context, getString(R.string.setting_plugin_update_none), Toast.LENGTH_SHORT).show()
                    else -> {
                        // Install everything that changed; each plugin is independent so a
                        // partial failure on one shouldn't block the others.
                        updates.forEach { asset ->
                            PluginUpdateManager.downloadAndInstall(context, asset) { success, message ->
                                Toast.makeText(context, message, if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }

        BaseSettingsWrapper(
            context,
            binding.crashDiagnosticsLayout
        ) {
            val snapshot = com.cyplixlauncher.mc.feature.log.CrashAnalyzer.getTelemetrySnapshot(context)
            val message = if (snapshot.isEmpty()) {
                getString(R.string.crash_diagnostics_dialog_empty)
            } else {
                snapshot.joinToString("\n") { (title, count) -> "$title: $count" }
            }
            com.cyplixlauncher.mc.ui.dialog.TipDialog.Builder(context)
                .setTitle(R.string.crash_diagnostics_dialog_title)
                .setMessage(message)
                .setCenterMessage(false)
                .setSelectable(true)
                .setShowCancel(false)
                .setConfirm(R.string.generic_ok)
                .showDialog()
        }
    }

    private fun showCrashRuleEditor(context: android.content.Context) {
        val rules = com.cyplixlauncher.mc.feature.log.CrashAnalyzer.getCustomRules()
        val message = if (rules.isEmpty()) {
            getString(R.string.crash_rule_editor_empty)
        } else {
            rules.mapIndexed { i, r -> "${i + 1}. ${r.pattern} → ${r.tip}" }.joinToString("\n")
        }
        com.cyplixlauncher.mc.ui.dialog.TipDialog.Builder(context)
            .setTitle(R.string.setting_crash_rule_editor_title)
            .setMessage(message)
            .setCenterMessage(false)
            .setSelectable(true)
            .setShowCancel(true)
            .setCancel(R.string.generic_add)
            .setCancelClickListener { promptAddCrashRule(context) }
            .setConfirm(R.string.generic_ok)
            .showDialog()
    }

    private fun promptAddCrashRule(context: android.content.Context) {
        com.cyplixlauncher.mc.ui.dialog.EditTextDialog.Builder(context)
            .setTitle(R.string.setting_crash_rule_editor_title)
            .setHintText(R.string.crash_rule_editor_pattern_hint)
            .setAsRequired()
            .setConfirmListener { editText, _ ->
                promptAddCrashRuleTip(context, editText.text.toString())
                true
            }
            .showDialog()
    }

    private fun promptAddCrashRuleTip(context: android.content.Context, pattern: String) {
        com.cyplixlauncher.mc.ui.dialog.EditTextDialog.Builder(context)
            .setTitle(R.string.setting_crash_rule_editor_title)
            .setHintText(R.string.crash_rule_editor_tip_hint)
            .setAsRequired()
            .setConfirmListener { editText, _ ->
                com.cyplixlauncher.mc.feature.log.CrashAnalyzer.addCustomRule(pattern, editText.text.toString())
                Toast.makeText(context, R.string.generic_ok, Toast.LENGTH_SHORT).show()
                showCrashRuleEditor(context)
                true
            }
            .showDialog()
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, TurtleTransitions.enter()))
    }
}
