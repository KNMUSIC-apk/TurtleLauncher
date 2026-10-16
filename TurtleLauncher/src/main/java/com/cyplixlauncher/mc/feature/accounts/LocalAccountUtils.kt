package com.cyplixlauncher.mc.feature.accounts

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import com.cyplixlauncher.mc.feature.log.Logging
import com.cyplixlauncher.mc.utils.path.PathManager
import java.io.File
import com.cyplixlauncher.mc.R
import com.cyplixlauncher.mc.setting.AllSettings
import com.cyplixlauncher.mc.ui.dialog.TipDialog
import com.cyplixlauncher.mc.utils.ZHTools
import com.cyplixlauncher.mc.utils.path.UrlManager

class LocalAccountUtils {
    companion object {
        @JvmStatic
        fun checkUsageAllowed(listener: CheckResultListener) {
            if (AccountsManager.hasMicrosoftAccount()) {
                listener.onUsageAllowed()
            } else {
                listener.onUsageDenied()
            }
        }

        /**
         * Gives a new offline account the bundled Steve skin. Without a skin file the game picks
         * Steve or Alex from the account UUID, so the default is written out explicitly.
         */
        @JvmStatic
        fun assignDefaultSteveSkin(context: Context, uniqueUuid: String) {
            runCatching {
                val target = File(PathManager.DIR_USER_SKIN, "$uniqueUuid.png")
                if (target.exists()) return
                target.parentFile?.mkdirs()
                context.assets.open("steve.png").use { input -> target.outputStream().use { input.copyTo(it) } }
            }.onFailure { Logging.e("LocalAccountUtils", "Failed to assign the default Steve skin", it) }
        }

        @JvmStatic
        fun saveReminders(checked: Boolean) {
            AllSettings.localAccountReminders.put(!checked).save()
        }

        @JvmStatic
        @SuppressLint("InflateParams")
        fun openDialog(
            activity: Activity,
            confirmClickListener: TipDialog.OnConfirmClickListener?,
            message: String?,
            confirm: Int
        ) {
            TipDialog.Builder(activity)
                .setTitle(R.string.generic_warning)
                .setMessage(message)
                .setWarning()
                .setShowCheckBox(true)
                .setCheckBox(R.string.generic_no_more_reminders)
                .setConfirmClickListener(confirmClickListener)
                .setConfirm(confirm)
                .setCancelClickListener { ZHTools.openLink(activity, UrlManager.URL_MINECRAFT) }
                .setCancel(R.string.account_purchase_minecraft_account)
                .showDialog()
        }
    }

    interface CheckResultListener {
        fun onUsageAllowed()
        fun onUsageDenied()
    }
}
