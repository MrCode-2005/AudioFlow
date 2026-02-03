package com.audioflow.player.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.audioflow.player.ui.home.HomeScreen
import com.audioflow.player.ui.library.LibraryScreen
import com.audioflow.player.ui.player.NowPlayingScreen
import com.audioflow.player.ui.search.SearchScreen
import com.audioflow.player.ui.theme.*

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : Screen(
        route = "home",
        title = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )
    
    data object Search : Screen(
        route = "search",
        title = "Search",
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search
    )
    
    data object Library : Screen(
        route = "library",
        title = "Your Library",
        selectedIcon = Icons.Filled.LibraryMusic,
        unselectedIcon = Icons.Outlined.LibraryMusic
    )
    
    data object NowPlaying : Screen(
        route = "now_playing",
        title = "Now Playing",
        selectedIcon = Icons.Filled.PlayCircle,
        unselectedIcon = Icons.Outlined.PlayCircle
    )
}

@Composable
fun AudioFlowNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    val bottomNavScreens = listOf(Screen.Home, Screen.Search, Screen.Library)
    val showBottomBar = currentDestination?.route in bottomNavScreens.map { it.route }
    
    Scaffold(
        containerColor = SpotifyBlack,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = SpotifyBlack,
                    contentColor = TextPrimary
                ) {
                    bottomNavScreens.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TextPrimary,
                                selectedTextColor = TextPrimary,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = SpotifyBlack
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onTrackClick = { /* Already playing from HomeScreen */ },
                    onNavigateToPlayer = {
                        navController.navigate(Screen.NowPlaying.route)
                    }
                )
            }
            
            composable(Screen.Search.route) {
                SearchScreen(
                    onNavigateToPlayer = {
                        navController.navigate(Screen.NowPlaying.route)
                    }
                )
            }
            
            composable(Screen.Library.route) {
                LibraryScreen(
                    onNavigateToPlayer = {
                        navController.navigate(Screen.NowPlaying.route)
                    }
                )
            }
            
            composable(Screen.NowPlaying.route) {
                NowPlayingScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
