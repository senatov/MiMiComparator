/*
 * MainController — FXML controller 4 MiMiComparator.
 * tree expand/collapse, filter, column headers, sync scroll,
 * CLI autocompare, DiffCellFactory coloring.
 * Iakov Senatov, 2026
 */
package org.senatov.mimicomparator

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
import javafx.scene.layout.BorderPane
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.stage.Popup
import javafx.stage.Stage
import org.senatov.mimicomparator.cli.CliArgs
import org.senatov.mimicomparator.compare.DirectoryComparator
import org.senatov.mimicomparator.compare.FileContentComparator
import org.senatov.mimicomparator.helpers.log.LogHelper
import org.senatov.mimicomparator.helpers.log.LogTag
import org.senatov.mimicomparator.model.CompareLineItem
import org.senatov.mimicomparator.model.tree.DirTreeModel
import org.senatov.mimicomparator.ui.cell.DiffCellFactory
import org.senatov.mimicomparator.ui.config.ComparatorState
import org.senatov.mimicomparator.ui.config.ComparatorStateService
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern
import java.util.stream.Collectors


class MainController {

    private val log = LoggerFactory.getLogger(MainController::class.java)

    companion object {
        private const val STATUS_DIR_MODE = "DIR mode"
        private const val STATUS_FILE_MODE = "FILE mode"
        private const val STATUS_SWAPPED = "⇄ swapped"
        private const val STATUS_CLIPBOARD_COPIED = "📋 copied"
        private const val TITLE_HOME = "Home"
        private const val TITLE_COMPARE = "Documents - Folder Compare"
        private val EVENT_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
    }

    @FXML
    private lateinit var rootPane: BorderPane
    @FXML
    private lateinit var topChrome: VBox
    @FXML
    private lateinit var bottomChrome: VBox
    @FXML
    private lateinit var bottomBar: HBox

    // --- left panel ---
    @FXML
    private lateinit var leftPanel: VBox
    @FXML
    private lateinit var leftPathField: TextField
    @FXML
    private lateinit var leftListView: ListView<CompareLineItem>
    @FXML
    private lateinit var leftColumnHeader: HBox

    // --- right panel ---
    @FXML
    private lateinit var rightPanel: VBox
    @FXML
    private lateinit var rightPathField: TextField
    @FXML
    private lateinit var rightListView: ListView<CompareLineItem>
    @FXML
    private lateinit var rightColumnHeader: HBox

    // --- center strip ---
    @FXML
    private lateinit var contentBox: HBox
    @FXML
    private lateinit var centerStrip: VBox
    @FXML
    private lateinit var copyRightBtn: Button
    @FXML
    private lateinit var copyLeftBtn: Button
    @FXML
    private lateinit var diffBtn: Button
    @FXML
    private lateinit var equalBtn: Button
    @FXML
    private lateinit var deleteBtn: Button
    @FXML
    private lateinit var swapBtn: Button

    // --- toolbar ---
    @FXML
    private lateinit var mainToolBar: ToolBar
    @FXML
    private lateinit var syncScrollToggle: ToggleButton
    @FXML
    private lateinit var dirModeToggle: ToggleButton
    @FXML
    private lateinit var showIdenticalCheck: CheckMenuItem
    @FXML
    private lateinit var showDirsCheck: CheckMenuItem
    @FXML
    private lateinit var diffCountLabel: Label
    @FXML
    private lateinit var eventLogView: ListView<String>
    @FXML
    private lateinit var filterField: TextField
    @FXML
    private lateinit var statusLeft: Label
    @FXML
    private lateinit var statusCenter: Label
    @FXML
    private lateinit var statusRight: Label

    private var leftPath: Path? = null
    private var rightPath: Path? = null
    private var dirMode = false
    private var pendingCliArgs: CliArgs? = null
    private var leftTreeModel: DirTreeModel? = null
    private var rightTreeModel: DirTreeModel? = null
    private var lastDirResult: org.senatov.mimicomparator.compare.DirCompareResult? = null
    private val stateService = ComparatorStateService()
    private var comparatorState: ComparatorState? = null
    private var restoringState = false
    private var leftPanelRatio = 0.5
    private val ratioPopupLabel = Label()
    private val ratioPopup = Popup()
    private var homeView: BorderPane? = null


    @FXML
    private fun initialize() {
        log.debug(LogTag.UI, "[{}]", LogHelper.method())
        comparatorState = stateService.load()
        configureCompareLists()
        installDiffCellFactories()
        setupEventLog()
        setupClickToExpand()
        addProgrammaticUi()
        restoreUiFromState()
        setupSyncScroll()
        setupResizableCenterStrip()
        updateCenterStripState()
        updateColumnHeaderVisibility()
        Platform.runLater { executeCliAutoCompare() }
    }


    fun applyCliArgs(args: CliArgs) {
        log.info(LogTag.CLI, "args L={} R={} auto={}", args.leftPath, args.rightPath, args.autoCompare)
        pendingCliArgs = args
    }


    private fun executeCliAutoCompare() {
        val cli = pendingCliArgs ?: return
        log.info(LogTag.CLI, "apply auto={} dirExplicit={}", cli.autoCompare, cli.hasExplicitDirMode())
        if (cli.leftPath == null && cli.rightPath == null && !cli.hasExplicitDirMode()) {
            showHomeView()
            return
        }
        showCompareView()
        restoringState = true
        try {
            cli.left()?.let { applyLeftPath(it) }
            cli.right()?.let { applyRightPath(it) }
            if (cli.hasExplicitDirMode()) setDirMode(cli.isDirMode)
        } finally {
            restoringState = false
        }
        persistInputPaths()
        updateCenterStripState()
        if (cli.autoCompare) onCompare()
    }


    private fun installDiffCellFactories() {
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
        log.debug(LogTag.UI, "toggle {} side={}", relPath, if (isLeft) "L" else "R")
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
        log.debug(LogTag.UI, "tree view L={} R={} filter='{}'", leftItems.size, rightItems.size, filterText)
    }


    private fun updateColumnHeaderVisibility() {
        leftColumnHeader.isVisible = dirMode; leftColumnHeader.isManaged = dirMode
        rightColumnHeader.isVisible = dirMode; rightColumnHeader.isManaged = dirMode
    }

    // ═══ filter ═══
    @FXML
    private fun onFilterChanged() {
        log.debug(LogTag.UI, "filter '{}'", filterField.text)
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

    // ═══ programmatic UI extras ═══
    private fun addProgrammaticUi() {
        configureToolbarButtons()
        ratioPopupLabel.style = "-fx-background-color:#fff8c9; -fx-border-color:#d4c36a; -fx-border-radius:8; " +
                "-fx-background-radius:8; -fx-padding:6 10 6 10; -fx-font-weight:700; -fx-text-fill:#5d4a00;"
        ratioPopup.content.add(ratioPopupLabel)
        ratioPopup.isAutoHide = false
        ratioPopup.isHideOnEscape = false
    }

    private fun configureToolbarButtons() {
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

    private fun installToolbarGraphic(button: ButtonBase) {
        val rawText = button.text ?: return
        val parts = rawText.split("\n", limit = 2)
        val sourceIcon = parts.firstOrNull().orEmpty()
        val labelText = parts.getOrNull(1).orEmpty()
        val iconText = higToolbarIcon(sourceIcon, labelText)
        val color = when (button) {
            diffBtn -> "#b32020"
            equalBtn -> "#1f7a1f"
            else -> "#111111"
        }
        val icon = Label(iconText).apply {
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

    private fun configureCompareLists() {
        val listStyle = "-fx-background-color:#ffffff; -fx-border-width:0; -fx-font-smoothing-type:gray; -fx-opacity:1;"
        leftListView.fixedCellSize = 21.0
        rightListView.fixedCellSize = 21.0
        leftListView.style = listStyle
        rightListView.style = listStyle
    }

    private fun restoreUiFromState() {
        val state = comparatorState ?: return
        log.debug(LogTag.STATE, "restore UI dir={} sync={} ratio={}", state.isDirMode, state.isSyncScroll, state.splitRatio)
        restoringState = true
        try {
            syncScrollToggle.isSelected = state.isSyncScroll
            setDirMode(state.isDirMode)
            leftPanelRatio = state.splitRatio.coerceIn(0.15, 0.85)
        } finally {
            restoringState = false
        }
    }

    private fun setupResizableCenterStrip() {
        HBox.setHgrow(leftPanel, Priority.ALWAYS)
        HBox.setHgrow(rightPanel, Priority.ALWAYS)
        leftPanel.minWidth = 160.0
        rightPanel.minWidth = 160.0

        Platform.runLater {
            applyPanelRatio(leftPanelRatio)
            contentBox.widthProperty().addListener { _, _, _ -> applyPanelRatio(leftPanelRatio) }
        }

        centerStrip.addEventFilter(MouseEvent.MOUSE_PRESSED) { event ->
            applyRatioFromPointer(event.sceneX)
            showRatioPopup(event)
            event.consume()
        }
        centerStrip.addEventFilter(MouseEvent.MOUSE_DRAGGED) { event ->
            applyRatioFromPointer(event.sceneX)
            showRatioPopup(event)
            event.consume()
        }
        centerStrip.addEventFilter(MouseEvent.MOUSE_RELEASED) { _ ->
            hideRatioPopup()
            persistUiState()
        }
    }

    private fun applyRatioFromPointer(sceneX: Double) {
        val bounds = contentBox.localToScene(contentBox.boundsInLocal) ?: return
        val usableWidth = bounds.width - centerStrip.width
        if (usableWidth <= 0.0) return
        val leftWidth = (sceneX - bounds.minX - centerStrip.width / 2.0).coerceIn(usableWidth * 0.15, usableWidth * 0.85)
        applyPanelRatio(leftWidth / usableWidth)
    }

    private fun applyPanelRatio(ratio: Double) {
        if (contentBox.width <= 0.0) return
        leftPanelRatio = ratio.coerceIn(0.15, 0.85)
        val usableWidth = (contentBox.width - centerStrip.width).coerceAtLeast(0.0)
        if (usableWidth <= 0.0) return
        leftPanel.prefWidth = usableWidth * leftPanelRatio
        rightPanel.prefWidth = usableWidth * (1.0 - leftPanelRatio)
        updateRatioPopupText()
    }

    private fun showRatioPopup(event: MouseEvent) {
        updateRatioPopupText()
        val stage = centerStrip.scene?.window ?: return
        if (!ratioPopup.isShowing) ratioPopup.show(stage)
        ratioPopup.x = event.screenX + 16.0
        ratioPopup.y = event.screenY - 18.0
    }

    private fun updateRatioPopupText() {
        val leftPercent = (leftPanelRatio * 100.0).toInt()
        ratioPopupLabel.text = "$leftPercent% / ${100 - leftPercent}%"
    }

    private fun hideRatioPopup() {
        if (ratioPopup.isShowing) ratioPopup.hide()
    }

    @FXML
    private fun onSwapPanels() {
        showCompareView()
        log.info(LogTag.UI, "swap panels")
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
        persistInputPaths()
    }

    @FXML
    private fun onLoadHome() {
        showHomeView()
    }

    private fun showHomeView() {
        log.info(LogTag.UI, "show home")
        rootPane.top = null
        homeView = buildHomeView()
        rootPane.center = homeView
        rootPane.bottom = null
        updateWindowTitle(TITLE_HOME)
    }

    private fun showCompareView() {
        if (rootPane.center !== contentBox) {
            log.info(LogTag.UI, "show compare")
            rootPane.top = topChrome
            rootPane.center = contentBox
            rootPane.bottom = bottomChrome
            updateWindowTitle(TITLE_COMPARE)
            Platform.runLater { applyPanelRatio(leftPanelRatio) }
        }
    }

    private fun setupEventLog() {
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

    private fun buildHomeView(): BorderPane {
        val root = BorderPane().apply {
            style = "-fx-background-color:#f7f7f7; -fx-font-family:'${App.sfProDisplayFamily()}','Helvetica Neue',Arial,sans-serif;"
        }
        root.left = buildSessionsPane()
        root.center = buildHomeContent()
        return root
    }

    private fun buildSessionsPane(): VBox {
        val savedName = savedSessionName()
        val treeRoot = TreeItem("Sessions").apply {
            isExpanded = true
            children.add(TreeItem("New"))
            children.add(TreeItem("Auto-saved").apply {
                isExpanded = true
                children.add(TreeItem("Today").apply {
                    isExpanded = true
                    children.add(TreeItem(savedName))
                })
                children.add(TreeItem("More than 6 days ago"))
            })
        }
        val tree = TreeView(treeRoot).apply {
            isShowRoot = false
            prefWidth = 240.0
            selectionModel.select(treeRoot.children[1].children[0].children[0])
            setOnMouseClicked { event ->
                if (event.clickCount >= 2) openSavedSession()
            }
        }
        val search = TextField().apply {
            promptText = "Search"
            prefHeight = 24.0
        }
        return VBox(4.0).apply {
            style = "-fx-background-color:#e6e6e6; -fx-border-color:#bdbdbd; -fx-border-width:0 1 0 0;"
            children.add(Label("Sessions").apply {
                style = "-fx-font-size:15; -fx-padding:4 8 2 8;"
            })
            children.add(tree)
            VBox.setVgrow(tree, Priority.ALWAYS)
            children.add(HBox(4.0, Button("+"), Button("-"), search).apply {
                padding = Insets(4.0, 6.0, 6.0, 6.0)
                HBox.setHgrow(search, Priority.ALWAYS)
            })
        }
    }

    private fun buildHomeContent(): VBox {
        val state = comparatorState ?: ComparatorState.defaults()
        val left = state.leftInputPath.ifBlank { System.getProperty("user.home", "") }
        val right = state.rightInputPath
        val title = Label("▣ ${savedSessionName()}").apply {
            style = "-fx-font-size:18; -fx-font-weight:700;"
        }
        val paths = VBox(4.0, Label(left), Label(right)).apply {
            style = "-fx-font-size:13; -fx-text-fill:#222;"
        }
        val open = Button("Open").apply {
            prefWidth = 84.0
            setOnAction { openSavedSession() }
        }
        val edit = Button("Edit").apply {
            prefWidth = 84.0
            setOnAction { showCompareView() }
        }
        val intro = Label("Drag folders or files onto session icon\nor click a session icon to begin:").apply {
            alignment = Pos.CENTER
            style = "-fx-font-size:15; -fx-text-alignment:center;"
        }
        val actions = GridPane().apply {
            hgap = 44.0
            vgap = 34.0
            alignment = Pos.CENTER
            add(homeAction("▣", "Folder Compare") { startEmptyCompare(dir = true) }, 0, 0)
            add(homeAction("▣↻", "Folder Sync") { startEmptyCompare(dir = true) }, 1, 0)
            add(homeAction("▤", "Text Compare") { startEmptyCompare(dir = false) }, 2, 0)
            add(homeAction("▤✎", "Text Edit") { startEmptyCompare(dir = false) }, 3, 0)
            add(homeAction("0101", "Hex Compare") { startEmptyCompare(dir = false) }, 0, 1)
        }
        return VBox(26.0).apply {
            padding = Insets(12.0)
            children.add(title)
            children.add(paths)
            children.add(HBox(10.0, open, edit))
            children.add(Region().apply { minHeight = 48.0 })
            children.add(intro)
            children.add(actions)
            alignment = Pos.TOP_LEFT
            VBox.setVgrow(actions, Priority.ALWAYS)
        }
    }

    private fun homeAction(icon: String, label: String, action: () -> Unit): VBox {
        val iconLabel = Label(icon).apply {
            minWidth = 84.0
            minHeight = 58.0
            alignment = Pos.CENTER
            style = "-fx-font-size:26; -fx-background-color:#f1d89f; -fx-border-color:#777; -fx-border-radius:3; -fx-background-radius:3;"
        }
        val text = Label(label).apply {
            alignment = Pos.CENTER
            maxWidth = 130.0
            style = "-fx-font-size:15;"
        }
        return VBox(8.0, iconLabel, text).apply {
            alignment = Pos.CENTER
            setOnMouseClicked { action() }
        }
    }

    private fun startEmptyCompare(dir: Boolean) {
        showCompareView()
        setDirMode(dir)
    }

    private fun openSavedSession() {
        showCompareView()
        val state = comparatorState ?: return
        appendEvent("Load comparison: ${state.leftInputPath} <-> ${state.rightInputPath}")
        restoringState = true
        try {
            restoreSavedPath(state.leftInputPath, isLeft = true)
            restoreSavedPath(state.rightInputPath, isLeft = false)
        } finally {
            restoringState = false
        }
        if (leftPath != null && rightPath != null) onCompare()
    }

    private fun savedSessionName(): String {
        val raw = comparatorState?.leftInputPath.orEmpty()
        return raw.takeIf { it.isNotBlank() }?.let { Path.of(it).fileName?.toString() } ?: "Documents"
    }

    private fun updateWindowTitle(title: String) {
        (rootPane.scene?.window as? Stage)?.title = title
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
    @FXML
    private fun onOpenLeft() {
        showCompareView()
        chooseFileOrDir("Open Left")?.let { p ->
            log.info(LogTag.UI, "open left {}", p)
            applyLeftPath(p)
            loadDirectoryPreview(p, leftListView, isLeft = true)
        }
    }

    @FXML
    private fun onOpenRight() {
        showCompareView()
        chooseFileOrDir("Open Right")?.let { p ->
            log.info(LogTag.UI, "open right {}", p)
            applyRightPath(p)
            loadDirectoryPreview(p, rightListView, isLeft = false)
        }
    }

    @FXML
    private fun onCompare() {
        showCompareView()
        val lp = leftPath;
        val rp = rightPath
        if (lp == null || rp == null) {
            log.warn(LogTag.COMPARE, "compare blocked: missing side L={} R={}", lp, rp)
            showAlert("Load both sides first."); return
        }
        if (lp == rp) {
            log.warn(LogTag.COMPARE, "compare blocked: same path {}", lp)
            showAlert("Both sides point to the same location. Comparing them is pointless 🙄"); return
        }
        log.info(LogTag.COMPARE, "compare dir={} L={} R={}", dirMode, lp, rp)
        appendEvent("Load comparison: $lp <-> $rp")
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
            log.error(LogTag.COMPARE, "compare failed: {}", ex.message)
            showAlert("Compare failed: ${ex.message}")
        }
    }

    @FXML
    private fun onRefresh() {
        log.info(LogTag.UI, "refresh")
        appendEvent("Fast refresh")
        leftPath?.let { loadDirectoryPreview(it, leftListView, isLeft = true) }
        rightPath?.let { loadDirectoryPreview(it, rightListView, isLeft = false) }
    }

    @FXML
    private fun onQuit() {
        log.info(LogTag.APP, "quit")
        Platform.exit()
    }

    @FXML
    private fun onToggleIdentical() {
        log.info(LogTag.UI, "show identical {}", showIdenticalCheck.isSelected)
        onCompare()
    }

    @FXML
    private fun onToggleDirMode() {
        setDirMode(!dirMode)
        persistUiState()
    }

    private fun setDirMode(enabled: Boolean) {
        if (dirMode != enabled) log.info(LogTag.UI, "mode {}", if (enabled) "dir" else "file")
        dirMode = enabled
        dirModeToggle.isSelected = enabled
        showDirsCheck.isSelected = enabled
        statusCenter.text = if (enabled) STATUS_DIR_MODE else STATUS_FILE_MODE
        installDiffCellFactories()
        updateColumnHeaderVisibility()
        if (!restoringState) persistInputPaths()
    }

    @FXML
    private fun onExpandAll() {
        log.info(LogTag.UI, "expand all")
        leftTreeModel?.expandAll(); rightTreeModel?.expandAll()
        refreshTreeViews()
    }

    @FXML
    private fun onCollapseAll() {
        log.info(LogTag.UI, "collapse all")
        leftTreeModel?.collapseAll(); rightTreeModel?.collapseAll()
        refreshTreeViews()
    }

    private fun restoreInputsFromState() {
        val state = comparatorState ?: return
        restoringState = true
        try {
            restoreSavedPath(state.leftInputPath, isLeft = true)
            restoreSavedPath(state.rightInputPath, isLeft = false)
        } finally {
            restoringState = false
        }
        persistInputPaths()
    }

    private fun restoreSavedPath(rawPath: String, isLeft: Boolean) {
        if (rawPath.isBlank()) return
        log.debug(LogTag.STATE, "restore {} path {}", if (isLeft) "left" else "right", rawPath)
        try {
            val restored = Path.of(rawPath)
            if (isLeft) {
                applyLeftPath(restored); loadDirectoryPreview(restored, leftListView, true)
            } else {
                applyRightPath(restored); loadDirectoryPreview(restored, rightListView, false)
            }
        } catch (ex: Exception) {
            log.warn(LogTag.STATE, "restore {} failed {}", if (isLeft) "left" else "right", rawPath, ex)
        }
    }

    private fun applyLeftPath(path: Path) {
        leftPath = path
        leftPathField.text = path.toString()
        log.debug(LogTag.UI, "left path {}", path)
        if (!restoringState) persistInputPaths()
    }

    private fun applyRightPath(path: Path) {
        rightPath = path
        rightPathField.text = path.toString()
        log.debug(LogTag.UI, "right path {}", path)
        if (!restoringState) persistInputPaths()
    }

    private fun persistInputPaths() {
        if (restoringState) return
        val state = comparatorState ?: ComparatorState.defaults().also { comparatorState = it }
        state.leftInputPath = leftPath?.toString() ?: ""
        state.rightInputPath = rightPath?.toString() ?: ""
        state.isDirMode = dirMode
        state.isSyncScroll = syncScrollToggle.isSelected
        state.splitRatio = leftPanelRatio
        stateService.save(state)
    }

    private fun persistUiState() {
        if (restoringState) return
        val state = comparatorState ?: ComparatorState.defaults().also { comparatorState = it }
        state.isDirMode = dirMode
        state.isSyncScroll = syncScrollToggle.isSelected
        state.splitRatio = leftPanelRatio
        stateService.save(state)
    }

    // ═══ center strip stubs ═══
    @FXML
    private fun onCopyToRight() {
        log.info(LogTag.UI, "copy right requested")
        statusCenter.text = "→ copy to right (stub)"
    }

    @FXML
    private fun onCopyToLeft() {
        log.info(LogTag.UI, "copy left requested")
        statusCenter.text = "← copy to left (stub)"
    }

    @FXML
    private fun onShowDiff() {
        log.info(LogTag.UI, "show diffs only")
        statusCenter.text = "showing diffs only"
    }

    @FXML
    private fun onShowEqual() {
        log.info(LogTag.UI, "show equal only")
        statusCenter.text = "showing identical only"
    }

    @FXML
    private fun onDeleteSelected() {
        log.info(LogTag.UI, "delete requested")
        statusCenter.text = "🗑 delete (stub)"
    }

    @FXML
    private fun onSyncScroll() {
        log.info(LogTag.UI, "sync scroll {}", syncScrollToggle.isSelected)
        persistUiState()
    }

    @FXML
    private fun onCopyPathLeft() {
        leftPath?.let { copyToClipboard(it.toString()) }
    }

    @FXML
    private fun onCopyPathRight() {
        rightPath?.let { copyToClipboard(it.toString()) }
    }

    @FXML
    private fun onAbout() {
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
        val chosen = if (dirMode) {
            DirectoryChooser().apply { this.title = title }
                .showDialog(getStage())?.toPath()
        } else {
            FileChooser().apply { this.title = title }
                .showOpenDialog(getStage())?.toPath()
        }
        log.debug(LogTag.UI, "chooser '{}' -> {}", title, chosen)
        return chosen
    }

    private fun loadDirectoryPreview(path: Path, listView: ListView<CompareLineItem>, isLeft: Boolean) {
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
        } catch (ex: IOException) {
            log.error(LogTag.IO, "read failed {}: {}", path, ex.message)
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

    private fun appendEvent(message: String) {
        if (!::eventLogView.isInitialized) return
        eventLogView.items.add("${EVENT_TIME_FMT.format(LocalDateTime.now())}  $message")
        eventLogView.scrollTo(eventLogView.items.size - 1)
    }

    private fun getStage(): Stage = leftPathField.scene.window as Stage

    private fun showAlert(msg: String) {
        Alert(Alert.AlertType.WARNING).apply { contentText = msg }.showAndWait()
    }

    private fun copyToClipboard(text: String) {
        Clipboard.getSystemClipboard().setContent(ClipboardContent().apply { putString(text) })
        statusCenter.text = STATUS_CLIPBOARD_COPIED
        log.info(LogTag.UI, "clipboard copied chars={}", text.length)
    }
}
