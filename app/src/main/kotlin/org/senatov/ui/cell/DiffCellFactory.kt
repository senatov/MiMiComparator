/*
 * DiffCellFactory — ListCell factory 4 CompareLineItem.
 * Dir mode: system bg, zebra striping, indent, disclosure triangles,
 *           columns Name / Size / Modified (TC-style).
 * File mode: IntelliJ-style diff coloring.
 * Iakov Senatov, 2026
 */
package org.senatov.ui.cell

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.OverrunStyle
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.util.Callback
import org.senatov.compare.DirectoryComparator
import org.senatov.model.CompareLineItem
import org.senatov.model.DiffStatus


class DiffCellFactory(private val dirMode: Boolean, private val mirrored: Boolean = false) :
    Callback<ListView<CompareLineItem>, ListCell<CompareLineItem>> {

    companion object {
        private const val ROW_HEIGHT = 24.0
        private const val NAME_FONT_SIZE = 15.0
        private const val META_FONT_SIZE = 14

        private fun monoStyle() =
            "-fx-font-size:${NAME_FONT_SIZE};-fx-font-weight:400;-fx-font-smoothing-type:gray;-fx-opacity:1;"

        private fun monoSmallStyle() =
            "-fx-font-size:${META_FONT_SIZE};-fx-font-weight:400;-fx-font-smoothing-type:gray;-fx-opacity:1;"

        private const val INDENT_PX = 18

        // dir mode zebra
        private const val DIR_EVEN = "#FFFFFF"
        private const val DIR_ODD = "#FAFAFA"
        private const val DIR_ADDED = "#FFFFFF"
        private const val DIR_MISSING = "#FFFFFF"
        private const val DIR_MODIFIED = "#FFF8D8"

        // file mode IntelliJ
        private const val FILE_IDENTICAL = "#FFFFFF"
        private const val FILE_MODIFIED = "#B8D4FF"
        private const val FILE_ADDED = "#C8E6C9"
        private const val FILE_MISSING = "#E0E0E0"
        private const val FILE_HEADER = "#E3F2FD"

        // text colors
        private const val TXT = "#111111"
        private const val TXT_MISS = "#6E6E73"
        private const val TXT_MOD = "#B00020"
        private const val TXT_DIFF = "#0A64FF"
        private const val TXT_DIR = "#111111"
        private const val TXT_SIZE = "#1D1D1F"
    }


    override fun call(listView: ListView<CompareLineItem>): ListCell<CompareLineItem> =
        if (dirMode) DirCell(mirrored) else FileCell()


    // ═══ Dir mode cell: columns Name | Size | Modified ═══
    private class DirCell(private val mirrored: Boolean) : ListCell<CompareLineItem>() {
        private var rowBackground = DIR_EVEN
        private val row = HBox(4.0)
        private val nameBox = HBox(2.0)
        private val markerLabel = Label()
        private val disclosureLabel = Label()
        private val iconLabel = Label()
        private val nameLabel = Label()
        private val sizeLabel = Label()
        private val dateLabel = Label()

        init {
            selectedProperty().addListener { _, _, _ -> applyRowBackground() }
            markerLabel.style = monoStyle() + "-fx-text-fill:$TXT;"
            markerLabel.minWidth = 15.0
            markerLabel.alignment = Pos.CENTER_LEFT
            disclosureLabel.style = monoStyle() + "-fx-text-fill:$TXT;"
            disclosureLabel.minWidth = 14.0
            disclosureLabel.alignment = Pos.CENTER
            iconLabel.style =
                "-fx-font-size:15;-fx-font-weight:400;-fx-font-smoothing-type:gray;-fx-text-fill:$TXT;-fx-opacity:1;"
            iconLabel.minWidth = 17.0
            iconLabel.alignment = Pos.CENTER
            nameLabel.style = monoStyle() + "-fx-text-fill:$TXT;"
            nameLabel.minWidth = 0.0
            nameLabel.maxWidth = Double.MAX_VALUE
            nameLabel.textOverrun = OverrunStyle.ELLIPSIS
            HBox.setHgrow(nameLabel, Priority.ALWAYS)
            nameBox.alignment = Pos.CENTER_LEFT
            nameBox.minWidth = 0.0
            nameBox.maxWidth = Double.MAX_VALUE
            nameBox.children.addAll(markerLabel, disclosureLabel, iconLabel, nameLabel)
            HBox.setHgrow(nameBox, Priority.ALWAYS)
            sizeLabel.style = monoSmallStyle() + "-fx-text-fill:$TXT_SIZE;"
            sizeLabel.minWidth = 92.0
            sizeLabel.prefWidth = 92.0
            sizeLabel.alignment = Pos.CENTER_RIGHT
            dateLabel.style = monoSmallStyle() + "-fx-text-fill:$TXT_SIZE;"
            dateLabel.minWidth = 128.0
            dateLabel.prefWidth = 128.0
            dateLabel.alignment = Pos.CENTER_RIGHT
            row.alignment = Pos.CENTER_LEFT
            row.minWidth = 0.0
            row.maxWidth = Double.MAX_VALUE
            if (mirrored) row.children.addAll(dateLabel, sizeLabel, nameBox)
            else row.children.addAll(nameBox, sizeLabel, dateLabel)
            row.padding = Insets(0.0, 5.0, 0.0, 5.0)
            row.minHeight = ROW_HEIGHT
            row.prefHeight = ROW_HEIGHT
            minHeight = ROW_HEIGHT
            prefHeight = ROW_HEIGHT
            row.prefWidthProperty().bind(widthProperty().subtract(2.0))
        }

        override fun updateItem(item: CompareLineItem?, empty: Boolean) {
            super.updateItem(item, empty)
            if (empty || item == null) {
                text = null
                graphic = null
                style = "-fx-background-color:transparent;"
                return
            }
            text = null
            if (item.status == DiffStatus.MISSING) {
                nameBox.padding = Insets.EMPTY
                markerLabel.text = ""
                disclosureLabel.text = ""
                iconLabel.text = ""
                nameLabel.text = ""
                sizeLabel.text = ""
                dateLabel.text = ""
                rowBackground = pickDirBg(item.status, index)
                applyRowBackground()
                graphic = row
                return
            }
            val marker = markerPrefix(item.status)
            val fg = when {
                item.status == DiffStatus.MODIFIED -> TXT_MOD
                item.status == DiffStatus.ADDED -> TXT_DIFF
                item.isDirectory -> TXT_DIR
                else -> TXT
            }
            markerLabel.text = marker
            markerLabel.style = monoStyle() + "-fx-text-fill:$fg;"
            disclosureLabel.text = if (item.isDirectory) (if (item.isExpanded) "▾" else "▸") else ""
            disclosureLabel.style = monoStyle() + "-fx-text-fill:$fg;"
            iconLabel.text = if (item.isDirectory) "▱" else fileGlyph(item.text)
            iconLabel.style =
                "-fx-font-size:15;-fx-font-weight:400;-fx-font-smoothing-type:gray;-fx-text-fill:$fg;-fx-opacity:1;"
            nameLabel.text = item.text
            nameLabel.style = monoStyle() + "-fx-text-fill:$fg;"
            nameBox.padding = Insets(0.0, 0.0, 0.0, (item.indentLevel * INDENT_PX).toDouble())
            sizeLabel.text = DirectoryComparator.formatSize(item.size)
            sizeLabel.style = monoSmallStyle() + "-fx-text-fill:$TXT_SIZE;"
            dateLabel.text = DirectoryComparator.formatDate(item.lastModifiedMs)
            dateLabel.style = monoSmallStyle() + "-fx-text-fill:$TXT_SIZE;"
            rowBackground = pickDirBg(item.status, index)
            applyRowBackground()
            graphic = row
        }

        private fun applyRowBackground() {
            val background = if (isSelected) "#B9D8FA" else rowBackground
            style = "-fx-background-color:$background;-fx-padding:0;-fx-opacity:1;"
        }

        private fun fileGlyph(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "txt", "asc", "ics", "doc", "docx" -> "≡"
            else -> "?"
        }

        private fun pickDirBg(st: DiffStatus, index: Int): String = when (st) {
            DiffStatus.ADDED -> DIR_ADDED
            DiffStatus.MISSING -> DIR_MISSING
            DiffStatus.MODIFIED -> DIR_MODIFIED
            else -> if (index % 2 == 0) DIR_EVEN else DIR_ODD
        }

        private fun markerPrefix(st: DiffStatus): String = when (st) {
            DiffStatus.IDENTICAL -> ""
            DiffStatus.MODIFIED -> "≠"
            DiffStatus.ADDED -> "+"
            DiffStatus.MISSING -> "-"
            DiffStatus.HEADER -> "#"
        }
    }


    // ═══ File mode cell: IntelliJ-style line diff ═══
    private class FileCell : ListCell<CompareLineItem>() {
        override fun updateItem(item: CompareLineItem?, empty: Boolean) {
            super.updateItem(item, empty)
            if (empty || item == null) {
                text = null
                style = monoStyle() + "-fx-background-color:transparent;"
                return
            }
            text = item.formatted()
            val (bg, fg) = when (item.status) {
                DiffStatus.MODIFIED -> FILE_MODIFIED to TXT_MOD
                DiffStatus.ADDED -> FILE_ADDED to TXT
                DiffStatus.MISSING -> FILE_MISSING to TXT_MISS
                DiffStatus.HEADER -> FILE_HEADER to TXT
                else -> FILE_IDENTICAL to TXT
            }
            minHeight = ROW_HEIGHT
            prefHeight = ROW_HEIGHT
            style = monoStyle() + "-fx-background-color:$bg;-fx-text-fill:$fg;-fx-padding:1 5 1 5;"
        }
    }
}
