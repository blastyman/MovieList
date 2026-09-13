package com.example.movielist.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

@Composable
fun ScreenHeader(title: String, onMenuClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style =
                MaterialTheme.typography.displayLarge.copy(
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.Center),
        )
        IconButton(onClick = onMenuClick, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
        }
    }
}
