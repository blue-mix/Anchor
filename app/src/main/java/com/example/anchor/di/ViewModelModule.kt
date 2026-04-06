package com.example.anchor.di

import com.example.anchor.domain.usecase.discovery.GetDiscoveredDevicesUseCase
import com.example.anchor.domain.usecase.discovery.StartDiscoveryUseCase
import com.example.anchor.domain.usecase.discovery.StopDiscoveryUseCase
import com.example.anchor.domain.usecase.media.BrowseMediaUseCase
import com.example.anchor.domain.usecase.media.GetDirectoryStatsUseCase
import com.example.anchor.domain.usecase.media.GetMediaItemUseCase
import com.example.anchor.domain.usecase.media.GetThumbnailUseCase
import com.example.anchor.domain.usecase.media.StreamMediaUseCase
import com.example.anchor.domain.usecase.server.AddSharedDirectoryUseCase
import com.example.anchor.domain.usecase.server.StartServerUseCase
import com.example.anchor.domain.usecase.server.StopServerUseCase
import com.example.anchor.ui.discovery.DiscoveryViewModel
import com.example.anchor.ui.browser.RemoteBrowserViewModel
import com.example.anchor.ui.dashboard.DashboardViewModel
import com.example.anchor.ui.onboarding.OnboardingViewModel
import com.example.anchor.ui.player.PlayerViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    // ── Media Use Cases ──────────────────────────────────────
    factory { BrowseMediaUseCase(get()) }
    factory { GetMediaItemUseCase(get()) }
    factory { GetThumbnailUseCase(get()) }
    factory { GetDirectoryStatsUseCase(get()) }
    factory { StreamMediaUseCase() }

    // ── Server Use Cases ─────────────────────────────────────
    factory { StartServerUseCase(get()) }
    factory { StopServerUseCase(get()) }
    factory { AddSharedDirectoryUseCase(get()) }

    // ── Discovery Use Cases ──────────────────────────────────
    factory { StartDiscoveryUseCase(get()) }
    factory { StopDiscoveryUseCase(get()) }
    factory { GetDiscoveredDevicesUseCase(get()) }

    // ── ViewModels ────────────────────────────────────────────
    viewModel { DiscoveryViewModel(get(), get(), get()) }
    viewModel { DashboardViewModel(get(),get()) }
    viewModel { RemoteBrowserViewModel() }
    viewModel { OnboardingViewModel(get()) }
    viewModel { PlayerViewModel(get()) }
}