package org.senatov

import javafx.application.Platform
import javafx.geometry.Orientation
import javafx.scene.control.ListView
import javafx.scene.control.ScrollBar
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import org.senatov.helpers.log.LogTag

internal fun MainController.setupSyncScroll() {
    log.debug(LogTag.UI, "setupSyncScroll()")
    Platform.runLater {
        bindScrollBars(leftListView, rightListView)
        bindScrollBars(rightListView, leftListView)
        bindScrollBars(leftListView, operationListView)
        bindScrollBars(rightListView, operationListView)
        bindScrollBars(operationListView, leftListView)
        bindScrollBars(operationListView, rightListView)
        bindScrollBars(previewLeftView, previewRightView)
        bindScrollBars(previewRightView, previewLeftView)
    }
}

private fun MainController.bindScrollBars(source: ListView<*>, target: ListView<*>) {
    log.debug(LogTag.UI, "bindScrollBars(source={}, target={})", source, target)
    val srcBar = findScrollBar(source) ?: return
    val tgtBar = findScrollBar(target) ?: return
    srcBar.valueProperty().addListener { _, _, nv ->
        if (syncScrollToggle.isSelected) tgtBar.value = nv.toDouble()
    }
}

private fun findScrollBar(listView: ListView<*>): ScrollBar? =
    listView.lookupAll(".scroll-bar").filterIsInstance<ScrollBar>()
        .firstOrNull { it.orientation == Orientation.VERTICAL }

internal fun MainController.setupResizableCenterStrip() {
    log.debug(LogTag.UI, "setupResizableCenterStrip()")
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
    centerStrip.addEventFilter(MouseEvent.MOUSE_RELEASED) {
        hideRatioPopup()
        persistUiState()
    }
}

private fun MainController.applyRatioFromPointer(sceneX: Double) {
    log.debug(LogTag.UI, "applyRatioFromPointer(sceneX={})", sceneX)
    val bounds = contentBox.localToScene(contentBox.boundsInLocal) ?: return
    val usableWidth = bounds.width - centerStrip.width
    if (usableWidth <= 0.0) return
    val leftWidth = (sceneX - bounds.minX - centerStrip.width / 2.0)
        .coerceIn(usableWidth * 0.15, usableWidth * 0.85)
    applyPanelRatio(leftWidth / usableWidth)
}

internal fun MainController.applyPanelRatio(ratio: Double) {
    log.debug(LogTag.UI, "applyPanelRatio(ratio={})", ratio)
    if (contentBox.width <= 0.0) return
    leftPanelRatio = ratio.coerceIn(0.15, 0.85)
    val usableWidth = (contentBox.width - centerStrip.width).coerceAtLeast(0.0)
    if (usableWidth <= 0.0) return
    leftPanel.prefWidth = usableWidth * leftPanelRatio
    rightPanel.prefWidth = usableWidth * (1.0 - leftPanelRatio)
    updateRatioPopupText()
}

private fun MainController.showRatioPopup(event: MouseEvent) {
    log.debug(LogTag.UI, "showRatioPopup(sceneX={}, sceneY={})", event.screenX, event.screenY)
    updateRatioPopupText()
    val stage = centerStrip.scene?.window ?: return
    if (!ratioPopup.isShowing) ratioPopup.show(stage)
    ratioPopup.x = event.screenX + 16.0
    ratioPopup.y = event.screenY - 18.0
}

private fun MainController.updateRatioPopupText() {
    log.debug(LogTag.UI, "updateRatioPopupText()")
    val leftPercent = (leftPanelRatio * 100.0).toInt()
    ratioPopupLabel.text = "$leftPercent% / ${100 - leftPercent}%"
}

private fun MainController.hideRatioPopup() {
    log.debug(LogTag.UI, "hideRatioPopup()")
    if (ratioPopup.isShowing) ratioPopup.hide()
}
