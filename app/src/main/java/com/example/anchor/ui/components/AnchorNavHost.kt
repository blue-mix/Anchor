package com.example.anchor.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.anchor.ui.DiscoveryScreen
import com.example.anchor.ui.browser.RemoteBrowserScreen
import com.example.anchor.ui.dashboard.DashboardScreen
import com.example.anchor.ui.onboarding.OnboardingScreen


@Composable
fun AnchorNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: NavigationRoutes = NavigationRoutes.Dashboard
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable<NavigationRoutes.Onboarding> {
            OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate(NavigationRoutes.Dashboard) {
                        popUpTo(NavigationRoutes.Onboarding) { inclusive = true }
                    }
                }
            )
        }

        composable<NavigationRoutes.Dashboard> {
            DashboardScreen(
                onNavigateToDiscovery = {
                    navController.navigate(NavigationRoutes.Discovery)
                }
            )
        }

        composable<NavigationRoutes.Discovery> {
            DiscoveryScreen(
                onDeviceClick = { device ->
                    navController.navigate(
                        NavigationRoutes.RemoteBrowser(
                            deviceName = device.displayName,
                            baseUrl = device.baseUrl
                        )
                    )
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable<NavigationRoutes.RemoteBrowser> { backStackEntry ->
            val route: NavigationRoutes.RemoteBrowser = backStackEntry.toRoute()
            RemoteBrowserScreen(
                deviceName = route.deviceName,
                baseUrl = route.baseUrl,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onPlayMedia = { url, title, mimeType ->
                    navController.navigate(
                        NavigationRoutes.Player(
                            mediaUrl = url,
                            mediaTitle = title,
                            mimeType = mimeType
                        )
                    )
                }
            )
        }

        composable<NavigationRoutes.Player> { backStackEntry ->
            val route: NavigationRoutes.Player = backStackEntry.toRoute()
            PlayerScreen(
                mediaUrl = route.mediaUrl,
                mediaTitle = route.mediaTitle,
                mimeType = route.mimeType,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}