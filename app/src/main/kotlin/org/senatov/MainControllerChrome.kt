package org.senatov

import javafx.animation.ScaleTransition
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.stage.Stage
import javafx.util.Duration
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
    leftListView.fixedCellSize = 27.0
    rightListView.fixedCellSize = 27.0
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
    configurePathButtons()
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

private fun MainController.configurePathButtons() {
    installPathButton(leftPathMenuButton, "#1f6feb", "Open the left path chooser")
    installPathButton(rightPathMenuButton, "#1f6feb", "Open the right path chooser")
    installPathButton(leftPathBrowseButton, "#5f7c8a", "Browse for the left file or folder")
    installPathButton(rightPathBrowseButton, "#5f7c8a", "Browse for the right file or folder")
}

private fun installPathButton(button: Button, accent: String, helpText: String) {
    button.minWidth = 34.0
    button.prefWidth = 34.0
    button.minHeight = 28.0
    button.prefHeight = 28.0
    button.focusTraversableProperty().set(false)
    button.installStandardHelp(helpText)
    applyPathButtonStyle(button, accent, PathButtonState.NORMAL)
    button.setOnMouseEntered {
        animatePathButton(button, 1.06)
        applyPathButtonStyle(button, accent, PathButtonState.HOVER)
    }
    button.setOnMouseExited {
        animatePathButton(button, 1.0)
        applyPathButtonStyle(button, accent, PathButtonState.NORMAL)
    }
    button.setOnMousePressed {
        animatePathButton(button, 0.94)
        applyPathButtonStyle(button, accent, PathButtonState.PRESSED)
    }
    button.setOnMouseReleased {
        animatePathButton(button, if (button.isHover) 1.06 else 1.0)
        applyPathButtonStyle(button, accent, if (button.isHover) PathButtonState.HOVER else PathButtonState.NORMAL)
    }
}

private enum class PathButtonState {
    NORMAL,
    HOVER,
    PRESSED,
}

private fun applyPathButtonStyle(button: Button, accent: String, state: PathButtonState) {
    val fill = when (state) {
        PathButtonState.NORMAL -> "#ffffff"
        PathButtonState.HOVER -> "#eef5ff"
        PathButtonState.PRESSED -> "#dceaff"
    }
    val border = when (state) {
        PathButtonState.NORMAL -> "#aeb7c2"
        PathButtonState.HOVER -> "#6ea8fe"
        PathButtonState.PRESSED -> "#1f6feb"
    }
    val shadow = when (state) {
        PathButtonState.NORMAL -> "dropshadow(gaussian,rgba(0,0,0,0.18),3,0,0,1)"
        PathButtonState.HOVER -> "dropshadow(gaussian,rgba(31,111,235,0.34),6,0,0,1)"
        PathButtonState.PRESSED -> "innershadow(gaussian,rgba(0,0,0,0.22),4,0,0,1)"
    }
    button.style = "-fx-font-family:'System'; -fx-font-size:23; -fx-font-weight:400; " +
            "-fx-text-fill:$accent; -fx-background-color:$fill; -fx-background-radius:6; " +
            "-fx-border-color:$border; -fx-border-width:1; -fx-border-radius:6; " +
            "-fx-padding:0; -fx-effect:$shadow;"
}

private fun animatePathButton(button: Button, scale: Double) {
    ScaleTransition(Duration.millis(85.0), button).apply {
        toX = scale
        toY = scale
        play()
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
    if (leftPath != null && rightPath != null) compareCurrentInputs()
    else loadDirectoryPreview(path, if (isLeft) leftListView else rightListView, isLeft)
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
    mainToolBar.prefHeight = 60.0
    mainToolBar.style = "-fx-background-color:#f7f7f8; -fx-border-color:#d8d8dc; -fx-border-width:0 0 1 0; -fx-padding:6 7 6 7;"
    mainToolBar.items.filterIsInstance<ButtonBase>().forEach { button ->
        installToolbarGraphic(button)
        button.minWidth = 46.0
        button.prefWidth = 46.0
        button.minHeight = 46.0
        button.prefHeight = 46.0
        button.style = "-fx-padding:0; -fx-background-color:transparent; -fx-background-radius:8; -fx-font-smoothing-type:gray;"
    }
    syncScrollToggle.prefWidth = 46.0
}

private fun MainController.installToolbarGraphic(button: ButtonBase) {
    val rawText = button.text ?: return
    val parts = rawText.split("\n", limit = 2)
    val sourceIcon = parts.firstOrNull().orEmpty()
    val labelText = parts.getOrNull(1).orEmpty()
    val spec = toolbarIconSpec(sourceIcon, labelText)
    val helpText = toolbarHelpText(sourceIcon, labelText, button.tooltip?.text)
    val icon = Label(spec.glyph).apply {
        alignment = Pos.CENTER
        maxWidth = Double.MAX_VALUE
        style = spec.style
    }
    button.text = null
    button.installStandardHelp(helpText)
    button.graphic = icon
    button.contentDisplay = ContentDisplay.GRAPHIC_ONLY
}

private data class ToolbarIconSpec(
    val glyph: String,
    val color: String = "#2f343a",
    val size: Int = 32,
    val emoji: Boolean = false,
) {
    val style: String
        get() {
            val family = if (emoji) "'Apple Color Emoji','System'" else "'System'"
            val effect = if (emoji) "" else "-fx-effect:dropshadow(gaussian,rgba(255,255,255,0.85),0,0,0,1);"
            return "-fx-font-family:$family; -fx-font-size:$size; -fx-font-weight:400; " +
                    "-fx-text-fill:$color; -fx-opacity:1; -fx-font-smoothing-type:gray; $effect"
        }
}

private fun toolbarIconSpec(sourceIcon: String, labelText: String): ToolbarIconSpec = when (labelText) {
    "Home" -> ToolbarIconSpec("⌂", "#4a6f9f", 34)
    "Sessions" -> ToolbarIconSpec("🗂", size = 30, emoji = true)
    "All" -> ToolbarIconSpec("✱", "#2d6cdf", 34)
    "Diffs" -> ToolbarIconSpec("≠", "#d45a5a", 34)
    "Same" -> ToolbarIconSpec("=", "#4aa564", 34)
    "Struct." -> ToolbarIconSpec("▣", "#6f63c6", 33)
    "Minor" -> ToolbarIconSpec("≈", "#2f8b9a", 34)
    "Rules" -> ToolbarIconSpec("♟", "#6b7280", 32)
    "Expand" -> ToolbarIconSpec("⊞", "#2f7d60", 32)
    "Collapse" -> ToolbarIconSpec("⊟", "#8a6f36", 32)
    "Select" -> ToolbarIconSpec("✓", "#2f7d60", 34)
    "Files" -> ToolbarIconSpec("≠", "#d45a5a", 34)
    "Refresh" -> ToolbarIconSpec("↻", "#2f7aa8", 34)
    "Swap" -> ToolbarIconSpec("⇄", "#6d62be", 34)
    "Stop" -> ToolbarIconSpec("×", "#9aa0a6", 34)
    "Filters" -> ToolbarIconSpec("⌕", "#5f7c8a", 32)
    "Peek" -> ToolbarIconSpec("🔎", size = 29, emoji = true)
    else -> ToolbarIconSpec(sourceIcon, "#2f343a", 32)
}

private fun toolbarHelpText(sourceIcon: String, labelText: String, existingText: String?): String {
    existingText?.takeIf { it.isNotBlank() }?.let { return it }
    return when (labelText.ifBlank { sourceIcon }) {
        "Home" -> "Show the home screen"
        "Sessions" -> "Open saved comparison sessions"
        "All" -> "Show all comparison rows"
        "Diffs" -> "Show only differing rows"
        "Same" -> "Show identical rows"
        "Struct." -> "Toggle directory structure comparison"
        "Minor" -> "Toggle minor difference handling"
        "Rules" -> "Open comparison rules"
        "Expand" -> "Expand all directory nodes"
        "Collapse" -> "Collapse all directory nodes"
        "Select" -> "Select comparison items"
        "Files" -> "Show file differences"
        "Refresh" -> "Refresh the current comparison"
        "Swap" -> "Swap left and right sides"
        "Stop" -> "Stop or clear the current action"
        "Filters" -> "Open filter options"
        "Peek" -> "Preview filtered content"
        "↕" -> "Toggle synchronized scrolling"
        else -> labelText.ifBlank { "Toolbar action" }
    }
}

internal fun MainController.setupEventLog() {
    eventLogView.isVisible = false
    eventLogView.isManaged = false
    eventLogView.fixedCellSize = 20.0
    eventLogView.style = "-fx-font-size:13; -fx-font-weight:400; -fx-text-fill:#1d1d1f;"
    bottomChrome.style = "-fx-background-color:#f5f5f7; -fx-border-color:#d8d8dc; -fx-border-width:1 0 0 0;"
    bottomBar.style = "-fx-background-color:#f5f5f7;"
    listOf(statusLeft, statusCenter, statusRight, diffCountLabel).forEach {
        it.style = "-fx-text-fill:#1d1d1f; -fx-font-size:13; -fx-font-weight:400;"
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
