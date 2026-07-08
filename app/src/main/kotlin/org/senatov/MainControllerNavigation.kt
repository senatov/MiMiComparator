package org.senatov

import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeView
import javafx.scene.layout.*
import org.senatov.helpers.log.LogHelper
import org.senatov.helpers.log.LogTag
import org.senatov.ui.config.ComparatorState
import java.nio.file.Path

internal fun MainController.showHomeView() {
    log.debug(LogTag.UI, "showHomeView()")
    rootPane.top = null
    homeView = buildHomeView()
    rootPane.center = homeView
    rootPane.bottom = null
    updateWindowTitle(TITLE_HOME)
}

internal fun MainController.showCompareView() {
    log.debug(LogTag.UI, "showCompareView()")
    if (rootPane.center !== contentBox) {
        log.info(org.senatov.helpers.log.LogTag.UI, "show compare")
        rootPane.top = topChrome
        rootPane.center = contentBox
        rootPane.bottom = bottomChrome
        updateWindowTitle(TITLE_COMPARE)
        Platform.runLater { applyPanelRatio(leftPanelRatio) }
    }
}

private fun MainController.buildHomeView(): BorderPane {
    log.debug(LogTag.UI, "buildHomeView()")
    val root = BorderPane().apply {
        style = "-fx-background-color:#f7f7f7;"
    }
    root.left = buildSessionsPane()
    root.center = buildHomeContent()
    return root
}

private fun MainController.buildSessionsPane(): VBox {
    log.debug(LogTag.UI, "buildSessionsPane()")
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
        val addSession = Button("+").apply { installStandardHelp("Create a new comparison session") }
        val removeSession = Button("-").apply { installStandardHelp("Remove the selected session") }
        children.add(HBox(4.0, addSession, removeSession, search).apply {
            padding = Insets(4.0, 6.0, 6.0, 6.0)
            HBox.setHgrow(search, Priority.ALWAYS)
        })
    }
}

private fun MainController.buildHomeContent(): VBox {
    log.debug(LogTag.UI, "buildHomeContent()")
    val state = comparatorState ?: ComparatorState.defaults()
    val left = state.leftInputPath.ifBlank { System.getProperty("user.home", "") }
    val right = state.rightInputPath
    val title = Label("▣ ${savedSessionName()}").apply {
        style = "-fx-font-size:18; -fx-font-weight:700;"
    }
    val paths = VBox(4.0, Label(left), Label(right)).apply {
        style = "-fx-font-size:14; -fx-text-fill:#222;"
    }
    val open = Button("Open").apply {
        prefWidth = 84.0
        installStandardHelp("Open the selected saved comparison")
        setOnAction { openSavedSession() }
    }
    val edit = Button("Edit").apply {
        prefWidth = 84.0
        installStandardHelp("Edit the current comparison setup")
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
        installStandardHelp("Start $label")
        setOnMouseClicked { action() }
    }
}

internal fun MainController.startEmptyCompare(dir: Boolean) {
    LogHelper.enter(log, LogTag.UI, "startEmptyCompare", "dir" to dir)
    showCompareView()
    setDirMode(dir)
}

internal fun MainController.openSavedSession() {
    log.debug(LogTag.UI, "openSavedSession()")
    showCompareView()
    val state = comparatorState ?: return
    appendEvent("Load comparison: ${state.leftInputPath} <-> ${state.rightInputPath}")
    restoringState = true
    try {
        restoreSavedPath(state.leftInputPath, isLeft = true)
        restoreSavedPath(state.rightInputPath, isLeft = false)
    }
    finally {
        restoringState = false
    }
    if (leftPath != null && rightPath != null) compareCurrentInputs()
}

internal fun MainController.savedSessionName(): String {
    log.debug(LogTag.UI, "savedSessionName()")
    val raw = comparatorState?.leftInputPath.orEmpty()
    return raw.takeIf { it.isNotBlank() }?.let { Path.of(it).fileName?.toString() } ?: "Documents"
}
