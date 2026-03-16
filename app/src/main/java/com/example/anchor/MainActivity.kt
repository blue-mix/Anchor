package com.example.anchor

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
import com.example.anchor.ui.components.AnchorNavHost
import com.example.anchor.ui.components.NavigationRoutes
import com.example.anchor.ui.onboarding.OnboardingScreen
import com.example.anchor.ui.theme.AnchorTheme


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

