package cl.radiosatanaz.app

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object RadioPlaybackState {
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    internal fun setPlaying(value: Boolean) {
        _isPlaying.value = value
    }

    internal fun setError(value: String?) {
        _error.value = value
    }
}

@UnstableApi
class RadioPlaybackService : MediaSessionService() {

    companion object {
        const val ACTION_PLAY = "cl.radiosatanaz.app.PLAY"
        const val ACTION_PAUSE = "cl.radiosatanaz.app.PAUSE"
        const val ACTION_STOP = "cl.radiosatanaz.app.STOP"
        const val ACTION_TOGGLE_MUTE = "cl.radiosatanaz.app.TOGGLE_MUTE"
        private const val STREAM_URL = "https://stream.zeno.fm/fbf9aexghzzuv"
        private const val METADATA_URL = "https://api.zeno.fm/mounts/metadata/subscribe/fbf9aexghzzuv"
        private const val LOGO_URL = "https://radiosatanaz.ozzylatorcl.workers.dev/assets/logo-radio-s474n4zz-transparent.png"
        private const val SESSION_ACTION_STOP = "cl.radiosatanaz.app.session.STOP"
        private const val SESSION_ACTION_MUTE = "cl.radiosatanaz.app.session.MUTE"
    }

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastAudibleVolume = 1f

    private val stopCommand = SessionCommand(SESSION_ACTION_STOP, Bundle.EMPTY)
    private val muteCommand = SessionCommand(SESSION_ACTION_MUTE, Bundle.EMPTY)

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
            setMediaItem(buildRadioItem())
            prepare()

            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    RadioPlaybackState.setPlaying(isPlaying)
                    if (isPlaying) RadioPlaybackState.setError(null)
                }

                override fun onPlayerError(error: PlaybackException) {
                    RadioPlaybackState.setPlaying(false)
                    RadioPlaybackState.setError("${error.errorCodeName}: ${error.message.orEmpty()}")
                }
            })
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            474,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopButton = CommandButton.Builder()
            .setDisplayName("Detener / salir")
            .setIconResId(R.drawable.ic_stop_notification)
            .setSessionCommand(stopCommand)
            .build()

        val muteButton = CommandButton.Builder()
            .setDisplayName("Silenciar / activar sonido")
            .setIconResId(R.drawable.ic_volume_off_notification)
            .setSessionCommand(muteCommand)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openAppPendingIntent)
            .setCallback(SessionCallback())
            .setCustomLayout(listOf(stopButton, muteButton))
            .build()

        serviceScope.launch {
            listenToZenoMetadata { artist, title ->
                withContext(Dispatchers.Main) {
                    updateNotificationMetadata(artist, title)
                }
            }
        }
    }

    private fun buildRadioItem(
        title: String = "Radio S474N4zZ",
        artist: String = "Rock & Metal en vivo"
    ): MediaItem =
        MediaItem.Builder()
            .setMediaId("radio-s474n4zz-live")
            .setUri(STREAM_URL)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle("Radio S474N4zZ · Desde Villa Alemana para el mundo")
                    .setArtworkUri(Uri.parse(LOGO_URL))
                    .setIsPlayable(true)
                    .build()
            )
            .build()

    private fun updateNotificationMetadata(artist: String, title: String) {
        if (title.isBlank()) return
        val updatedItem = buildRadioItem(
            title = title,
            artist = artist.ifBlank { "Radio S474N4zZ" }
        )
        if (player.mediaItemCount == 0) {
            player.setMediaItem(updatedItem)
            player.prepare()
        } else {
            player.replaceMediaItem(0, updatedItem)
        }
    }

    private fun playRadio() {
        if (player.mediaItemCount == 0) {
            player.setMediaItem(buildRadioItem())
        }
        if (player.playbackState == Player.STATE_IDLE || player.playerError != null) {
            player.prepare()
        }
        player.play()
    }

    private fun stopRadio() {
        RadioPlaybackState.setPlaying(false)
        player.pause()
        player.clearMediaItems()
        stopSelf()
    }

    private fun toggleMute() {
        if (player.volume > 0.01f) {
            lastAudibleVolume = player.volume
            player.volume = 0f
        } else {
            player.volume = lastAudibleVolume.coerceIn(0.1f, 1f)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> playRadio()
            ACTION_PAUSE -> player.pause()
            ACTION_STOP -> stopRadio()
            ACTION_TOGGLE_MUTE -> toggleMute()
        }
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.isPlaying) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        RadioPlaybackState.setPlaying(false)
        serviceScope.cancel()
        mediaSession?.release()
        mediaSession = null
        player.release()
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(stopCommand)
                .add(muteCommand)
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            return when (customCommand.customAction) {
                SESSION_ACTION_STOP -> {
                    stopRadio()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                SESSION_ACTION_MUTE -> {
                    toggleMute()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                else -> super.onCustomCommand(session, controller, customCommand, args)
            }
        }
    }

    private suspend fun listenToZenoMetadata(onTrack: suspend (artist: String, title: String) -> Unit) {
        var lastStreamTitle = ""
        while (currentCoroutineContext().isActive) {
            try {
                withContext(Dispatchers.IO) {
                    val connection = (URL(METADATA_URL).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 15_000
                        readTimeout = 20_000
                        setRequestProperty("Accept", "text/event-stream")
                        setRequestProperty("Cache-Control", "no-cache")
                    }
                    try {
                        connection.inputStream.bufferedReader().use { reader ->
                            while (currentCoroutineContext().isActive) {
                                val line = reader.readLine() ?: break
                                if (!line.startsWith("data:")) continue
                                val streamTitle = runCatching {
                                    JSONObject(line.removePrefix("data:").trim()).optString("streamTitle")
                                }.getOrDefault("")
                                if (streamTitle.isBlank() || streamTitle == lastStreamTitle) continue
                                lastStreamTitle = streamTitle
                                val (artist, title) = splitStreamTitle(streamTitle)
                                onTrack(artist, title)
                            }
                        }
                    } finally {
                        connection.disconnect()
                    }
                }
            } catch (_: Exception) {
            }
            delay(3_000)
        }
    }

    private fun splitStreamTitle(value: String): Pair<String, String> {
        for (separator in listOf(" - ", " – ", " — ")) {
            val index = value.indexOf(separator)
            if (index > 0) {
                return value.substring(0, index).trim() to value.substring(index + separator.length).trim()
            }
        }
        return "Radio S474N4zZ" to value.trim()
    }
}
