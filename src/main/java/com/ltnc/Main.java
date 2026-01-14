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

    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        Database.init();
        showLogin();
    }

    public void showLogin() {
        try {
            WebView webView = new WebView();
            WebEngine engine = webView.getEngine();
            engine.setJavaScriptEnabled(true);
            engine.setUserAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36");

            com.ltnc.controller.LoginController controller = new com.ltnc.controller.LoginController();

            // Setup Bridge
            engine.getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue == Worker.State.SUCCEEDED) {
                    JSObject window = (JSObject) engine.executeScript("window");
                    window.setMember("javaBridge", controller);
                }
            });

            engine.load(getClass().getResource("/view/Login.html").toExternalForm());

            Scene scene = new Scene(webView, 420, 650); // Resize for login (Portrait)
            primaryStage.setTitle("QL Kho ĐH Y Hà Nội - Đăng Nhập");
            primaryStage.setScene(scene);

            // Remove maximized for login screen for better aesthetics
            primaryStage.setMaximized(false);
            primaryStage.setResizable(false);
            primaryStage.centerOnScreen();

            primaryStage.show();

            // Handle Login Success
            controller.setOnUserAuthenticated(user -> {
                System.out.println("User authenticated: " + user.getName());
                showMainWindow(user);
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showMainWindow(com.ltnc.model.User user) {
        try {
            WebView webView = new WebView();
            WebEngine engine = webView.getEngine();
            engine.setJavaScriptEnabled(true);
            engine.setUserAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36");

            MainWindowController controller = new MainWindowController();
            controller.setMainEngine(engine);
            controller.setCurrentUser(user); // Pass User
            controller.setStage(primaryStage); // Pass Stage for logout logic

            engine.getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue == Worker.State.SUCCEEDED) {
                    JSObject window = (JSObject) engine.executeScript("window");
                    window.setMember("javaBridge", controller);

                    // Update UI with User Info
                    // Assuming we have a JS function to update user info, or we can inject it via
                    // DOM manipulation
                    // We'll update the HTML to have IDs for name and dept
                }
            });

            // Debug: Listen for alerts from JS
            engine.setOnAlert(event -> System.out.println("[ALERT] " + event.getData()));

            String url = getClass().getResource("/view/MainWindow.html").toExternalForm();
            engine.load(url);

            Scene scene = new Scene(webView);
            primaryStage.setTitle("QL Kho ĐH Y Hà Nội - Trang chủ");
            primaryStage.setScene(scene);
            primaryStage.setResizable(true); // Enable resize for Main Window
            primaryStage.setMaximized(true);
            primaryStage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}