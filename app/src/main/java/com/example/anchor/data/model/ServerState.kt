package com.example.anchor.data.model

sealed class ServerState {
    object Stopped : ServerState()
    object Starting : ServerState()
    data class Running(val ipAddress: String, val port: Int) : ServerState()
    data class Error(val message: String) : ServerState()
}