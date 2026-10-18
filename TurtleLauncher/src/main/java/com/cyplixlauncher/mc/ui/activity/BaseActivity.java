package com.cyplixlauncher.mc.ui.activity;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.cyplixlauncher.mc.context.ContextExecutor;
import com.cyplixlauncher.mc.context.LocaleHelper;
import com.cyplixlauncher.mc.event.single.LauncherIgnoreNotchEvent;
import com.cyplixlauncher.mc.feature.accounts.AccountsManager;
import com.cyplixlauncher.mc.feature.customprofilepath.ProfilePathManager;
import com.cyplixlauncher.mc.feature.log.Logging;
import com.cyplixlauncher.mc.plugins.PluginLoader;
import com.cyplixlauncher.mc.renderer.Renderers;
import com.cyplixlauncher.mc.setting.AllSettings;
import com.cyplixlauncher.mc.task.TaskExecutors;
import com.cyplixlauncher.mc.utils.StoragePermissionsUtils;

import net.endiq.launcher.MissingStorageActivity;
import net.endiq.launcher.Tools;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

public abstract class BaseActivity extends AppCompatActivity {

    private static final long PERMISSION_RECHECK_DEBOUNCE_MS = 1500L;
    private long lastPermissionCheckElapsedMs = -PERMISSION_RECHECK_DEBOUNCE_MS;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.Companion.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LocaleHelper.Companion.setLocale(this);
        Tools.setFullscreen(this);
        Tools.updateWindowSize(this);

        checkStoragePermissions(true);
        // Load the renderer.
        Renderers.INSTANCE.init(false);
        // Load the plugin.
        PluginLoader.loadAllPlugins(this, false);
        // Refresh the game path.
        ProfilePathManager.INSTANCE.refreshPath();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextExecutor.setActivity(this);
        if (!Tools.checkStorageRoot()) {
            startActivity(new Intent(this, MissingStorageActivity.class));
            finish();
            return;
        }

        checkStoragePermissions(false);

        TaskExecutors.getDefault().execute(AccountsManager.INSTANCE::reload);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        Tools.setFullscreen(this);
        Tools.ignoreNotch(shouldIgnoreNotch(),this);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Tools.getDisplayMetrics(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Tools.setFullscreen(this);
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, @NonNull Configuration newConfig) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
        Tools.setFullscreen(this);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            Logging.w("BaseActivity", getClass().getSimpleName() + " received onTrimMemory(" + level + ")");
        }
    }

    @Subscribe
    public void event(LauncherIgnoreNotchEvent event) {
        Tools.ignoreNotch(shouldIgnoreNotch(),this);
    }

    /** @return Whether or not the notch should be ignored */
    public boolean shouldIgnoreNotch() {
        return AllSettings.getIgnoreNotchLauncher().getValue();
    }

    private void checkStoragePermissions(boolean force) {
        if (force) {
            lastPermissionCheckElapsedMs = SystemClock.elapsedRealtime();
            StoragePermissionsUtils.checkPermissions(this);
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (now - lastPermissionCheckElapsedMs < PERMISSION_RECHECK_DEBOUNCE_MS) return;
        lastPermissionCheckElapsedMs = now;

        TaskExecutors.getDefault().execute(() -> StoragePermissionsUtils.checkPermissions(this));
    }
}
