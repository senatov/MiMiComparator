package org.senatov

import javafx.collections.FXCollections
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.control.ListView
import javafx.scene.input.MouseEvent
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import org.senatov.compare.CompareMode
import org.senatov.compare.DirectoryComparator
import org.senatov.compare.FileContentComparator
import org.senatov.helpers.log.LogTag
import org.senatov.model.*
import org.senatov.model.tree.TreeEntryDetails
import org.senatov.model.tree.TreeLocation
import org.senatov.ui.config.ComparatorState
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.regex.Pattern
import java.util.stream.Collectors
import kotlin.math.max

internal fun MainController.executeCliAutoCompare() {
    log.debug(LogTag.CLI, "executeCliAutoCompare()")
    val cli = pendingCliArgs ?: return
    log.info(LogTag.CLI, "apply auto={} dirExplicit={}", cli.autoCompare, cli.hasExplicitDirMode())
    if (cli.leftPath == null && cli.rightPath == null && !cli.hasExplicitDirMode()) {
        showCompareView()
        val state = comparatorState ?: return
        restoringState = true
        try {
            restoreSavedPath(state.leftInputPath, ComparisonSide.LEFT)
            restoreSavedPath(state.rightInputPath, ComparisonSide.RIGHT)
        }
        finally {
            restoringState = false
        }
        if (leftPath != null && rightPath != null) compareCurrentInputs()
        return
    }
    showCompareView()
    restoringState = true
    try {
        cli.leftPath?.let { applyPath(it, ComparisonSide.LEFT) }
        cli.rightPath?.let { applyPath(it, ComparisonSide.RIGHT) }
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
    log.debug(LogTag.UI, "setupClickToExpand()")
    leftListView.setOnMouseClicked { event -> handleTreeClick(event, leftListView) }
    rightListView.setOnMouseClicked { event -> handleTreeClick(event, rightListView) }
}

internal fun MainController.setupSelectionPreview() {
    leftListView.selectionModel.selectedIndexProperty().addListener { _, _, value ->
        synchronizeSelection(value.toInt(), leftListView)
    }
    rightListView.selectionModel.selectedIndexProperty().addListener { _, _, value ->
        synchronizeSelection(value.toInt(), rightListView)
    }
    leftListView.selectionModel.selectedIndexProperty().addListener { _, _, _ -> updateToolbarActions() }
    updateToolbarActions()
}

internal fun MainController.updateToolbarActions() {
    val index = leftListView.selectionModel.selectedIndex
    val left = leftListView.items.getOrNull(index)
    val right = rightListView.items.getOrNull(index)
    copyRightBtn.isDisable = index < 0 || left == null || left.status == DiffStatus.MISSING
    copyLeftBtn.isDisable = index < 0 || right == null || right.status == DiffStatus.MISSING
    diffBtn.isDisable = leftPath == null || rightPath == null
    equalBtn.isDisable = leftPath == null || rightPath == null
}

internal fun MainController.copySelectedItem(sourceSide: ComparisonSide) {
    val index = leftListView.selectionModel.selectedIndex
    if (index < 0) return
    val item = (if (sourceSide == ComparisonSide.LEFT) leftListView else rightListView).items.getOrNull(index) ?: return
    if (item.status == DiffStatus.MISSING) return
    val sourceRoot = if (sourceSide == ComparisonSide.LEFT) leftPath else rightPath
    val targetRoot = if (sourceSide == ComparisonSide.LEFT) rightPath else leftPath
    if (sourceRoot == null || targetRoot == null) return
    val source = if (dirMode) sourceRoot.resolve(item.relativePath) else sourceRoot
    val target = if (dirMode) targetRoot.resolve(item.relativePath) else targetRoot
    if (!Files.exists(source)) {
        showAlert("Source no longer exists: $source")
        return
    }
    val direction = if (sourceSide == ComparisonSide.LEFT) "right" else "left"
    val confirmed = Alert(Alert.AlertType.CONFIRMATION).apply {
        title = "Copy item"
        headerText = "Copy ${source.fileName} to the $direction side?"
        contentText = if (Files.exists(target)) "The existing item will be replaced." else target.toString()
    }.showAndWait().filter { it == ButtonType.OK }.isPresent
    if (!confirmed) return
    runCatching { copyRecursivelyReplacing(source, target) }.onSuccess {
                statusCenter.text = "Copied ${source.fileName} to the $direction"
                refreshPreviews()
            }.onFailure { showAlert("Copy failed: ${it.message}") }
}

private fun copyRecursivelyReplacing(source: Path, target: Path) {
    if (!Files.isDirectory(source)) {
        target.parent?.let(Files::createDirectories)
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
        return
    }
    Files.walk(source).use { paths ->
        paths.forEach { current ->
            val destination = target.resolve(source.relativize(current))
            if (Files.isDirectory(current)) Files.createDirectories(destination)
            else Files.copy(current, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
        }
    }
}

private fun MainController.synchronizeSelection(index: Int, source: ListView<CompareLineItem>) {
    if (syncingSelection || index < 0) return
    syncingSelection = true
    try {
        val target = if (source === leftListView) rightListView else leftListView
        target.selectionModel.select(index)
        target.scrollTo(index)
        operationListView.selectionModel.select(index)
        operationListView.scrollTo(index)
        showSelectedFilePreview(index)
    }
    finally {
        syncingSelection = false
    }
}

private fun MainController.showSelectedFilePreview(index: Int) {
    val leftItem = leftListView.items.getOrNull(index)
    val rightItem = rightListView.items.getOrNull(index)
    if (leftItem?.isDirectory == true || rightItem?.isDirectory == true) {
        clearPreview("Folder selected")
        return
    }
    val relativePath = leftItem?.relativePath?.ifBlank { null }
        ?: rightItem?.relativePath?.ifBlank { null }
    val leftFile = if (dirMode && relativePath != null) leftPath?.resolve(relativePath) else leftPath
    val rightFile = if (dirMode && relativePath != null) rightPath?.resolve(relativePath) else rightPath
    val leftLines = readPreviewLines(leftFile)
    val rightLines = readPreviewLines(rightFile)
    if (leftLines == null && rightLines == null) {
        clearPreview("Cannot show file")
        return
    }

    val leftOutput = mutableListOf<String>()
    val rightOutput = mutableListOf<String>()
    var differences = 0
    val count = max(leftLines?.size ?: 0, rightLines?.size ?: 0)
    for (lineIndex in 0 until count) {
        val leftLine = leftLines?.getOrNull(lineIndex)
        val rightLine = rightLines?.getOrNull(lineIndex)
        val different = leftLine != rightLine
        if (different) differences++
        val leftStatus = when {
            leftLine == null -> 'X'
            rightLine == null -> 'A'
            different -> 'M'
            else -> 'I'
        }
        val rightStatus = when {
            rightLine == null -> 'X'
            leftLine == null -> 'A'
            different -> 'M'
            else -> 'I'
        }
        leftOutput += "$leftStatus${lineIndex + 1}\t${leftLine.orEmpty()}"
        rightOutput += "$rightStatus${lineIndex + 1}\t${rightLine.orEmpty()}"
    }
    previewLeftView.items = FXCollections.observableArrayList(leftOutput)
    previewRightView.items = FXCollections.observableArrayList(rightOutput)
    previewDiffCountLabel.text = if (differences == 1) "1 difference" else "$differences differences"
    statusCenter.text = if (differences == 0) "Contents are identical" else "Text representation differs from file content on disk"
    previewNoticeBox.style = if (differences == 0) {
        "-fx-background-color:#f3f7ff; -fx-border-color:#b8d1ff; -fx-border-width:1 0 1 0; -fx-padding:0 14;"
    } else {
        "-fx-background-color:#fff8e8; -fx-border-color:#efc968; -fx-border-width:1 0 1 0; -fx-padding:0 14;"
    }
    previewNoticeIcon.text = if (differences == 0) "●" else "▲"
}

private fun MainController.readPreviewLines(path: Path?): List<String>? {
    if (path == null || !Files.isRegularFile(path)) return null
    return runCatching { Files.readAllLines(path) }.getOrNull()
}

private fun MainController.clearPreview(message: String) {
    previewLeftView.items.clear()
    previewRightView.items.clear()
    previewDiffCountLabel.text = "0 differences"
    statusCenter.text = message
}

private fun MainController.handleTreeClick(event: MouseEvent, listView: ListView<CompareLineItem>) {
    log.debug(LogTag.UI, "handleTreeClick(clickCount={})", event.clickCount)
    if (!dirMode || event.clickCount < 2) return
    val item = listView.selectionModel.selectedItem ?: return
    if (!item.isDirectory) return
    log.debug(LogTag.UI, "toggle {}", item.relativePath)
    leftTreeModel?.toggleExpand(item.relativePath)
    rightTreeModel?.toggleExpand(item.relativePath)
    refreshTreeViews()
}

internal fun MainController.refreshTreeViews() {
    log.debug(LogTag.UI, "refreshTreeViews()")
    val leftModel = leftTreeModel ?: return
    val rightModel = rightTreeModel ?: return
    val (leftItems, rightItems) = filterPairedRows(leftModel.toFlatList(), rightModel.toFlatList())
    leftListView.items = FXCollections.observableArrayList(leftItems)
    rightListView.items = FXCollections.observableArrayList(rightItems)
    updateOperationRows(leftItems, rightItems)
    updateToolbarActions()
    log.debug(LogTag.UI, "tree view L={} R={} filter='{}'", leftItems.size, rightItems.size, filterField.text)
}

internal fun MainController.updateColumnHeaderVisibility() {
    log.debug(LogTag.UI, "updateColumnHeaderVisibility()")
    leftColumnHeader.isVisible = dirMode
    leftColumnHeader.isManaged = dirMode
    rightColumnHeader.isVisible = dirMode
    rightColumnHeader.isManaged = dirMode
}

internal fun MainController.applyCurrentFilter() {
    log.debug(LogTag.UI, "applyCurrentFilter()")
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

private fun MainController.filterPairedRows(
    leftItems: List<CompareLineItem>,
    rightItems: List<CompareLineItem>
): Pair<List<CompareLineItem>, List<CompareLineItem>> {
    val pattern = filterField.text.takeIf { it.isNotBlank() }?.let { buildFilterPattern(it) }
    val leftFiltered = mutableListOf<CompareLineItem>()
    val rightFiltered = mutableListOf<CompareLineItem>()
    val count = minOf(leftItems.size, rightItems.size)
    for (index in 0 until count) {
        val left = leftItems[index]
        val right = rightItems[index]
        val identical = left.status == DiffStatus.IDENTICAL && right.status == DiffStatus.IDENTICAL
        if (!showIdenticalCheck.isSelected && identical) continue
        if (showOnlyIdentical && !identical) continue
        if (pattern != null && !matchesEitherSide(left, right, pattern)) continue
        leftFiltered.add(left)
        rightFiltered.add(right)
    }
    return leftFiltered to rightFiltered
}

private fun matchesEitherSide(left: CompareLineItem, right: CompareLineItem, pattern: Pattern): Boolean =
    left.isDirectory || right.isDirectory || pattern.matcher(left.text).find() || pattern.matcher(right.text).find()

internal fun MainController.openPath(side: ComparisonSide) {
    log.debug(LogTag.UI, "openPath(side={})", side)
    showCompareView()
    chooseFileOrDir(side.openDialogTitle)?.let { path ->
        log.info(LogTag.UI, "open {} {}", side.logName, path)
        applyPath(path, side)
        if (leftPath != null && rightPath != null) compareCurrentInputs()
        else loadDirectoryPreview(path, side)
    }
}

internal fun MainController.compareCurrentInputs() {
    log.debug(LogTag.COMPARE, "compareCurrentInputs()")
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
    log.debug(LogTag.COMPARE, "compareDirectories(left={}, right={})", left, right)
    val result = DirectoryComparator.compareTree(left, right, CompareMode.fromDisplayName(compareModeChoice.value))
    leftTreeModel = result.leftModel
    rightTreeModel = result.rightModel
    refreshTreeViews()
    diffCountLabel.text = "diffs: ${result.diffCount}"
    statusCenter.text = result.statusText()
}

private fun MainController.compareFiles(left: Path, right: Path) {
    log.debug(LogTag.COMPARE, "compareFiles(left={}, right={})", left, right)
    val result = FileContentComparator.compare(left, right, showIdenticalCheck.isSelected)
    val pairedItems = result.leftItems.zip(result.rightItems).filter { (leftItem, rightItem) ->
        !showOnlyIdentical || (leftItem.status == DiffStatus.IDENTICAL && rightItem.status == DiffStatus.IDENTICAL)
    }
    val visibleLeft = pairedItems.map { it.first }
    val visibleRight = pairedItems.map { it.second }
    leftListView.items = FXCollections.observableArrayList(visibleLeft)
    rightListView.items = FXCollections.observableArrayList(visibleRight)
    updateOperationRows(visibleLeft, visibleRight)
    updateToolbarActions()
    diffCountLabel.text = "diffs: ${result.diffCount}"
    statusCenter.text = result.statusText()
}

internal fun MainController.refreshPreviews() {
    log.debug(LogTag.UI, "refreshPreviews()")
    appendEvent("Fast refresh")
    if (leftPath != null && rightPath != null) {
        compareCurrentInputs()
        return
    }
    leftPath?.let { loadDirectoryPreview(it, ComparisonSide.LEFT) }
    rightPath?.let { loadDirectoryPreview(it, ComparisonSide.RIGHT) }
}

internal fun MainController.toggleDirMode() {
    log.debug(LogTag.UI, "toggleDirMode()")
    setDirMode(!dirMode)
    persistUiState()
}

internal fun MainController.setDirMode(enabled: Boolean) {
    log.debug(LogTag.UI, "setDirMode(enabled={})", enabled)
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
    log.debug(LogTag.UI, "expandAllTrees()")
    log.info(LogTag.UI, "expand all")
    leftTreeModel?.expandAll()
    rightTreeModel?.expandAll()
    refreshTreeViews()
}

internal fun MainController.collapseAllTrees() {
    log.debug(LogTag.UI, "collapseAllTrees()")
    log.info(LogTag.UI, "collapse all")
    leftTreeModel?.collapseAll()
    rightTreeModel?.collapseAll()
    refreshTreeViews()
}

internal fun MainController.restoreUiFromState() {
    log.debug(LogTag.STATE, "restoreUiFromState()")
    val state = comparatorState ?: return
    log.debug(
        LogTag.STATE,
        "restore UI dir={} sync={} horizontalRatio={} previewRatio={}",
        state.isDirMode,
        state.isSyncScroll,
        state.splitRatio,
        state.previewSplitRatio,
    )
    restoringState = true
    try {
        syncScrollToggle.isSelected = state.isSyncScroll
        setDirMode(state.isDirMode)
        leftPanelRatio = state.splitRatio.coerceIn(0.15, 0.85)
        comparisonSplitPane.setDividerPositions(state.previewSplitRatio.coerceIn(0.20, 0.90))
    }
    finally {
        restoringState = false
    }
}

internal fun MainController.restoreSavedPath(rawPath: String, side: ComparisonSide) {
    log.debug(LogTag.STATE, "restoreSavedPath(rawPath={}, side={})", rawPath, side)
    if (rawPath.isBlank()) return
    log.debug(LogTag.STATE, "restore {} path {}", side.logName, rawPath)
    try {
        val restored = Path.of(rawPath)
        applyPath(restored, side)
        loadDirectoryPreview(restored, side)
    }
    catch (ex: Exception) {
        log.warn(LogTag.STATE, "restore {} failed {}", side.logName, rawPath, ex)
    }
}

internal fun MainController.applyPath(path: Path, side: ComparisonSide) {
    log.debug(LogTag.UI, "applyPath(path={}, side={})", path, side)
    if (side == ComparisonSide.LEFT) {
        leftPath = path
        leftPathField.text = path.toString()
    } else {
        rightPath = path
        rightPathField.text = path.toString()
    }
    log.debug(LogTag.UI, "{} path {}", side.logName, path)
    updateComparisonTitle()
    if (!restoringState) persistInputPaths()
}

internal fun MainController.persistInputPaths() {
    log.debug(LogTag.STATE, "persistInputPaths()")
    if (restoringState) return
    val state = comparatorState ?: ComparatorState().also { comparatorState = it }
    state.leftInputPath = leftPath?.toString() ?: ""
    state.rightInputPath = rightPath?.toString() ?: ""
    state.isDirMode = dirMode
    state.isSyncScroll = syncScrollToggle.isSelected
    state.splitRatio = leftPanelRatio
    state.previewSplitRatio = comparisonSplitPane.dividers.firstOrNull()?.position ?: state.previewSplitRatio
    state.compareMode = compareModeChoice.value ?: COMPARE_MODES.first()
    stateService.save(state)
}

internal fun MainController.persistUiState() {
    log.debug(LogTag.STATE, "persistUiState()")
    if (restoringState) return
    val state = comparatorState ?: ComparatorState().also { comparatorState = it }
    state.isDirMode = dirMode
    state.isSyncScroll = syncScrollToggle.isSelected
    state.splitRatio = leftPanelRatio
    state.previewSplitRatio = comparisonSplitPane.dividers.firstOrNull()?.position ?: state.previewSplitRatio
    state.compareMode = compareModeChoice.value ?: COMPARE_MODES.first()
    stateService.save(state)
}

internal fun MainController.swapPanels() {
    log.debug(LogTag.UI, "swapPanels()")
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
    updateOperationRows(rightItems, leftItems)

    val tmpModel = leftTreeModel
    leftTreeModel = rightTreeModel
    rightTreeModel = tmpModel
    val tmpStatus = statusLeft.text
    statusLeft.text = statusRight.text
    statusRight.text = tmpStatus
    statusCenter.text = STATUS_SWAPPED
    updateComparisonTitle()
    persistInputPaths()
}

internal fun MainController.updateOperationRows(
    leftItems: List<CompareLineItem>,
    rightItems: List<CompareLineItem>,
) {
    val count = minOf(leftItems.size, rightItems.size)
    val operations = (0 until count).map { index ->
        val left = leftItems[index]
        val right = rightItems[index]
        when {
            left.status == DiffStatus.MISSING -> "←"
            right.status == DiffStatus.MISSING -> "→"
            left.status == DiffStatus.MODIFIED || right.status == DiffStatus.MODIFIED -> "≠"
            left.status == DiffStatus.IDENTICAL && right.status == DiffStatus.IDENTICAL -> "="
            else -> ""
        }
    }
    operationListView.items = FXCollections.observableArrayList(operations)
}

internal fun MainController.updateCenterStripState() {
    log.debug(LogTag.UI, "updateCenterStripState()")
    val hasBoth = leftPath != null && rightPath != null
    // File-system mutation is not implemented yet. Keep the reference controls
    // visible, but never present a status-only stub as a working copy command.
    copyRightBtn.isDisable = true
    copyLeftBtn.isDisable = true
    diffBtn.isDisable = !hasBoth
    equalBtn.isDisable = !hasBoth
    deleteBtn.isDisable = true
}

internal fun MainController.loadDirectoryPreview(path: Path, side: ComparisonSide) {
    log.debug(LogTag.IO, "loadDirectoryPreview(path={}, side={})", path, side)
    val listView = if (side == ComparisonSide.LEFT) leftListView else rightListView
    try {
        if (Files.isDirectory(path)) {
            setDirMode(true)
            val entries = listDirEntries(path)
            listView.items = FXCollections.observableArrayList(entries)
            updateStatus(side, "${entries.size} entries")
            log.info(LogTag.IO, "preview {} dir entries={}", side.logName, entries.size)
        } else {
            setDirMode(false)
            val lines = Files.readAllLines(path)
            val items = lines.mapIndexed { i, line ->
                CompareLineItem(LineContent(i + 1, line), DiffStatus.IDENTICAL)
            }
            listView.items = FXCollections.observableArrayList(items)
            updateStatus(side, "${lines.size} lines")
            log.info(LogTag.IO, "preview {} file lines={}", side.logName, lines.size)
        }
    }
    catch (ex: IOException) {
        log.error(LogTag.IO, "read failed {}: {}", path, ex.message)
        showAlert("Can't read: $path\n${ex.message}")
    }
    updateCenterStripState()
}

private fun MainController.chooseFileOrDir(title: String): Path? {
    log.debug(LogTag.UI, "chooseFileOrDir(title={})", title)
    val chosen = if (dirMode) {
        DirectoryChooser().apply { this.title = title }.showDialog(getStage())?.toPath()
    } else {
        FileChooser().apply { this.title = title }.showOpenDialog(getStage())?.toPath()
    }
    log.debug(LogTag.UI, "chooser '{}' -> {}", title, chosen)
    return chosen
}

private fun MainController.listDirEntries(dir: Path): List<CompareLineItem> {
    log.debug(LogTag.IO, "listDirEntries(dir={})", dir)
    Files.list(dir).use { stream ->
        return stream.sorted().map { path ->
            val attr = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes::class.java)
            CompareLineItem(
                content = LineContent(number = 0, text = path.fileName.toString()),
                status = DiffStatus.IDENTICAL,
                treeState = TreeDisplayState(
                    details = TreeEntryDetails(
                        location = TreeLocation(path.fileName.toString(), depth = 0),
                        isDirectory = Files.isDirectory(path),
                        metadata = FileMetadata(
                            size = if (Files.isDirectory(path)) 0 else attr.size(),
                            lastModifiedMs = attr.lastModifiedTime().toMillis(),
                        ),
                    ),
                    isExpanded = false,
                ),
            )
        }.collect(Collectors.toList())
    }
}