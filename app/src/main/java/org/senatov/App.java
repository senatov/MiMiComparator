/*
 * App.java
 * MiMiComparator
 *
 * Created by Iakov Senatov.
 * Copyright © 2026 Iakov Senatov. All rights reserved.
 *
 * Description:
 * JavaFX application entry point for launching the FXML-based desktop UI.
 */
package org.senatov;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import atlantafx.base.theme.CupertinoLight;

import java.io.IOException;

public final class App extends Application {
    private static final double DEFAULT_WIDTH = 1100;
    private static final double DEFAULT_HEIGHT = 760;
    private static final String WINDOW_TITLE = "MiMiComparator";
    private static final String FXML_FILE_NAME = "/org/senatov/MiMiComparator.fxml";

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        // apply macOS-style Cupertino theme before loading UI
        Application.setUserAgentStylesheet(new CupertinoLight().getUserAgentStylesheet());
        Parent root = loadRootView();
        Scene scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        stage.setTitle(WINDOW_TITLE);
        stage.setScene(scene);
        stage.show();
    }

    private Parent loadRootView() throws IOException {
        FXMLLoader loader = new FXMLLoader(App.class.getResource(FXML_FILE_NAME));
        if (loader.getLocation() == null) {
            throw new IOException("FXML resource not found: " + FXML_FILE_NAME);
        }
        return loader.load();
    }
}