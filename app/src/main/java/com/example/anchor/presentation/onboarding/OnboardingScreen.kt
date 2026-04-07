package com.example.anchor.presentation.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.anchor.core.util.PermissionUtils
import org.koin.androidx.compose.koinViewModel

/**
 * Onboarding screen — walks the user through permissions and network check.
 *
 * No type changes from original — all types used here are from the core
 * and onboarding packages, both unchanged.
 * Only change: [viewModel()] → [koinViewModel()].
 */
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) onOnboardingComplete()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = uiState.currentStep,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn())
                    .togetherWith(slideOutHorizontally { -it } + fadeOut())
            },
            label = "onboarding_transition"
        ) { step ->
            when (step) {
                OnboardingStep.WELCOME ->
                    WelcomeStep(onContinue = { viewModel.moveToNextStep() })

                OnboardingStep.MEDIA_PERMISSIONS ->
                    MediaPermissionsStep(
                        onPermissionsResult = { viewModel.onMediaPermissionsResult(it) },
                        onSkip = { viewModel.moveToNextStep() }
                    )

                OnboardingStep.NOTIFICATION_PERMISSION ->
                    NotificationPermissionStep(
                        onPermissionResult = { viewModel.onNotificationPermissionResult(it) },
                        onSkip = { viewModel.moveToNextStep() }
                    )

                OnboardingStep.NETWORK_CHECK ->
                    NetworkCheckStep(
                        isConnected = uiState.isConnectedToWifi,
                        ipAddress = uiState.localIpAddress,
                        onRefresh = { viewModel.refreshNetworkState() },
                        onContinue = { viewModel.moveToNextStep() }
                    )

                OnboardingStep.COMPLETE ->
                    CompleteStep(onFinish = { viewModel.skipToComplete() })
            }
        }
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    OnboardingStepLayout(
        icon = Icons.Rounded.Wifi,
        title = "Welcome to Anchor",
        description = "Turn your phone into a local media server. Stream videos, music, and photos to any device on your Wi-Fi network — no internet or cloud required.",
        primaryButtonText = "Get Started",
        onPrimaryClick = onContinue
    )
}

@Composable
private fun MediaPermissionsStep(
    onPermissionsResult: (Boolean) -> Unit,
    onSkip: () -> Unit
) {
    val permissions = PermissionUtils.getRequiredMediaPermissions()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { map -> onPermissionsResult(map.values.all { it }) }

    OnboardingStepLayout(
        icon = Icons.Rounded.Folder,
        title = "Access Your Media",
        description = "Anchor needs permission to access your videos, music, and photos so it can share them with other devices on your network.",
        primaryButtonText = "Grant Access",
        onPrimaryClick = { launcher.launch(permissions.toTypedArray()) },
        secondaryButtonText = "Skip for Now",
        onSecondaryClick = onSkip
    )
}

@Composable
private fun NotificationPermissionStep(
    onPermissionResult: (Boolean) -> Unit,
    onSkip: () -> Unit
) {
    val permission = PermissionUtils.getNotificationPermission()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onPermissionResult(it) }

    OnboardingStepLayout(
        icon = Icons.Rounded.Notifications,
        title = "Stay Informed",
        description = "Enable notifications to see when Anchor is actively serving media.",
        primaryButtonText = "Enable Notifications",
        onPrimaryClick = { permission?.let { launcher.launch(it) } ?: onPermissionResult(true) },
        secondaryButtonText = "Skip",
        onSecondaryClick = onSkip
    )
}

@Composable
private fun NetworkCheckStep(
    isConnected: Boolean,
    ipAddress: String?,
    onRefresh: () -> Unit,
    onContinue: () -> Unit
) {
    OnboardingStepLayout(
        icon = Icons.Rounded.Wifi,
        title = if (isConnected) "Connected!" else "Connect to Wi-Fi",
        description = if (isConnected)
            "You're connected to your local network.\n\nYour device IP: ${ipAddress ?: "Detecting…"}"
        else
            "Please connect to a Wi-Fi network to use Anchor.",
        primaryButtonText = if (isConnected) "Continue" else "Refresh",
        onPrimaryClick = if (isConnected) onContinue else onRefresh,
        secondaryButtonText = if (!isConnected) "Continue Anyway" else null,
        onSecondaryClick = if (!isConnected) onContinue else null
    )
}

@Composable
private fun CompleteStep(onFinish: () -> Unit) {
    OnboardingStepLayout(
        icon = Icons.Rounded.CheckCircle,
        title = "You're All Set!",
        description = "Anchor is ready to use. Start hosting your media or discover other servers on your network.",
        primaryButtonText = "Start Using Anchor",
        onPrimaryClick = onFinish
    )
}

@Composable
private fun OnboardingStepLayout(
    icon: ImageVector,
    title: String,
    description: String,
    primaryButtonText: String,
    onPrimaryClick: () -> Unit,
    secondaryButtonText: String? = null,
    onSecondaryClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.size(120.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onPrimaryClick, modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)) {
            Text(primaryButtonText)
        }
        if (secondaryButtonText != null && onSecondaryClick != null) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onSecondaryClick, modifier = Modifier.fillMaxWidth()) {
                Text(secondaryButtonText)
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}