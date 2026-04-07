package com.example.anchor.presentation.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.anchor.core.util.NetworkUtils
import com.example.anchor.core.util.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val mediaPermissionsGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val isConnectedToWifi: Boolean = false,
    val localIpAddress: String? = null,
    val isComplete: Boolean = false
)

enum class OnboardingStep {
    WELCOME,
    MEDIA_PERMISSIONS,
    NOTIFICATION_PERMISSION,
    NETWORK_CHECK,
    COMPLETE
}

/**
 * ViewModel for the onboarding flow.
 *
 * No logic changes from original — this ViewModel only uses [PermissionUtils]
 * and [NetworkUtils] from the core layer, which are unchanged.
 * Package declaration kept as-is; file moved to the presentation screens
 * directory to match the target structure.
 */
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        checkInitialState()
    }

    private fun checkInitialState() {
        val context = getApplication<Application>()
        _uiState.update { state ->
            state.copy(
                mediaPermissionsGranted = PermissionUtils.areMediaPermissionsGranted(context),
                notificationPermissionGranted = PermissionUtils.isNotificationPermissionGranted(
                    context
                ),
                isConnectedToWifi = NetworkUtils.isConnectedToWifi(context),
                localIpAddress = NetworkUtils.getLocalIpAddress(context)
            )
        }
    }

    fun moveToNextStep() {
        viewModelScope.launch {
            val nextStep = when (_uiState.value.currentStep) {
                OnboardingStep.WELCOME -> OnboardingStep.MEDIA_PERMISSIONS
                OnboardingStep.MEDIA_PERMISSIONS -> OnboardingStep.NOTIFICATION_PERMISSION
                OnboardingStep.NOTIFICATION_PERMISSION -> OnboardingStep.NETWORK_CHECK
                OnboardingStep.NETWORK_CHECK -> OnboardingStep.COMPLETE
                OnboardingStep.COMPLETE -> OnboardingStep.COMPLETE
            }
            _uiState.update { it.copy(currentStep = nextStep) }
            if (nextStep == OnboardingStep.COMPLETE) {
                _uiState.update { it.copy(isComplete = true) }
            }
        }
    }

    fun onMediaPermissionsResult(granted: Boolean) {
        _uiState.update { it.copy(mediaPermissionsGranted = granted) }
        if (granted) moveToNextStep()
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(notificationPermissionGranted = granted) }
        moveToNextStep()   // notification permission is optional — always advance
    }

    fun refreshNetworkState() {
        val context = getApplication<Application>()
        _uiState.update { state ->
            state.copy(
                isConnectedToWifi = NetworkUtils.isConnectedToWifi(context),
                localIpAddress = NetworkUtils.getLocalIpAddress(context)
            )
        }
    }

    fun skipToComplete() {
        _uiState.update { it.copy(currentStep = OnboardingStep.COMPLETE, isComplete = true) }
    }
}