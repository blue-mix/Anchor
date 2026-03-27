package com.example.anchor.di

import com.example.anchor.data.repository.DeviceRepositoryImpl
import com.example.anchor.data.repository.MediaRepositoryImpl
import com.example.anchor.data.repository.ServerRepositoryImpl
import com.example.anchor.domain.repository.DeviceRepository
import com.example.anchor.domain.repository.MediaRepository
import com.example.anchor.domain.repository.ServerRepository
import com.example.anchor.server.AnchorHttpServer
import com.example.anchor.server.UpnpDiscoveryManager
import com.example.anchor.server.handler.BrowseHandler
import com.example.anchor.server.handler.FileHandler
import com.example.anchor.server.handler.ThumbnailHandler
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val serverModule = module {

    single(named("serverPort")) { 8080 }
    
    single<MediaRepository> { MediaRepositoryImpl(get(), get()) }
    single<DeviceRepository> { DeviceRepositoryImpl(get(), get()) }
    single<ServerRepository> { ServerRepositoryImpl(get()) }
    
    single { UpnpDiscoveryManager(androidContext()) }

    // ── Handlers ──────────────────────────────────────────────
    
    single { FileHandler() }
    single { BrowseHandler(get()) }
    single { ThumbnailHandler(get()) }

    // ── Server Orchestrator ───────────────────────────────────
    
    single {
        AnchorHttpServer(
            context = androidContext(),
            port = get(named("serverPort")),
            mediaRepository = get(),
            json = get()
        )
    }
}