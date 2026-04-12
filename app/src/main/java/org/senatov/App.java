package org.senatov;


/*
 * App.java — entry point for MiMiComparator.
 * Loads Cupertino theme, FXML, shows stage.
 * CLI: MiMiComparator <left> <right> or --left/--right
 * Iakov Senatov, 2026
 */

import atlantafx.base.theme.CupertinoLight;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;
import org.senatov.cli.CliArgs;
import org.senatov.helpers.log.LogHelper;
import org.senatov.ui.config.ComparatorState;
import org.senatov.ui.config.ComparatorStateService;

import javafx.scene.image.Image;
import javax.imageio.ImageIO;
import java.awt.Taskbar;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;


@Slf4j
public final class App extends Application {

    private static final String WINDOW_TITLE = "MiMiComparator";
    private static final String FXML_FILE_NAME = "/org/senatov/MiMiComparator.fxml";
    private static final String[] APP_ICON_PNG_RESOURCES = {
            "/icons/icon_512x512.png",
            "/icons/icon_128x128.png"
    };
    private static final String APP_ICON_ICNS_RESOURCE = "/icons/MiMiComparator.icns";
    private static final String[] SF_PRO_DISPLAY_FONT_RESOURCES = {
            "/fonts/SF-Pro-Display-Light.otf",
            "/fonts/SF-Pro-Display-LightItalic.otf",
            "/fonts/SF-Pro-Display-Medium.otf",
            "/fonts/SF-Pro-Display-Thin.otf",
            "/fonts/SF-Pro-Display-ThinItalic.otf"
    };
    private static final double FONT_PRELOAD_SIZE = 14;
    private static volatile String sfProDisplayFamily = "SF Pro Display";

    private final ComparatorStateService stateService = new ComparatorStateService();
    private static CliArgs cliArgs;
    private ComparatorState appState;
    private final PauseTransition stageAutosaveDebounce = new PauseTransition(Duration.millis(350));


    public static void main(String[] args) {
        log.info("MiMiComparator starting...");
        cliArgs = CliArgs.parse(List.of(args));
        launch(args);
    }


    @Override
    public void start(Stage stage) throws Exception {
        log.debug("[{}]", LogHelper.method());
        Application.setUserAgentStylesheet(new CupertinoLight().getUserAgentStylesheet());
        preloadEmbeddedFonts();
        appState = stateService.load();
        Parent root = loadRootView();
        double width = appState.getWindow().getWidth();
        double height = appState.getWindow().getHeight();
        Scene scene = new Scene(root, width, height);
        stage.setTitle(WINDOW_TITLE);
        applyAppIcons(stage);
        stage.setScene(scene);
        configureStageAutosaveDebounce(stage);
        applyStageState(stage, appState);
        installStageAutosave(stage);
        stage.show();
        saveState(stage);
        log.info("stage shown x={} y={} w={} h={}",
                stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
    }

    private void applyAppIcons(Stage stage) {
        log.debug("[{}]", LogHelper.method());
        loadStageIcons(stage);
        applyTaskbarIcon();
    }


    private void loadStageIcons(Stage stage) {
        for (String resourcePath : APP_ICON_PNG_RESOURCES) {
            try (InputStream inputStream = App.class.getResourceAsStream(resourcePath)) {
                if (inputStream == null) {
                    log.debug("app icon png not found: {}", resourcePath);
                    continue;
                }
                Image image = new Image(inputStream);
                stage.getIcons().add(image);
                log.info("stage icon loaded: {}", resourcePath);
            } catch (IOException exception) {
                log.error("failed to read stage icon: {}", resourcePath, exception);
            }
        }
    }


    private void applyTaskbarIcon() {
        if (!Taskbar.isTaskbarSupported()) {
            log.debug("taskbar is not supported on this platform");
            return;
        }
        try {
            Taskbar taskbar = Taskbar.getTaskbar();
            for (String resourcePath : APP_ICON_PNG_RESOURCES) {
                try (InputStream inputStream = App.class.getResourceAsStream(resourcePath)) {
                    if (inputStream == null) {
                        continue;
                    }
                    BufferedImage image = ImageIO.read(inputStream);
                    if (image == null) {
                        continue;
                    }
                    taskbar.setIconImage(image);
                    log.info("taskbar icon applied: {}", resourcePath);
                    return;
                }
            }
            log.debug("no PNG icon could be applied to taskbar; icns resource available at {}", APP_ICON_ICNS_RESOURCE);
        } catch (Exception exception) {
            log.warn("failed to apply taskbar icon", exception);
        }
    }


    public static String sfProDisplayFamily() {
        return sfProDisplayFamily;
    }


    private static void preloadEmbeddedFonts() {
        log.debug("[{}]", LogHelper.method());
        for (String resourcePath : SF_PRO_DISPLAY_FONT_RESOURCES) {
            try (InputStream inputStream = App.class.getResourceAsStream(resourcePath)) {
                if (inputStream == null) {
                    log.debug("embedded font not found: {}", resourcePath);
                    continue;
                }
                Font font = Font.loadFont(inputStream, FONT_PRELOAD_SIZE);
                if (font == null) {
                    log.warn("failed to load embedded font: {}", resourcePath);
                    continue;
                }
                sfProDisplayFamily = font.getFamily();
                log.info("embedded font loaded: resource={} family='{}'", resourcePath, sfProDisplayFamily);
            } catch (IOException exception) {
                log.error("failed to read embedded font: {}", resourcePath, exception);
            }
        }
    }

    private void applyStageState(Stage stage, ComparatorState state) {
        log.debug("[{}]", LogHelper.method());
        ComparatorState.WindowState win = state.getWindow();
        stage.setWidth(win.getWidth());
        stage.setHeight(win.getHeight());
        stage.setX(win.getX());
        stage.setY(win.getY());
        if (win.isMaximized()) {
            stage.setMaximized(true);
        }
    }


    private void installStageAutosave(Stage stage) {
        log.debug("[{}]", LogHelper.method());
        stage.xProperty().addListener((o, ov, nv) -> requestStageAutosave());
        stage.yProperty().addListener((o, ov, nv) -> requestStageAutosave());
        stage.widthProperty().addListener((o, ov, nv) -> requestStageAutosave());
        stage.heightProperty().addListener((o, ov, nv) -> requestStageAutosave());
        stage.maximizedProperty().addListener((o, ov, nv) -> requestStageAutosave());
        stage.setOnCloseRequest(event -> saveState(stage));
    }


    private void configureStageAutosaveDebounce(Stage stage) {
        stageAutosaveDebounce.setOnFinished(event -> saveState(stage));
    }


    private void requestStageAutosave() {
        stageAutosaveDebounce.playFromStart();
    }


    private void saveState(Stage stage) {
        if (appState == null) {
            appState = stateService.load();
        }
        ComparatorState.WindowState win = appState.getWindow();
        win.setX(stage.getX());
        win.setY(stage.getY());
        win.setWidth(stage.getWidth());
        win.setHeight(stage.getHeight());
        win.setMaximized(stage.isMaximized());
        stateService.save(appState);
    }


    private Parent loadRootView() throws IOException {
        log.debug("[{}]", LogHelper.method());
        FXMLLoader loader = new FXMLLoader(App.class.getResource(FXML_FILE_NAME));
        if (loader.getLocation() == null) {
            throw new IOException("FXML resource not found: " + FXML_FILE_NAME);
        }
        Parent root = loader.load();
        MainController controller = loader.getController();
        controller.applyCliArgs(cliArgs);
        log.debug("FXML loaded, CLI args injected");
        return root;
    }
}