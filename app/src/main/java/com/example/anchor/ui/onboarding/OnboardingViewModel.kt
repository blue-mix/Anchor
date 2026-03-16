package com.example.anchor.ui.onboarding

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
                notificationPermissionGranted = PermissionUtils.isNotificationPermissionGranted(context),
                isConnectedToWifi = NetworkUtils.isConnectedToWifi(context),
                localIpAddress = NetworkUtils.getLocalIpAddress(context)
            )
        }
    }

    fun moveToNextStep() {
        viewModelScope.launch {
            val currentStep = _uiState.value.currentStep
            val nextStep = when (currentStep) {
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
        if (granted) {
            moveToNextStep()
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(notificationPermissionGranted = granted) }
        // Move forward regardless - notification permission is optional
        moveToNextStep()
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
        _uiState.update {
            it.copy(
                currentStep = OnboardingStep.COMPLETE,
                isComplete = true
            )
        }
    }
}