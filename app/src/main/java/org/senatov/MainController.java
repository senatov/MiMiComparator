/*
 * MainController.java — FXML controller for MiMiComparator
 * Handles file/dir compare, center-strip actions, sync scroll.
 * Demo: programmatic buttons, pale-yellow panels, Apache Commons, Lombok logging.
 * Iakov Senatov, 2026
 */
package org.senatov;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.collections4.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class MainController {

    // pale yellow for list panels — like classic Norton/TC style
    private static final String PALE_YELLOW_BG =
            "-fx-control-inner-background: #FFFDE7; -fx-background-color: #FFFDE7;";

    // --- left panel ---
    @FXML private VBox leftPanel;
    @FXML private TextField leftPathField;
    @FXML private ListView<String> leftListView;

    // --- right panel ---
    @FXML private VBox rightPanel;
    @FXML private TextField rightPathField;
    @FXML private ListView<String> rightListView;

    // --- center strip ---
    @FXML private VBox centerStrip;
    @FXML private Button copyRightBtn;
    @FXML private Button copyLeftBtn;
    @FXML private Button diffBtn;
    @FXML private Button equalBtn;
    @FXML private Button deleteBtn;

    // --- toolbar ---
    @FXML private ToolBar mainToolBar;
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
    private List<String> leftLines  = Collections.emptyList();
    private List<String> rightLines = Collections.emptyList();



    @FXML
    private void initialize() {
        log.info("controller init — setting up UI");
        applyPaleYellowPanels();
        addProgrammaticButtons();
        setupSyncScroll();
        updateCenterStripState();
        log.debug("init done, Apache Commons ver: {}", StringUtils.class.getPackage().getImplementationVersion());
    }



    // ═══ programmatic UI demo ═══

    /** paint both list panels pale yellow — classic file manager look */
    private void applyPaleYellowPanels() {
        leftListView.setStyle(PALE_YELLOW_BG
                + "-fx-font-family:'JetBrains Mono','Menlo','Courier New',monospace;"
                + "-fx-font-size:13;");
        rightListView.setStyle(PALE_YELLOW_BG
                + "-fx-font-family:'JetBrains Mono','Menlo','Courier New',monospace;"
                + "-fx-font-size:13;");
        leftPanel.setStyle("-fx-background-color: #FFFDE7;");
        rightPanel.setStyle("-fx-background-color: #FFFDE7;");
        log.info("panels painted pale yellow");
    }



    /** add buttons purely from Java code — no FXML needed */
    private void addProgrammaticButtons() {
        // -- button 1: "Swap" in center strip
        var swapBtn = new Button("⇄");
        swapBtn.setPrefWidth(36);
        swapBtn.setPrefHeight(28);
        swapBtn.setStyle("-fx-font-size:16; -fx-font-weight:bold;");
        swapBtn.setTooltip(new Tooltip("Swap left ↔ right"));
        swapBtn.setOnAction(e -> onSwapPanels());
        centerStrip.getChildren().add(3, swapBtn);  // insert after ← button
        log.debug("added Swap button to center strip");

        // -- button 2: "Home" in toolbar — loads user home dir
        var homeBtn = new Button("🏠 Home");
        homeBtn.setOnAction(e -> onLoadHome());
        // find the right place in toolbar — after the "Dirs" toggle
        mainToolBar.getItems().add(7, new Separator());
        mainToolBar.getItems().add(8, homeBtn);
        log.debug("added Home button to toolbar");

        // -- button 3: quick-info in statusbar area — demo HBox from code
        var infoBox = new HBox(6);
        infoBox.setAlignment(Pos.CENTER_LEFT);
        infoBox.setPadding(new Insets(2, 6, 2, 6));
        var javaVer = new Label("Java " + Runtime.version().feature());
        javaVer.setStyle("-fx-text-fill:#888; -fx-font-size:11;");
        var osLabel = new Label(System.getProperty("os.name"));
        osLabel.setStyle("-fx-text-fill:#888; -fx-font-size:11;");
        infoBox.getChildren().addAll(javaVer, new Separator(), osLabel);
        // append to the bottom status bar (parent of statusLeft)
        if (statusLeft.getParent() instanceof HBox statusBar) {
            statusBar.getChildren().add(infoBox);
            log.debug("added Java/OS info to status bar");
        }
    }



    /** swap panels — demo using Apache Commons CollectionUtils */
    private void onSwapPanels() {
        log.info("swapping panels");
        // swap paths
        var tmp = leftPath; leftPath = rightPath; rightPath = tmp;
        leftPathField.setText(leftPath != null ? leftPath.toString() : "");
        rightPathField.setText(rightPath != null ? rightPath.toString() : "");
        // swap list contents using CollectionUtils
        var leftItems = new ArrayList<>(leftListView.getItems());
        var rightItems = new ArrayList<>(rightListView.getItems());
        leftListView.setItems(FXCollections.observableArrayList(
                CollectionUtils.emptyIfNull(rightItems)));
        rightListView.setItems(FXCollections.observableArrayList(
                CollectionUtils.emptyIfNull(leftItems)));
        // swap raw lines
        var tmpLines = leftLines; leftLines = rightLines; rightLines = tmpLines;
        var tmpStatus = statusLeft.getText();
        statusLeft.setText(statusRight.getText());
        statusRight.setText(tmpStatus);
        statusCenter.setText("⇄ swapped");
        log.debug("swap done — L={} R={}", leftPath, rightPath);
    }



    /** load user's home directory into left panel — demo using StringUtils */
    private void onLoadHome() {
        String home = System.getProperty("user.home");
        if (StringUtils.isNotBlank(home)) {
            log.info("loading home dir: {}", home);
            leftPath = Path.of(home);
            leftPathField.setText(StringUtils.abbreviate(home, 60));
            loadContent(leftPath, leftListView, true);
        }
    }



    // ═══ sync scroll ═══

    private void setupSyncScroll() {
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
            if (node instanceof ScrollBar sb
                    && sb.getOrientation() == javafx.geometry.Orientation.VERTICAL) {
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



    // ═══ menu / toolbar actions ═══

    @FXML
    private void onOpenLeft() {
        Path p = chooseFileOrDir("Open Left");
        if (p != null) {
            leftPath = p;
            leftPathField.setText(p.toString());
            loadContent(p, leftListView, true);
            log.info("opened left: {}", p);
        }
    }



    @FXML
    private void onOpenRight() {
        Path p = chooseFileOrDir("Open Right");
        if (p != null) {
            rightPath = p;
            rightPathField.setText(p.toString());
            loadContent(p, rightListView, false);
            log.info("opened right: {}", p);
        }
    }



    @FXML
    private void onCompare() {
        if (leftPath == null || rightPath == null) {
            showAlert("Load both sides first."); return;
        }
        log.info("compare: dir={} L={} R={}", dirMode, leftPath, rightPath);
        if (dirMode) compareDirs(); else compareFiles();
    }



    @FXML private void onRefresh() {
        log.debug("refresh");
        if (leftPath != null)  loadContent(leftPath,  leftListView,  true);
        if (rightPath != null) loadContent(rightPath, rightListView, false);
    }



    @FXML private void onQuit() {
        log.info("quit requested");
        Platform.exit();
    }



    @FXML private void onToggleIdentical() { onCompare(); }



    @FXML
    private void onToggleDirMode() {
        dirMode = !dirMode;
        dirModeToggle.setSelected(dirMode);
        showDirsCheck.setSelected(dirMode);
        statusCenter.setText(dirMode ? "DIR mode" : "FILE mode");
        log.debug("dir mode toggled: {}", dirMode);
    }



    @FXML private void onExpandAll()   { /* tree mode placeholder */ }
    @FXML private void onCollapseAll() { /* tree mode placeholder */ }



    // ═══ center strip actions ═══

    @FXML private void onCopyToRight()    { statusCenter.setText("→ copy to right (stub)"); }
    @FXML private void onCopyToLeft()     { statusCenter.setText("← copy to left (stub)"); }
    @FXML private void onShowDiff()       { statusCenter.setText("showing diffs only"); }
    @FXML private void onShowEqual()      { statusCenter.setText("showing identical only"); }
    @FXML private void onDeleteSelected() { statusCenter.setText("🗑 delete (stub)"); }
    @FXML private void onCopyPathLeft()   { if (leftPath != null) copyToClipboard(leftPath.toString()); }
    @FXML private void onCopyPathRight()  { if (rightPath != null) copyToClipboard(rightPath.toString()); }
    @FXML private void onSyncScroll()     { /* toggle handled by ToggleButton */ }



    @FXML
    private void onAbout() {
        var dlg = new Alert(Alert.AlertType.INFORMATION);
        dlg.setTitle("About");
        dlg.setHeaderText("MiMiComparator");
        dlg.setContentText("Dual-pane file & directory comparator.\n"
                + "Libs: Log4j2 + Lombok + Apache Commons\n"
                + "Theme: AtlantaFX Cupertino\n"
                + "© 2026 Iakov Senatov");
        dlg.showAndWait();
    }



    // ═══ file / dir loading ═══

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
                log.debug("loaded dir {} — {} entries", path, entries.size());
            } else {
                dirMode = false;
                dirModeToggle.setSelected(false);
                List<String> lines = Files.readAllLines(path);
                if (isLeft) leftLines = lines; else rightLines = lines;
                var numbered = new ArrayList<String>();
                for (int i = 0; i < lines.size(); i++) {
                    numbered.add(String.format("%4d │ %s", i + 1, lines.get(i)));
                }
                listView.setItems(FXCollections.observableArrayList(numbered));
                updateStatus(isLeft, lines.size() + " lines");
                log.debug("loaded file {} — {} lines", path, lines.size());
            }
        } catch (IOException ex) {
            log.error("cant read {}: {}", path, ex.getMessage());
            showAlert("Can't read: " + path + "\n" + ex.getMessage());
        }
        updateCenterStripState();
    }



    // ═══ compare logic ═══

    private void compareFiles() {
        int maxLen = Math.max(leftLines.size(), rightLines.size());
        var leftResult  = new ArrayList<String>();
        var rightResult = new ArrayList<String>();
        int diffs = 0;
        for (int i = 0; i < maxLen; i++) {
            String l = i < leftLines.size()  ? leftLines.get(i)  : "";
            String r = i < rightLines.size() ? rightLines.get(i) : "";
            boolean same = StringUtils.equals(l, r);  // apache commons
            if (!same) diffs++;
            String marker = same ? "  " : "≠ ";
            if (same && !showIdenticalCheck.isSelected()) continue;
            leftResult.add(String.format("%s%4d │ %s", marker, i + 1, l));
            rightResult.add(String.format("%s%4d │ %s", marker, i + 1, r));
        }
        leftListView.setItems(FXCollections.observableArrayList(leftResult));
        rightListView.setItems(FXCollections.observableArrayList(rightResult));
        diffCountLabel.setText("diffs: " + diffs);
        statusCenter.setText(diffs == 0 ? "✅ identical" : "≠ " + diffs + " differences");
        log.info("compare done: {} diffs in {} lines", diffs, maxLen);
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
            // use Apache Commons CollectionUtils for set operations
            var onlyLeft  = CollectionUtils.subtract(leftNames, rightNames);
            var onlyRight = CollectionUtils.subtract(rightNames, leftNames);
            var allNames  = new TreeSet<>(leftNames);
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
            log.info("dir compare: {} diffs, onlyL={} onlyR={}", diffs, onlyLeft.size(), onlyRight.size());
        } catch (IOException ex) {
            log.error("dir compare failed: {}", ex.getMessage());
            showAlert("Dir compare failed: " + ex.getMessage());
        }
    }



    // ═══ utils ═══

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
