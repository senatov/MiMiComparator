/*
 * MainController.java — FXML controller for MiMiComparator
 * Handles file/dir compare, center-strip actions, sync scroll.
 * Iakov Senatov, 2026
 */
package org.senatov;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class MainController {

    // --- left panel ---
    @FXML private TextField leftPathField;
    @FXML private ListView<String> leftListView;

    // --- right panel ---
    @FXML private TextField rightPathField;
    @FXML private ListView<String> rightListView;

    // --- center strip ---
    @FXML private Button copyRightBtn;
    @FXML private Button copyLeftBtn;
    @FXML private Button diffBtn;
    @FXML private Button equalBtn;
    @FXML private Button deleteBtn;

    // --- toolbar ---
    @FXML private ToggleButton syncScrollToggle;
    @FXML private ToggleButton dirModeToggle;
    @FXML private CheckMenuItem showIdenticalCheck;
    @FXML private CheckMenuItem showDirsCheck;
    @FXML private Label diffCountLabel;
    @FXML private Label statusLeft;
    @FXML private Label statusCenter;
    @FXML private Label statusRight;

    private Path leftPath;
    private Path rightPath;
    private boolean dirMode = false;

    // raw lines for text compare
    private List<String> leftLines  = Collections.emptyList();
    private List<String> rightLines = Collections.emptyList();


    // ─── init ───

    @FXML
    private void initialize() {
        setupSyncScroll();
        updateCenterStripState();
    }


    private void setupSyncScroll() {
        // sync scroll: when left scrolls, right follows & vice versa
        // ListView doesn't expose scrollbar directly, so we use a lookup hack
        Platform.runLater(() -> {
            bindScrollBars(leftListView, rightListView);
            bindScrollBars(rightListView, leftListView);
        });
    }


    private void bindScrollBars(ListView<?> source, ListView<?> target) {
        ScrollBar srcBar = findScrollBar(source);
        ScrollBar tgtBar = findScrollBar(target);
        if (srcBar != null && tgtBar != null) {
            srcBar.valueProperty().addListener((obs, old, val) -> {
                if (syncScrollToggle.isSelected()) {
                    tgtBar.setValue(val.doubleValue());
                }
            });
        }
    }


    private ScrollBar findScrollBar(ListView<?> lv) {
        for (var node : lv.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar sb && sb.getOrientation() == javafx.geometry.Orientation.VERTICAL) {
                return sb;
            }
        }
        return null;
    }


    private void updateCenterStripState() {
        boolean hasLeft  = leftPath != null;
        boolean hasRight = rightPath != null;
        boolean hasBoth  = hasLeft && hasRight;
        copyRightBtn.setDisable(!hasBoth);
        copyLeftBtn.setDisable(!hasBoth);
        diffBtn.setDisable(!hasBoth);
        equalBtn.setDisable(!hasBoth);
        deleteBtn.setDisable(!hasLeft && !hasRight);
    }


    // ─── menu / toolbar actions ───

    @FXML
    private void onOpenLeft() {
        Path p = chooseFileOrDir("Open Left");
        if (p != null) {
            leftPath = p;
            leftPathField.setText(p.toString());
            loadContent(p, leftListView, true);
        }
    }


    @FXML
    private void onOpenRight() {
        Path p = chooseFileOrDir("Open Right");
        if (p != null) {
            rightPath = p;
            rightPathField.setText(p.toString());
            loadContent(p, rightListView, false);
        }
    }


    @FXML
    private void onCompare() {
        if (leftPath == null || rightPath == null) {
            showAlert("Load both sides first.");
            return;
        }
        if (dirMode) {
            compareDirs();
        } else {
            compareFiles();
        }
    }


    @FXML private void onRefresh() {
        if (leftPath != null)  loadContent(leftPath,  leftListView,  true);
        if (rightPath != null) loadContent(rightPath, rightListView, false);
    }


    @FXML private void onQuit() { Platform.exit(); }

    @FXML private void onToggleIdentical() { onCompare(); }

    @FXML
    private void onToggleDirMode() {
        dirMode = !dirMode;
        dirModeToggle.setSelected(dirMode);
        showDirsCheck.setSelected(dirMode);
        statusCenter.setText(dirMode ? "DIR mode" : "FILE mode");
    }

    @FXML private void onExpandAll()   { /* placeholder for tree mode */ }
    @FXML private void onCollapseAll() { /* placeholder for tree mode */ }


    // ─── center strip actions ───

    @FXML
    private void onCopyToRight() {
        statusCenter.setText("→ copy to right (stub)");
    }


    @FXML
    private void onCopyToLeft() {
        statusCenter.setText("← copy to left (stub)");
    }


    @FXML
    private void onShowDiff() {
        statusCenter.setText("showing diffs only");
        // TODO: filter lists to show only differing lines/files
    }


    @FXML
    private void onShowEqual() {
        statusCenter.setText("showing identical only");
        // TODO: filter lists to show only matching lines/files
    }


    @FXML
    private void onDeleteSelected() {
        statusCenter.setText("🗑 delete (stub)");
    }


    @FXML
    private void onCopyPathLeft() {
        if (leftPath != null) copyToClipboard(leftPath.toString());
    }


    @FXML
    private void onCopyPathRight() {
        if (rightPath != null) copyToClipboard(rightPath.toString());
    }


    @FXML
    private void onAbout() {
        var dlg = new Alert(Alert.AlertType.INFORMATION);
        dlg.setTitle("About");
        dlg.setHeaderText("MiMiComparator");
        dlg.setContentText("Dual-pane file & directory comparator.\n© 2026 Iakov Senatov");
        dlg.showAndWait();
    }


    @FXML
    private void onSyncScroll() {
        // toggle handled by ToggleButton binding
    }


    // ─── file / dir loading ───

    private Path chooseFileOrDir(String title) {
        if (dirMode) {
            var dc = new DirectoryChooser();
            dc.setTitle(title);
            File f = dc.showDialog(getStage());
            return f != null ? f.toPath() : null;
        } else {
            var fc = new FileChooser();
            fc.setTitle(title);
            File f = fc.showOpenDialog(getStage());
            return f != null ? f.toPath() : null;
        }
    }


    private void loadContent(Path path, ListView<String> listView, boolean isLeft) {
        try {
            if (Files.isDirectory(path)) {
                // dir listing
                dirMode = true;
                dirModeToggle.setSelected(true);
                List<String> entries;
                try (var stream = Files.list(path)) {
                    entries = stream
                        .map(p -> (Files.isDirectory(p) ? "📁 " : "   ") + p.getFileName())
                        .sorted()
                        .collect(Collectors.toList());
                }
                listView.setItems(FXCollections.observableArrayList(entries));
                updateStatus(isLeft, entries.size() + " entries");
            } else {
                // text file
                dirMode = false;
                dirModeToggle.setSelected(false);
                List<String> lines = Files.readAllLines(path);
                if (isLeft) leftLines = lines; else rightLines = lines;
                // prepend line numbers
                var numbered = new ArrayList<String>();
                for (int i = 0; i < lines.size(); i++) {
                    numbered.add(String.format("%4d │ %s", i + 1, lines.get(i)));
                }
                listView.setItems(FXCollections.observableArrayList(numbered));
                updateStatus(isLeft, lines.size() + " lines");
            }
        } catch (IOException ex) {
            showAlert("Can't read: " + path + "\n" + ex.getMessage());
        }
        updateCenterStripState();
    }


    // ─── compare logic ───

    private void compareFiles() {
        int maxLen = Math.max(leftLines.size(), rightLines.size());
        var leftResult  = new ArrayList<String>();
        var rightResult = new ArrayList<String>();
        int diffs = 0;

        for (int i = 0; i < maxLen; i++) {
            String l = i < leftLines.size()  ? leftLines.get(i)  : "";
            String r = i < rightLines.size() ? rightLines.get(i) : "";
            boolean same = l.equals(r);
            if (!same) diffs++;
            String marker = same ? "  " : "≠ ";
            if (same && !showIdenticalCheck.isSelected()) {
                continue; // skip identical if toggled off
            }
            leftResult.add(String.format("%s%4d │ %s", marker, i + 1, l));
            rightResult.add(String.format("%s%4d │ %s", marker, i + 1, r));
        }

        leftListView.setItems(FXCollections.observableArrayList(leftResult));
        rightListView.setItems(FXCollections.observableArrayList(rightResult));
        diffCountLabel.setText("diffs: " + diffs);
        statusCenter.setText(diffs == 0 ? "✅ identical" : "≠ " + diffs + " differences");
    }


    private void compareDirs() {
        try {
            Set<String> leftNames;
            Set<String> rightNames;
            try (var s = Files.list(leftPath)) {
                leftNames = s.map(p -> p.getFileName().toString()).collect(Collectors.toSet());
            }
            try (var s = Files.list(rightPath)) {
                rightNames = s.map(p -> p.getFileName().toString()).collect(Collectors.toSet());
            }
            var allNames = new TreeSet<>(leftNames);
            allNames.addAll(rightNames);

            var leftResult  = new ArrayList<String>();
            var rightResult = new ArrayList<String>();
            int diffs = 0;

            for (String name : allNames) {
                boolean inLeft  = leftNames.contains(name);
                boolean inRight = rightNames.contains(name);
                if (inLeft && inRight) {
                    leftResult.add("   " + name);
                    rightResult.add("   " + name);
                } else {
                    diffs++;
                    leftResult.add(inLeft   ? "→  " + name : "   ‹missing›");
                    rightResult.add(inRight ? "←  " + name : "   ‹missing›");
                }
            }
            leftListView.setItems(FXCollections.observableArrayList(leftResult));
            rightListView.setItems(FXCollections.observableArrayList(rightResult));
            diffCountLabel.setText("diffs: " + diffs);
            statusCenter.setText(diffs == 0 ? "✅ dirs identical" : "≠ " + diffs + " differ");
        } catch (IOException ex) {
            showAlert("Dir compare failed: " + ex.getMessage());
        }
    }


    // ─── utils ───

    private void updateStatus(boolean isLeft, String text) {
        if (isLeft) statusLeft.setText(text); else statusRight.setText(text);
    }


    private Stage getStage() {
        return (Stage) leftPathField.getScene().getWindow();
    }


    private void showAlert(String msg) {
        var a = new Alert(Alert.AlertType.WARNING);
        a.setContentText(msg);
        a.showAndWait();
    }


    private void copyToClipboard(String text) {
        var cb = javafx.scene.input.Clipboard.getSystemClipboard();
        var content = new javafx.scene.input.ClipboardContent();
        content.putString(text);
        cb.setContent(content);
        statusCenter.setText("📋 copied");
    }
}
