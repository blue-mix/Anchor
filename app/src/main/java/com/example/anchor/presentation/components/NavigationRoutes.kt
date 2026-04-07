// app/src/main/java/com.example.anchor.presentation/navigation/NavigationRoutes.kt

package com.example.anchor.presentation.components

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for the app.
 */
sealed interface NavigationRoutes {

    @Serializable
    data object Onboarding : NavigationRoutes

    @Serializable
    data object Dashboard : NavigationRoutes

    @Serializable
    data object Discovery : NavigationRoutes

    @Serializable
    data class RemoteBrowser(
        val deviceName: String,
        val baseUrl: String
    ) : NavigationRoutes

    @Serializable
    data class Player(
        val mediaUrl: String,
        val mediaTitle: String,
        val mimeType: String = ""
    ) : NavigationRoutes
}