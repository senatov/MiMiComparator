/*
 * Smoke test — verifies FXML loads w/o crash.
 * Iakov Senatov, 2026
 */
package org.senatov

import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class AppTest {

    @Test
    fun fxmlResourceExists() {
        val url = App::class.java.getResource("/org/senatov/MiMiComparator.fxml")
        assertNotNull(url, "FXML resource must be on classpath")
    }
}
