package com.cyplixlauncher.mc.event.sticky

import com.cyplixlauncher.mc.feature.version.install.Addon
import com.cyplixlauncher.mc.feature.version.install.InstallTask

/**
 * Broadcast after an install task is selected.
 * @param addon whose install task was selected
 * @param selectedVersion the selected version
 * @param task the selected task
 * @see com.cyplixlauncher.mc.feature.version.install.Addon
 */
class SelectInstallTaskEvent(val addon: Addon, val selectedVersion: String, val task: InstallTask)