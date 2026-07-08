package org.senatov

import javafx.animation.ScaleTransition
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.stage.Stage
import javafx.util.Duration
import org.senatov.compare.CompareMode
import org.senatov.helpers.log.LogTag
import org.senatov.ui.cell.DiffCellFactory
import org.senatov.ui.config.ComparatorState
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
internal val COMPARE_MODES = CompareMode.entries.map { it.displayName }
private val EVENT_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

internal fun MainController.addProgrammaticUi() {
    log.debug(LogTag.UI, "addProgrammaticUi()")
    configureToolbarButtons()
    ratioPopupLabel.style = "-fx-background-color:#fff8c9; -fx-border-color:#d4c36a; -fx-border-radius:8; " +
            "-fx-background-radius:8; -fx-padding:6 10 6 10; -fx-font-weight:700; -fx-text-fill:#5d4a00;"
    ratioPopup.content.add(ratioPopupLabel)
    ratioPopup.isAutoHide = false
    ratioPopup.isHideOnEscape = false
}

internal fun MainController.configureCompareLists() {
    log.debug(LogTag.UI, "configureCompareLists()")
    val listStyle = "-fx-background-color:#ffffff; -fx-border-width:0; -fx-font-smoothing-type:gray; -fx-opacity:1;"
    leftListView.fixedCellSize = 23.0
    rightListView.fixedCellSize = 23.0
    leftListView.style = listStyle
    rightListView.style = listStyle
    operationListView.fixedCellSize = 23.0
    operationListView.isFocusTraversable = false
    operationListView.selectionModel.selectionMode = SelectionMode.SINGLE
    operationListView.style = "-fx-background-color:#fafafa; -fx-border-width:0; -fx-padding:0;"
    operationListView.setCellFactory {
        object : ListCell<String>() {
            override fun updateItem(item: String?, empty: Boolean) {
                super.updateItem(item, empty)
                text = if (empty) null else item
                alignment = Pos.CENTER
                style = when (item) {
                    "→", "←" -> "-fx-text-fill:#2f80ed; -fx-font-size:19; -fx-background-color:#fafafa; -fx-padding:0;"
                    "≠" -> "-fx-text-fill:#e15361; -fx-font-size:17; -fx-font-weight:bold; -fx-background-color:#fafafa; -fx-padding:0;"
                    "=" -> "-fx-text-fill:#6a9f73; -fx-font-size:16; -fx-background-color:#fafafa; -fx-padding:0;"
                    else -> "-fx-background-color:#fafafa; -fx-padding:0;"
                }
            }
        }
    }
}

internal fun MainController.configurePreviewPane() {
    listOf(previewLeftView, previewRightView).forEach { view ->
        view.fixedCellSize = 22.0
        view.style = "-fx-background-color:white; -fx-border-width:0; -fx-font-family:'System'; -fx-font-size:13;"
        view.setCellFactory {
            object : ListCell<String>() {
                override fun updateItem(item: String?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        text = null
                        style = "-fx-background-color:white;"
                        return
                    }
                    val status = item.first()
                    text = item.drop(1)
                    style = when (status) {
                        'M' -> "-fx-background-color:#c8ddf7; -fx-text-fill:#273458; -fx-padding:2 8;"
                        'A' -> "-fx-background-color:#d9edda; -fx-text-fill:#273458; -fx-padding:2 8;"
                        'X' -> "-fx-background-color:#eeeeee; -fx-text-fill:#999999; -fx-padding:2 8;"
                        else -> "-fx-background-color:white; -fx-text-fill:#273458; -fx-padding:2 8;"
                    }
                }
            }
        }
    }
}

internal fun MainController.installDiffCellFactories() {
    log.debug(LogTag.UI, "installDiffCellFactories()")
    leftListView.cellFactory = DiffCellFactory(dirMode)
    rightListView.cellFactory = DiffCellFactory(dirMode, mirrored = true)
}

internal fun MainController.configurePathFields() {
    log.debug(LogTag.UI, "configurePathFields()")
    installPathField(leftPathField, ComparisonSide.LEFT)
    installPathField(rightPathField, ComparisonSide.RIGHT)
    configurePathButtons()
    filterDebounce.setOnFinished { applyCurrentFilter() }
    filterField.textProperty().addListener { _, _, _ -> filterDebounce.playFromStart() }
}

private fun MainController.installPathField(field: TextField, side: ComparisonSide) {
    log.debug(LogTag.UI, "installPathField(field={}, side={})", field, side)
    field.minHeight = 24.0
    field.prefHeight = 24.0
    field.maxHeight = 24.0
    field.setOnAction { commitPathField(field, side) }
    field.focusedProperty().addListener { _, wasFocused, isFocused ->
        if (wasFocused && !isFocused) commitPathField(field, side)
    }
}

private fun MainController.configurePathButtons() {
    log.debug(LogTag.UI, "configurePathButtons()")
    installPathButton(leftPathMenuButton, "Open the left path chooser")
    installPathButton(rightPathMenuButton, "Open the right path chooser")
    installPathButton(leftPathBrowseButton, "Browse for the left file or folder")
    installPathButton(rightPathBrowseButton, "Browse for the right file or folder")
}

private fun installPathButton(button: Button, helpText: String) {
    button.minWidth = 27.0
    button.prefWidth = 27.0
    button.minHeight = 24.0
    button.prefHeight = 24.0
    button.focusTraversableProperty().set(false)
    button.installStandardHelp(helpText)
    button.style = "-fx-font-family:'System'; -fx-font-size:15; -fx-text-fill:#75839a; " +
            "-fx-background-color:transparent; -fx-border-width:0; -fx-padding:0;"
    button.setOnMouseEntered {
        animatePathButton(button, 1.06)
    }
    button.setOnMouseExited {
        animatePathButton(button, 1.0)
    }
    button.setOnMousePressed {
        animatePathButton(button, 0.94)
    }
    button.setOnMouseReleased {
        animatePathButton(button, if (button.isHover) 1.06 else 1.0)
    }
}

private fun animatePathButton(button: Button, scale: Double) {
    ScaleTransition(Duration.millis(85.0), button).apply {
        toX = scale
        toY = scale
        play()
    }
}

private fun MainController.commitPathField(field: TextField, side: ComparisonSide) {
    log.debug(LogTag.UI, "commitPathField(field={}, side={})", field, side)
    val raw = field.text.trim()
    val current = if (side == ComparisonSide.LEFT) leftPath else rightPath
    if (raw.isBlank()) {
        clearPathSide(side)
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
    applyPath(path, side)
    if (leftPath != null && rightPath != null) compareCurrentInputs()
    else loadDirectoryPreview(path, side)
}

private fun MainController.clearPathSide(side: ComparisonSide) {
    log.debug(LogTag.UI, "clearPathSide(side={})", side)
    if (side == ComparisonSide.LEFT) {
        leftPath = null
        leftPathField.text = ""
        leftListView.items.clear()
        updateStatus(ComparisonSide.LEFT, "No file loaded")
    } else {
        rightPath = null
        rightPathField.text = ""
        rightListView.items.clear()
        updateStatus(ComparisonSide.RIGHT, "No file loaded")
    }
    updateCenterStripState()
    updateComparisonTitle()
    operationListView.items.clear()
    persistInputPaths()
}

private fun MainController.configureToolbarButtons() {
    log.debug(LogTag.UI, "configureToolbarButtons()")
    mainToolBar.prefHeight = 44.0
    mainToolBar.style = "-fx-background-color:#f4f4f4; -fx-border-color:#cfcfcf; -fx-border-width:0 0 1 0; -fx-padding:4 8;"
    mainToolBar.items.filterIsInstance<ButtonBase>().forEach { button ->
        button.minWidth = 29.0
        button.prefWidth = 29.0
        button.minHeight = 29.0
        button.prefHeight = 29.0
        button.style =
            "-fx-padding:0; -fx-font-size:19; -fx-text-fill:#607188; -fx-background-color:transparent; -fx-background-radius:4;"
    }
    filterField.style = "-fx-background-color:white; -fx-border-color:#c4c4c4; -fx-border-radius:5; -fx-background-radius:5;"
}

internal fun MainController.configureCompareModes() {
    compareModeChoice.items.setAll(COMPARE_MODES)
    val savedMode = comparatorState?.compareMode
    compareModeChoice.selectionModel.select(
        savedMode?.takeIf { it in COMPARE_MODES } ?: COMPARE_MODES.first()
    )
}

internal fun MainController.applyCompareMode() {
    val state = comparatorState ?: ComparatorState().also { comparatorState = it }
    state.compareMode = compareModeChoice.value ?: COMPARE_MODES.first()
    if (!restoringState) stateService.save(state)
    if (leftPath != null && rightPath != null) compareCurrentInputs()
}

private fun MainController.installToolbarGraphic(button: ButtonBase) {
    log.debug(LogTag.UI, "installToolbarGraphic(button={})", button)
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
    log.debug(LogTag.UI, "setupEventLog()")
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

internal fun MainController.updateStatus(side: ComparisonSide, text: String) {
    log.debug(LogTag.UI, "updateStatus(side={}, text={})", side, text)
    if (side == ComparisonSide.LEFT) statusLeft.text = text else statusRight.text = text
}

internal fun MainController.appendEvent(message: String) {
    log.debug(LogTag.UI, "appendEvent(message={})", message)
    eventLogView.items.add("${EVENT_TIME_FMT.format(LocalDateTime.now())}  $message")
    eventLogView.scrollTo(eventLogView.items.size - 1)
}

internal fun MainController.getStage(): Stage {
    log.debug(LogTag.UI, "getStage()")
    return leftPathField.scene.window as Stage
}

internal fun MainController.updateWindowTitle(title: String) {
    log.debug(LogTag.UI, "updateWindowTitle(title={})", title)
    (rootPane.scene?.window as? Stage)?.title = title
}

internal fun MainController.updateComparisonTitle() {
    val left = leftPath
    val right = rightPath
    if (left == null && right == null) {
        updateWindowTitle(TITLE_COMPARE)
        return
    }
    val leftName = left?.fileName?.toString() ?: "…"
    val rightName = right?.fileName?.toString() ?: "…"
    val parent = left?.parent ?: right?.parent
    val suffix = parent?.let { " (${it})" }.orEmpty()
    updateWindowTitle("$leftName - $rightName$suffix")
}

internal fun MainController.showAlert(msg: String) {
    log.debug(LogTag.UI, "showAlert(msg={})", msg)
    Alert(Alert.AlertType.WARNING).apply { contentText = msg }.showAndWait()
}

internal fun MainController.copyToClipboard(text: String) {
    log.debug(LogTag.UI, "copyToClipboard(text={})", text)
    Clipboard.getSystemClipboard().setContent(ClipboardContent().apply { putString(text) })
    statusCenter.text = STATUS_CLIPBOARD_COPIED
    log.info(org.senatov.helpers.log.LogTag.UI, "clipboard copied chars={}", text.length)
}

internal fun MainController.showAboutDialog() {
    log.debug(LogTag.UI, "showAboutDialog()")
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
    log.debug(LogTag.UI, "setStubStatus(text={})", text)
    statusCenter.text = text
}
