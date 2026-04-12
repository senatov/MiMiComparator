/*
 * DiffCellFactory.java — ListCell factory for CompareLineItem.
 * Dir mode: system bg, zebra striping, indent, disclosure triangles,
 *           columns Name / Size / Modified (TC-style).
 * File mode: IntelliJ-style diff coloring.
 * Iakov Senatov, 2026
 */
package org.senatov.ui.cell;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.Callback;
import org.senatov.compare.DirectoryComparator;
import org.senatov.model.CompareLineItem;
import org.senatov.model.CompareLineItem.DiffStatus;
import org.senatov.App;


public final class DiffCellFactory
        implements Callback<ListView<CompareLineItem>, ListCell<CompareLineItem>> {

    private static String monoStyle() {
        return "-fx-font-family:'" + App.sfProDisplayFamily() + "','Helvetica Neue',Arial,sans-serif;-fx-font-size:14;-fx-font-weight:300;";
    }


    private static String monoSmallStyle() {
        return "-fx-font-family:'" + App.sfProDisplayFamily() + "','Helvetica Neue',Arial,sans-serif;-fx-font-size:13;-fx-font-weight:300;";
    }
    private static final int INDENT_PX = 18;

    private final boolean dirMode;

    // dir mode zebra
    private static final String DIR_EVEN = "#FFFFFF";
    private static final String DIR_ODD = "#F5F5F5";
    private static final String DIR_ADDED = "#D4EDDA";
    private static final String DIR_MISSING = "#E8E8E8";
    private static final String DIR_MODIFIED = "#FFF3CD";

    // file mode IntelliJ
    private static final String FILE_IDENTICAL = "#FFFFFF";
    private static final String FILE_MODIFIED = "#B8D4FF";
    private static final String FILE_ADDED = "#C8E6C9";
    private static final String FILE_MISSING = "#E0E0E0";
    private static final String FILE_HEADER = "#E3F2FD";

    // text colors
    private static final String TXT = "#333333";
    private static final String TXT_MISS = "#999999";
    private static final String TXT_MOD = "#0D47A1";
    private static final String TXT_DIR = "#1A237E";


    public DiffCellFactory(boolean dirMode) {
        this.dirMode = dirMode;
    }


    @Override
    public ListCell<CompareLineItem> call(ListView<CompareLineItem> listView) {
        return dirMode ? new DirCell() : new FileCell();
    }


    // ═══ Dir mode cell: columns Name | Size | Modified ═══
    private static final class DirCell extends ListCell<CompareLineItem> {

        private final HBox row = new HBox(4);
        private final Label nameLabel = new Label();
        private final Label sizeLabel = new Label();
        private final Label dateLabel = new Label();


        DirCell() {
            nameLabel.setStyle(monoStyle() + "-fx-text-fill:" + TXT + ";");
            nameLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(nameLabel, Priority.ALWAYS);

            sizeLabel.setStyle(monoSmallStyle() + "-fx-text-fill:#555;");
            sizeLabel.setMinWidth(90);
            sizeLabel.setPrefWidth(90);
            sizeLabel.setAlignment(Pos.CENTER_RIGHT);

            dateLabel.setStyle(monoSmallStyle() + "-fx-text-fill:#777;");
            dateLabel.setMinWidth(140);
            dateLabel.setPrefWidth(140);
            dateLabel.setAlignment(Pos.CENTER_RIGHT);

            row.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().addAll(nameLabel, sizeLabel, dateLabel);
            row.setPadding(new Insets(1, 4, 1, 4));
        }


        @Override
        protected void updateItem(CompareLineItem item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setStyle("-fx-background-color:transparent;");
                return;
            }

            setText(null);

            // indent + disclosure + icon + name
            String indent = " ".repeat(item.getIndentLevel() * 2);
            String disclosure = item.isDirectory() ? (item.isExpanded() ? "▼ " : "▶ ") : "  ";
            String icon = item.isDirectory() ? "📁 " : "■ ";
            String marker = markerPrefix(item.getStatus());

            String fg = TXT;
            if (item.getStatus() == DiffStatus.MISSING) {
                fg = TXT_MISS;
            } else if (item.getStatus() == DiffStatus.MODIFIED) {
                fg = TXT_MOD;
            } else if (item.isDirectory()) {
                fg = TXT_DIR;
            }

            nameLabel.setText(indent + marker + disclosure + icon + item.getText());
            nameLabel.setStyle(monoStyle() + "-fx-text-fill:" + fg + ";");
            nameLabel.setPadding(new Insets(0, 0, 0, item.getIndentLevel() * INDENT_PX));

            // size + date from DirTreeNode data stored in text for files
            // For tree nodes: size/date come from the node itself via getter
            sizeLabel.setText(item.isDirectory() ? "" : DirectoryComparator.formatSize(item.getSize()));
            dateLabel.setText(DirectoryComparator.formatDate(item.getLastModifiedMs()));

            String bg = pickDirBg(item.getStatus(), getIndex());
            setStyle("-fx-background-color:" + bg + ";-fx-padding:0;");
            setGraphic(row);
        }


        private static String pickDirBg(DiffStatus st, int index) {
            return switch (st) {
                case ADDED -> DIR_ADDED;
                case MISSING -> DIR_MISSING;
                case MODIFIED -> DIR_MODIFIED;
                default -> (index % 2 == 0) ? DIR_EVEN : DIR_ODD;
            };
        }


        private static String markerPrefix(DiffStatus st) {
            return switch (st) {
                case IDENTICAL -> "";
                case MODIFIED -> "≠ ";
                case ADDED -> "+ ";
                case MISSING -> "- ";
                case HEADER -> "# ";
            };
        }
    }


    // ═══ File mode cell: IntelliJ-style line diff ═══
    private static final class FileCell extends ListCell<CompareLineItem> {

        @Override
        protected void updateItem(CompareLineItem item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setStyle(monoStyle() + "-fx-background-color:transparent;");
                return;
            }

            setText(item.formatted());

            DiffStatus st = item.getStatus();
            String bg;
            String fg = TXT;

            switch (st) {
                case MODIFIED -> { bg = FILE_MODIFIED; fg = TXT_MOD; }
                case ADDED -> bg = FILE_ADDED;
                case MISSING -> { bg = FILE_MISSING; fg = TXT_MISS; }
                case HEADER -> bg = FILE_HEADER;
                default -> bg = FILE_IDENTICAL;
            }

            setStyle(monoStyle() + "-fx-background-color:" + bg + ";-fx-text-fill:" + fg + ";");
        }
    }
}