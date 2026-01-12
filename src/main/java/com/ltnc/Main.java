package com.ltnc;

import com.ltnc.controller.MainWindowController;
import com.ltnc.model.Database;
import javafx.application.Application;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

public class Main extends Application {
    private MainWindowController controller;

    @Override
    public void start(Stage stage) {
        Database.init();
        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();
        engine.setJavaScriptEnabled(true);
        engine.setUserAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36");

        // Prevent GC: Create strong reference
        // REFACTOR: Use MainWindowController as the bridge directly
        this.controller = new MainWindowController();
        this.controller.setMainEngine(engine);

        engine.getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) engine.executeScript("window");
                // Inject the strong reference
                window.setMember("javaBridge", this.controller);
                System.out.println("✓ Java object 'javaBridge' injected into WebView (Controller)");
            }
        });

        // Debug: Listen for alerts from JS
        engine.setOnAlert(event -> System.out.println("[ALERT] " + event.getData()));

        // Debug: Listen for exceptions
        engine.getLoadWorker().exceptionProperty().addListener((obs, oldExc, newExc) -> {
            if (newExc != null) {
                System.err.println("WebEngine Exception: " + newExc.getMessage());
                newExc.printStackTrace();
            }
        });

        String url = getClass().getResource("/view/MainWindow.html").toExternalForm();
        engine.load(url);

        Scene scene = new Scene(webView);
        stage.setTitle("LTNC - UI Preview");
        stage.setScene(scene);
        stage.setMaximized(true); // Set maximize TRƯỚC khi show
        stage.show(); // Show SAU khi set maximize
    }

    public static void main(String[] args) {
        launch(args);
    }
}