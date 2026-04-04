/*
 * MainController.java — FXML controller for MiMiComparator
 * Handles file/dir compare, center-strip actions, sync scroll.
 * CLI autocompare, DiffCellFactory coloring, deep compare.
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
import javafx.scene.layout.Pane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.senatov.cli.CliArgs;
import org.senatov.compare.CompareResult;
import org.senatov.compare.DirectoryComparator;
import org.senatov.compare.FileContentComparator;
import org.senatov.helpers.log.LogHelper;
import org.senatov.model.CompareLineItem;
import org.senatov.ui.cell.DiffCellFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Slf4j
public class MainController {

    private static final String PALE_YELLOW_BG =
            "-fx-control-inner-background: #FFFDE7; -fx-background-color: #FFFDE7;";
    private static final String MONO_FONT_STYLE =
            "-fx-font-family:'JetBrains Mono','Menlo','Courier New',monospace;"
                    + "-fx-font-size:13;";
    private static final String PANEL_BG_STYLE = "-fx-background-color: #FFFDE7;";
    private static final String STATUS_DIR_MODE = "DIR mode";
    private static final String STATUS_FILE_MODE = "FILE mode";
    private static final String STATUS_SWAPPED = "⇄ swapped";
    private static final String STATUS_CLIPBOARD_COPIED = "📋 copied";

    // --- left panel ---
    @FXML private VBox leftPanel;
    @FXML private TextField leftPathField;
    @FXML private ListView<CompareLineItem> leftListView;

    // --- right panel ---
    @FXML private VBox rightPanel;
    @FXML private TextField rightPathField;
    @FXML private ListView<CompareLineItem> rightListView;

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
    private CliArgs pendingCliArgs;


    @FXML
    private void initialize() {
        log.debug("[{}]", LogHelper.method());
        log.info("controller init");

        applyPaleYellowPanels();
        installDiffCellFactories();
        addProgrammaticButtons();
        setupSyncScroll();
        updateCenterStripState();

        Platform.runLater(this::executeCliAutoCompare);
    }


    public void applyCliArgs(CliArgs args) {
        log.info("CLI args received: L={} R={} auto={}",
                args.getLeftPath(), args.getRightPath(), args.isAutoCompare());
        this.pendingCliArgs = args;
    }


    private void executeCliAutoCompare() {
        if (pendingCliArgs == null || !pendingCliArgs.isAutoCompare()) {
            return;
        }

        log.info("CLI autocompare: loading paths");
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
        log.debug("[{}]", LogHelper.method());
        DiffCellFactory factory = new DiffCellFactory();
        leftListView.setCellFactory(factory);
        rightListView.setCellFactory(factory);
    }


    private void applyPaleYellowPanels() {
        log.debug("[{}]", LogHelper.method());
        leftPanel.setStyle(PANEL_BG_STYLE);
        rightPanel.setStyle(PANEL_BG_STYLE);
        log.info("panels painted pale yellow");
    }


    private void addProgrammaticButtons() {
        log.debug("[{}]", LogHelper.method());

        var swapBtn = new Button("⇄");
        swapBtn.setPrefWidth(36);
        swapBtn.setPrefHeight(28);
        swapBtn.setStyle("-fx-font-size:16; -fx-font-weight:bold;");
        swapBtn.setTooltip(new Tooltip("Swap left ↔ right"));
        swapBtn.setOnAction(e -> onSwapPanels());
        centerStrip.getChildren().add(3, swapBtn);

        var homeBtn = new Button("🏠 Home");
        homeBtn.setOnAction(e -> onLoadHome());
        mainToolBar.getItems().add(7, new Separator());
        mainToolBar.getItems().add(8, homeBtn);

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

        String tmpStatus = statusLeft.getText();
        statusLeft.setText(statusRight.getText());
        statusRight.setText(tmpStatus);
        statusCenter.setText(STATUS_SWAPPED);
    }


    private void onLoadHome() {
        String home = System.getProperty("user.home");
        if (StringUtils.isNotBlank(home)) {
            log.info("loading home dir: {}", home);
            leftPath = Path.of(home);
            leftPathField.setText(StringUtils.abbreviate(home, 60));
            loadDirectoryPreview(leftPath, leftListView, true);
        }
    }


    private void setupSyncScroll() {
        Platform.runLater(() -> {
            bindScrollBars(leftListView, rightListView);
            bindScrollBars(rightListView, leftListView);
        });
    }


    private void bindScrollBars(ListView<?> source, ListView<?> target) {
        ScrollBar sourceBar = findScrollBar(source);
        ScrollBar targetBar = findScrollBar(target);

        if (sourceBar == null || targetBar == null) {
            return;
        }

        sourceBar.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (syncScrollToggle.isSelected()) {
                targetBar.setValue(newVal.doubleValue());
            }
        });
    }


    private ScrollBar findScrollBar(ListView<?> listView) {
        for (var node : listView.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar sb && sb.getOrientation() == Orientation.VERTICAL) {
                return sb;
            }
        }
        return null;
    }


    private void updateCenterStripState() {
        boolean hasLeft = leftPath != null;
        boolean hasRight = rightPath != null;
        boolean hasBoth = hasLeft && hasRight;

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
            loadDirectoryPreview(p, leftListView, true);
            log.info("opened left: {}", p);
        }
    }


    @FXML
    private void onOpenRight() {
        Path p = chooseFileOrDir("Open Right");
        if (p != null) {
            rightPath = p;
            rightPathField.setText(p.toString());
            loadDirectoryPreview(p, rightListView, false);
            log.info("opened right: {}", p);
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
            CompareResult result;
            if (dirMode) {
                result = DirectoryComparator.compare(leftPath, rightPath, showIdenticalCheck.isSelected());
            } else {
                result = FileContentComparator.compare(leftPath, rightPath, showIdenticalCheck.isSelected());
            }

            leftListView.setItems(FXCollections.observableArrayList(result.leftItems()));
            rightListView.setItems(FXCollections.observableArrayList(result.rightItems()));
            diffCountLabel.setText("diffs: " + result.diffCount());
            statusCenter.setText(result.statusText());
            log.info("compare done: {}", result.summary());
        } catch (IOException ex) {
            log.error("compare failed: {}", ex.getMessage());
            showAlert("Compare failed: " + ex.getMessage());
        }
    }


    @FXML
    private void onRefresh() {
        log.debug("refresh");
        if (leftPath != null) {
            loadDirectoryPreview(leftPath, leftListView, true);
        }
        if (rightPath != null) {
            loadDirectoryPreview(rightPath, rightListView, false);
        }
    }


    @FXML
    private void onQuit() {
        log.info("quit requested");
        Platform.exit();
    }


    @FXML
    private void onToggleIdentical() {
        onCompare();
    }


    @FXML
    private void onToggleDirMode() {
        setDirMode(!dirMode);
    }

    private void setDirMode(boolean enabled) {
        dirMode = enabled;
        dirModeToggle.setSelected(enabled);
        showDirsCheck.setSelected(enabled);
        statusCenter.setText(enabled ? STATUS_DIR_MODE : STATUS_FILE_MODE);
    }


    @FXML private void onExpandAll() { }
    @FXML private void onCollapseAll() { }


    // ═══ center strip actions ═══

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
    }


    @FXML
    private void onShowEqual() {
        statusCenter.setText("showing identical only");
    }


    @FXML
    private void onDeleteSelected() {
        statusCenter.setText("🗑 delete (stub)");
    }


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


    @FXML private void onSyncScroll() { }


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
                    items.add(new CompareLineItem(i + 1, lines.get(i), CompareLineItem.DiffStatus.IDENTICAL));
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
                        String prefix = Files.isDirectory(p) ? "📁 " : "   ";
                        String name = prefix + p.getFileName();
                        return new CompareLineItem(0, name, CompareLineItem.DiffStatus.IDENTICAL);
                    })
                    .collect(Collectors.toList());
        }
    }


    // ═══ utils ═══

    private void updateStatus(boolean isLeft, String text) {
        if (isLeft) {
            statusLeft.setText(text);
            return;
        }
        statusRight.setText(text);
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
