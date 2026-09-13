package com.example.movielist.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.movielist.ui.theme.MovieRed

@Composable
fun DrawerItem(text: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else Color.LightGray,
            )
        },
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Default),
            )
        },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        colors =
            NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MovieRed,
                unselectedContainerColor = Color.Transparent,
                selectedTextColor = Color.White,
                unselectedTextColor = Color.LightGray,
            ),
    )
}
