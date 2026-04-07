package com.example.anchor.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.anchor.core.util.PermissionUtils
import com.example.anchor.presentation.components.AnchorNavHost
import com.example.anchor.presentation.components.NavigationRoutes
import com.example.anchor.presentation.onboarding.OnboardingScreen
import com.example.anchor.presentation.theme.AnchorTheme

/**
 * Single-activity entry point.
 *
 * No logic changes from original — this file only uses [PermissionUtils]
 * from the core layer and the navigation / UI component layer, both unchanged.
 *
 * The only structural note: [OnboardingScreen] and [AnchorNavHost] still
 * reference their existing package paths; those are updated in the
 * presentation layer pass (screens + navigation).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val permissionsGranted = PermissionUtils.areAllPermissionsGranted(this)

        setContent {
            AnchorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var onboardingComplete by rememberSaveable {
                        mutableStateOf(permissionsGranted)
                    }

                    if (!onboardingComplete) {
                        OnboardingScreen(
                            onOnboardingComplete = { onboardingComplete = true }
                        )
                    } else {
                        val navController = rememberNavController()
                        AnchorNavHost(
                            navController = navController,
                            startDestination = NavigationRoutes.Dashboard
                        )
                    }
                }
            }
        }
    }
}