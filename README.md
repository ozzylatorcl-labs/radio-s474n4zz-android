# Radio S474N4zZ Android V1

Primera base nativa creada desde cero para **celulares Android + Android TV**.

## Ya incluido

- Splash/flash de carga con el **diablo original** de Radio S474N4zZ.
- Animación de arranque y barra de progreso.
- Reproductor nativo con Media3 / ExoPlayer.
- MediaSessionService para reproducción en segundo plano.
- Controles de play/pausa.
- Conexión al stream real de Zeno.
- "Ahora suena" en tiempo real mediante la Metadata API de Zeno.
- Noticias en español desde el Worker de Radio S474N4zZ.
- Fotos reales de noticias.
- Diseño adaptable:
  - celular: navegación inferior.
  - Android TV: menú lateral y foco para control remoto.
- Launcher para Android TV.
- Workflow de GitHub Actions para generar el APK sin Android Studio.

## Servicios conectados

- Stream: `https://stream.zeno.fm/fbf9aexghzzuv`
- Metadata: `https://api.zeno.fm/mounts/metadata/subscribe/fbf9aexghzzuv`
- Noticias: `https://radiosatanaz-news-api.ozzylatorcl.workers.dev/api/news?v=es-img-4`
- Web: `https://radiosatanaz.ozzylatorcl.workers.dev/`
