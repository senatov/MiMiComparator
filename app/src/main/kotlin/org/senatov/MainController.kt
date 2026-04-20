/*
 * MainController — FXML controller 4 MiMiComparator.
 * tree expand/collapse, filter, column headers, sync scroll,
 * CLI autocompare, DiffCellFactory coloring.
 * Iakov Senatov, 2026
 */
package org.senatov

import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.stage.Stage
import org.apache.commons.lang3.StringUtils
import org.senatov.cli.CliArgs
import org.senatov.compare.DirectoryComparator
import org.senatov.compare.FileContentComparator
import org.senatov.helpers.log.LogHelper
import org.senatov.model.CompareLineItem
import org.senatov.model.tree.DirTreeModel
import org.senatov.ui.cell.DiffCellFactory
import org.senatov.ui.config.ComparatorState
import org.senatov.ui.config.ComparatorStateService
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern
import java.util.stream.Collectors


class MainController {

    private val log = LoggerFactory.getLogger(MainController::class.java)

    companion object {
        private const val STATUS_DIR_MODE = "DIR mode"
        private const val STATUS_FILE_MODE = "FILE mode"
        private const val STATUS_SWAPPED = "⇄ swapped"
        private const val STATUS_CLIPBOARD_COPIED = "📋 copied"
    }

    // --- left panel ---
    @FXML private lateinit var leftPanel: VBox
    @FXML private lateinit var leftPathField: TextField
    @FXML private lateinit var leftListView: ListView<CompareLineItem>
    @FXML private lateinit var leftColumnHeader: HBox

    // --- right panel ---
    @FXML private lateinit var rightPanel: VBox
    @FXML private lateinit var rightPathField: TextField
    @FXML private lateinit var rightListView: ListView<CompareLineItem>
    @FXML private lateinit var rightColumnHeader: HBox
    // --- center strip ---
    @FXML private lateinit var centerStrip: VBox
    @FXML private lateinit var copyRightBtn: Button
    @FXML private lateinit var copyLeftBtn: Button
    @FXML private lateinit var diffBtn: Button
    @FXML private lateinit var equalBtn: Button
    @FXML private lateinit var deleteBtn: Button
    // --- toolbar ---
    @FXML private lateinit var mainToolBar: ToolBar
    @FXML private lateinit var syncScrollToggle: ToggleButton
    @FXML private lateinit var dirModeToggle: ToggleButton
    @FXML private lateinit var showIdenticalCheck: CheckMenuItem
    @FXML private lateinit var showDirsCheck: CheckMenuItem
    @FXML private lateinit var diffCountLabel: Label
    @FXML private lateinit var filterField: TextField
    @FXML private lateinit var statusLeft: Label
    @FXML private lateinit var statusCenter: Label
    @FXML private lateinit var statusRight: Label

    private var leftPath: Path? = null
    private var rightPath: Path? = null
    private var dirMode = false
    private var pendingCliArgs: CliArgs? = null
    private var leftTreeModel: DirTreeModel? = null
    private var rightTreeModel: DirTreeModel? = null
    private var lastDirResult: org.senatov.compare.DirCompareResult? = null
    private val stateService = ComparatorStateService()
    private var comparatorState: ComparatorState? = null
    private var restoringState = false


    @FXML
    private fun initialize() {
        log.debug("[{}]", LogHelper.method())
        comparatorState = stateService.load()
        installDiffCellFactories()
        setupClickToExpand()
        addProgrammaticButtons()
        setupSyncScroll()
        restoreInputsFromState()
        updateCenterStripState()
        updateColumnHeaderVisibility()
        Platform.runLater { executeCliAutoCompare() }
    }


    fun applyCliArgs(args: CliArgs) {
        log.info("CLI args: L={} R={} auto={}", args.leftPath, args.rightPath, args.autoCompare)
        pendingCliArgs = args
    }


    private fun executeCliAutoCompare() {
        val cli = pendingCliArgs ?: return
        restoringState = true
        try {
            cli.left()?.let { applyLeftPath(it) }
            cli.right()?.let { applyRightPath(it) }
            if (cli.hasExplicitDirMode()) setDirMode(cli.isDirMode)
        } finally { restoringState = false }
        persistInputPaths()
        updateCenterStripState()
        if (cli.autoCompare) onCompare()
    }


    private fun installDiffCellFactories() {
        log.debug("[{}] dirMode={}", LogHelper.method(), dirMode)
        val factory = DiffCellFactory(dirMode)
        leftListView.cellFactory = factory
        rightListView.cellFactory = factory
    }


    private fun setupClickToExpand() {
        leftListView.setOnMouseClicked { e -> handleTreeClick(e, leftListView, true) }
        rightListView.setOnMouseClicked { e -> handleTreeClick(e, rightListView, false) }
    }


    private fun handleTreeClick(event: MouseEvent, listView: ListView<CompareLineItem>, isLeft: Boolean) {
        if (!dirMode || event.clickCount < 2) return
        val item = listView.selectionModel.selectedItem ?: return
        if (!item.isDirectory) return
        val relPath = item.relativePath
        log.info("toggle expand: {} side={}", relPath, if (isLeft) "L" else "R")
        leftTreeModel?.toggleExpand(relPath)
        rightTreeModel?.toggleExpand(relPath)
        refreshTreeViews()
    }


    private fun refreshTreeViews() {
        val lt = leftTreeModel ?: return
        val rt = rightTreeModel ?: return
        var leftItems = lt.toFlatList()
        var rightItems = rt.toFlatList()
        val filterText = filterField.text
        if (filterText.isNotBlank()) {
            val pattern = buildFilterPattern(filterText)
            leftItems = applyFilter(leftItems, pattern)
            rightItems = applyFilter(rightItems, pattern)
        }
        leftListView.items = FXCollections.observableArrayList(leftItems)
        rightListView.items = FXCollections.observableArrayList(rightItems)
    }


    private fun updateColumnHeaderVisibility() {
        leftColumnHeader.isVisible = dirMode; leftColumnHeader.isManaged = dirMode
        rightColumnHeader.isVisible = dirMode; rightColumnHeader.isManaged = dirMode
    }

    // ═══ filter ═══
    @FXML private fun onFilterChanged() {
        log.info("filter changed: '{}'", filterField.text)
        if (dirMode && leftTreeModel != null) refreshTreeViews()
        else if (!dirMode) onCompare()
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

    // ═══ programmatic buttons ═══
    private fun addProgrammaticButtons() {
        val swapBtn = Button("⇄").apply {
            prefWidth = 36.0; prefHeight = 28.0
            style = "-fx-font-size:16; -fx-font-weight:bold;"
            tooltip = Tooltip("Swap left ↔ right")
            setOnAction { onSwapPanels() }
        }
        centerStrip.children.add(3, swapBtn)
        val homeBtn = Button("🏠 Home").apply { setOnAction { onLoadHome() } }
        val paneIndex = mainToolBar.items.size - 2
        mainToolBar.items.add(paneIndex, Separator())
        mainToolBar.items.add(paneIndex + 1, homeBtn)
        val infoBox = HBox(6.0).apply {
            alignment = Pos.CENTER_LEFT
            padding = Insets(2.0, 6.0, 2.0, 6.0)
        }
        val javaVer = Label("Java ${Runtime.version().feature()}").apply {
            style = "-fx-text-fill:#888; -fx-font-size:11;"
        }
        val osLabel = Label(System.getProperty("os.name")).apply {
            style = "-fx-text-fill:#888; -fx-font-size:11;"
        }
        infoBox.children.addAll(javaVer, Separator(), osLabel)
        (statusLeft.parent as? HBox)?.children?.add(infoBox)
    }

    private fun onSwapPanels() {
        log.info("swapping panels")
        val tmp = leftPath; leftPath = rightPath; rightPath = tmp
        leftPathField.text = leftPath?.toString() ?: ""
        rightPathField.text = rightPath?.toString() ?: ""
        val li = ArrayList(leftListView.items)
        val ri = ArrayList(rightListView.items)
        leftListView.items = FXCollections.observableArrayList(ri)
        rightListView.items = FXCollections.observableArrayList(li)
        val tmpM = leftTreeModel; leftTreeModel = rightTreeModel; rightTreeModel = tmpM
        val tmpS = statusLeft.text; statusLeft.text = statusRight.text; statusRight.text = tmpS
        statusCenter.text = STATUS_SWAPPED
    }

    private fun onLoadHome() {
        val home = System.getProperty("user.home")
        if (home.isNotBlank()) {
            leftPath = Path.of(home)
            leftPathField.text = StringUtils.abbreviate(home, 60)
            loadDirectoryPreview(leftPath!!, leftListView, isLeft = true)
        }
    }

    // ═══ sync scroll ═══
    private fun setupSyncScroll() {
        Platform.runLater {
            bindScrollBars(leftListView, rightListView)
            bindScrollBars(rightListView, leftListView)
        }
    }

    private fun bindScrollBars(source: ListView<*>, target: ListView<*>) {
        val srcBar = findScrollBar(source) ?: return
        val tgtBar = findScrollBar(target) ?: return
        srcBar.valueProperty().addListener { _, _, nv ->
            if (syncScrollToggle.isSelected) tgtBar.value = nv.toDouble()
        }
    }

    private fun findScrollBar(lv: ListView<*>): ScrollBar? =
        lv.lookupAll(".scroll-bar").filterIsInstance<ScrollBar>()
            .firstOrNull { it.orientation == Orientation.VERTICAL }

    private fun updateCenterStripState() {
        val hasBoth = leftPath != null && rightPath != null
        copyRightBtn.isDisable = !hasBoth
        copyLeftBtn.isDisable = !hasBoth
        diffBtn.isDisable = !hasBoth
        equalBtn.isDisable = !hasBoth
        deleteBtn.isDisable = leftPath == null && rightPath == null
    }

    // ═══ menu / toolbar actions ═══
    @FXML private fun onOpenLeft() {
        chooseFileOrDir("Open Left")?.let { p ->
            applyLeftPath(p)
            loadDirectoryPreview(p, leftListView, isLeft = true)
        }
    }

    @FXML private fun onOpenRight() {
        chooseFileOrDir("Open Right")?.let { p ->
            applyRightPath(p)
            loadDirectoryPreview(p, rightListView, isLeft = false)
        }
    }

    @FXML private fun onCompare() {
        val lp = leftPath; val rp = rightPath
        if (lp == null || rp == null) { showAlert("Load both sides first."); return }
        if (lp == rp) { showAlert("Both sides point to the same location. Comparing them is pointless 🙄"); return }
        log.info("compare: dir={} L={} R={}", dirMode, lp, rp)
        try {
            if (dirMode) {
                val result = DirectoryComparator.compareTree(lp, rp)
                lastDirResult = result
                leftTreeModel = result.leftModel
                rightTreeModel = result.rightModel
                refreshTreeViews()
                diffCountLabel.text = "diffs: ${result.diffCount}"
                statusCenter.text = result.statusText()
            } else {
                val result = FileContentComparator.compare(lp, rp, showIdenticalCheck.isSelected)
                leftListView.items = FXCollections.observableArrayList(result.leftItems)
                rightListView.items = FXCollections.observableArrayList(result.rightItems)
                diffCountLabel.text = "diffs: ${result.diffCount}"
                statusCenter.text = result.statusText()
            }
        } catch (ex: IOException) {
            log.error("compare failed: {}", ex.message)
            showAlert("Compare failed: ${ex.message}")
        }
    }

    @FXML private fun onRefresh() {
        leftPath?.let { loadDirectoryPreview(it, leftListView, isLeft = true) }
        rightPath?.let { loadDirectoryPreview(it, rightListView, isLeft = false) }
    }

    @FXML private fun onQuit() { Platform.exit() }
    @FXML private fun onToggleIdentical() { onCompare() }

    @FXML private fun onToggleDirMode() { setDirMode(!dirMode) }

    private fun setDirMode(enabled: Boolean) {
        dirMode = enabled
        dirModeToggle.isSelected = enabled
        showDirsCheck.isSelected = enabled
        statusCenter.text = if (enabled) STATUS_DIR_MODE else STATUS_FILE_MODE
        installDiffCellFactories()
        updateColumnHeaderVisibility()
        if (!restoringState) persistInputPaths()
    }

    @FXML private fun onExpandAll() {
        log.info("expand all")
        leftTreeModel?.expandAll(); rightTreeModel?.expandAll()
        refreshTreeViews()
    }

    @FXML private fun onCollapseAll() {
        log.info("collapse all")
        leftTreeModel?.collapseAll(); rightTreeModel?.collapseAll()
        refreshTreeViews()
    }

    private fun restoreInputsFromState() {
        val state = comparatorState ?: return
        restoringState = true
        try {
            restoreSavedPath(state.leftInputPath, isLeft = true)
            restoreSavedPath(state.rightInputPath, isLeft = false)
        } finally { restoringState = false }
        persistInputPaths()
    }

    private fun restoreSavedPath(rawPath: String, isLeft: Boolean) {
        if (rawPath.isBlank()) return
        try {
            val restored = Path.of(rawPath)
            if (isLeft) { applyLeftPath(restored); loadDirectoryPreview(restored, leftListView, true) }
            else { applyRightPath(restored); loadDirectoryPreview(restored, rightListView, false) }
        } catch (ex: Exception) {
            log.warn("failed to restore {} input path: {}", if (isLeft) "left" else "right", rawPath, ex)
        }
    }

    private fun applyLeftPath(path: Path) {
        leftPath = path
        leftPathField.text = path.toString()
        if (!restoringState) persistInputPaths()
    }

    private fun applyRightPath(path: Path) {
        rightPath = path
        rightPathField.text = path.toString()
        if (!restoringState) persistInputPaths()
    }

    private fun persistInputPaths() {
        if (restoringState) return
        val state = comparatorState ?: ComparatorState.defaults().also { comparatorState = it }
        state.leftInputPath = leftPath?.toString() ?: ""
        state.rightInputPath = rightPath?.toString() ?: ""
        stateService.save(state)
    }

    // ═══ center strip stubs ═══
    @FXML private fun onCopyToRight() { statusCenter.text = "→ copy to right (stub)" }
    @FXML private fun onCopyToLeft() { statusCenter.text = "← copy to left (stub)" }
    @FXML private fun onShowDiff() { statusCenter.text = "showing diffs only" }
    @FXML private fun onShowEqual() { statusCenter.text = "showing identical only" }
    @FXML private fun onDeleteSelected() { statusCenter.text = "🗑 delete (stub)" }
    @FXML private fun onSyncScroll() {}
    @FXML private fun onCopyPathLeft() { leftPath?.let { copyToClipboard(it.toString()) } }
    @FXML private fun onCopyPathRight() { rightPath?.let { copyToClipboard(it.toString()) } }

    @FXML private fun onAbout() {
        Alert(Alert.AlertType.INFORMATION).apply {
            title = "About"; headerText = "MiMiComparator"
            contentText = "Dual-pane file & directory comparator.\n" +
                "Libs: Log4j2 + Kotlin + Apache Commons + Jackson\n" +
                "Theme: AtlantaFX Cupertino\n" +
                "© 2026 Iakov Senatov"
        }.showAndWait()
    }

    // ═══ file / dir loading ═══
    private fun chooseFileOrDir(title: String): Path? {
        return if (dirMode) {
            DirectoryChooser().apply { this.title = title }
                .showDialog(getStage())?.toPath()
        } else {
            FileChooser().apply { this.title = title }
                .showOpenDialog(getStage())?.toPath()
        }
    }

    private fun loadDirectoryPreview(path: Path, listView: ListView<CompareLineItem>, isLeft: Boolean) {
        try {
            if (Files.isDirectory(path)) {
                setDirMode(true)
                val entries = listDirEntries(path)
                listView.items = FXCollections.observableArrayList(entries)
                updateStatus(isLeft, "${entries.size} entries")
            } else {
                setDirMode(false)
                val lines = Files.readAllLines(path)
                val items = lines.mapIndexed { i, line ->
                    CompareLineItem(i + 1, line, CompareLineItem.DiffStatus.IDENTICAL)
                }
                listView.items = FXCollections.observableArrayList(items)
                updateStatus(isLeft, "${lines.size} lines")
            }
        } catch (ex: IOException) {
            log.error("cant read {}: {}", path, ex.message)
            showAlert("Can't read: $path\n${ex.message}")
        }
        updateCenterStripState()
    }

    private fun listDirEntries(dir: Path): List<CompareLineItem> {
        Files.list(dir).use { stream ->
            return stream.sorted().map { p ->
                val isDir = Files.isDirectory(p)
                CompareLineItem(
                    lineNumber = 0, text = p.fileName.toString(),
                    status = CompareLineItem.DiffStatus.IDENTICAL,
                    indentLevel = 0, isDirectory = isDir,
                    relativePath = p.fileName.toString()
                )
            }.collect(Collectors.toList())
        }
    }

    // ═══ utils ═══
    private fun updateStatus(isLeft: Boolean, text: String) {
        if (isLeft) statusLeft.text = text else statusRight.text = text
    }

    private fun getStage(): Stage = leftPathField.scene.window as Stage

    private fun showAlert(msg: String) {
        Alert(Alert.AlertType.WARNING).apply { contentText = msg }.showAndWait()
    }

    private fun copyToClipboard(text: String) {
        Clipboard.getSystemClipboard().setContent(ClipboardContent().apply { putString(text) })
        statusCenter.text = STATUS_CLIPBOARD_COPIED
    }
}
