/*
 * DiffCellFactory.java — ListCell factory for CompareLineItem.
 * Colors rows by DiffStatus: green=identical, red=modified,
 * yellow=added, grey=missing, blue=header.
 * Iakov Senatov, 2026
 */
package org.senatov.ui.cell;

import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;
import org.senatov.model.CompareLineItem;


public final class DiffCellFactory
        implements Callback<ListView<CompareLineItem>, ListCell<CompareLineItem>> {

    private static final String MONO_FONT =
            "-fx-font-family:'JetBrains Mono','Menlo','Courier New',monospace;"
                    + "-fx-font-size:13;";

    private static final String COLOR_IDENTICAL = "#FFFDE7";
    private static final String COLOR_MODIFIED = "#FFCCCC";
    private static final String COLOR_ADDED = "#CCFFCC";
    private static final String COLOR_MISSING = "#E0E0E0";
    private static final String COLOR_HEADER = "#CCE5FF";


    @Override
    public ListCell<CompareLineItem> call(ListView<CompareLineItem> listView) {
        return new DiffCell();
    }


    private static final class DiffCell extends ListCell<CompareLineItem> {

        @Override
        protected void updateItem(CompareLineItem item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setStyle(MONO_FONT + "-fx-background-color:" + COLOR_IDENTICAL + ";");
                return;
            }

            setText(item.formatted());
            String bgColor = pickColor(item.status());
            setStyle(MONO_FONT + "-fx-background-color:" + bgColor + ";");
        }


        private static String pickColor(CompareLineItem.DiffStatus status) {
            return switch (status) {
                case IDENTICAL -> COLOR_IDENTICAL;
                case MODIFIED -> COLOR_MODIFIED;
                case ADDED -> COLOR_ADDED;
                case MISSING -> COLOR_MISSING;
                case HEADER -> COLOR_HEADER;
            };
        }
    }
}
