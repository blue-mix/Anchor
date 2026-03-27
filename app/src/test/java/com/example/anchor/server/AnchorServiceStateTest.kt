package com.example.anchor.server

import com.example.anchor.domain.model.ServerStatus
import com.example.anchor.server.service.AnchorServiceState
import com.example.anchor.server.service.LogLevel
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AnchorServiceStateTest {

    @AfterEach
    fun tearDown() {
        // Always reset between tests — singleton state leaks between tests otherwise
        AnchorServiceState.reset()
    }

    // ── Status transitions ────────────────────────────────────

    @Nested
    inner class StatusTransitions {

        @Test
        fun `initial status is Stopped`() {
            assertThat(AnchorServiceState.status.value)
                .isInstanceOf(ServerStatus.Stopped::class.java)
        }

        @Test
        fun `setStarting emits Starting`() {
            AnchorServiceState.setStarting()
            assertThat(AnchorServiceState.status.value)
                .isInstanceOf(ServerStatus.Starting::class.java)
        }

        @Test
        fun `setRunning emits Running with correct url`() {
            AnchorServiceState.setRunning("192.168.1.5", 8080)
            val status = AnchorServiceState.status.value
            assertThat(status).isInstanceOf(ServerStatus.Running::class.java)
            assertThat((status as ServerStatus.Running).url)
                .isEqualTo("http://192.168.1.5:8080")
        }

        @Test
        fun `setRunning boolean overload with true emits Running`() {
            AnchorServiceState.setRunning(true, "192.168.1.5", 9090)
            assertThat(AnchorServiceState.status.value)
                .isInstanceOf(ServerStatus.Running::class.java)
        }

        @Test
        fun `setRunning boolean overload with false emits Stopped`() {
            AnchorServiceState.setRunning("192.168.1.5", 8080)
            AnchorServiceState.setRunning(false)
            assertThat(AnchorServiceState.status.value)
                .isInstanceOf(ServerStatus.Stopped::class.java)
        }

        @Test
        fun `setStopped emits Stopped`() {
            AnchorServiceState.setRunning("192.168.1.5", 8080)
            AnchorServiceState.setStopped()
            assertThat(AnchorServiceState.status.value)
                .isInstanceOf(ServerStatus.Stopped::class.java)
        }

        @Test
        fun `setError emits Error with message`() {
            AnchorServiceState.setError("Network unavailable")
            val status = AnchorServiceState.status.value
            assertThat(status).isInstanceOf(ServerStatus.Error::class.java)
            assertThat((status as ServerStatus.Error).message).isEqualTo("Network unavailable")
        }

        @Test
        fun `reset returns to Stopped`() {
            AnchorServiceState.setRunning("192.168.1.5", 8080)
            AnchorServiceState.reset()
            assertThat(AnchorServiceState.status.value)
                .isInstanceOf(ServerStatus.Stopped::class.java)
        }
    }

    // ── Logging ───────────────────────────────────────────────

    @Nested
    inner class Logging {

        @Test
        fun `addLog appends entry`() {
            AnchorServiceState.addLog("Server started")
            assertThat(AnchorServiceState.logs.value).hasSize(1)
            assertThat(AnchorServiceState.logs.value.first().message)
                .isEqualTo("Server started")
        }

        @Test
        fun `addLog default level is INFO`() {
            AnchorServiceState.addLog("hello")
            assertThat(AnchorServiceState.logs.value.first().level)
                .isEqualTo(LogLevel.INFO)
        }

        @Test
        fun `addLog respects explicit level`() {
            AnchorServiceState.addLog("oops", LogLevel.ERROR)
            assertThat(AnchorServiceState.logs.value.first().level)
                .isEqualTo(LogLevel.ERROR)
        }

        @Test
        fun `logs are capped at 200 entries`() {
            repeat(250) { AnchorServiceState.addLog("entry $it") }
            assertThat(AnchorServiceState.logs.value.size).isAtMost(200)
        }

        @Test
        fun `clearLogs empties the list`() {
            AnchorServiceState.addLog("a")
            AnchorServiceState.addLog("b")
            AnchorServiceState.clearLogs()
            assertThat(AnchorServiceState.logs.value).isEmpty()
        }

        @Test
        fun `setError also appends log entry`() {
            AnchorServiceState.setError("disk full")
            assertThat(AnchorServiceState.logs.value.any { it.message == "disk full" })
                .isTrue()
        }

        @Test
        fun `setRunning appends server-started log`() {
            AnchorServiceState.setRunning("10.0.0.1", 8080)
            assertThat(AnchorServiceState.logs.value.any { it.message.contains("10.0.0.1") })
                .isTrue()
        }

        @Test
        fun `reset clears logs`() {
            AnchorServiceState.addLog("kept?")
            AnchorServiceState.reset()
            assertThat(AnchorServiceState.logs.value).isEmpty()
        }
    }

    // ── Shared directories ────────────────────────────────────

    @Nested
    inner class SharedDirectories {

        @Test
        fun `setSharedDirectories updates flow`() {
            AnchorServiceState.setSharedDirectories(listOf("/storage/emulated/0/Movies"))
            assertThat(AnchorServiceState.sharedDirectories.value).hasSize(1)
        }

        @Test
        fun `reset clears shared directories`() {
            AnchorServiceState.setSharedDirectories(listOf("/some/path"))
            AnchorServiceState.reset()
            assertThat(AnchorServiceState.sharedDirectories.value).isEmpty()
        }
    }
}