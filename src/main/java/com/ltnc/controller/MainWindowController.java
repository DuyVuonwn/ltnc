package com.ltnc.controller;

import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import netscape.javascript.JSObject;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainWindowController {

    // =========================
    // OPEN HANDOVER WINDOW
    // =========================
    private WebEngine mainEngine;

    public void setMainEngine(WebEngine engine) {
        this.mainEngine = engine;
    }

    // =========================
    // OPEN HANDOVER WINDOW
    // =========================
    public void openHandoverWindow() {
        openModalWindow("MedInventory - Handover Assets", "/view/HandoverWindow.html", new HandoverWindowController(),
                true);
        // Refresh after close
        if (mainEngine != null) {
            mainEngine.executeScript(
                    "if(window.loadStats) window.loadStats(); if(window.loadAssets) window.loadAssets();");
        }
    }

    // =========================
    // DATA METHODS
    // =========================
    public String getAssets() {
        com.ltnc.model.AssetDAO dao = new com.ltnc.model.AssetDAO();
        java.util.List<com.ltnc.model.Asset> assets = dao.getAllAssets();

        // Manual JSON serialization since no library is available
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < assets.size(); i++) {
            com.ltnc.model.Asset a = assets.get(i);
            json.append("{")
                    .append("\"id\":\"").append(escape(a.getId())).append("\",")
                    .append("\"name\":\"").append(escape(a.getName())).append("\",")
                    .append("\"asset_category\":\"").append(escape(a.getAsset_category())).append("\",")
                    .append("\"base_unit\":\"").append(escape(a.getBase_unit())).append("\",")
                    .append("\"total_quantity\":").append(a.getTotal_quantity()).append(",")
                    .append("\"current_stock\":").append(a.getCurrent_stock()).append(",")
                    .append("\"isDistributed\":").append(a.isDistributed())
                    .append("}");
            if (i < assets.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
        return json.toString();
    }

    private String escape(String s) {
        if (s == null)
            return "";
        return s.replace("\"", "\\\"").replace("\n", "\\n");
    }

    public String getHistory(String assetId) {
        com.ltnc.model.AssetDAO dao = new com.ltnc.model.AssetDAO();
        java.util.List<java.util.Map<String, Object>> history = dao.getTransactions(assetId);

        // Simple JSON Builder
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < history.size(); i++) {
            java.util.Map<String, Object> r = history.get(i);
            json.append("{")
                    .append("\"date\":\"").append(r.get("date")).append("\",")
                    .append("\"type\":\"").append(r.get("type")).append("\",")
                    .append("\"qty\":").append(r.get("qty")).append(",")
                    .append("\"price\":").append(r.get("price")).append(",")
                    .append("\"reason\":\"").append(escape((String) r.get("reason"))).append("\",")
                    .append("\"note\":\"").append(escape((String) r.get("note"))).append("\"")
                    .append("}");
            if (i < history.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
        return json.toString();
    }

    public String getDashboardStats() {
        com.ltnc.model.AssetDAO dao = new com.ltnc.model.AssetDAO();
        java.util.Map<String, Integer> stats = dao.getDashboardStats();

        return new StringBuilder()
                .append("{")
                .append("\"totalStock\":").append(stats.getOrDefault("totalStock", 0)).append(",")
                .append("\"totalHandover\":").append(stats.getOrDefault("totalHandover", 0)).append(",")
                .append("\"totalDamage\":").append(stats.getOrDefault("totalDamage", 0))
                .append("}")
                .toString();
    }

    public String getAssetUsage(String assetId) {
        com.ltnc.model.AssetDAO dao = new com.ltnc.model.AssetDAO();
        java.util.List<java.util.Map<String, Object>> usage = dao.getAssetUsage(assetId);

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < usage.size(); i++) {
            java.util.Map<String, Object> u = usage.get(i);
            json.append("{")
                    .append("\"department\":\"").append(escape((String) u.get("department"))).append("\",")
                    .append("\"quantity\":").append(u.get("quantity"))
                    .append("}");
            if (i < usage.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
        return json.toString();
    }

    // =========================
    // ACTION METHODS
    // =========================
    public void importAssets() {
        openModalWindow("MedInventory - Import Assets", "/view/ImportAssets.html", new ImportAssetsController(), true);
    }

    public void performLiquidation() {
        openModalWindow("MedInventory - Liquidation", "/view/Liquidation.html", new LiquidationController());
    }

    public void reportDamage() {
        openModalWindow("MedInventory - Report Damage", "/view/ReportDamage.html", new ReportDamageController(), true);
        if (mainEngine != null) {
            mainEngine.executeScript(
                    "if(window.loadStats) window.loadStats(); if(window.loadAssets) window.loadAssets();");
        }
    }

    public void performStocktake() {
        openModalWindow("MedInventory - Stocktake", "/view/Stocktake.html", new StocktakeController(), true);
    }

    public void viewDatabase() {
        openModalWindow("MedInventory - Database Debugger", "/view/DatabaseViewer.html", new DatabaseViewerController(),
                false); // Non-blocking
    }

    public void viewReports() {
        openModalWindow("MedInventory - Reports", "/view/Reports.html", new ReportsController());
    }

    // =========================
    // GENERIC MODAL OPENER
    // =========================
    // =========================
    // GENERIC MODAL OPENER
    // =========================
    private void openModalWindow(String title, String htmlPath, Object controller) {
        openModalWindow(title, htmlPath, controller, false);
    }

    private void openModalWindow(String title, String htmlPath, Object controller, boolean wait) {
        System.out.println("Opening modal window: " + title);
        try {
            // Task to run on FX Thread
            Runnable task = () -> {
                try {
                    Stage stage = new Stage();
                    stage.initModality(Modality.APPLICATION_MODAL);

                    if (controller instanceof HandoverWindowController) {
                        ((HandoverWindowController) controller).setStage(stage);
                    } else if (controller instanceof ReportDamageController) {
                        ((ReportDamageController) controller).setStage(stage);
                    } else if (controller instanceof StocktakeController) {
                        ((StocktakeController) controller).setStage(stage);
                    }

                    WebView webView = new WebView();
                    WebEngine engine = webView.getEngine();

                    engine.setJavaScriptEnabled(true);
                    engine.setUserAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36");

                    engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                        if (newState == Worker.State.SUCCEEDED) {
                            JSObject window = (JSObject) engine.executeScript("window");
                            window.setMember("javaBridge", controller);
                            engine.executeScript("if(window.initData) window.initData();");
                        }
                    });

                    String url = getClass().getResource(htmlPath).toExternalForm();
                    engine.load(url);

                    Scene scene = new Scene(webView, 1024, 768);
                    stage.setTitle(title);
                    stage.setScene(scene);

                    if (wait) {
                        stage.showAndWait();
                    } else {
                        stage.show();
                    }

                    System.out.println("✓ Modal '" + title + "' closed (or opened async)");
                } catch (Exception e) {
                    System.err.println("Error opening window '" + title + "': " + e.getMessage());
                    e.printStackTrace();
                }
            };

            if (wait) {
                // If we want to wait, we must be on FX thread or block until done.
                // JS Bridge usually runs on FX Thread.
                if (javafx.application.Platform.isFxApplicationThread()) {
                    task.run();
                } else {
                    // If not on FX thread, we can't easily block and wait for FX task without
                    // Future
                    // but for this app, assume FX thread call from JS.
                    javafx.application.Platform.runLater(task);
                }
            } else {
                javafx.application.Platform.runLater(task);
            }

        } catch (Exception e) {
            System.err.println("✗ Failed to initiate modal window: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================
    // BRIDGE METHODS
    // =========================
    public String hello() {
        return "Connection successful from MainWindowController!";
    }

    // =========================
    // UTILITY: LOG FROM JS
    // =========================
    public void log(String message) {
        System.out.println("[JS->Java] " + message);
    }

    public String searchAssets(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "[]";
        }
        com.ltnc.model.AssetDAO dao = new com.ltnc.model.AssetDAO();
        java.util.List<com.ltnc.model.Asset> assets = dao.searchAssets(query);
        JSONArray json = new JSONArray();
        for (com.ltnc.model.Asset a : assets) {
            JSONObject obj = new JSONObject();
            obj.put("id", a.getId());
            obj.put("name", a.getName());
            obj.put("type", a.getAsset_category());
            obj.put("manufacturer", a.getManufacturer());
            json.put(obj);
        }
        return json.toString();
    }
}