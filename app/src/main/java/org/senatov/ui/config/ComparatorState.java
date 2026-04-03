package org.senatov.ui.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent UI state for MiMiComparator.
 * Stored as JSON under ~/.mimi/comparator/.
 */
public class ComparatorState {

    private WindowState window = new WindowState();
    private PanelState leftPanel = new PanelState();
    private PanelState rightPanel = new PanelState();
    private boolean dirMode = true;
    private boolean syncScroll = true;
    private boolean showDirs = true;
    private boolean showEqual = true;
    private boolean showDifferent = true;
    private boolean showOnlyLeft = true;
    private boolean showOnlyRight = true;
    private String lastStatusLeft = "";
    private String lastStatusCenter = "";
    private String lastStatusRight = "";

    public ComparatorState() {
        // Default constructor for JSON serialization
    }

    public WindowState getWindow() {
        return window;
    }

    public void setWindow(WindowState window) {
        this.window = window != null ? window : new WindowState();
    }

    public PanelState getLeftPanel() {
        return leftPanel;
    }

    public void setLeftPanel(PanelState leftPanel) {
        this.leftPanel = leftPanel != null ? leftPanel : new PanelState();
    }

    public PanelState getRightPanel() {
        return rightPanel;
    }

    public void setRightPanel(PanelState rightPanel) {
        this.rightPanel = rightPanel != null ? rightPanel : new PanelState();
    }

    public boolean isDirMode() {
        return dirMode;
    }

    public void setDirMode(boolean dirMode) {
        this.dirMode = dirMode;
    }

    public boolean isSyncScroll() {
        return syncScroll;
    }

    public void setSyncScroll(boolean syncScroll) {
        this.syncScroll = syncScroll;
    }

    public boolean isShowDirs() {
        return showDirs;
    }

    public void setShowDirs(boolean showDirs) {
        this.showDirs = showDirs;
    }

    public boolean isShowEqual() {
        return showEqual;
    }

    public void setShowEqual(boolean showEqual) {
        this.showEqual = showEqual;
    }

    public boolean isShowDifferent() {
        return showDifferent;
    }

    public void setShowDifferent(boolean showDifferent) {
        this.showDifferent = showDifferent;
    }

    public boolean isShowOnlyLeft() {
        return showOnlyLeft;
    }

    public void setShowOnlyLeft(boolean showOnlyLeft) {
        this.showOnlyLeft = showOnlyLeft;
    }

    public boolean isShowOnlyRight() {
        return showOnlyRight;
    }

    public void setShowOnlyRight(boolean showOnlyRight) {
        this.showOnlyRight = showOnlyRight;
    }

    public String getLastStatusLeft() {
        return lastStatusLeft;
    }

    public void setLastStatusLeft(String lastStatusLeft) {
        this.lastStatusLeft = safeString(lastStatusLeft);
    }

    public String getLastStatusCenter() {
        return lastStatusCenter;
    }

    public void setLastStatusCenter(String lastStatusCenter) {
        this.lastStatusCenter = safeString(lastStatusCenter);
    }

    public String getLastStatusRight() {
        return lastStatusRight;
    }

    public void setLastStatusRight(String lastStatusRight) {
        this.lastStatusRight = safeString(lastStatusRight);
    }

    public static ComparatorState defaults() {
        return new ComparatorState();
    }

    private static String safeString(String value) {
        return value != null ? value : "";
    }

    public static class WindowState {

        private double x = 120.0;
        private double y = 120.0;
        private double width = 1400.0;
        private double height = 900.0;
        private boolean maximized;

        public WindowState() {
            // Default constructor for JSON serialization
        }

        public double getX() {
            return x;
        }

        public void setX(double x) {
            this.x = x;
        }

        public double getY() {
            return y;
        }

        public void setY(double y) {
            this.y = y;
        }

        public double getWidth() {
            return width;
        }

        public void setWidth(double width) {
            this.width = width;
        }

        public double getHeight() {
            return height;
        }

        public void setHeight(double height) {
            this.height = height;
        }

        public boolean isMaximized() {
            return maximized;
        }

        public void setMaximized(boolean maximized) {
            this.maximized = maximized;
        }
    }

    public static class PanelState {

        private String path = "";
        private int selectedIndex = -1;
        private double scrollPosition;
        private List<String> visibleItems = new ArrayList<>();
        private List<String> rawLines = new ArrayList<>();

        public PanelState() {
            // Default constructor for JSON serialization
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = safeString(path);
        }

        public int getSelectedIndex() {
            return selectedIndex;
        }

        public void setSelectedIndex(int selectedIndex) {
            this.selectedIndex = selectedIndex;
        }

        public double getScrollPosition() {
            return scrollPosition;
        }

        public void setScrollPosition(double scrollPosition) {
            this.scrollPosition = scrollPosition;
        }

        public List<String> getVisibleItems() {
            return visibleItems;
        }

        public void setVisibleItems(List<String> visibleItems) {
            this.visibleItems = visibleItems != null ? new ArrayList<>(visibleItems) : new ArrayList<>();
        }

        public List<String> getRawLines() {
            return rawLines;
        }

        public void setRawLines(List<String> rawLines) {
            this.rawLines = rawLines != null ? new ArrayList<>(rawLines) : new ArrayList<>();
        }
    }
}