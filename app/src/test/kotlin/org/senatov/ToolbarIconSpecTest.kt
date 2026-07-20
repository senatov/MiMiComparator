package org.senatov

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolbarIconSpecTest {
    @Test
    fun `toolbar actions receive large distinct colored glyphs`() {
        val copy = toolbarIconSpec("→", "Copy selected item to the right")
        val differences = toolbarIconSpec("≠", "Show differences")
        val refresh = toolbarIconSpec("↻", "Refresh comparison")

        assertEquals("→", copy.glyph)
        assertTrue(copy.size >= 27)
        assertEquals(3, setOf(copy.color, differences.color, refresh.color).size)
        assertTrue("-fx-font-weight:700" in copy.style)
    }
}
