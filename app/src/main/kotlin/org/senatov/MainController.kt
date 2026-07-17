/*
 * MainController — FXML entry point for MiMiComparator.
 * Coordinates focused controller modules and keeps FXML handlers stable.
 */
package org.senatov

import javafx.animation.PauseTransition
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Popup
import javafx.util.Duration
import org.senatov.cli.CliArgs
import org.senatov.helpers.log.LogTag
import org.senatov.model.CompareLineItem
import org.senatov.model.tree.DirTreeModel
import org.senatov.ui.config.ComparatorState
import org.senatov.ui.config.ComparatorStateService
import org.senatov.ui.preview.PreviewSettings
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
    internal val comparisonSplitPane = SplitPane()
    @FXML
    internal lateinit var centerStrip: VBox

    @FXML
    internal lateinit var operationListView: ListView<String>
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
    internal lateinit var previewLeftView: ListView<String>

    @FXML
    internal lateinit var previewRightView: ListView<String>

    @FXML
    internal lateinit var previewDiffCountLabel: Label

    @FXML
    internal lateinit var previewToolBar: ToolBar

    @FXML
    internal lateinit var previewSettingsBtn: Button

    @FXML
    internal lateinit var sideBySideViewToggle: ToggleButton

    @FXML
    internal lateinit var unifiedViewToggle: ToggleButton

    @FXML
    internal lateinit var previewNoticeBox: HBox

    @FXML
    internal lateinit var previewNoticeIcon: Label
    @FXML
    internal lateinit var filterField: TextField

    @FXML
    internal lateinit var compareModeChoice: ComboBox<String>
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
    internal val stateService = ComparatorStateService()
    internal var comparatorState: ComparatorState? = null
    internal var restoringState = false
    internal var leftPanelRatio = 0.5
    internal val ratioPopupLabel = Label()
    internal val ratioPopup = Popup()
    internal var syncingSelection = false
    internal var showOnlyIdentical = false
    internal val filterDebounce = PauseTransition(Duration.millis(180.0))
    internal val previewSplitSaveDebounce = PauseTransition(Duration.millis(250.0))
    internal val previewSettings = PreviewSettings()

    @FXML
    private fun initialize() {
        log.debug(LogTag.UI, "initialize()")
        comparatorState = stateService.load()
        configureCompareLists()
        configurePreviewPane()
        configurePreviewSettingsMenu()
        setupSelectionPreview()
        installDiffCellFactories()
        configurePathFields()
        configureCompareModes()
        setupEventLog()
        setupClickToExpand()
        addProgrammaticUi()
        setupResizablePreviewPane()
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
    private fun onLoadHome() = uiAction("onLoadHome") { showHomeView() }
    @FXML
    private fun onOpenLeft() = uiAction("onOpenLeft") { openPath(ComparisonSide.LEFT) }
    @FXML
    private fun onOpenRight() = uiAction("onOpenRight") { openPath(ComparisonSide.RIGHT) }
    @FXML
    private fun onCompare() = uiAction("onCompare") { compareCurrentInputs() }
    @FXML
    private fun onRefresh() = uiAction("onRefresh") { refreshPreviews() }
    @FXML
    private fun onQuit() = uiAction("onQuit") { Platform.exit() }
    @FXML
    private fun onToggleIdentical() = uiAction("onToggleIdentical") {
        showOnlyIdentical = false
        compareCurrentInputs()
    }
    @FXML
    private fun onToggleDirMode() = uiAction("onToggleDirMode") { toggleDirMode() }
    @FXML
    private fun onExpandAll() = uiAction("onExpandAll") { expandAllTrees() }
    @FXML
    private fun onCollapseAll() = uiAction("onCollapseAll") { collapseAllTrees() }
    @FXML
    private fun onFilterChanged() = uiAction("onFilterChanged") { applyCurrentFilter() }

    @FXML
    private fun onCompareModeChanged() = uiAction("onCompareModeChanged") { applyCompareMode() }

    @FXML
    private fun onSwapPanels() = uiAction("onSwapPanels") { swapPanels() }
    @FXML
    private fun onCopyToRight() = uiAction("onCopyToRight") { copySelectedItem(ComparisonSide.LEFT) }
    @FXML
    private fun onCopyToLeft() = uiAction("onCopyToLeft") { copySelectedItem(ComparisonSide.RIGHT) }
    @FXML
    private fun onShowDiff() = uiAction("onShowDiff") {
        showOnlyIdentical = false
        showIdenticalCheck.isSelected = false
        compareCurrentInputs()
    }
    @FXML
    private fun onShowEqual() = uiAction("onShowEqual") {
        showOnlyIdentical = true
        showIdenticalCheck.isSelected = true
        compareCurrentInputs()
    }
    @FXML
    private fun onDeleteSelected() = uiAction("onDeleteSelected") { setStubStatus("🗑 delete (stub)") }
    @FXML
    private fun onSyncScroll() = uiAction("onSyncScroll") { persistUiState() }

    @FXML
    private fun onPreviewSettings() = uiAction("onPreviewSettings") { showPreviewSettingsMenu() }

    @FXML
    private fun onSideBySideView() = uiAction("onSideBySideView") { sideBySideViewToggle.isSelected = true }

    @FXML
    private fun onUnifiedView() = uiAction("onUnifiedView") { sideBySideViewToggle.isSelected = true }
    @FXML
    private fun onCopyPathLeft() = uiAction("onCopyPathLeft") { leftPath?.let { copyToClipboard(it.toString()) } }
    @FXML
    private fun onCopyPathRight() = uiAction("onCopyPathRight") { rightPath?.let { copyToClipboard(it.toString()) } }
    @FXML
    private fun onAbout() = uiAction("onAbout") { showAboutDialog() }

    private inline fun uiAction(method: String, action: () -> Unit) {
        log.debug(LogTag.UI, "{}()", method)
        action()
    }
}