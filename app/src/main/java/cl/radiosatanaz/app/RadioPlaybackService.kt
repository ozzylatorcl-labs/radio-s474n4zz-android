package cl.radiosatanaz.app

import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

class RadioPlaybackService : MediaSessionService() {

    companion object {
        const val ACTION_PLAY = "cl.radiosatanaz.app.PLAY"
        const val ACTION_PAUSE = "cl.radiosatanaz.app.PAUSE"
        private const val STREAM_URL = "https://stream.zeno.fm/fbf9aexghzzuv"
        private const val LOGO_URL = "https://radiosatanaz.ozzylatorcl.workers.dev/assets/logo-radio-s474n4zz-transparent.png"
    }

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

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

        mediaSession = MediaSession.Builder(this, player).build()
    }

    private fun buildRadioItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId("radio-s474n4zz-live")
            .setUri(STREAM_URL)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Radio S474N4zZ")
                    .setArtist("Rock & Metal en vivo")
                    .setAlbumTitle("Desde Villa Alemana para el mundo")
                    .setArtworkUri(Uri.parse(LOGO_URL))
                    .setIsPlayable(true)
                    .build()
            )
            .build()

    private fun playRadio() {
        if (player.mediaItemCount == 0) {
            player.setMediaItem(buildRadioItem())
        }
        if (player.playbackState == Player.STATE_IDLE || player.playerError != null) {
            player.prepare()
        }
        player.play()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> playRadio()
            ACTION_PAUSE -> player.pause()
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
        mediaSession?.release()
        mediaSession = null
        player.release()
        super.onDestroy()
    }
}
