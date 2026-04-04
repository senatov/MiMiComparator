/*
 * App.java — entry point for MiMiComparator.
 * Loads Cupertino theme, FXML, shows stage.
 * CLI: MiMiComparator <left> <right> or --left/--right
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
import org.senatov.cli.CliArgs;
import org.senatov.helpers.log.LogHelper;
import org.senatov.ui.config.ComparatorState;
import org.senatov.ui.config.ComparatorStateService;

import java.io.IOException;
import java.util.List;


@Slf4j
public final class App extends Application {

    private static final String WINDOW_TITLE = "MiMiComparator";
    private static final String FXML_FILE_NAME = "/org/senatov/MiMiComparator.fxml";

    private final ComparatorStateService stateService = new ComparatorStateService();
    private static CliArgs cliArgs;


    public static void main(String[] args) {
        log.info("MiMiComparator starting...");
        cliArgs = CliArgs.parse(List.of(args));
        launch(args);
    }


    @Override
    public void start(Stage stage) throws Exception {
        log.debug("[{}]", LogHelper.method());
        Application.setUserAgentStylesheet(new CupertinoLight().getUserAgentStylesheet());

        ComparatorState state = stateService.load();
        Parent root = loadRootView();

        double width = state.getWindow().getWidth();
        double height = state.getWindow().getHeight();
        Scene scene = new Scene(root, width, height);

        stage.setTitle(WINDOW_TITLE);
        stage.setScene(scene);
        applyStageState(stage, state);
        installStageAutosave(stage);
        stage.show();

        saveState(stage);
        log.info("stage shown x={} y={} w={} h={}",
                stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
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
        stage.xProperty().addListener((o, ov, nv) -> saveState(stage));
        stage.yProperty().addListener((o, ov, nv) -> saveState(stage));
        stage.widthProperty().addListener((o, ov, nv) -> saveState(stage));
        stage.heightProperty().addListener((o, ov, nv) -> saveState(stage));
        stage.setOnCloseRequest(event -> saveState(stage));
    }


    private void saveState(Stage stage) {
        ComparatorState state = stateService.load();
        ComparatorState.WindowState win = state.getWindow();
        win.setX(stage.getX());
        win.setY(stage.getY());
        win.setWidth(stage.getWidth());
        win.setHeight(stage.getHeight());
        win.setMaximized(stage.isMaximized());
        stateService.save(state);
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
