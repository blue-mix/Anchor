package com.example.anchor.di

import com.example.anchor.core.result.Result
import com.example.anchor.core.util.MulticastLockManager
import com.example.anchor.data.source.local.FileSystemDataSource
import com.example.anchor.data.source.local.PreferencesDataSource
import com.example.anchor.data.source.local.ThumbnailCache
import com.example.anchor.domain.model.DirectoryListing
import com.example.anchor.domain.repository.MediaRepository
import com.example.anchor.domain.usecase.media.BrowseMediaUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File

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

    // ── Use Case Optimization ─────────────────────────────────
    // Using factory for UseCases to ensure fresh state if needed,
    // though most are stateless.
    factory { BrowseMediaUseCase(get()) }
}