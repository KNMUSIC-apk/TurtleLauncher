package com.endiq.turtlelauncher.feature.background

import android.content.Context
import android.net.Uri
import android.view.TextureView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.utils.path.PathManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Optional looping video shown behind the main menu.
 *
 * The chosen video is copied into the background folder, so it keeps working even if the
 * original file is moved or deleted. Video is used only while that copy exists.
 */
object VideoBackground {
    private const val FILE_NAME = "main_menu_video.mp4"
    private var player: ExoPlayer? = null

    @JvmStatic
    fun file(): File = File(PathManager.DIR_BACKGROUND, FILE_NAME)

    @JvmStatic
    fun hasVideo(): Boolean = file().isFile

    /** Copies the picked video into the background folder. Returns false if the copy failed. */
    @JvmStatic
    fun importFrom(context: Context, uri: Uri): Boolean {
        return try {
            PathManager.DIR_BACKGROUND.mkdirs()
            val input = context.contentResolver.openInputStream(uri) ?: return false
            input.use { source ->
                FileOutputStream(file()).use { target -> source.copyTo(target) }
            }
            true
        } catch (e: IOException) {
            Logging.w("VideoBackground", "Could not import the video background", e)
            file().takeIf { it.exists() }?.delete()
            false
        }
    }

    /** Removes the video and stops any playback. */
    @JvmStatic
    fun clear() {
        release()
        file().takeIf { it.exists() }?.delete()
    }

    /**
     * Starts looping, muted playback into [textureView]. [onFailure] runs on the main thread
     * if the video cannot be played, so the caller can fall back to the still background.
     */
    @JvmStatic
    fun start(context: Context, textureView: TextureView, onFailure: Runnable) {
        if (!hasVideo()) return
        release()
        val exo = ExoPlayer.Builder(context.applicationContext).build()
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Logging.w("VideoBackground", "Video background failed to play", error)
                release()
                onFailure.run()
            }
        })
        exo.setVideoTextureView(textureView)
        exo.repeatMode = Player.REPEAT_MODE_ALL
        exo.volume = 0f
        exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(file())))
        exo.prepare()
        exo.playWhenReady = true
        player = exo
    }

    /** Stops playback and frees the decoder. Safe to call when nothing is playing. */
    @JvmStatic
    fun release() {
        player?.release()
        player = null
    }
}
