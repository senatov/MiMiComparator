/*
 * App.java — entry point for MiMiComparator.
 * Loads Cupertino theme, FXML, shows stage.
 * Iakov Senatov, 2026
 */
package org.senatov;


import atlantafx.base.theme.CupertinoLight;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.senatov.helpers.log.LogHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public final class App extends Application {

    private static final double DEFAULT_WIDTH = 1300;
    private static final double DEFAULT_HEIGHT = 1100;
    private static final String WINDOW_TITLE = "MiMiComparator";
    private static final String FXML_FILE_NAME = "/org/senatov/MiMiComparator.fxml";

    private static final Path CONFIG_DIR = Path.of(System.getProperty("user.home"), ".mimi", "comparator");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("comparator-state.json");


    public static void main(String[] args) {
        log.info("MiMiComparator starting...");
        launch(args);
    }


    @Override
    public void start(Stage stage) throws Exception {
        log.debug("[{}]", LogHelper.method());
        Application.setUserAgentStylesheet(new CupertinoLight().getUserAgentStylesheet());

        final ComparatorState state = loadComparatorState();
        final Parent root = loadRootView();
        final Scene scene = new Scene(root, state.windowWidth, state.windowHeight);

        stage.setTitle(WINDOW_TITLE);
        stage.setScene(scene);
        applyStageState(stage, state);
        installStageAutosave(stage);
        stage.show();

        saveComparatorState(stage);
        log.info("stage shown x={} y={} width={} height={}", stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
    }
    private void applyStageState(Stage stage, ComparatorState state) {
        log.debug("[{}]", LogHelper.method());

        stage.setWidth(state.windowWidth);
        stage.setHeight(state.windowHeight);

        if (state.windowX != null) {
            stage.setX(state.windowX);
        }
        if (state.windowY != null) {
            stage.setY(state.windowY);
        }
    }


    private void installStageAutosave(Stage stage) {
        log.debug("[{}]", LogHelper.method());

        stage.xProperty().addListener((obs, oldValue, newValue) -> saveComparatorState(stage));
        stage.yProperty().addListener((obs, oldValue, newValue) -> saveComparatorState(stage));
        stage.widthProperty().addListener((obs, oldValue, newValue) -> saveComparatorState(stage));
        stage.heightProperty().addListener((obs, oldValue, newValue) -> saveComparatorState(stage));
        stage.setOnCloseRequest(event -> saveComparatorState(stage));
    }


    private ComparatorState loadComparatorState() {
        log.debug("[{}]", LogHelper.method());

        ensureConfigDirectory();

        if (!Files.exists(CONFIG_FILE)) {
            log.info("config not found, using defaults: {}", CONFIG_FILE);
            return ComparatorState.defaults();
        }

        try {
            final String json = Files.readString(CONFIG_FILE, StandardCharsets.UTF_8);
            final ComparatorState loadedState = ComparatorState.fromJson(json);
            log.info("config loaded from {}", CONFIG_FILE);
            return loadedState;
        } catch (Exception exception) {
            log.error("failed to read config {}, using defaults", CONFIG_FILE, exception);
            return ComparatorState.defaults();
        }
    }


    private void saveComparatorState(Stage stage) {
        ensureConfigDirectory();

        final ComparatorState state = ComparatorState.fromStage(stage);
        final String json = state.toJson();

        try {
            Files.writeString(
                    CONFIG_FILE,
                    json,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            log.debug("config saved to {}", CONFIG_FILE);
        } catch (Exception exception) {
            log.error("failed to write config {}", CONFIG_FILE, exception);
        }
    }


    private void ensureConfigDirectory() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create config directory: " + CONFIG_DIR, exception);
        }
    }


    private Parent loadRootView() throws IOException {
        log.debug("[{}]", LogHelper.method());

        final FXMLLoader loader = new FXMLLoader(App.class.getResource(FXML_FILE_NAME));
        if (loader.getLocation() == null) {
            throw new IOException("FXML resource not found: " + FXML_FILE_NAME);
        }

        log.debug("FXML loaded from {}", FXML_FILE_NAME);
        return loader.load();
    }


    private static final class ComparatorState {
        private static final Pattern WINDOW_X_PATTERN = Pattern.compile("\"windowX\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");
        private static final Pattern WINDOW_Y_PATTERN = Pattern.compile("\"windowY\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");
        private static final Pattern WINDOW_WIDTH_PATTERN = Pattern.compile("\"windowWidth\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");
        private static final Pattern WINDOW_HEIGHT_PATTERN = Pattern.compile("\"windowHeight\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");

        private final Double windowX;
        private final Double windowY;
        private final double windowWidth;
        private final double windowHeight;


        private ComparatorState(Double windowX, Double windowY, double windowWidth, double windowHeight) {
            this.windowX = windowX;
            this.windowY = windowY;
            this.windowWidth = normalizeSize(windowWidth, DEFAULT_WIDTH);
            this.windowHeight = normalizeSize(windowHeight, DEFAULT_HEIGHT);
        }


        private static ComparatorState defaults() {
            return new ComparatorState(null, null, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        }


        private static ComparatorState fromStage(Stage stage) {
            return new ComparatorState(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        }


        private static ComparatorState fromJson(String json) {
            final Double loadedX = extractOptionalDouble(json, WINDOW_X_PATTERN);
            final Double loadedY = extractOptionalDouble(json, WINDOW_Y_PATTERN);
            final double loadedWidth = extractRequiredDouble(json, WINDOW_WIDTH_PATTERN, DEFAULT_WIDTH);
            final double loadedHeight = extractRequiredDouble(json, WINDOW_HEIGHT_PATTERN, DEFAULT_HEIGHT);
            return new ComparatorState(loadedX, loadedY, loadedWidth, loadedHeight);
        }


        private String toJson() {
            final StringBuilder builder = new StringBuilder();
            builder.append("{\n");
            builder.append("  \"windowX\": ").append(windowX == null ? "null" : windowX).append(",\n");
            builder.append("  \"windowY\": ").append(windowY == null ? "null" : windowY).append(",\n");
            builder.append("  \"windowWidth\": ").append(windowWidth).append(",\n");
            builder.append("  \"windowHeight\": ").append(windowHeight).append("\n");
            builder.append("}\n");
            return builder.toString();
        }


        private static Double extractOptionalDouble(String json, Pattern pattern) {
            final Matcher matcher = pattern.matcher(json);
            if (!matcher.find()) {
                return null;
            }
            return Double.parseDouble(matcher.group(1));
        }


        private static double extractRequiredDouble(String json, Pattern pattern, double defaultValue) {
            final Matcher matcher = pattern.matcher(json);
            if (!matcher.find()) {
                return defaultValue;
            }
            return Double.parseDouble(matcher.group(1));
        }


        private static double normalizeSize(double value, double fallback) {
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 200.0) {
                return fallback;
            }
            return value;
        }
    }
}