package com.cyplixlauncher.mc.ui.fragment.settings

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.cyplixlauncher.mc.feature.background.VideoBackground
import com.endiq.anim.AnimPlayer
import com.endiq.anim.animations.Animations
import com.cyplixlauncher.mc.R
import com.cyplixlauncher.mc.databinding.SettingsFragmentLauncherBinding
import com.cyplixlauncher.mc.event.single.PageOpacityChangeEvent
import com.cyplixlauncher.mc.event.single.MainBackgroundChangeEvent
import android.view.View
import com.cyplixlauncher.mc.feature.update.UpdateUtils
import com.cyplixlauncher.mc.setting.AllSettings
import com.cyplixlauncher.mc.ui.fragment.CustomBackgroundFragment
import com.cyplixlauncher.mc.ui.fragment.FragmentWithAnim
import com.cyplixlauncher.mc.ui.fragment.settings.wrapper.BaseSettingsWrapper
import com.cyplixlauncher.mc.ui.fragment.settings.wrapper.EditTextSettingsWrapper
import com.cyplixlauncher.mc.ui.fragment.settings.wrapper.ListSettingsWrapper
import com.cyplixlauncher.mc.ui.fragment.settings.wrapper.SeekBarSettingsWrapper
import com.cyplixlauncher.mc.ui.fragment.settings.wrapper.SwitchSettingsWrapper
import com.cyplixlauncher.mc.utils.CleanUpCache.Companion.start
import com.cyplixlauncher.mc.utils.WarningTooltip
import com.cyplixlauncher.mc.utils.ZHTools
import net.endiq.launcher.LauncherActivity
import org.greenrobot.eventbus.EventBus
import com.cyplixlauncher.mc.utils.anim.TurtleTransitions


class LauncherSettingsFragment() : AbstractSettingsFragment(R.layout.settings_fragment_launcher, SettingCategory.LAUNCHER) {
    companion object {
        const val TAG: String = "LauncherSettingsFragment"
    }

    private lateinit var binding: SettingsFragmentLauncherBinding
    private var parentFragment: FragmentWithAnim? = null
    private lateinit var openVideoLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openVideoLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            val appContext = context ?: return@registerForActivityResult
            if (uri == null) return@registerForActivityResult
            val messageRes = if (VideoBackground.importFrom(appContext, uri)) {
                R.string.video_background_saved
            } else {
                R.string.video_background_failed
            }
            Toast.makeText(appContext, messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    constructor(parentFragment: FragmentWithAnim?) : this() {
        this.parentFragment = parentFragment
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SettingsFragmentLauncherBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()
        binding.subSettingsBackButton.setOnClickListener { com.cyplixlauncher.mc.utils.ZHTools.onBackPressed(requireActivity()) }

        SwitchSettingsWrapper(
            context,
            AllSettings.checkLibraries,
            binding.checkLibrariesLayout,
            binding.checkLibraries
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.verifyManifest,
            binding.verifyManifestLayout,
            binding.verifyManifest
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.resourceImageCache,
            binding.resourceImageCacheLayout,
            binding.resourceImageCache
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.addFullResourceName,
            binding.addFullResourceNameLayout,
            binding.addFullResourceName
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.aiAssistantEnabled,
            binding.aiAssistantEnabledLayout,
            binding.aiAssistantEnabled
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.aiAssistantHistoryEnabled,
            binding.aiAssistantHistoryEnabledLayout,
            binding.aiAssistantHistoryEnabled
        )

        ListSettingsWrapper(
            context,
            AllSettings.downloadSource,
            binding.downloadSourceLayout,
            binding.downloadSourceTitle,
            binding.downloadSourceValue,
            R.array.download_source_names, R.array.download_source_values
        )

        ListSettingsWrapper(
            context,
            AllSettings.dnsServer,
            binding.dnsServerLayout,
            binding.dnsServerTitle,
            binding.dnsServerValue,
            R.array.dns_server_names, R.array.dns_server_values
        )

        SeekBarSettingsWrapper(
            context,
            AllSettings.maxDownloadThreads,
            binding.maxDownloadThreadsLayout,
            binding.maxDownloadThreadsTitle,
            binding.maxDownloadThreadsSummary,
            binding.maxDownloadThreadsValue,
            binding.maxDownloadThreads,
            ""
        )

        // Every language in the launcher's list (the same one the game-language picker uses),
        // plus "Follow System Language" first.
        ListSettingsWrapper(
            context,
            AllSettings.launcherLanguage,
            binding.launcherLanguageLayout,
            binding.launcherLanguageTitle,
            binding.launcherLanguageValue,
            R.array.all_game_language, R.array.all_game_language_value
        )

        ListSettingsWrapper(
            context,
            AllSettings.launcherTheme,
            binding.launcherThemeLayout,
            binding.launcherThemeTitle,
            binding.launcherThemeValue,
            R.array.launcher_theme_names, R.array.launcher_theme_values
        ).apply {
            onValueChanged = { com.cyplixlauncher.mc.feature.theme.LauncherTheme.apply(it) }
        }

        bindColorRow(binding.launcherBgColorLayout, binding.launcherBgColorValue,
            AllSettings.launcherBackgroundColor, R.string.setting_launcher_bg_color) {
            EventBus.getDefault().post(MainBackgroundChangeEvent())
        }
        bindColorRow(binding.launcherIconColorLayout, binding.launcherIconColorValue,
            AllSettings.launcherIconColor, R.string.setting_launcher_icon_color) {
            EventBus.getDefault().post(MainBackgroundChangeEvent())
        }

        BaseSettingsWrapper(
            context,
            binding.customBackgroundLayout
        ) {
            ZHTools.swapFragmentWithAnim(
                    this,
                    CustomBackgroundFragment::class.java,
                    CustomBackgroundFragment.TAG,
                    null
                )
        }

        BaseSettingsWrapper(
            context,
            binding.videoBackgroundLayout
        ) {
            openVideoLauncher.launch(arrayOf("video/*"))
        }
        binding.videoBackgroundLayout.setOnLongClickListener {
            VideoBackground.clear()
            Toast.makeText(context, R.string.video_background_cleared, Toast.LENGTH_SHORT).show()
            true
        }

        SwitchSettingsWrapper(
            context,
            AllSettings.animation,
            binding.animationLayout,
            binding.animation
        )

        ListSettingsWrapper(
            context,
            AllSettings.animationEnter,
            binding.animationEnterLayout,
            binding.animationEnterTitle,
            binding.animationEnterValue,
            R.array.animation_enter_names, R.array.animation_enter_values
        )

        ListSettingsWrapper(
            context,
            AllSettings.animationExit,
            binding.animationExitLayout,
            binding.animationExitTitle,
            binding.animationExitValue,
            R.array.animation_exit_names, R.array.animation_exit_values
        )

        SeekBarSettingsWrapper(
            context,
            AllSettings.animationSpeed,
            binding.animationSpeedLayout,
            binding.animationSpeedTitle,
            binding.animationSpeedSummary,
            binding.animationSpeedValue,
            binding.animationSpeed,
            "ms"
        )

        SeekBarSettingsWrapper(
            context,
            AllSettings.pageOpacity,
            binding.pageOpacityLayout,
            binding.pageOpacityTitle,
            binding.pageOpacitySummary,
            binding.pageOpacityValue,
            binding.pageOpacity,
            "%"
        ).setOnSeekBarProgressChangeListener {
            EventBus.getDefault().post(PageOpacityChangeEvent(it))
        }

        SwitchSettingsWrapper(
            context,
            AllSettings.enableLogOutput,
            binding.enableLogOutputLayout,
            binding.enableLogOutput
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.quitLauncher,
            binding.quitLauncherLayout,
            binding.quitLauncher
        )

        BaseSettingsWrapper(
            context,
            binding.cleanUpCacheLayout
        ) {
            start(context)
        }

        BaseSettingsWrapper(
            context,
            binding.checkUpdateLayout
        ) {
            UpdateUtils.checkDownloadedPackage(context, force = true, ignore = false)
        }

        SwitchSettingsWrapper(
            context,
            AllSettings.acceptPreReleaseUpdates,
            binding.acceptPreReleaseUpdatesLayout,
            binding.acceptPreReleaseUpdates
        )

        val notificationPermissionRequest = SwitchSettingsWrapper(
            context,
            AllSettings.notificationPermissionRequest,
            binding.notificationPermissionRequestLayout,
            binding.notificationPermissionRequest
        )
        setupNotificationRequestPreference(notificationPermissionRequest)

        // Controls that used to live on the removed "Trò chơi" page.
        SwitchSettingsWrapper(context, AllSettings.useAngle, binding.useAngleLayout, binding.useAngle)
        WarningTooltip.attach(binding.useAngleWarning, getString(R.string.setting_warning_angle))

        SwitchSettingsWrapper(
            context,
            AllSettings.versionIsolation,
            binding.versionIsolationLayout,
            binding.versionIsolation
        )

        EditTextSettingsWrapper(
            AllSettings.versionCustomInfo,
            binding.versionCustomInfoLayout,
            binding.versionCustomInfoEdittext
        )

        EditTextSettingsWrapper(
            AllSettings.curseForgeApiKey,
            binding.curseForgeApiKeyLayout,
            binding.curseForgeApiKeyEdittext
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.autoSetGameLanguage,
            binding.autoSetGameLanguageLayout,
            binding.autoSetGameLanguage
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.gameLanguageOverridden,
            binding.gameLanguageOverriddenLayout,
            binding.gameLanguageOverridden
        )

        ListSettingsWrapper(
            context,
            AllSettings.setGameLanguage,
            binding.setGameLanguageLayout,
            binding.setGameLanguageTitle,
            binding.setGameLanguageValue,
            R.array.all_game_language, R.array.all_game_language_value
        )
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, TurtleTransitions.enter()))
    }

    private fun setupNotificationRequestPreference(notificationPermissionRequest: SwitchSettingsWrapper) {
        val activity = requireActivity()
        if (activity is LauncherActivity) {
            if (ZHTools.checkForNotificationPermission()) notificationPermissionRequest.setGone()
            notificationPermissionRequest.switchView.setOnCheckedChangeListener { _, _ ->
                activity.askForNotificationPermission {
                    notificationPermissionRequest.mainView.visibility = View.GONE
                }
            }
        } else {
            notificationPermissionRequest.mainView.visibility = View.GONE
        }
    }

    private fun bindColorRow(
        row: View,
        valueView: android.widget.TextView,
        setting: com.cyplixlauncher.mc.setting.unit.StringSettingUnit,
        titleRes: Int,
        onChanged: () -> Unit
    ) {
        fun refresh() {
            valueView.text = setting.getValue().ifBlank { getString(R.string.generic_default) }
        }
        refresh()
        row.setOnClickListener {
            com.cyplixlauncher.mc.ui.dialog.ColorPickerDialog.show(
                requireContext(), getString(titleRes), setting.getValue()
            ) { hex ->
                setting.put(hex ?: "").save()
                refresh()
                onChanged()
            }
        }
    }
}