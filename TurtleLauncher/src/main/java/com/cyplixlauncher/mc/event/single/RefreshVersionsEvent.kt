package com.cyplixlauncher.mc.event.single

/**
 * Broadcast when the version list is refreshed.
 * Version refreshes happen asynchronously, so event handling must move to the UI thread.
 * @see com.cyplixlauncher.mc.feature.version.VersionsManager
 */
class RefreshVersionsEvent(val mode: MODE) {
    enum class MODE {
        START, END
    }
}