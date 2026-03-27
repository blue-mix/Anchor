package com.example.anchor.core.extension

import com.example.anchor.core.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

// ── Flow → Result transformations ────────────────────────────

/**
 * Maps each emission of a [Flow<T>] into [Result.Success<T>], and
 * catches any exception into [Result.Error], so downstream collectors
 * never see a thrown exception.
 */
fun <T> Flow<T>.asResult(): Flow<Result<T>> =
    this.map<T, Result<T>> { Result.Success(it) }
        .catch { e -> emit(Result.Error(e.message ?: "Unknown error", e)) }

/**
 * Logs each emission to [android.util.Log] under [tag] at the debug level.
 * Useful for tracing StateFlow updates during development.
 */
fun <T> Flow<T>.debugLog(tag: String, transform: (T) -> String = { it.toString() }): Flow<T> =
    this.onEach { android.util.Log.d(tag, transform(it)) }

// ── Result flow helpers ───────────────────────────────────────

/**
 * Maps only the [Result.Success] emissions of a [Flow<Result<T>>],
 * leaving [Result.Error] and [Result.Loading] unchanged.
 */
fun <T, R> Flow<Result<T>>.mapSuccess(transform: (T) -> R): Flow<Result<R>> =
    this.map { result -> result.map(transform) }