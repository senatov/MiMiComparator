package org.senatov.ui.preview

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewSettingsTest {
    private val settings = PreviewSettings()

    @Test
    fun `none compares exact content`() {
        assertFalse(settings.linesMatch(" value ", "value"))
        assertTrue(settings.linesMatch("value", "value"))
    }

    @Test
    fun `trim whitespaces ignores surrounding whitespace`() {
        settings.ignoreDifferences = IgnoreDifferences.TRIM_WHITESPACES

        assertTrue(settings.linesMatch("  value", "value  "))
        assertFalse(settings.linesMatch("a b", "ab"))
    }

    @Test
    fun `ignore whitespaces removes all whitespace`() {
        settings.ignoreDifferences = IgnoreDifferences.WHITESPACES

        assertTrue(settings.linesMatch("a b\tc", "abc"))
    }

    @Test
    fun `ignore formatting collapses whitespace runs`() {
        settings.ignoreDifferences = IgnoreDifferences.FORMATTING

        assertTrue(settings.linesMatch("  alpha   beta ", "alpha beta"))
    }
}
