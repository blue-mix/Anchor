package com.example.anchor.core.result

import kotlinx.coroutines.CancellationException

/**
 * Typed wrapper for all repository and use-case return values.
 */
sealed class Result<out T> {

    data class Success<out T>(val data: T) : Result<T>()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : Result<Nothing>()

    data object Loading : Result<Nothing>()

    // ── Transformation ────────────────────────────────────────

    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> this
    }

    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
        is Loading -> this
    }

    fun getOrNull(): T? = if (this is Success) data else null

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw cause ?: IllegalStateException(message)
        is Loading -> throw IllegalStateException("Result is still loading")
    }

    fun getOrDefault(default: @UnsafeVariance T): T =
        if (this is Success) data else default

    fun errorOrNull(): Error? = this as? Error

    // ── Side-effect callbacks ─────────────────────────────────

    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (message: String, cause: Throwable?) -> Unit): Result<T> {
        if (this is Error) action(message, cause)
        return this
    }

    inline fun onLoading(action: () -> Unit): Result<T> {
        if (this is Loading) action()
        return this
    }

    // ── State checks ──────────────────────────────────────────

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    // ── Companion ─────────────────────────────────────────────

    companion object {
        fun error(message: String, cause: Throwable? = null): Result<Nothing> =
            Error(message, cause)
    }
}

/**
 * Wraps a potentially-throwing block in [Result.Success] or [Result.Error].
 */
inline fun <T> resultOf(block: () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.Error(e.message ?: "Unknown error", e)
}

/**
 * Suspending variant of [resultOf].
 */
suspend inline fun <T> suspendResultOf(crossinline block: suspend () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.Error(e.message ?: "Unknown error", e)
}

/**
 * Converts a nullable value to [Result], using [errorMessage] when null.
 */
fun <T : Any> T?.toResult(errorMessage: String = "Value was null"): Result<T> =
    if (this != null) Result.Success(this) else Result.Error(errorMessage)
