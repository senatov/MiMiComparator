package org.senatov

import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.stage.Stage
import org.senatov.ui.cell.DiffCellFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal const val STATUS_DIR_MODE = "DIR mode"
internal const val STATUS_FILE_MODE = "FILE mode"
internal const val STATUS_SWAPPED = "⇄ swapped"
internal const val STATUS_CLIPBOARD_COPIED = "📋 copied"
internal const val TITLE_HOME = "Home"
internal const val TITLE_COMPARE = "Documents - Folder Compare"
private val EVENT_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

internal fun MainController.addProgrammaticUi() {
    configureToolbarButtons()
    ratioPopupLabel.style = "-fx-background-color:#fff8c9; -fx-border-color:#d4c36a; -fx-border-radius:8; " +
            "-fx-background-radius:8; -fx-padding:6 10 6 10; -fx-font-weight:700; -fx-text-fill:#5d4a00;"
    ratioPopup.content.add(ratioPopupLabel)
    ratioPopup.isAutoHide = false
    ratioPopup.isHideOnEscape = false
}

internal fun MainController.configureCompareLists() {
    val listStyle = "-fx-background-color:#ffffff; -fx-border-width:0; -fx-font-smoothing-type:gray; -fx-opacity:1;"
    leftListView.fixedCellSize = 21.0
    rightListView.fixedCellSize = 21.0
    leftListView.style = listStyle
    rightListView.style = listStyle
}

internal fun MainController.installDiffCellFactories() {
    val factory = DiffCellFactory(dirMode)
    leftListView.cellFactory = factory
    rightListView.cellFactory = factory
}

internal fun MainController.configurePathFields() {
    installPathField(leftPathField, isLeft = true)
    installPathField(rightPathField, isLeft = false)
}

private fun MainController.installPathField(field: TextField, isLeft: Boolean) {
    field.minHeight = 24.0
    field.prefHeight = 24.0
    field.maxHeight = 24.0
    field.setOnAction { commitPathField(field, isLeft) }
    field.focusedProperty().addListener { _, wasFocused, isFocused ->
        if (wasFocused && !isFocused) commitPathField(field, isLeft)
    }
}

private fun MainController.commitPathField(field: TextField, isLeft: Boolean) {
    val raw = field.text.trim()
    val current = if (isLeft) leftPath else rightPath
    if (raw.isBlank()) {
        clearPathSide(isLeft)
        return
    }
    val path = runCatching { Path.of(raw).toAbsolutePath().normalize() }.getOrElse {
        statusCenter.text = "Invalid path"
        field.text = current?.toString() ?: raw
        return
    }
    if (path == current?.toAbsolutePath()?.normalize()) return
    if (!Files.exists(path)) {
        statusCenter.text = "Path not found"
        field.text = current?.toString() ?: raw
        return
    }
    showCompareView()
    applyPath(path, isLeft)
    loadDirectoryPreview(path, if (isLeft) leftListView else rightListView, isLeft)
}

private fun MainController.clearPathSide(isLeft: Boolean) {
    if (isLeft) {
        leftPath = null
        leftPathField.text = ""
        leftListView.items.clear()
        updateStatus(isLeft = true, "No file loaded")
    } else {
        rightPath = null
        rightPathField.text = ""
        rightListView.items.clear()
        updateStatus(isLeft = false, "No file loaded")
    }
    updateCenterStripState()
    persistInputPaths()
}

private fun MainController.configureToolbarButtons() {
    mainToolBar.prefHeight = 54.0
    mainToolBar.style = "-fx-background-color:#f5f5f7; -fx-border-color:#d8d8dc; -fx-border-width:0 0 1 0; -fx-padding:5 6 5 6;"
    mainToolBar.items.filterIsInstance<ButtonBase>().forEach { button ->
        installToolbarGraphic(button)
        button.minWidth = 42.0
        button.prefWidth = 42.0
        button.minHeight = 42.0
        button.prefHeight = 42.0
        button.style = "-fx-padding:0; -fx-background-radius:8; -fx-font-smoothing-type:gray;"
    }
    syncScrollToggle.prefWidth = 42.0
}

private fun MainController.installToolbarGraphic(button: ButtonBase) {
    val rawText = button.text ?: return
    val parts = rawText.split("\n", limit = 2)
    val sourceIcon = parts.firstOrNull().orEmpty()
    val labelText = parts.getOrNull(1).orEmpty()
    val color = when (button) {
        diffBtn -> "#b32020"
        equalBtn -> "#1f7a1f"
        else -> "#111111"
    }
    val icon = Label(higToolbarIcon(sourceIcon, labelText)).apply {
        alignment = Pos.CENTER
        maxWidth = Double.MAX_VALUE
        style = "-fx-font-family:'System'; -fx-font-size:20; -fx-font-weight:400; -fx-text-fill:$color; -fx-opacity:1;"
    }
    button.text = null
    button.tooltip = button.tooltip ?: labelText.takeIf { it.isNotBlank() }?.let { Tooltip(it) }
    button.graphic = icon
    button.contentDisplay = ContentDisplay.GRAPHIC_ONLY
}

private fun higToolbarIcon(sourceIcon: String, labelText: String): String = when (labelText) {
    "Home" -> "⌂"
    "Sessions" -> "▣"
    "All" -> "✱"
    "Diffs" -> "≠"
    "Same" -> "="
    "Structure" -> "▣"
    "Minor" -> "≈"
    "Rules" -> "♟"
    "Expand" -> "⊞"
    "Collapse" -> "⊟"
    "Select" -> "✓"
    "Files" -> "≠"
    "Refresh" -> "↻"
    "Swap" -> "⇄"
    "Stop" -> "×"
    "Filters" -> "⊂"
    "Peek" -> "⌕"
    else -> sourceIcon
}

internal fun MainController.setupEventLog() {
    eventLogView.isVisible = false
    eventLogView.isManaged = false
    eventLogView.fixedCellSize = 18.0
    eventLogView.style = "-fx-font-size:12; -fx-font-weight:400; -fx-text-fill:#1d1d1f;"
    bottomChrome.style = "-fx-background-color:#f5f5f7; -fx-border-color:#d8d8dc; -fx-border-width:1 0 0 0;"
    bottomBar.style = "-fx-background-color:#f5f5f7;"
    listOf(statusLeft, statusCenter, statusRight, diffCountLabel).forEach {
        it.style = "-fx-text-fill:#1d1d1f; -fx-font-family:'System'; -fx-font-size:12; -fx-font-weight:400;"
    }
    appendEvent("Username: ${System.getProperty("user.name", "")}")
    appendEvent("Load comparison: <->")
}

internal fun MainController.updateStatus(isLeft: Boolean, text: String) {
    if (isLeft) statusLeft.text = text else statusRight.text = text
}

internal fun MainController.appendEvent(message: String) {
    eventLogView.items.add("${EVENT_TIME_FMT.format(LocalDateTime.now())}  $message")
    eventLogView.scrollTo(eventLogView.items.size - 1)
}

internal fun MainController.getStage(): Stage = leftPathField.scene.window as Stage

internal fun MainController.updateWindowTitle(title: String) {
    (rootPane.scene?.window as? Stage)?.title = title
}

internal fun MainController.showAlert(msg: String) {
    Alert(Alert.AlertType.WARNING).apply { contentText = msg }.showAndWait()
}

internal fun MainController.copyToClipboard(text: String) {
    Clipboard.getSystemClipboard().setContent(ClipboardContent().apply { putString(text) })
    statusCenter.text = STATUS_CLIPBOARD_COPIED
    log.info(org.senatov.helpers.log.LogTag.UI, "clipboard copied chars={}", text.length)
}

internal fun MainController.showAboutDialog() {
    Alert(Alert.AlertType.INFORMATION).apply {
        title = "About"
        headerText = "MiMiComparator"
        contentText = "Dual-pane file & directory comparator.\n" +
                "Libs: Log4j2 + Kotlin + Apache Commons + Jackson\n" +
                "Theme: AtlantaFX Cupertino\n" +
                "© 2026 Iakov Senatov"
    }.showAndWait()
}

internal fun MainController.setStubStatus(text: String) {
    statusCenter.text = text
}