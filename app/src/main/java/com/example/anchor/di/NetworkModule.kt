package com.example.anchor.di

import com.example.anchor.data.source.remote.HttpClientDataSource
import com.example.anchor.data.source.remote.SsdpDataSource
import kotlinx.serialization.json.Json
import org.koin.dsl.module

/**
 * Networking and serialisation dependencies.
 *
 * Registered here:
 *  - Shared [Json] instance (used by Ktor content-negotiation AND HttpClientDataSource)
 *  - [SsdpDataSource] — raw UDP socket management
 *  - [HttpClientDataSource] — HTTP calls to remote Anchor / UPnP devices
 */
val networkModule = module {

    /**
     * Shared [Json] instance.
     * lenient = true tolerates minor schema differences between Anchor versions.
     */
    single {
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
        }
    }

    /**
     * SsdpDataSource — stateless socket factory.
     * Sockets are opened per-call, so a singleton is fine and lightweight.
     */
    single { SsdpDataSource() }

    /**
     * HttpClientDataSource — uses [java.net.URL.readText] under the hood.
     * Receives the shared Json instance for DTO deserialization.
     */
    single { HttpClientDataSource(get()) }
}