package com.e46.boardlauncher.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

data class NowPlayingState(
    val hasPermission: Boolean,
    val hasActiveSession: Boolean,
    val title: String,
    val artist: String,
    val sourceApp: String,
    val packageName: String?,
    val albumArt: Bitmap?,
    val isPlaying: Boolean
)

class MediaControllerHelper(
    private val context: Context,
    private val onStateChanged: (NowPlayingState) -> Unit
) {
    private val sessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val listenerComponent =
        ComponentName(context, MediaNotificationListener::class.java)

    private var activeController: MediaController? = null
    private var isListening = false

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            emitCurrentState()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            emitCurrentState()
        }

        override fun onSessionDestroyed() {
            refreshSessions()
        }
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            bindBestController(controllers.orEmpty())
        }

    fun start() {
        if (!MediaNotificationListener.isPermissionGranted(context)) {
            detachController()
            emitNoPermissionState()
            return
        }

        try {
            if (!isListening) {
                sessionManager.addOnActiveSessionsChangedListener(
                    sessionsChangedListener,
                    listenerComponent
                )
                isListening = true
            }
            refreshSessions()
        } catch (_: SecurityException) {
            isListening = false
            detachController()
            emitNoPermissionState()
        }
    }

    fun stop() {
        if (isListening) {
            try {
                sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
            } catch (_: Exception) {
            }
            isListening = false
        }
        detachController()
    }

    fun refreshSessions() {
        if (!MediaNotificationListener.isPermissionGranted(context)) {
            detachController()
            emitNoPermissionState()
            return
        }

        try {
            val controllers = sessionManager.getActiveSessions(listenerComponent)
            bindBestController(controllers)
        } catch (_: SecurityException) {
            detachController()
            emitNoPermissionState()
        }
    }

    private fun bindBestController(controllers: List<MediaController>) {
        val chosen = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull()

        if (chosen?.sessionToken != activeController?.sessionToken) {
            detachController()
            activeController = chosen
            activeController?.registerCallback(controllerCallback)
        }
        emitCurrentState()
    }

    private fun detachController() {
        activeController?.let {
            try {
                it.unregisterCallback(controllerCallback)
            } catch (_: Exception) {
            }
        }
        activeController = null
    }

    private fun emitNoPermissionState() {
        onStateChanged(
            NowPlayingState(
                hasPermission = false,
                hasActiveSession = false,
                title = "Now Playing Inactive",
                artist = "Tap here to enable Media Bridge",
                sourceApp = "MEDIA BRIDGE",
                packageName = null,
                albumArt = null,
                isPlaying = false
            )
        )
    }

    private fun emitCurrentState() {
        val controller = activeController
        if (controller == null) {
            onStateChanged(
                NowPlayingState(
                    hasPermission = true,
                    hasActiveSession = false,
                    title = "No Active Audio Stream",
                    artist = "Start Spotify, YouTube Music, or Radio",
                    sourceApp = "STANDBY",
                    packageName = null,
                    albumArt = null,
                    isPlaying = false
                )
            )
            return
        }

        val meta = controller.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: "Unknown Track"
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotBlank() }
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?.takeIf { it.isNotBlank() }
            ?: "Connected Audio"

        val art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        val scaledArt = art?.let {
            try {
                Bitmap.createScaledBitmap(it, 112, 112, true)
            } catch (_: Exception) {
                null
            }
        }

        val appName = try {
            val ai = context.packageManager.getApplicationInfo(controller.packageName, 0)
            context.packageManager.getApplicationLabel(ai).toString().uppercase()
        } catch (_: Exception) {
            "AUDIO"
        }

        val state = controller.playbackState?.state
        val playing = state == PlaybackState.STATE_PLAYING ||
            state == PlaybackState.STATE_BUFFERING

        onStateChanged(
            NowPlayingState(
                hasPermission = true,
                hasActiveSession = true,
                title = title,
                artist = artist,
                sourceApp = appName,
                packageName = controller.packageName,
                albumArt = scaledArt,
                isPlaying = playing
            )
        )
    }

    fun skipPrevious() {
        activeController?.transportControls?.skipToPrevious()
    }

    fun togglePlayPause() {
        val controller = activeController ?: return
        val state = controller.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    fun skipNext() {
        activeController?.transportControls?.skipToNext()
    }

    fun getActivePackageName(): String? = activeController?.packageName
}
