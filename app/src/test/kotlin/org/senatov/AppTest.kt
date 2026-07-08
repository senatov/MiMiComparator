/*
 * Smoke test — verifies FXML loads w/o crash.
 * Iakov Senatov, 2026
 */
package org.senatov

import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppTest {

    @Test
    fun fxmlResourceExists() {
        val url = App::class.java.getResource("/org/senatov/MiMiComparator.fxml")
        assertNotNull(url, "FXML resource must be on classpath")
    }

    @Test
    fun defaultLogFileIsCreatedInTmp() {
        LoggerFactory.getLogger(AppTest::class.java).info("log file location test")
        assertTrue(Files.isRegularFile(Path.of("/tmp/MiMiComparator.log")))
    }
}
