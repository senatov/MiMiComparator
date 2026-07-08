package org.senatov.ui.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ComparatorStateSerializationTest {
    private val objectMapper = ObjectMapper().registerKotlinModule()

    @Test
    fun `round-trips extracted state structures`() {
        val state = ComparatorState(
            window = WindowState(x = 42.0, width = 1280.0),
            leftPanel = PanelState(path = "/left", selectedIndex = 3),
            rightPanel = PanelState(path = "/right", selectedIndex = 5),
            compareMode = "Size",
        )

        val restored = objectMapper.readValue(
            objectMapper.writeValueAsBytes(state),
            ComparatorState::class.java,
        )

        assertEquals(state, restored)
    }
}
