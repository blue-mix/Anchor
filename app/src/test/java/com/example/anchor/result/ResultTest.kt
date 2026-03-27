package com.example.anchor.result

import com.example.anchor.core.result.Result
import com.example.anchor.core.result.resultOf
import com.example.anchor.core.result.suspendResultOf
import com.example.anchor.core.result.toResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ResultTest {

    // ── Success ───────────────────────────────────────────────

    @Nested
    inner class SuccessTests {

        @Test
        fun `isSuccess is true for Success`() {
            assertThat(Result.Success(1).isSuccess).isTrue()
        }

        @Test
        fun `isError and isLoading are false for Success`() {
            val r = Result.Success("hello")
            assertThat(r.isError).isFalse()
            assertThat(r.isLoading).isFalse()
        }

        @Test
        fun `getOrNull returns data on Success`() {
            assertThat(Result.Success(42).getOrNull()).isEqualTo(42)
        }

        @Test
        fun `getOrDefault returns data on Success`() {
            assertThat(Result.Success(7).getOrDefault(99)).isEqualTo(7)
        }

        @Test
        fun `getOrThrow returns data on Success`() {
            assertThat(Result.Success("ok").getOrThrow()).isEqualTo("ok")
        }

        @Test
        fun `map transforms value on Success`() {
            val result = Result.Success(10).map { it * 3 }
            assertThat((result as Result.Success).data).isEqualTo(30)
        }

        @Test
        fun `flatMap chains to another Result`() {
            val result = Result.Success(5).flatMap { Result.Success(it + 1) }
            assertThat((result as Result.Success).data).isEqualTo(6)
        }

        @Test
        fun `flatMap propagates inner Error`() {
            val result = Result.Success(5).flatMap { Result.Error("nope") }
            assertThat(result.isError).isTrue()
        }

        @Test
        fun `onSuccess callback fires`() {
            var fired = false
            Result.Success(1).onSuccess { fired = true }
            assertThat(fired).isTrue()
        }

        @Test
        fun `onError callback does not fire on Success`() {
            var fired = false
            Result.Success(1).onError { _, _ -> fired = true }
            assertThat(fired).isFalse()
        }
    }

    // ── Error ─────────────────────────────────────────────────

    @Nested
    inner class ErrorTests {

        @Test
        fun `isError is true for Error`() {
            assertThat(Result.Error("boom").isError).isTrue()
        }

//        @Test
//        fun `getOrNull returns null on Error`() {
//            assertThat(Result.Error("x").getOrNull()).isNull()
//        }

        @Test
        fun `getOrDefault returns default on Error`() {
            val r: Result<Int> = Result.Error("x")
            assertThat(r.getOrDefault(99)).isEqualTo(99)
        }

        @Test
        fun `getOrThrow throws on Error`() {
            val r: Result<Int> = Result.Error("explode", RuntimeException("inner"))
            val thrown = runCatching { r.getOrThrow() }.exceptionOrNull()
            assertThat(thrown).isNotNull()
        }

        @Test
        fun `map passes through Error unchanged`() {
            val r: Result<Int> = Result.Error("oops")
            val mapped = r.map { it * 2 }
            assertThat(mapped.isError).isTrue()
            assertThat((mapped as Result.Error).message).isEqualTo("oops")
        }

        @Test
        fun `errorOrNull returns Error instance`() {
            val r: Result<Int> = Result.Error("msg", IllegalStateException())
            assertThat(r.errorOrNull()).isNotNull()
        }

        @Test
        fun `errorOrNull returns null on Success`() {
            assertThat(Result.Success(1).errorOrNull()).isNull()
        }

        @Test
        fun `onError callback fires with correct message`() {
            var captured = ""
            Result.Error("failed").onError { msg, _ -> captured = msg }
            assertThat(captured).isEqualTo("failed")
        }

        @Test
        fun `onSuccess callback does not fire on Error`() {
            var fired = false
            val r: Result<Int> = Result.Error("x")
            r.onSuccess { fired = true }
            assertThat(fired).isFalse()
        }
    }

    // ── Loading ───────────────────────────────────────────────

    @Nested
    inner class LoadingTests {

        @Test
        fun `isLoading is true for Loading`() {
            assertThat(Result.Loading.isLoading).isTrue()
        }

        @Test
        fun `getOrNull returns null on Loading`() {
            assertThat((Result.Loading as Result<Int>).getOrNull()).isNull()
        }

        @Test
        fun `onLoading callback fires`() {
            var fired = false
            (Result.Loading as Result<Int>).onLoading { fired = true }
            assertThat(fired).isTrue()
        }
    }

    // ── resultOf ──────────────────────────────────────────────

    @Nested
    inner class ResultOfTests {

        @Test
        fun `wraps successful block`() {
            val result = resultOf { 1 + 1 }
            assertThat(result.isSuccess).isTrue()
            assertThat((result as Result.Success).data).isEqualTo(2)
        }

        @Test
        fun `wraps exception as Error`() {
            val result = resultOf<Int> { throw IllegalStateException("kaboom") }
            assertThat(result.isError).isTrue()
            assertThat((result as Result.Error).message).isEqualTo("kaboom")
        }

        @Test
        fun `includes throwable cause on Error`() {
            val cause = RuntimeException("root cause")
            val result = resultOf<Int> { throw cause }
            assertThat((result as Result.Error).cause).isEqualTo(cause)
        }
    }

    // ── suspendResultOf ───────────────────────────────────────

    @Nested
    inner class SuspendResultOfTests {

        @Test
        fun `wraps coroutine result`() = runTest {
            val result = suspendResultOf { "async value" }
            assertThat(result.isSuccess).isTrue()
            assertThat((result as Result.Success).data).isEqualTo("async value")
        }

        @Test
        fun `wraps coroutine exception`() = runTest {
            val result = suspendResultOf<Int> { throw Exception("async error") }
            assertThat(result.isError).isTrue()
        }
    }

    // ── toResult ──────────────────────────────────────────────

    @Nested
    inner class ToResultTests {

        @Test
        fun `non-null value becomes Success`() {
            val result = "hello".toResult()
            assertThat(result.isSuccess).isTrue()
        }

        @Test
        fun `null value becomes Error`() {
            val result = (null as String?).toResult("was null")
            assertThat(result.isError).isTrue()
            assertThat((result as Result.Error).message).isEqualTo("was null")
        }
    }
}