/*
 * Smoke test — just verifies the FXML loads w/o crash.
 * Iakov Senatov, 2026
 */
package org.senatov;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppTest {

    @Test
    void fxmlResourceExists() {
        var url = App.class.getResource("/org/senatov/MiMiComparator.fxml");
        assertNotNull(url, "FXML resource must be on classpath");
    }
}
