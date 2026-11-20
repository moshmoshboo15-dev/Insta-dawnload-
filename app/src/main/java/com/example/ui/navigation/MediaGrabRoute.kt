package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface MediaGrabRoute {
    @Serializable
    data object Download : MediaGrabRoute

    @Serializable
    data object History : MediaGrabRoute

    @Serializable
    data object WhatsApp : MediaGrabRoute

    @Serializable
    data object Settings : MediaGrabRoute
}
