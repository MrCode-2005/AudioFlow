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
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.audioflow.player.ui.create.CreateScreen
import com.audioflow.player.ui.home.HomeScreen
import com.audioflow.player.ui.library.LibraryScreen
import com.audioflow.player.ui.player.NowPlayingScreen
import com.audioflow.player.ui.search.SearchScreen
import com.audioflow.player.ui.settings.SettingsScreen
import com.audioflow.player.ui.settings.YouTubeLoginScreen
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
        title = "Library",
        selectedIcon = Icons.Filled.LibraryMusic,
        unselectedIcon = Icons.Outlined.LibraryMusic
    )
    
    data object Create : Screen(
        route = "create",
        title = "Create",
        selectedIcon = Icons.Filled.AddCircle,
        unselectedIcon = Icons.Outlined.AddCircle
    )
    
    data object NowPlaying : Screen(
        route = "now_playing",
        title = "Now Playing",
        selectedIcon = Icons.Filled.PlayCircle,
        unselectedIcon = Icons.Outlined.PlayCircle
    )
    
    data object Settings : Screen(
        route = "settings",
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
    
    data object YouTubeLogin : Screen(
        route = "youtube_login",
        title = "YouTube Login",
        selectedIcon = Icons.Filled.Login,
        unselectedIcon = Icons.Outlined.Lock
    )

    data object PlaylistDetail : Screen(
        route = "playlist/{playlistId}",
        title = "Playlist",
        selectedIcon = Icons.Filled.PlaylistPlay,
        unselectedIcon = Icons.Outlined.PlaylistPlay
    )
}

@Composable
fun AudioFlowNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    val bottomNavScreens = listOf(Screen.Home, Screen.Search, Screen.Create, Screen.Library)
    // Check if current route starts with any bottom nav screen route (to handle arguments)
    val showBottomBar = currentDestination?.route?.let { route ->
        bottomNavScreens.any { screen -> route.startsWith(screen.route) }
    } == true
    
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
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onNavigateToSearch = {
                        navController.navigate(Screen.Search.route)
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
            
            composable(
                route = "${Screen.Library.route}?filter={filter}",
                arguments = listOf(
                    navArgument("filter") {
                        type = NavType.StringType
                        nullable = true
                    }
                )
            ) {
                LibraryScreen(
                    onNavigateToPlayer = {
                        navController.navigate(Screen.NowPlaying.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToPlaylist = { playlistId ->
                        navController.navigate(Screen.PlaylistDetail.route.replace("{playlistId}", playlistId))
                    }
                )
            }
            
            composable(Screen.Create.route) {
                CreateScreen(
                    onNavigateToPlayer = {
                        navController.navigate(Screen.NowPlaying.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToPlaylist = { playlistId ->
                        navController.navigate(Screen.PlaylistDetail.route.replace("{playlistId}", playlistId))
                    },
                    onNavigateToLibrary = {
                        navController.navigate("${Screen.Library.route}?filter=PLAYLISTS") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

        composable(Screen.PlaylistDetail.route) {
            com.audioflow.player.ui.library.PlaylistDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlayer = {
                    navController.navigate(Screen.NowPlaying.route) {
                        launchSingleTop = true
                    }
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
            
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = {
                        navController.popBackStack()
                    },
                    onConnectYouTube = {
                        navController.navigate(Screen.YouTubeLogin.route)
                    }
                )
            }
            
            composable(Screen.YouTubeLogin.route) {
                YouTubeLoginScreen(
                    onLoginSuccess = {
                        navController.popBackStack(Screen.Settings.route, false)
                    },
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
