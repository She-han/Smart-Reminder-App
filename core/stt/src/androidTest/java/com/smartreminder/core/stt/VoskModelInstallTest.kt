package com.smartreminder.core.stt

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies the real download -> unzip -> verify pipeline against the live Vosk model host.
 * Network-dependent by nature (first-launch model install always needs the network once).
 */
class VoskModelInstallTest {

    @Test
    fun ensureModel_downloadsUnpacksAndVerifies() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = VoskModelManager(ctx)

        val result = manager.ensureModel()

        assertTrue("model install failed: ${result.exceptionOrNull()}", result.isSuccess)
        val dir = result.getOrThrow()
        assertTrue(File(dir, "conf/model.conf").exists())
        assertTrue(File(dir, "am/final.mdl").exists())
        assertTrue(manager.isReady())
        assertTrue(manager.state.value is ModelState.Ready)
    }
}
