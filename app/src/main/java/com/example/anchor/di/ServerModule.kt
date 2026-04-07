package com.example.anchor.di

import com.example.anchor.data.repository.DeviceRepositoryImpl
import com.example.anchor.data.repository.MediaRepositoryImpl
import com.example.anchor.data.repository.ServerRepositoryImpl
import com.example.anchor.domain.repository.DeviceRepository
import com.example.anchor.domain.repository.MediaRepository
import com.example.anchor.domain.repository.ServerRepository
import com.example.anchor.data.server.AnchorHttpServer
import com.example.anchor.data.server.DlnaManager
import com.example.anchor.data.server.PathResolver
import com.example.anchor.data.server.RouteHandlers
import com.example.anchor.data.server.RouteProvider
import com.example.anchor.data.server.ServerInfoBuilder
import com.example.anchor.data.server.SharedDirectoryManager
import com.example.anchor.data.server.UpnpDiscoveryManager
import com.example.anchor.data.server.handler.BrowseHandler
import com.example.anchor.data.server.handler.FileHandler
import com.example.anchor.data.server.handler.SoapHandler
import com.example.anchor.data.server.handler.ThumbnailHandler
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val serverModule = module {

    single(named("serverPort")) { 8080 }
    
    single<MediaRepository> { MediaRepositoryImpl(get(), get()) }
    single<DeviceRepository> { DeviceRepositoryImpl(get(), get()) }
    single<ServerRepository> { ServerRepositoryImpl(get()) }
    
    single { UpnpDiscoveryManager(androidContext()) }

    // ── Managers ─────────────────────────────────────────────
    
    single { SharedDirectoryManager() }
    single { 
        DlnaManager(
            context = androidContext(),
            directoryManager = get()
        )
    }
    single { ServerInfoBuilder(get()) }
    single { PathResolver(get()) }

    // ── Handlers ──────────────────────────────────────────────
    
    single { FileHandler(get()) }
    single { BrowseHandler(get(), get(),get()) }
    single { ThumbnailHandler(get(), get()) }
    single { SoapHandler(get()) }
    
    single { 
        RouteHandlers(
            browse = get(),
            file = get(),
            thumbnail = get(),
            soap = get()
        )
    }

    // ── Routing ───────────────────────────────────────────────
    
    single {
        RouteProvider(
            directoryManager = get(),
            handlers = get(),
            dlnaManager = get(),
            serverInfoBuilder = get(),
            json = get()
        )
    }

    // ── Server Orchestrator ───────────────────────────────────
    
    single {
        AnchorHttpServer(
            port = get(named("serverPort")),
            directoryManager = get(),
            dlnaManager = get(),
            routeProvider = get()
        )
    }
}