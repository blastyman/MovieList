package com.example.movielist.ui.navigation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.movielist.ui.components.DrawerItem
import com.example.movielist.ui.theme.MovieDrawer
import kotlinx.coroutines.launch

@Composable
fun AppDrawer(
    drawerState: DrawerState,
    currentSection: AppSection,
    onSectionChange: (AppSection) -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(220.dp),
                drawerContainerColor = MovieDrawer,
            ) {
                Spacer(modifier = Modifier.height(36.dp))

                AppSection.entries.forEach { section ->
                    val icon =
                        when (section) {
                            AppSection.HOME -> Icons.Default.Home
                            AppSection.LISTS -> Icons.Default.Folder
                            AppSection.MATCHES -> Icons.Default.Group
                            AppSection.USER -> Icons.Default.Person
                        }
                    DrawerItem(section.displayName, icon, currentSection == section) {
                        onSectionChange(section)
                        scope.launch { drawerState.close() }
                    }
                }
            }
        },
        content = content,
    )
}
