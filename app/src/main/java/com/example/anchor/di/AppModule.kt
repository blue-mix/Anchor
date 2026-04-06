package com.example.anchor.di

import com.example.anchor.core.util.MulticastLockManager
import com.example.anchor.data.source.local.FileSystemDataSource
import com.example.anchor.data.source.local.PreferencesDataSource
import com.example.anchor.data.source.local.ThumbnailCache
import com.example.anchor.server.AndroidServerController
import com.example.anchor.server.ServerController
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Application-wide singletons with no more specific category.
 */
val appModule = module {

    // ── Local Data Sources ────────────────────────────────────
    single { FileSystemDataSource() }
    
    // Lazy initialize heavy components
    single { ThumbnailCache(androidContext()) }
    single { PreferencesDataSource(androidContext()) }
    single { MulticastLockManager(androidContext()) }

    // ── Infrastructure ────────────────────────────────────────
    single<ServerController> { AndroidServerController(androidContext()) }
}
