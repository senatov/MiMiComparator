package org.senatov

import javafx.collections.FXCollections
import javafx.scene.control.ListView
import javafx.scene.input.MouseEvent
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import org.senatov.compare.DirectoryComparator
import org.senatov.compare.FileContentComparator
import org.senatov.helpers.log.LogTag
import org.senatov.model.CompareLineItem
import org.senatov.ui.config.ComparatorState
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern
import java.util.stream.Collectors

internal fun MainController.executeCliAutoCompare() {
    val cli = pendingCliArgs ?: return
    log.info(LogTag.CLI, "apply auto={} dirExplicit={}", cli.autoCompare, cli.hasExplicitDirMode())
    if (cli.leftPath == null && cli.rightPath == null && !cli.hasExplicitDirMode()) {
        showHomeView()
        return
    }
    showCompareView()
    restoringState = true
    try {
        cli.left()?.let { applyPath(it, isLeft = true) }
        cli.right()?.let { applyPath(it, isLeft = false) }
        if (cli.hasExplicitDirMode()) setDirMode(cli.isDirMode)
    }
    finally {
        restoringState = false
    }
    persistInputPaths()
    updateCenterStripState()
    if (cli.autoCompare) compareCurrentInputs()
}

internal fun MainController.setupClickToExpand() {
    leftListView.setOnMouseClicked { event -> handleTreeClick(event, leftListView) }
    rightListView.setOnMouseClicked { event -> handleTreeClick(event, rightListView) }
}

private fun MainController.handleTreeClick(event: MouseEvent, listView: ListView<CompareLineItem>) {
    if (!dirMode || event.clickCount < 2) return
    val item = listView.selectionModel.selectedItem ?: return
    if (!item.isDirectory) return
    log.debug(LogTag.UI, "toggle {}", item.relativePath)
    leftTreeModel?.toggleExpand(item.relativePath)
    rightTreeModel?.toggleExpand(item.relativePath)
    refreshTreeViews()
}

internal fun MainController.refreshTreeViews() {
    val leftModel = leftTreeModel ?: return
    val rightModel = rightTreeModel ?: return
    var leftItems = leftModel.toFlatList()
    var rightItems = rightModel.toFlatList()
    val filterText = filterField.text
    if (filterText.isNotBlank()) {
        val pattern = buildFilterPattern(filterText)
        leftItems = applyFilter(leftItems, pattern)
        rightItems = applyFilter(rightItems, pattern)
    }
    leftListView.items = FXCollections.observableArrayList(leftItems)
    rightListView.items = FXCollections.observableArrayList(rightItems)
    log.debug(LogTag.UI, "tree view L={} R={} filter='{}'", leftItems.size, rightItems.size, filterText)
}

internal fun MainController.updateColumnHeaderVisibility() {
    leftColumnHeader.isVisible = dirMode
    leftColumnHeader.isManaged = dirMode
    rightColumnHeader.isVisible = dirMode
    rightColumnHeader.isManaged = dirMode
}

internal fun MainController.applyCurrentFilter() {
    log.debug(LogTag.UI, "filter '{}'", filterField.text)
    if (dirMode && leftTreeModel != null) refreshTreeViews() else if (!dirMode) compareCurrentInputs()
}

private fun buildFilterPattern(filterText: String): Pattern {
    val sb = StringBuilder()
    for (part in filterText.split("[,;\\s]+".toRegex())) {
        val trimmed = part.trim()
        if (trimmed.isEmpty()) continue
        if (sb.isNotEmpty()) sb.append("|")
        val regex = trimmed.replace(".", "\\.").replace("*", ".*").replace("?", ".")
        sb.append("($regex)")
    }
    return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE)
}

private fun applyFilter(items: List<CompareLineItem>, pattern: Pattern): List<CompareLineItem> =
    items.filter { it.isDirectory || pattern.matcher(it.text).find() }

internal fun MainController.openPath(isLeft: Boolean) {
    showCompareView()
    chooseFileOrDir(if (isLeft) "Open Left" else "Open Right")?.let { path ->
        log.info(LogTag.UI, "open {} {}", if (isLeft) "left" else "right", path)
        applyPath(path, isLeft)
        loadDirectoryPreview(path, if (isLeft) leftListView else rightListView, isLeft)
    }
}

internal fun MainController.compareCurrentInputs() {
    showCompareView()
    val lp = leftPath
    val rp = rightPath
    if (lp == null || rp == null) {
        log.warn(LogTag.COMPARE, "compare blocked: missing side L={} R={}", lp, rp)
        showAlert("Load both sides first.")
        return
    }
    if (lp == rp) {
        log.warn(LogTag.COMPARE, "compare blocked: same path {}", lp)
        showAlert("Both sides point to the same location. Comparing them is pointless 🙄")
        return
    }
    log.info(LogTag.COMPARE, "compare dir={} L={} R={}", dirMode, lp, rp)
    appendEvent("Load comparison: $lp <-> $rp")
    try {
        if (dirMode) compareDirectories(lp, rp) else compareFiles(lp, rp)
    }
    catch (ex: IOException) {
        log.error(LogTag.COMPARE, "compare failed: {}", ex.message)
        showAlert("Compare failed: ${ex.message}")
    }
}

private fun MainController.compareDirectories(left: Path, right: Path) {
    val result = DirectoryComparator.compareTree(left, right)
    lastDirResult = result
    leftTreeModel = result.leftModel
    rightTreeModel = result.rightModel
    refreshTreeViews()
    diffCountLabel.text = "diffs: ${result.diffCount}"
    statusCenter.text = result.statusText()
}

private fun MainController.compareFiles(left: Path, right: Path) {
    val result = FileContentComparator.compare(left, right, showIdenticalCheck.isSelected)
    leftListView.items = FXCollections.observableArrayList(result.leftItems)
    rightListView.items = FXCollections.observableArrayList(result.rightItems)
    diffCountLabel.text = "diffs: ${result.diffCount}"
    statusCenter.text = result.statusText()
}

internal fun MainController.refreshPreviews() {
    log.info(LogTag.UI, "refresh")
    appendEvent("Fast refresh")
    leftPath?.let { loadDirectoryPreview(it, leftListView, isLeft = true) }
    rightPath?.let { loadDirectoryPreview(it, rightListView, isLeft = false) }
}

internal fun MainController.toggleDirMode() {
    setDirMode(!dirMode)
    persistUiState()
}

internal fun MainController.setDirMode(enabled: Boolean) {
    if (dirMode != enabled) log.info(LogTag.UI, "mode {}", if (enabled) "dir" else "file")
    dirMode = enabled
    dirModeToggle.isSelected = enabled
    showDirsCheck.isSelected = enabled
    statusCenter.text = if (enabled) STATUS_DIR_MODE else STATUS_FILE_MODE
    installDiffCellFactories()
    updateColumnHeaderVisibility()
    if (!restoringState) persistInputPaths()
}

internal fun MainController.expandAllTrees() {
    log.info(LogTag.UI, "expand all")
    leftTreeModel?.expandAll()
    rightTreeModel?.expandAll()
    refreshTreeViews()
}

internal fun MainController.collapseAllTrees() {
    log.info(LogTag.UI, "collapse all")
    leftTreeModel?.collapseAll()
    rightTreeModel?.collapseAll()
    refreshTreeViews()
}

internal fun MainController.restoreUiFromState() {
    val state = comparatorState ?: return
    log.debug(LogTag.STATE, "restore UI dir={} sync={} ratio={}", state.isDirMode, state.isSyncScroll, state.splitRatio)
    restoringState = true
    try {
        syncScrollToggle.isSelected = state.isSyncScroll
        setDirMode(state.isDirMode)
        leftPanelRatio = state.splitRatio.coerceIn(0.15, 0.85)
    }
    finally {
        restoringState = false
    }
}

internal fun MainController.restoreSavedPath(rawPath: String, isLeft: Boolean) {
    if (rawPath.isBlank()) return
    log.debug(LogTag.STATE, "restore {} path {}", if (isLeft) "left" else "right", rawPath)
    try {
        val restored = Path.of(rawPath)
        applyPath(restored, isLeft)
        loadDirectoryPreview(restored, if (isLeft) leftListView else rightListView, isLeft)
    }
    catch (ex: Exception) {
        log.warn(LogTag.STATE, "restore {} failed {}", if (isLeft) "left" else "right", rawPath, ex)
    }
}

internal fun MainController.applyPath(path: Path, isLeft: Boolean) {
    if (isLeft) {
        leftPath = path
        leftPathField.text = path.toString()
    } else {
        rightPath = path
        rightPathField.text = path.toString()
    }
    log.debug(LogTag.UI, "{} path {}", if (isLeft) "left" else "right", path)
    if (!restoringState) persistInputPaths()
}

internal fun MainController.persistInputPaths() {
    if (restoringState) return
    val state = comparatorState ?: ComparatorState.defaults().also { comparatorState = it }
    state.leftInputPath = leftPath?.toString() ?: ""
    state.rightInputPath = rightPath?.toString() ?: ""
    state.isDirMode = dirMode
    state.isSyncScroll = syncScrollToggle.isSelected
    state.splitRatio = leftPanelRatio
    stateService.save(state)
}

internal fun MainController.persistUiState() {
    if (restoringState) return
    val state = comparatorState ?: ComparatorState.defaults().also { comparatorState = it }
    state.isDirMode = dirMode
    state.isSyncScroll = syncScrollToggle.isSelected
    state.splitRatio = leftPanelRatio
    stateService.save(state)
}

internal fun MainController.swapPanels() {
    showCompareView()
    log.info(LogTag.UI, "swap panels")
    val tmpPath = leftPath
    leftPath = rightPath
    rightPath = tmpPath
    leftPathField.text = leftPath?.toString() ?: ""
    rightPathField.text = rightPath?.toString() ?: ""

    val leftItems = ArrayList(leftListView.items)
    val rightItems = ArrayList(rightListView.items)
    leftListView.items = FXCollections.observableArrayList(rightItems)
    rightListView.items = FXCollections.observableArrayList(leftItems)

    val tmpModel = leftTreeModel
    leftTreeModel = rightTreeModel
    rightTreeModel = tmpModel
    val tmpStatus = statusLeft.text
    statusLeft.text = statusRight.text
    statusRight.text = tmpStatus
    statusCenter.text = STATUS_SWAPPED
    persistInputPaths()
}

internal fun MainController.updateCenterStripState() {
    val hasBoth = leftPath != null && rightPath != null
    copyRightBtn.isDisable = !hasBoth
    copyLeftBtn.isDisable = !hasBoth
    diffBtn.isDisable = !hasBoth
    equalBtn.isDisable = !hasBoth
    deleteBtn.isDisable = leftPath == null && rightPath == null
}

internal fun MainController.loadDirectoryPreview(path: Path, listView: ListView<CompareLineItem>, isLeft: Boolean) {
    try {
        if (Files.isDirectory(path)) {
            setDirMode(true)
            val entries = listDirEntries(path)
            listView.items = FXCollections.observableArrayList(entries)
            updateStatus(isLeft, "${entries.size} entries")
            log.info(LogTag.IO, "preview {} dir entries={}", if (isLeft) "left" else "right", entries.size)
        } else {
            setDirMode(false)
            val lines = Files.readAllLines(path)
            val items = lines.mapIndexed { i, line ->
                CompareLineItem(i + 1, line, CompareLineItem.DiffStatus.IDENTICAL)
            }
            listView.items = FXCollections.observableArrayList(items)
            updateStatus(isLeft, "${lines.size} lines")
            log.info(LogTag.IO, "preview {} file lines={}", if (isLeft) "left" else "right", lines.size)
        }
    }
    catch (ex: IOException) {
        log.error(LogTag.IO, "read failed {}: {}", path, ex.message)
        showAlert("Can't read: $path\n${ex.message}")
    }
    updateCenterStripState()
}

private fun MainController.chooseFileOrDir(title: String): Path? {
    val chosen = if (dirMode) {
        DirectoryChooser().apply { this.title = title }.showDialog(getStage())?.toPath()
    } else {
        FileChooser().apply { this.title = title }.showOpenDialog(getStage())?.toPath()
    }
    log.debug(LogTag.UI, "chooser '{}' -> {}", title, chosen)
    return chosen
}

private fun listDirEntries(dir: Path): List<CompareLineItem> {
    Files.list(dir).use { stream ->
        return stream.sorted().map { path ->
            CompareLineItem(
                lineNumber = 0,
                text = path.fileName.toString(),
                status = CompareLineItem.DiffStatus.IDENTICAL,
                indentLevel = 0,
                isDirectory = Files.isDirectory(path),
                relativePath = path.fileName.toString()
            )
        }.collect(Collectors.toList())
    }
}