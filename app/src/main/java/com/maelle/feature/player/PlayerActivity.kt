package com.maelle.feature.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.maelle.app.designsystem.theme.MaelleTheme
import java.io.File

class PlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Playback"

        setContent {
            MaelleTheme {
                PlayerScreen(filePath = filePath, title = title)
            }
        }
    }

    companion object {
        private const val EXTRA_FILE_PATH = "file_path"
        private const val EXTRA_TITLE = "title"

        fun start(context: Context, filePath: String, title: String) {
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(EXTRA_FILE_PATH, filePath)
                    .putExtra(EXTRA_TITLE, title),
            )
        }
    }
}

private val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ssa", "ass")

private fun buildMediaItem(video: File, title: String): MediaItem {
    val builder = MediaItem.Builder()
        .setUri(video.toURI().toString())
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(title)
                .build(),
        )
    val sidecars = discoverSubtitleSidecars(video)
    if (sidecars.isNotEmpty()) {
        builder.setSubtitleConfigurations(sidecars)
    }
    return builder.build()
}

private fun discoverSubtitleSidecars(video: File): List<MediaItem.SubtitleConfiguration> {
    val baseName = video.nameWithoutExtension
    val siblings = video.parentFile
        ?.listFiles { candidate ->
            candidate.isFile &&
                candidate.extension.lowercase() in SUBTITLE_EXTENSIONS &&
                candidate.nameWithoutExtension.startsWith(baseName)
        }
        ?.sortedBy { it.name }
        ?: return emptyList()
    return siblings.map { sidecar ->
        MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(sidecar))
            .setMimeType(mimeTypeFor(sidecar.extension.lowercase()))
            .setLabel(
                sidecar.nameWithoutExtension
                    .removePrefix(baseName)
                    .trimStart('.', ' ')
                    .ifBlank { "Subtitles" },
            )
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
    }
}

private fun mimeTypeFor(extension: String): String {
    return when (extension) {
        "vtt" -> MimeTypes.TEXT_VTT
        "ssa", "ass" -> MimeTypes.TEXT_SSA
        else -> MimeTypes.APPLICATION_SUBRIP
    }
}

@Composable
private fun PlayerScreen(filePath: String, title: String) {
    val context = LocalContext.current
    val file = File(filePath)

    if (filePath.isBlank() || !file.exists()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Text("This download's file is no longer on disk.")
        }
        return
    }

    val player = remember(filePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(buildMediaItem(file, title))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(filePath) {
        onDispose {
            player.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = true
                }
            },
        )
    }
}
