package cl.radiosatanaz.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

private const val STREAM_URL = "https://stream.zeno.fm/fbf9aexghzzuv"
private const val DEMON_URL = "https://radiosatanaz.ozzylatorcl.workers.dev/assets/demon.png"
private const val LOGO_URL = "https://radiosatanaz.ozzylatorcl.workers.dev/assets/logo-radio-s474n4zz-transparent.png"

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
            if (loading) {
                SplashScreen(isTv = isTv) { splash = false }
            } else {
                PlayerScreen(isTv = isTv)
            }
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
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF3D0005), Color(0xFF090303), Color.Black)
                )
            )
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(24.dp)
                .widthIn(max = if (isTv) 700.dp else 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = DEMON_URL,
                contentDescription = "Diablo original Radio S474N4zZ",
                modifier = Modifier.size(if (isTv) 300.dp else 210.dp)
            )

            Spacer(Modifier.height(18.dp))

            Text(
                "RADIO S474N4zZ",
                color = Color.White,
                fontSize = if (isTv) 48.sp else 30.sp,
                fontWeight = FontWeight.Black
            )
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
                color = Color(0xFFE51C25),
                fontSize = if (isTv) 17.sp else 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFFFF2028).copy(alpha = flash.value * .30f))
        )
    }
}

@Composable
private fun PlayerScreen(isTv: Boolean) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(STREAM_URL))
            prepare()
        }
    }
    var playing by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF030303))
            .padding(if (isTv) 36.dp else 18.dp)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .widthIn(max = if (isTv) 900.dp else 520.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AsyncImage(
                model = LOGO_URL,
                contentDescription = "Logo Radio S474N4zZ",
                modifier = Modifier
                    .size(if (isTv) 280.dp else 220.dp)
                    .background(Color(0xFF080808), RoundedCornerShape(24.dp))
            )

            Spacer(Modifier.height(24.dp))
            Text("AHORA SUENA", color = Color(0xFFE51C25), fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text(
                "RADIO S474N4zZ",
                color = Color.White,
                fontSize = if (isTv) 38.sp else 26.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "Rock & Metal en vivo",
                color = Color(0xFFE51C25),
                fontSize = if (isTv) 24.sp else 18.sp
            )
            Spacer(Modifier.height(8.dp))
            Text("● EN VIVO · 128 kbps", color = Color(0xFFCCCCCC), fontSize = 13.sp)
            Spacer(Modifier.height(26.dp))

            Button(
                onClick = {
                    if (player.isPlaying) player.pause() else player.play()
                },
                modifier = Modifier.size(if (isTv) 96.dp else 76.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE51C25))
            ) {
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pausar" else "Reproducir",
                    modifier = Modifier.size(if (isTv) 52.dp else 40.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                if (isTv) "ANDROID TV · CONTROL REMOTO COMPATIBLE" else "RADIO · NOTICIAS · PROGRAMACIÓN · HISTORIA",
                color = Color(0xFF8D8D8D),
                fontSize = if (isTv) 15.sp else 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
