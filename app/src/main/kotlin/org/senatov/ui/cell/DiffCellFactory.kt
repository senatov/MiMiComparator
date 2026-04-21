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
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.util.Callback
import org.senatov.App
import org.senatov.compare.DirectoryComparator
import org.senatov.helpers.log.LogHelper.enter
import org.senatov.model.CompareLineItem
import org.senatov.model.CompareLineItem.DiffStatus


class DiffCellFactory(private val dirMode: Boolean)
    : Callback<ListView<CompareLineItem>, ListCell<CompareLineItem>> {

    companion object {
        private fun monoStyle() =
            "-fx-font-family:'${App.sfProDisplayFamily()}','Helvetica Neue',Arial,sans-serif;-fx-font-size:14;-fx-font-weight:300;"

        private fun monoSmallStyle() =
            "-fx-font-family:'${App.sfProDisplayFamily()}','Helvetica Neue',Arial,sans-serif;-fx-font-size:13;-fx-font-weight:300;"
        private const val INDENT_PX = 18
        // dir mode zebra
        private const val DIR_EVEN = "#FFFFFF"
        private const val DIR_ODD = "#F5F5F5"
        private const val DIR_ADDED = "#D4EDDA"
        private const val DIR_MISSING = "#E8E8E8"
        private const val DIR_MODIFIED = "#FFF3CD"
        // file mode IntelliJ
        private const val FILE_IDENTICAL = "#FFFFFF"
        private const val FILE_MODIFIED = "#B8D4FF"
        private const val FILE_ADDED = "#C8E6C9"
        private const val FILE_MISSING = "#E0E0E0"
        private const val FILE_HEADER = "#E3F2FD"
        // text colors
        private const val TXT = "#333333"
        private const val TXT_MISS = "#999999"
        private const val TXT_MOD = "#0D47A1"
        private const val TXT_DIR = "#1A237E"
    }


    override fun call(listView: ListView<CompareLineItem>): ListCell<CompareLineItem> =
        if (dirMode) DirCell() else FileCell()


    // ═══ Dir mode cell: columns Name | Size | Modified ═══
    private class DirCell : ListCell<CompareLineItem>() {
        private val row = HBox(4.0)
        private val nameLabel = Label()
        private val sizeLabel = Label()
        private val dateLabel = Label()
        init {
            nameLabel.style = monoStyle() + "-fx-text-fill:$TXT;"
            nameLabel.maxWidth = Double.MAX_VALUE
            HBox.setHgrow(nameLabel, Priority.ALWAYS)
            sizeLabel.style = monoSmallStyle() + "-fx-text-fill:#555;"
            sizeLabel.minWidth = 90.0; sizeLabel.prefWidth = 90.0
            sizeLabel.alignment = Pos.CENTER_RIGHT
            dateLabel.style = monoSmallStyle() + "-fx-text-fill:#777;"
            dateLabel.minWidth = 140.0; dateLabel.prefWidth = 140.0
            dateLabel.alignment = Pos.CENTER_RIGHT
            row.alignment = Pos.CENTER_LEFT
            row.children.addAll(nameLabel, sizeLabel, dateLabel)
            row.padding = Insets(1.0, 4.0, 1.0, 4.0)
        }

        override fun updateItem(item: CompareLineItem?, empty: Boolean) {
            super.updateItem(item, empty)
            if (empty || item == null) {
                text = null; graphic = null
                style = "-fx-background-color:transparent;"
                return
            }
            text = null
            val indent = " ".repeat(item.indentLevel * 2)
            val disclosure = if (item.isDirectory) (if (item.isExpanded) "▼ " else "▶ ") else "  "
            val icon = if (item.isDirectory) "📁 " else "■ "
            val marker = markerPrefix(item.status)
            val fg = when {
                item.status == DiffStatus.MISSING -> TXT_MISS
                item.status == DiffStatus.MODIFIED -> TXT_MOD
                item.isDirectory -> TXT_DIR
                else -> TXT
            }
            nameLabel.text = "$indent$marker$disclosure$icon${item.text}"
            nameLabel.style = monoStyle() + "-fx-text-fill:$fg;"
            nameLabel.padding = Insets(0.0, 0.0, 0.0, (item.indentLevel * INDENT_PX).toDouble())
            sizeLabel.text = if (item.isDirectory) "" else DirectoryComparator.formatSize(item.size)
            dateLabel.text = DirectoryComparator.formatDate(item.lastModifiedMs)
            style = "-fx-background-color:${pickDirBg(item.status, index)};-fx-padding:0;"
            graphic = row
        }

        private fun pickDirBg(st: DiffStatus, index: Int): String = when (st) {
            DiffStatus.ADDED -> DIR_ADDED
            DiffStatus.MISSING -> DIR_MISSING
            DiffStatus.MODIFIED -> DIR_MODIFIED
            else -> if (index % 2 == 0) DIR_EVEN else DIR_ODD
        }

        private fun markerPrefix(st: DiffStatus): String = when (st) {
            DiffStatus.IDENTICAL -> ""
            DiffStatus.MODIFIED -> "≠ "
            DiffStatus.ADDED -> "+ "
            DiffStatus.MISSING -> "- "
            DiffStatus.HEADER -> "# "
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
            style = monoStyle() + "-fx-background-color:$bg;-fx-text-fill:$fg;"
        }
    }
}