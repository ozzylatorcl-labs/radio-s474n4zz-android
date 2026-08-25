package cl.radiosatanaz.app

import android.content.ComponentName
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val METADATA_URL = "https://api.zeno.fm/mounts/metadata/subscribe/fbf9aexghzzuv"
private const val DEMON_URL = "https://radiosatanaz.ozzylatorcl.workers.dev/assets/demon.png"
private const val LOGO_URL = "https://radiosatanaz.ozzylatorcl.workers.dev/assets/logo-radio-s474n4zz-transparent.png"

private data class TrackInfo(
    val title: String = "Esperando información de la canción...",
    val artist: String = "Radio S474N4zZ",
    val album: String = "",
    val coverUrl: String = LOGO_URL
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RadioApp() }
    }
}

@Composable
private fun RadioApp() {
    var splash by remember { mutableStateOf(true) }
    val config = LocalConfiguration.current
    val isTv = (config.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFE51C25),
            background = Color(0xFF030303),
            surface = Color(0xFF090909)
        )
    ) {
        Crossfade(targetState = splash, label = "radio-root") { loading ->
            if (loading) SplashScreen(isTv) { splash = false }
            else PlayerScreen(isTv)
        }
    }
}

@Composable
private fun SplashScreen(isTv: Boolean, onDone: () -> Unit) {
    val flash = remember { Animatable(0f) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        flash.animateTo(1f, tween(100))
        flash.animateTo(0f, tween(450))
        progress.animateTo(1f, tween(1900))
        delay(250)
        onDone()
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF3D0005), Color(0xFF090303), Color.Black))
        )
    ) {
        Column(
            Modifier.align(Alignment.Center).padding(24.dp).widthIn(max = if (isTv) 700.dp else 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = DEMON_URL,
                contentDescription = "Diablo original Radio S474N4zZ",
                modifier = Modifier.size(if (isTv) 300.dp else 210.dp)
            )
            Spacer(Modifier.height(18.dp))
            Text("RADIO S474N4zZ", color = Color.White, fontSize = if (isTv) 48.sp else 30.sp, fontWeight = FontWeight.Black)
            Text(
                "DESDE VILLA ALEMANA PARA EL MUNDO",
                color = Color(0xFFCFCFCF),
                fontSize = if (isTv) 18.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(30.dp))
            LinearProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.fillMaxWidth(.8f).height(5.dp),
                color = Color(0xFFE51C25),
                trackColor = Color(0xFF351113)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (progress.value < .7f) "CARGANDO..." else "CONECTANDO CON EL INFIERNO...",
                color = Color(0xFFE51C25), fontSize = if (isTv) 17.sp else 11.sp, fontWeight = FontWeight.Bold
            )
        }
        Box(Modifier.fillMaxSize().background(Color(0xFFFF2028).copy(alpha = flash.value * .30f)))
    }
}

@Composable
private fun PlayerScreen(isTv: Boolean) {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var playing by remember { mutableStateOf(false) }
    var playFocused by remember { mutableStateOf(false) }
    var track by remember { mutableStateOf(TrackInfo()) }

    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, RadioPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess { c ->
                controller = c
                playing = c.isPlaying
                c.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        playing = isPlaying
                    }
                })
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            controller?.release()
            controller = null
        }
    }

    LaunchedEffect(Unit) {
        listenToZenoMetadata { artist, title ->
            track = TrackInfo(title = title, artist = artist, album = "Buscando álbum...", coverUrl = LOGO_URL)
            val enriched = lookupTrackOnDeezer(artist, title)
            if (enriched != null && track.title == title) track = enriched
            else if (track.title == title) track = track.copy(album = "")
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color(0xFF030303)).padding(if (isTv) 36.dp else 18.dp)
    ) {
        Column(
            Modifier.fillMaxSize().widthIn(max = if (isTv) 900.dp else 520.dp).align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AsyncImage(
                model = track.coverUrl.ifBlank { LOGO_URL },
                contentDescription = "Carátula de ${track.title}",
                modifier = Modifier.size(if (isTv) 320.dp else 230.dp).background(Color(0xFF080808), RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(24.dp))
            Text("AHORA SUENA", color = Color(0xFFE51C25), fontWeight = FontWeight.Black, fontSize = if (isTv) 20.sp else 14.sp)
            Spacer(Modifier.height(8.dp))
            Text(track.title, color = Color.White, fontSize = if (isTv) 38.sp else 26.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Color(0xFFE51C25), fontSize = if (isTv) 24.sp else 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (track.album.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(track.album, color = Color(0xFFB8B8B8), fontSize = if (isTv) 17.sp else 13.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(9.dp))
            Text("● EN VIVO · 128 kbps", color = Color(0xFFCCCCCC), fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { controller?.let { if (it.isPlaying) it.pause() else it.play() } },
                enabled = controller != null,
                modifier = Modifier.size(if (isTv) 104.dp else 76.dp).onFocusChanged { playFocused = it.isFocused }.then(
                    if (isTv && playFocused) Modifier.border(4.dp, Color.White, CircleShape) else Modifier
                ),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isTv && playFocused) Color(0xFFFF333B) else Color(0xFFE51C25), contentColor = Color.White)
            ) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pausar" else "Reproducir", Modifier.size(if (isTv) 56.dp else 40.dp))
            }

            Spacer(Modifier.height(18.dp))
            Text(
                if (isTv) "USA EL CONTROL REMOTO · D-PAD / OK" else "BAJA LA CORTINA DE ANDROID PARA CONTROLAR LA RADIO",
                color = Color(0xFF8D8D8D), fontSize = if (isTv) 15.sp else 11.sp, textAlign = TextAlign.Center
            )
        }
    }
}

private suspend fun listenToZenoMetadata(onTrack: suspend (artist: String, title: String) -> Unit) {
    var lastStreamTitle = ""
    while (currentCoroutineContext().isActive) {
        try {
            withContext(Dispatchers.IO) {
                val connection = (URL(METADATA_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"; connectTimeout = 15_000; readTimeout = 0
                    setRequestProperty("Accept", "text/event-stream"); setRequestProperty("Cache-Control", "no-cache")
                }
                try {
                    connection.inputStream.bufferedReader().use { reader ->
                        while (currentCoroutineContext().isActive) {
                            val line = reader.readLine() ?: break
                            if (!line.startsWith("data:")) continue
                            val streamTitle = runCatching { JSONObject(line.removePrefix("data:").trim()).optString("streamTitle") }.getOrDefault("")
                            if (streamTitle.isBlank() || streamTitle == lastStreamTitle) continue
                            lastStreamTitle = streamTitle
                            val (artist, title) = splitStreamTitle(streamTitle)
                            withContext(Dispatchers.Main) { onTrack(artist, title) }
                        }
                    }
                } finally { connection.disconnect() }
            }
        } catch (_: Exception) {}
        delay(3_000)
    }
}

private fun splitStreamTitle(value: String): Pair<String, String> {
    for (separator in listOf(" - ", " – ", " — ")) {
        val index = value.indexOf(separator)
        if (index > 0) return value.substring(0, index).trim() to value.substring(index + separator.length).trim()
    }
    return "Radio S474N4zZ" to value.trim()
}

private suspend fun lookupTrackOnDeezer(artist: String, title: String): TrackInfo? = withContext(Dispatchers.IO) {
    if (title.isBlank()) return@withContext null
    runCatching {
        val query = URLEncoder.encode(listOf(artist, title).filter { it.isNotBlank() }.joinToString(" "), "UTF-8")
        val connection = (URL("https://api.deezer.com/search?q=$query&limit=1").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 8_000; readTimeout = 8_000
            setRequestProperty("Accept", "application/json"); setRequestProperty("User-Agent", "RadioS474N4zZ-Android")
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            val data = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optJSONArray("data") ?: return@runCatching null
            if (data.length() == 0) return@runCatching null
            val item = data.optJSONObject(0) ?: return@runCatching null
            val album = item.optJSONObject("album")
            val artistObject = item.optJSONObject("artist")
            TrackInfo(
                title = item.optString("title").ifBlank { title },
                artist = artistObject?.optString("name").orEmpty().ifBlank { artist.ifBlank { "Radio S474N4zZ" } },
                album = album?.optString("title").orEmpty(),
                coverUrl = album?.optString("cover_xl").orEmpty().ifBlank { album?.optString("cover_big").orEmpty() }.ifBlank { LOGO_URL }
            )
        } finally { connection.disconnect() }
    }.getOrNull()
}
