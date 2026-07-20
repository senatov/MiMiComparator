package org.senatov

import javafx.geometry.Side
import javafx.scene.control.*
import org.senatov.ui.preview.DifferenceHighlighting
import org.senatov.ui.preview.IgnoreDifferences

internal fun MainController.configurePreviewSettingsMenu() {
    previewToolBar.styleClass.add("preview-toolbar")
    val helpTexts = mapOf<ButtonBase, String>(
            previewToolBar.items.filterIsInstance<Button>().first { it.text == "↑" } to "Expand all folders",
            previewToolBar.items.filterIsInstance<Button>().first { it.text == "↓" } to "Collapse all folders",
            previewToolBar.items.filterIsInstance<Button>().first { it.text == "✎" } to "Edit preview settings",
            previewToolBar.items.filterIsInstance<Button>().first { it.text == "←" } to "Copy preview item to the left",
            previewToolBar.items.filterIsInstance<Button>().first { it.text == "→" } to "Copy preview item to the right",
            sideBySideViewToggle to "Side-by-side viewer",
            unifiedViewToggle to "Unified viewer is not available yet",
            previewSettingsBtn to "Diff viewer settings",
    )
    configurePreviewToolbarGraphics(helpTexts)
}

internal fun MainController.showPreviewSettingsMenu() {
    buildPreviewSettingsMenu().show(previewSettingsBtn, Side.BOTTOM, 0.0, 2.0)
}

private fun MainController.buildPreviewSettingsMenu(): ContextMenu = ContextMenu().apply {
    styleClass += "preview-settings-menu"
    items += CheckMenuItem("Align Changes In Side-by-Side Diff").apply {
        isSelected = previewSettings.alignChanges
        isDisable = true
        setOnAction {
            previewSettings.alignChanges = isSelected
            refreshSelectedPreview()
        }
    }
    items += CheckMenuItem("Synchronize Scrolling").apply {
        isSelected = syncScrollToggle.isSelected
        setOnAction {
            syncScrollToggle.isSelected = isSelected
            persistUiState()
        }
    }
    items += SeparatorMenuItem()
    items += section("Ignore Differences")
    items += radioItems(
            values = IgnoreDifferences.entries,
            selected = previewSettings.ignoreDifferences,
            label = { it.label },
            onSelected = { selected ->
                previewSettings.ignoreDifferences = selected
                refreshSelectedPreview()
            },
    )
    items += SeparatorMenuItem()
    items += section("Highlighting Differences")
    items += highlightingItems()
    items += SeparatorMenuItem()
    items += Menu("Appearance").apply {
        items += MenuItem("Compact rows").apply {
            setOnAction { setPreviewRowHeight(19.0) }
        }
        items += MenuItem("Default rows").apply {
            setOnAction { setPreviewRowHeight(22.0) }
        }
    }
    items += MenuItem("Help\tF1").apply { setOnAction { showAboutDialog() } }
}

private fun section(text: String) = MenuItem(text).apply {
    isDisable = true
    styleClass += "preview-menu-section"
}

private fun <T> radioItems(
        values: List<T>,
        selected: T,
        label: (T) -> String,
        onSelected: (T) -> Unit,
): List<RadioMenuItem> {
    val group = ToggleGroup()
    return values.map { value ->
        RadioMenuItem(label(value)).apply {
            toggleGroup = group
            isSelected = value == selected
            setOnAction { onSelected(value) }
        }
    }
}

private fun MainController.highlightingItems(): List<RadioMenuItem> {
    val group = ToggleGroup()
    return DifferenceHighlighting.entries.map { value ->
        RadioMenuItem(value.label).apply {
            toggleGroup = group
            isSelected = value == previewSettings.highlighting
            isDisable = !value.supported
            setOnAction {
                previewSettings.highlighting = value
                refreshSelectedPreview()
            }
        }
    }
}

private fun MainController.refreshSelectedPreview() {
    leftListView.selectionModel.selectedIndex.takeIf { it >= 0 }?.let(::showSelectedFilePreview)
}

private fun MainController.setPreviewRowHeight(height: Double) {
    previewLeftView.fixedCellSize = height
    previewRightView.fixedCellSize = height
}