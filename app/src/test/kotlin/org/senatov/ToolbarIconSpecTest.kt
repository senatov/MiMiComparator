package org.senatov

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolbarIconSpecTest {
    @Test
    fun `toolbar actions receive large distinct colored glyphs`() {
        val copy = toolbarIconSpec("→", "Show new files on left side")
        val differences = toolbarIconSpec("≠", "Show differences")
        val refresh = toolbarIconSpec("↻", "Refresh comparison")

        assertEquals("→", copy.glyph)
        assertTrue(copy.size >= 25)
        assertEquals(3, setOf(copy.color, differences.color, refresh.color).size)
        assertTrue("-fx-font-weight:700" in copy.style)
    }

    @Test
    fun `synchronization actions use different green icons`() {
        val selected = toolbarIconSpec("▷", "Synchronize selected")
        val all = toolbarIconSpec("◷", "Synchronize All")

        assertEquals("▐▷", selected.glyph)
        assertEquals("◷▷", all.glyph)
        assertEquals(selected.color, all.color)
    }
}
