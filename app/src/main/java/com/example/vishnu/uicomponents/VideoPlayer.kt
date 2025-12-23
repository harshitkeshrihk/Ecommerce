package com.example.vishnu.uicomponents

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 1. Create the Player only ONCE using remember
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(videoUrl)
            setMediaItem(mediaItem)
            prepare() // Start buffering immediately
            playWhenReady = true // Intent to play as soon as ready
        }
    }

    // 2. Manage Lifecycle (Pause when app goes background, Resume when foreground)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // When app goes to background, pause to save battery/data
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                // When app comes back, resume ONLY if it was playing before
                Lifecycle.Event.ON_RESUME -> exoPlayer.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // 3. CLEANUP (Crucial for preventing memory leaks and lag)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    // 4. The UI View
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                // Optimizations for smoother video
                useController = true // Show controls
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM // Fills the box without black bars
            }
        },
        modifier = modifier.fillMaxSize()
    )
}