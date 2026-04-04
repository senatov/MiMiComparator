/*
 * MainController.java — FXML controller for MiMiComparator
 * Tree expand/collapse, filter, column headers, sync scroll,
 * CLI autocompare, DiffCellFactory coloring.
 * Iakov Senatov, 2026
 */
package org.senatov;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.senatov.cli.CliArgs;
import org.senatov.compare.CompareResult;
import org.senatov.compare.DirCompareResult;
import org.senatov.compare.DirectoryComparator;
import org.senatov.compare.FileContentComparator;
import org.senatov.helpers.log.LogHelper;
import org.senatov.model.CompareLineItem;
import org.senatov.model.tree.DirTreeModel;
import org.senatov.ui.cell.DiffCellFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Slf4j
public class MainController {

    private static final String STATUS_DIR_MODE = "DIR mode";
    private static final String STATUS_FILE_MODE = "FILE mode";
    private static final String STATUS_SWAPPED = "⇄ swapped";
    private static final String STATUS_CLIPBOARD_COPIED = "📋 copied";

    // --- left panel ---
    @FXML private VBox leftPanel;
    @FXML private TextField leftPathField;
    @FXML private ListView<CompareLineItem> leftListView;
    @FXML private HBox leftColumnHeader;

    // --- right panel ---
    @FXML private VBox rightPanel;
    @FXML private TextField rightPathField;
    @FXML private ListView<CompareLineItem> rightListView;
    @FXML private HBox rightColumnHeader;

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
    @FXML private TextField filterField;
    @FXML private Label statusLeft;
    @FXML private Label statusCenter;
    @FXML private Label statusRight;

    private Path leftPath;
    private Path rightPath;
    private boolean dirMode = false;
    private CliArgs pendingCliArgs;
    private DirTreeModel leftTreeModel;
    private DirTreeModel rightTreeModel;
    private DirCompareResult lastDirResult;


    @FXML
    private void initialize() {
        log.debug("[{}]", LogHelper.method());

        installDiffCellFactories();
        setupClickToExpand();
        addProgrammaticButtons();
        setupSyncScroll();
        updateCenterStripState();
        updateColumnHeaderVisibility();

        Platform.runLater(this::executeCliAutoCompare);
    }


    public void applyCliArgs(CliArgs args) {
        log.info("CLI args: L={} R={} auto={}",
                args.getLeftPath(), args.getRightPath(), args.isAutoCompare());
        this.pendingCliArgs = args;
    }


    private void executeCliAutoCompare() {
        if (pendingCliArgs == null || !pendingCliArgs.isAutoCompare()) {
            return;
        }

        pendingCliArgs.left().ifPresent(p -> {
            leftPath = p;
            leftPathField.setText(p.toString());
        });

        pendingCliArgs.right().ifPresent(p -> {
            rightPath = p;
            rightPathField.setText(p.toString());
        });

        if (pendingCliArgs.isDirMode()) {
            setDirMode(true);
        }

        updateCenterStripState();
        onCompare();
    }


    private void installDiffCellFactories() {
        log.debug("[{}] dirMode={}", LogHelper.method(), dirMode);
        DiffCellFactory factory = new DiffCellFactory(dirMode);
        leftListView.setCellFactory(factory);
        rightListView.setCellFactory(factory);
    }


    private void setupClickToExpand() {
        leftListView.setOnMouseClicked(e -> handleTreeClick(e, leftListView, true));
        rightListView.setOnMouseClicked(e -> handleTreeClick(e, rightListView, false));
    }


    private void handleTreeClick(MouseEvent event, ListView<CompareLineItem> listView, boolean isLeft) {
        if (!dirMode || event.getClickCount() < 2) {
            return;
        }

        CompareLineItem item = listView.getSelectionModel().getSelectedItem();
        if (item == null || !item.isDirectory()) {
            return;
        }

        String relPath = item.getRelativePath();
        log.info("toggle expand: {} side={}", relPath, isLeft ? "L" : "R");

        if (leftTreeModel != null) {
            leftTreeModel.toggleExpand(relPath);
        }
        if (rightTreeModel != null) {
            rightTreeModel.toggleExpand(relPath);
        }

        refreshTreeViews();
    }


    private void refreshTreeViews() {
        if (leftTreeModel == null || rightTreeModel == null) {
            return;
        }

        List<CompareLineItem> leftItems = leftTreeModel.toFlatList();
        List<CompareLineItem> rightItems = rightTreeModel.toFlatList();

        String filterText = filterField.getText();
        if (StringUtils.isNotBlank(filterText)) {
            Pattern pattern = buildFilterPattern(filterText);
            leftItems = applyFilter(leftItems, pattern);
            rightItems = applyFilter(rightItems, pattern);
        }

        leftListView.setItems(FXCollections.observableArrayList(leftItems));
        rightListView.setItems(FXCollections.observableArrayList(rightItems));
    }


    private void updateColumnHeaderVisibility() {
        boolean show = dirMode;
        leftColumnHeader.setVisible(show);
        leftColumnHeader.setManaged(show);
        rightColumnHeader.setVisible(show);
        rightColumnHeader.setManaged(show);
    }


    // ═══ filter ═══

    @FXML
    private void onFilterChanged() {
        log.info("filter changed: '{}'", filterField.getText());
        if (dirMode && leftTreeModel != null) {
            refreshTreeViews();
        } else if (!dirMode) {
            onCompare();
        }
    }


    private Pattern buildFilterPattern(String filterText) {
        String[] parts = filterText.split("[,;\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("|");
            }
            String regex = trimmed
                    .replace(".", "\\.")
                    .replace("*", ".*")
                    .replace("?", ".");
            sb.append("(").append(regex).append(")");
        }
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }


    private List<CompareLineItem> applyFilter(List<CompareLineItem> items, Pattern pattern) {
        return items.stream()
                .filter(item -> item.isDirectory() || pattern.matcher(item.getText()).find())
                .collect(Collectors.toList());
    }


    // ═══ programmatic buttons ═══

    private void addProgrammaticButtons() {
        var swapBtn = new Button("⇄");
        swapBtn.setPrefWidth(36);
        swapBtn.setPrefHeight(28);
        swapBtn.setStyle("-fx-font-size:16; -fx-font-weight:bold;");
        swapBtn.setTooltip(new Tooltip("Swap left ↔ right"));
        swapBtn.setOnAction(e -> onSwapPanels());
        centerStrip.getChildren().add(3, swapBtn);

        var homeBtn = new Button("🏠 Home");
        homeBtn.setOnAction(e -> onLoadHome());
        int paneIndex = mainToolBar.getItems().size() - 2;
        mainToolBar.getItems().add(paneIndex, new Separator());
        mainToolBar.getItems().add(paneIndex + 1, homeBtn);

        var infoBox = new HBox(6);
        infoBox.setAlignment(Pos.CENTER_LEFT);
        infoBox.setPadding(new Insets(2, 6, 2, 6));
        var javaVer = new Label("Java " + Runtime.version().feature());
        javaVer.setStyle("-fx-text-fill:#888; -fx-font-size:11;");
        var osLabel = new Label(System.getProperty("os.name"));
        osLabel.setStyle("-fx-text-fill:#888; -fx-font-size:11;");
        infoBox.getChildren().addAll(javaVer, new Separator(), osLabel);

        if (statusLeft.getParent() instanceof HBox statusBar) {
            statusBar.getChildren().add(infoBox);
        }
    }


    private void onSwapPanels() {
        log.info("swapping panels");
        Path tmpPath = leftPath;
        leftPath = rightPath;
        rightPath = tmpPath;
        leftPathField.setText(leftPath != null ? leftPath.toString() : "");
        rightPathField.setText(rightPath != null ? rightPath.toString() : "");

        var leftItems = new ArrayList<>(leftListView.getItems());
        var rightItems = new ArrayList<>(rightListView.getItems());
        leftListView.setItems(FXCollections.observableArrayList(rightItems));
        rightListView.setItems(FXCollections.observableArrayList(leftItems));

        DirTreeModel tmpModel = leftTreeModel;
        leftTreeModel = rightTreeModel;
        rightTreeModel = tmpModel;

        String tmpStatus = statusLeft.getText();
        statusLeft.setText(statusRight.getText());
        statusRight.setText(tmpStatus);
        statusCenter.setText(STATUS_SWAPPED);
    }


    private void onLoadHome() {
        String home = System.getProperty("user.home");
        if (StringUtils.isNotBlank(home)) {
            leftPath = Path.of(home);
            leftPathField.setText(StringUtils.abbreviate(home, 60));
            loadDirectoryPreview(leftPath, leftListView, true);
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
        if (srcBar == null || tgtBar == null) {
            return;
        }
        srcBar.valueProperty().addListener((o, ov, nv) -> {
            if (syncScrollToggle.isSelected()) {
                tgtBar.setValue(nv.doubleValue());
            }
        });
    }


    private ScrollBar findScrollBar(ListView<?> lv) {
        for (var node : lv.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar sb && sb.getOrientation() == Orientation.VERTICAL) {
                return sb;
            }
        }
        return null;
    }


    private void updateCenterStripState() {
        boolean hasBoth = leftPath != null && rightPath != null;
        copyRightBtn.setDisable(!hasBoth);
        copyLeftBtn.setDisable(!hasBoth);
        diffBtn.setDisable(!hasBoth);
        equalBtn.setDisable(!hasBoth);
        deleteBtn.setDisable(leftPath == null && rightPath == null);
    }


    // ═══ menu / toolbar actions ═══

    @FXML
    private void onOpenLeft() {
        Path p = chooseFileOrDir("Open Left");
        if (p != null) {
            leftPath = p;
            leftPathField.setText(p.toString());
            loadDirectoryPreview(p, leftListView, true);
        }
    }


    @FXML
    private void onOpenRight() {
        Path p = chooseFileOrDir("Open Right");
        if (p != null) {
            rightPath = p;
            rightPathField.setText(p.toString());
            loadDirectoryPreview(p, rightListView, false);
        }
    }


    @FXML
    private void onCompare() {
        if (leftPath == null || rightPath == null) {
            showAlert("Load both sides first.");
            return;
        }

        log.info("compare: dir={} L={} R={}", dirMode, leftPath, rightPath);

        try {
            if (dirMode) {
                lastDirResult = DirectoryComparator.compareTree(leftPath, rightPath);
                leftTreeModel = lastDirResult.leftModel();
                rightTreeModel = lastDirResult.rightModel();
                refreshTreeViews();
                diffCountLabel.setText("diffs: " + lastDirResult.diffCount());
                statusCenter.setText(lastDirResult.statusText());
            } else {
                CompareResult result = FileContentComparator.compare(
                        leftPath, rightPath, showIdenticalCheck.isSelected());
                leftListView.setItems(FXCollections.observableArrayList(result.leftItems()));
                rightListView.setItems(FXCollections.observableArrayList(result.rightItems()));
                diffCountLabel.setText("diffs: " + result.diffCount());
                statusCenter.setText(result.statusText());
            }
        } catch (IOException ex) {
            log.error("compare failed: {}", ex.getMessage());
            showAlert("Compare failed: " + ex.getMessage());
        }
    }


    @FXML
    private void onRefresh() {
        if (leftPath != null) {
            loadDirectoryPreview(leftPath, leftListView, true);
        }
        if (rightPath != null) {
            loadDirectoryPreview(rightPath, rightListView, false);
        }
    }


    @FXML private void onQuit() { Platform.exit(); }

    @FXML private void onToggleIdentical() { onCompare(); }


    @FXML
    private void onToggleDirMode() {
        setDirMode(!dirMode);
    }


    private void setDirMode(boolean enabled) {
        dirMode = enabled;
        dirModeToggle.setSelected(enabled);
        showDirsCheck.setSelected(enabled);
        statusCenter.setText(enabled ? STATUS_DIR_MODE : STATUS_FILE_MODE);
        installDiffCellFactories();
        updateColumnHeaderVisibility();
    }


    @FXML
    private void onExpandAll() {
        log.info("expand all");
        if (leftTreeModel != null) {
            leftTreeModel.expandAll();
        }
        if (rightTreeModel != null) {
            rightTreeModel.expandAll();
        }
        refreshTreeViews();
    }


    @FXML
    private void onCollapseAll() {
        log.info("collapse all");
        if (leftTreeModel != null) {
            leftTreeModel.collapseAll();
        }
        if (rightTreeModel != null) {
            rightTreeModel.collapseAll();
        }
        refreshTreeViews();
    }


    // ═══ center strip stubs ═══

    @FXML private void onCopyToRight() { statusCenter.setText("→ copy to right (stub)"); }
    @FXML private void onCopyToLeft() { statusCenter.setText("← copy to left (stub)"); }
    @FXML private void onShowDiff() { statusCenter.setText("showing diffs only"); }
    @FXML private void onShowEqual() { statusCenter.setText("showing identical only"); }
    @FXML private void onDeleteSelected() { statusCenter.setText("🗑 delete (stub)"); }
    @FXML private void onSyncScroll() { }


    @FXML
    private void onCopyPathLeft() {
        if (leftPath != null) {
            copyToClipboard(leftPath.toString());
        }
    }


    @FXML
    private void onCopyPathRight() {
        if (rightPath != null) {
            copyToClipboard(rightPath.toString());
        }
    }


    @FXML
    private void onAbout() {
        var dlg = new Alert(Alert.AlertType.INFORMATION);
        dlg.setTitle("About");
        dlg.setHeaderText("MiMiComparator");
        dlg.setContentText("Dual-pane file & directory comparator.\n"
                + "Libs: Log4j2 + Lombok + Apache Commons + Jackson\n"
                + "Theme: AtlantaFX Cupertino\n"
                + "© 2026 Iakov Senatov");
        dlg.showAndWait();
    }


    // ═══ file / dir loading (preview before compare) ═══

    private Path chooseFileOrDir(String title) {
        if (dirMode) {
            var dc = new DirectoryChooser();
            dc.setTitle(title);
            File f = dc.showDialog(getStage());
            return f != null ? f.toPath() : null;
        }
        var fc = new FileChooser();
        fc.setTitle(title);
        File f = fc.showOpenDialog(getStage());
        return f != null ? f.toPath() : null;
    }


    private void loadDirectoryPreview(Path path, ListView<CompareLineItem> listView, boolean isLeft) {
        try {
            if (Files.isDirectory(path)) {
                setDirMode(true);
                List<CompareLineItem> entries = listDirEntries(path);
                listView.setItems(FXCollections.observableArrayList(entries));
                updateStatus(isLeft, entries.size() + " entries");
            } else {
                setDirMode(false);
                List<String> lines = Files.readAllLines(path);
                List<CompareLineItem> items = new ArrayList<>();
                for (int i = 0; i < lines.size(); i++) {
                    items.add(new CompareLineItem(i + 1, lines.get(i),
                            CompareLineItem.DiffStatus.IDENTICAL));
                }
                listView.setItems(FXCollections.observableArrayList(items));
                updateStatus(isLeft, lines.size() + " lines");
            }
        } catch (IOException ex) {
            log.error("cant read {}: {}", path, ex.getMessage());
            showAlert("Can't read: " + path + "\n" + ex.getMessage());
        }
        updateCenterStripState();
    }


    private List<CompareLineItem> listDirEntries(Path dir) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .sorted()
                    .map(p -> {
                        boolean isDir = Files.isDirectory(p);
                        return new CompareLineItem(
                                0, p.getFileName().toString(),
                                CompareLineItem.DiffStatus.IDENTICAL,
                                0, isDir, p.getFileName().toString());
                    })
                    .collect(Collectors.toList());
        }
    }


    // ═══ utils ═══

    private void updateStatus(boolean isLeft, String text) {
        if (isLeft) {
            statusLeft.setText(text);
        } else {
            statusRight.setText(text);
        }
    }


    private Stage getStage() {
        return (Stage) leftPathField.getScene().getWindow();
    }


    private void showAlert(String msg) {
        var alert = new Alert(Alert.AlertType.WARNING);
        alert.setContentText(msg);
        alert.showAndWait();
    }


    private void copyToClipboard(String text) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
        statusCenter.setText(STATUS_CLIPBOARD_COPIED);
    }
}
