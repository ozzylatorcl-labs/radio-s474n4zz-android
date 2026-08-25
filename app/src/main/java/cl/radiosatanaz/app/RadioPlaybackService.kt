package cl.radiosatanaz.app

import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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

    internal fun setPlaying(value: Boolean) {
        _isPlaying.value = value
    }
}

class RadioPlaybackService : MediaSessionService() {

    companion object {
        const val ACTION_PLAY = "cl.radiosatanaz.app.PLAY"
        const val ACTION_PAUSE = "cl.radiosatanaz.app.PAUSE"
        const val ACTION_TOGGLE = "cl.radiosatanaz.app.TOGGLE"
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
            setWakeMode(C.WAKE_MODE_NETWORK)

            val mediaItem = MediaItem.Builder()
                .setUri(STREAM_URL)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Radio S474N4zZ")
                        .setArtist("Rock & Metal en vivo")
                        .setAlbumTitle("Desde Villa Alemana para el mundo")
                        .setArtworkUri(Uri.parse(LOGO_URL))
                        .build()
                )
                .build()

            setMediaItem(mediaItem)
            prepare()

            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    RadioPlaybackState.setPlaying(isPlaying)
                }
            })
        }

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> player.play()
            ACTION_PAUSE -> player.pause()
            ACTION_TOGGLE -> if (player.isPlaying) player.pause() else player.play()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Si está sonando, dejamos la sesión viva para la cortina de Android.
        if (!player.isPlaying) {
            stopSelf()
        }
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
