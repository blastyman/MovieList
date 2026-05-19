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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.movielist.ui.components.DrawerItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AppDrawer(
    drawerState: DrawerState,
    currentSection: AppSection,
    onSectionChange: (AppSection) -> Unit,
    scope: CoroutineScope,
    content: @Composable () -> Unit
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(220.dp),
                drawerContainerColor = Color(0xFF141414)
            ) {
                Spacer(modifier = Modifier.height(36.dp))

                DrawerItem("home", Icons.Default.Home, currentSection == AppSection.HOME) {
                    onSectionChange(AppSection.HOME)
                    scope.launch { drawerState.close() }
                }

                DrawerItem("lists", Icons.Default.Folder, currentSection == AppSection.LISTS) {
                    onSectionChange(AppSection.LISTS)
                    scope.launch { drawerState.close() }
                }

                DrawerItem("matches", Icons.Default.Group, currentSection == AppSection.MATCHES) {
                    onSectionChange(AppSection.MATCHES)
                    scope.launch { drawerState.close() }
                }

                DrawerItem("user", Icons.Default.Person, currentSection == AppSection.USER) {
                    onSectionChange(AppSection.USER)
                    scope.launch { drawerState.close() }
                }
            }
        },
        content = content
    )
}