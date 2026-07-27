package com.smartreminder.core.audio

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Smoke test of the full AudioRecord -> encoder path on the emulator. The emulator mic may
 * only provide silence, so this asserts the mechanics (a valid non-empty .m4a is produced and
 * the recorder reaches Finished), not audio content.
 */
@RunWith(AndroidJUnit4::class)
class VoiceRecorderTest {

    @get:Rule
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private lateinit var outFile: File

    @After
    fun tearDown() {
        if (::outFile.isInitialized) outFile.delete()
    }

    @Test
    fun recordsShortClip_reachesFinishedWithFile() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        outFile = File(ctx.cacheDir, "recorder-test-${System.nanoTime()}.m4a")

        val recorder = VoiceRecorder()
        recorder.start(outFile)

        // Let it capture ~1.2s.
        val startedBy = System.currentTimeMillis() + 3_000
        while (!recorder.isRecording && System.currentTimeMillis() < startedBy) Thread.sleep(20)
        Thread.sleep(1_200)
        recorder.stop()

        val deadline = System.currentTimeMillis() + 5_000
        while (recorder.state.value !is RecorderState.Finished &&
            recorder.state.value !is RecorderState.Failed &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(50)
        }

        val state = recorder.state.value
        assertTrue("expected Finished but was $state", state is RecorderState.Finished)
        state as RecorderState.Finished
        assertTrue("file should exist", state.file.exists())
        assertTrue("file should be non-empty", state.file.length() > 0)
        assertTrue("duration should be > 0", state.durationMs > 0)
    }
}
