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

import java.io.IOException;

@Slf4j
public final class App extends Application {

    private static final double DEFAULT_WIDTH = 1100;
    private static final double DEFAULT_HEIGHT = 760;
    private static final String WINDOW_TITLE = "MiMiComparator";
    private static final String FXML_FILE_NAME = "/org/senatov/MiMiComparator.fxml";


    public static void main(String[] args) {
        log.info("MiMiComparator starting...");
        launch(args);
    }


    @Override
    public void start(Stage stage) throws Exception {
        log.info("applying CupertinoLight theme");
        Application.setUserAgentStylesheet(new CupertinoLight().getUserAgentStylesheet());
        Parent root = loadRootView();
        Scene scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        stage.setTitle(WINDOW_TITLE);
        stage.setScene(scene);
        stage.show();
        log.info("stage shown {}x{}", DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }


    private Parent loadRootView() throws IOException {
        FXMLLoader loader = new FXMLLoader(App.class.getResource(FXML_FILE_NAME));
        if (loader.getLocation() == null) {
            throw new IOException("FXML resource not found: " + FXML_FILE_NAME);
        }
        log.debug("FXML loaded from {}", FXML_FILE_NAME);
        return loader.load();
    }
}
