package com.example.movielist.ui.user

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.movielist.R
import com.example.movielist.domain.model.AppUser
import com.example.movielist.ui.components.ScreenHeader
import com.example.movielist.ui.theme.MovieRed
import com.example.movielist.ui.theme.MovieSurface

@Composable
fun UserScreen(
    accountUsername: String?,
    onLogout: () -> Unit,
    users: List<AppUser>,
    selectedUserIndex: Int,
    onUserSelected: (Int) -> Unit,
    onMenuClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        ScreenHeader("user", onMenuClick)

        Spacer(modifier = Modifier.height(32.dp))

        // The real account. Replaced by a full profile screen in Phase 7.
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MovieSurface),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MovieRed,
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = stringResource(R.string.account_signed_in_as, accountUsername ?: "…"),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )

                TextButton(onClick = onLogout) {
                    Text(text = stringResource(R.string.account_log_out), color = MovieRed)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Local demo users that drive the current matches feature (replaced by friend groups in Phase 9).
        Text(
            text = stringResource(R.string.account_demo_users),
            color = Color.Gray,
            style = MaterialTheme.typography.labelLarge,
        )

        Spacer(modifier = Modifier.height(8.dp))

        users.forEachIndexed { index, user ->
            Card(
                onClick = { onUserSelected(index) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = if (index == selectedUserIndex) MovieRed else MovieSurface
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = user.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
