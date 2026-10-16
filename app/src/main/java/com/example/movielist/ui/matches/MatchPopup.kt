package com.example.movielist.ui.matches

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.movielist.R
import com.example.movielist.domain.model.GroupBrief
import com.example.movielist.domain.model.Match
import com.example.movielist.domain.model.UserSummary
import com.example.movielist.ui.components.MovieImage
import com.example.movielist.ui.components.UserAvatar
import com.example.movielist.ui.theme.MovieRed
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The "It's a Match!" moment after liking a movie someone else already liked: the screen dims,
 * the title springs in, the poster grows in the middle and the two of you slide in from the
 * sides, while a few hearts float up. Tapping the other person opens their profile, where you
 * can add them; the point of the app is meeting people through movies.
 */
@Composable
fun MatchPopup(
    match: Match,
    myName: String?,
    onOpenProfile: (UserSummary) -> Unit,
    onOpenGroup: (GroupBrief) -> Unit,
    onSeeMatches: () -> Unit,
    onKeepSwiping: () -> Unit,
) {
    BackHandler(onBack = onKeepSwiping)

    val scrim = remember(match) { Animatable(0f) }
    val title = remember(match) { Animatable(0.3f) }
    val poster = remember(match) { Animatable(0f) }
    val sides = remember(match) { Animatable(1f) } // 1 = off screen, 0 = in place
    val buttons = remember(match) { Animatable(0f) }

    LaunchedEffect(match) {
        launch { scrim.animateTo(1f, tween(250)) }
        launch {
            delay(120)
            title.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
        launch {
            delay(200)
            poster.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
        }
        launch {
            delay(350)
            sides.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
        }
        launch {
            delay(700)
            buttons.animateTo(1f, tween(300))
        }
    }

    BoxWithConstraints(
        modifier =
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = scrim.value }
                .background(Color.Black.copy(alpha = 0.88f))
                // Swallow taps so nothing behind the popup reacts.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        val screenWidth = maxWidth

        // The closest match: most likes in common with you.

        val person = match.people.first()
        FloatingHearts(match)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.match_title_small),
                color = Color.White,
                fontSize = 26.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.graphicsLayer { alpha = poster.value.coerceIn(0f, 1f) },
            )
            Text(
                text = stringResource(R.string.match_title),
                color = MovieRed,
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                modifier =
                    Modifier.graphicsLayer {
                        scaleX = title.value
                        scaleY = title.value
                        rotationZ = (1f - title.value) * -12f
                        alpha = ((title.value - 0.3f) / 0.7f).coerceIn(0f, 1f)
                    },
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Poster in the middle, you on the left corner, your friend on the right.
            Box(modifier = Modifier.width(240.dp).height(250.dp), contentAlignment = Alignment.TopCenter) {
                MovieImage(
                    posterPath = match.movie.posterPath,
                    contentDescription = match.movie.title,
                    modifier =
                        Modifier.width(150.dp)
                            .height(225.dp)
                            .graphicsLayer {
                                scaleX = poster.value
                                scaleY = poster.value
                            }
                            .clip(RoundedCornerShape(18.dp))
                            .border(3.dp, Color.White, RoundedCornerShape(18.dp)),
                    width = 342,
                )
                // Starts a whole screen-width away, so the avatars fly in from the edges.
                val slide = screenWidth.value * sides.value
                MatchAvatar(
                    name = myName ?: "?",
                    modifier = Modifier.align(Alignment.BottomStart).offset(x = (-slide).dp),
                )
                val openLabel = stringResource(R.string.match_open_profile, person.user.name)
                MatchAvatar(
                    name = person.user.name,
                    modifier =
                        Modifier.align(Alignment.BottomEnd)
                            .offset(x = slide.dp)
                            .clip(CircleShape)
                            .clickable(onClickLabel = openLabel) { onOpenProfile(person.user) },
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = buttons.value },
            ) {
                Text(
                    text = matchSentence(match),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = pluralStringResource(R.plurals.match_in_common, person.sharedLikes, person.sharedLikes),
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodyMedium,
                )
                // A like that completes a group: everyone in it now likes this movie.
                match.completedGroups.forEach { group ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier.clip(RoundedCornerShape(50))
                                .background(MovieRed.copy(alpha = 0.18f))
                                .clickable { onOpenGroup(group) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Default.Groups, contentDescription = null, tint = MovieRed, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.match_group_all, group.name),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { onOpenProfile(person.user) },
                    colors = ButtonDefaults.buttonColors(containerColor = MovieRed),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Text(
                        stringResource(R.string.match_view_profile, person.user.name),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onSeeMatches,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Text(stringResource(R.string.match_see), color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = onKeepSwiping) {
                    Text(stringResource(R.string.match_keep), color = Color.LightGray)
                }
            }
        }
    }
}

@Composable
private fun MatchAvatar(name: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(84.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
        UserAvatar(name, size = 76.dp)
    }
}

/** "You and Anna both like Inception." / "You, Anna and 2 other people like Inception." */
@Composable
private fun matchSentence(match: Match): String {
    val first = match.people.first().user.name
    val others = match.peopleCount - 1
    return if (others <= 0) {
        stringResource(R.string.match_with_one, first, match.movie.title)
    } else {
        pluralStringResource(R.plurals.match_with_many, others, first, others, match.movie.title)
    }
}

/** A dozen hearts that drift up from the bottom and fade, once, at random spots. */
@Composable
private fun FloatingHearts(match: Match) {
    val hearts = remember(match) {
        List(12) {
            Heart(
                x = Random.nextFloat(),
                size = 18 + Random.nextInt(22),
                delayMillis = Random.nextLong(0, 900),
                progress = Animatable(0f),
            )
        }
    }
    hearts.forEach { heart ->
        LaunchedEffect(heart) {
            delay(heart.delayMillis)
            heart.progress.animateTo(1f, tween(2200, easing = LinearOutSlowInEasing))
        }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        hearts.forEach { heart ->
            val p = heart.progress.value
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = MovieRed.copy(alpha = 0.7f),
                modifier =
                    Modifier.size(heart.size.dp)
                        .offset(x = maxWidth * heart.x, y = maxHeight * (1f - p) - heart.size.dp)
                        .graphicsLayer {
                            alpha = if (p == 0f) 0f else (1f - p)
                            rotationZ = (heart.x - 0.5f) * 40f * p
                        },
            )
        }
    }
}

private class Heart(val x: Float, val size: Int, val delayMillis: Long, val progress: Animatable<Float, *>)
