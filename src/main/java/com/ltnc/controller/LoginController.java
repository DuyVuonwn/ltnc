package com.ltnc.controller;

import com.ltnc.model.User;
import com.ltnc.model.UserDAO;
import javafx.application.Platform;

public class LoginController {

    private Runnable onLoginSuccess;
    private java.util.function.Consumer<User> onUserAuthenticated;

    public void setOnLoginSuccess(Runnable onLoginSuccess) {
        this.onLoginSuccess = onLoginSuccess;
    }

    public void setOnUserAuthenticated(java.util.function.Consumer<User> onUserAuthenticated) {
        this.onUserAuthenticated = onUserAuthenticated;
    }

    public boolean login(String username, String password) {
        UserDAO dao = new UserDAO();
        // Ensure default admin exists for first run

        User user = dao.login(username, password);
        if (user != null) {
            System.out.println("Login successful for user: " + user.getUsername());
            if (onUserAuthenticated != null) {
                onUserAuthenticated.accept(user);
            }
            if (onLoginSuccess != null) {
                // Must run on FX thread if it triggers UI change
                Platform.runLater(onLoginSuccess);
            }
            return true;
        } else {
            System.out.println("Login failed for user: " + username);
            return false;
        }
    }
}
