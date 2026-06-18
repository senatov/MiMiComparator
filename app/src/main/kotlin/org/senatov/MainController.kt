/*
 * MainController — FXML entry point for MiMiComparator.
 * Coordinates focused controller modules and keeps FXML handlers stable.
 */
package org.senatov

import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Popup
import org.senatov.cli.CliArgs
import org.senatov.compare.DirCompareResult
import org.senatov.helpers.log.LogHelper
import org.senatov.helpers.log.LogTag
import org.senatov.model.CompareLineItem
import org.senatov.model.tree.DirTreeModel
import org.senatov.ui.config.ComparatorState
import org.senatov.ui.config.ComparatorStateService
import org.slf4j.LoggerFactory
import java.nio.file.Path

class MainController {

    internal val log = LoggerFactory.getLogger(MainController::class.java)

    @FXML
    internal lateinit var rootPane: BorderPane
    @FXML
    internal lateinit var topChrome: VBox
    @FXML
    internal lateinit var bottomChrome: VBox
    @FXML
    internal lateinit var bottomBar: HBox

    @FXML
    internal lateinit var leftPanel: VBox
    @FXML
    internal lateinit var leftPathField: TextField
    @FXML
    internal lateinit var leftPathMenuButton: Button

    @FXML
    internal lateinit var leftPathBrowseButton: Button

    @FXML
    internal lateinit var leftListView: ListView<CompareLineItem>
    @FXML
    internal lateinit var leftColumnHeader: HBox

    @FXML
    internal lateinit var rightPanel: VBox
    @FXML
    internal lateinit var rightPathField: TextField

    @FXML
    internal lateinit var rightPathMenuButton: Button

    @FXML
    internal lateinit var rightPathBrowseButton: Button
    @FXML
    internal lateinit var rightListView: ListView<CompareLineItem>
    @FXML
    internal lateinit var rightColumnHeader: HBox

    @FXML
    internal lateinit var contentBox: HBox
    @FXML
    internal lateinit var centerStrip: VBox
    @FXML
    internal lateinit var copyRightBtn: Button
    @FXML
    internal lateinit var copyLeftBtn: Button
    @FXML
    internal lateinit var diffBtn: Button
    @FXML
    internal lateinit var equalBtn: Button
    @FXML
    internal lateinit var deleteBtn: Button
    @FXML
    internal lateinit var swapBtn: Button

    @FXML
    internal lateinit var mainToolBar: ToolBar
    @FXML
    internal lateinit var syncScrollToggle: ToggleButton
    @FXML
    internal lateinit var dirModeToggle: ToggleButton
    @FXML
    internal lateinit var showIdenticalCheck: CheckMenuItem
    @FXML
    internal lateinit var showDirsCheck: CheckMenuItem
    @FXML
    internal lateinit var diffCountLabel: Label
    @FXML
    internal lateinit var eventLogView: ListView<String>
    @FXML
    internal lateinit var filterField: TextField
    @FXML
    internal lateinit var statusLeft: Label
    @FXML
    internal lateinit var statusCenter: Label
    @FXML
    internal lateinit var statusRight: Label

    internal var leftPath: Path? = null
    internal var rightPath: Path? = null
    internal var dirMode = false
    internal var pendingCliArgs: CliArgs? = null
    internal var leftTreeModel: DirTreeModel? = null
    internal var rightTreeModel: DirTreeModel? = null
    internal var lastDirResult: DirCompareResult? = null
    internal val stateService = ComparatorStateService()
    internal var comparatorState: ComparatorState? = null
    internal var restoringState = false
    internal var leftPanelRatio = 0.5
    internal val ratioPopupLabel = Label()
    internal val ratioPopup = Popup()
    internal var homeView: BorderPane? = null

    @FXML
    private fun initialize() {
        log.debug(LogTag.UI, "[{}]", LogHelper.method())
        comparatorState = stateService.load()
        configureCompareLists()
        installDiffCellFactories()
        configurePathFields()
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

    @FXML
    private fun onLoadHome() = showHomeView()
    @FXML
    private fun onOpenLeft() = openPath(isLeft = true)
    @FXML
    private fun onOpenRight() = openPath(isLeft = false)
    @FXML
    private fun onCompare() = compareCurrentInputs()
    @FXML
    private fun onRefresh() = refreshPreviews()
    @FXML
    private fun onQuit() = Platform.exit()
    @FXML
    private fun onToggleIdentical() = compareCurrentInputs()
    @FXML
    private fun onToggleDirMode() = toggleDirMode()
    @FXML
    private fun onExpandAll() = expandAllTrees()
    @FXML
    private fun onCollapseAll() = collapseAllTrees()
    @FXML
    private fun onFilterChanged() = applyCurrentFilter()
    @FXML
    private fun onSwapPanels() = swapPanels()
    @FXML
    private fun onCopyToRight() = setStubStatus("→ copy to right (stub)")
    @FXML
    private fun onCopyToLeft() = setStubStatus("← copy to left (stub)")
    @FXML
    private fun onShowDiff() = setStubStatus("showing diffs only")
    @FXML
    private fun onShowEqual() = setStubStatus("showing identical only")
    @FXML
    private fun onDeleteSelected() = setStubStatus("🗑 delete (stub)")
    @FXML
    private fun onSyncScroll() = persistUiState()
    @FXML
    private fun onCopyPathLeft() = leftPath?.let { copyToClipboard(it.toString()) } ?: Unit
    @FXML
    private fun onCopyPathRight() = rightPath?.let { copyToClipboard(it.toString()) } ?: Unit
    @FXML
    private fun onAbout() = showAboutDialog()
}