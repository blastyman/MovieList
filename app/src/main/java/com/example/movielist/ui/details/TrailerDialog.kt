package com.example.movielist.ui.details

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.movielist.R
import com.example.movielist.domain.model.Trailer
import com.example.movielist.ui.settings.LocalAppSettings
import com.example.movielist.ui.theme.MovieRed
import com.example.movielist.ui.theme.MovieSurface
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * Plays [trailer] in a YouTube player inside the app. Some videos can't be embedded (the
 * uploader or YouTube blocks it); then the dialog says so, and "Open in YouTube" always works.
 */
@Composable
fun TrailerDialog(trailer: Trailer, onDismiss: () -> Unit) {
    val autoplay = LocalAppSettings.current.autoplayTrailers
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var failed by remember(trailer) { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MovieSurface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = trailer.name.ifBlank { stringResource(R.string.trailer_watch) },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.trailer_close), tint = Color.LightGray)
                    }
                }

                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    if (failed) {
                        Text(
                            text = stringResource(R.string.trailer_error),
                            color = Color.LightGray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp),
                        )
                    } else {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { viewContext ->
                                YouTubePlayerView(viewContext).apply {
                                    enableAutomaticInitialization = false
                                    // Pauses with the app and releases the WebView with the screen.
                                    lifecycleOwner.lifecycle.addObserver(this)
                                    initialize(
                                        object : AbstractYouTubePlayerListener() {
                                            override fun onReady(youTubePlayer: YouTubePlayer) {
                                                // "Play trailers right away" off: show it ready, wait for a tap.
                                                if (autoplay) youTubePlayer.loadVideo(trailer.youtubeKey, 0f)
                                                else youTubePlayer.cueVideo(trailer.youtubeKey, 0f)
                                            }

                                            override fun onError(
                                                youTubePlayer: YouTubePlayer,
                                                error: PlayerConstants.PlayerError,
                                            ) {
                                                failed = true
                                            }
                                        }
                                    )
                                }
                            },
                            onRelease = { view ->
                                lifecycleOwner.lifecycle.removeObserver(view)
                                view.release()
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                TextButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, trailer.youtubeUrl.toUri()))
                        } catch (exception: ActivityNotFoundException) {
                            failed = true
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(stringResource(R.string.trailer_open_youtube), color = MovieRed)
                }
            }
        }
    }
}
