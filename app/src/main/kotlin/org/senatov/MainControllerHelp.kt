package org.senatov

import javafx.scene.Node
import javafx.scene.control.ButtonBase
import javafx.scene.control.Control
import javafx.scene.control.Tooltip
import javafx.util.Duration

private const val HELP_SHOW_DELAY_MS = 350.0
private const val HELP_HIDE_DELAY_MS = 120.0
private const val HELP_VISIBLE_MS = 9000.0

internal fun ButtonBase.installStandardHelp(text: String) {
    applyStandardHelpToNode(this, text)
}

internal fun Node.installStandardHelp(text: String) {
    applyStandardHelpToNode(this, text)
}

private fun applyStandardHelpToNode(node: Node, text: String) {
    val cleanText = text.trim()
    if (cleanText.isEmpty()) return
    val tooltip = Tooltip(cleanText).apply {
        showDelay = Duration.millis(HELP_SHOW_DELAY_MS)
        hideDelay = Duration.millis(HELP_HIDE_DELAY_MS)
        showDuration = Duration.millis(HELP_VISIBLE_MS)
        isWrapText = true
        maxWidth = 320.0
    }
    if (node is Control) {
        node.tooltip = tooltip
    } else {
        Tooltip.install(node, tooltip)
    }
    node.accessibleText = cleanText
    node.accessibleHelp = cleanText
}
