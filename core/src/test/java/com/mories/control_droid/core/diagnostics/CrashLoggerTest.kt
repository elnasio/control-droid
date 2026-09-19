package com.mories.control_droid.core.diagnostics

import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CrashLoggerTest {
    private val context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        CrashLogger.clear(context)
    }

    @Test
    fun readLastCrash_returnsNull_whenNoCrashRecorded() {
        assertNull(CrashLogger.readLastCrash(context))
    }

    @Test
    fun install_capturesUncaughtException_andPreviousHandlerStillRuns() {
        var previousHandlerCalled = false
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> previousHandlerCalled = true }

        CrashLogger.install(context)

        val handler = Thread.getDefaultUncaughtExceptionHandler()
        val thread = Thread("crash-test-thread")
        val error = IllegalStateException("boom from test")

        handler?.uncaughtException(thread, error)

        val saved = CrashLogger.readLastCrash(context)
        assertTrue(previousHandlerCalled)
        assertTrue(saved != null && saved.contains("IllegalStateException"))
        assertTrue(saved != null && saved.contains("boom from test"))
        assertTrue(saved != null && saved.contains("crash-test-thread"))
    }

    @Test
    fun clear_removesSavedCrash() {
        val handler = run {
            CrashLogger.install(context)
            Thread.getDefaultUncaughtExceptionHandler()
        }
        handler?.uncaughtException(Thread.currentThread(), RuntimeException("temp"))
        assertTrue(CrashLogger.readLastCrash(context) != null)

        CrashLogger.clear(context)

        assertNull(CrashLogger.readLastCrash(context))
    }
}
