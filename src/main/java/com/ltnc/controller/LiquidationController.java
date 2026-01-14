package com.ltnc.controller;

public class LiquidationController {

    public void log(String message) {
        System.out.println("[Liquidation JS] " + message);
    }

    private com.ltnc.model.User currentUser;

    public void setCurrentUser(com.ltnc.model.User user) {
        this.currentUser = user;
    }

    private javafx.stage.Stage stage;

    public void setStage(javafx.stage.Stage stage) {
        this.stage = stage;
    }

    public void closeWindow() {
        if (stage != null) {
            javafx.application.Platform.runLater(() -> stage.close());
        }
    }

    public String getDepartments() {
        com.ltnc.model.AssetDAO dao = new com.ltnc.model.AssetDAO();
        java.util.List<java.util.Map<String, String>> depts = dao.getDepartments();
        org.json.JSONArray json = new org.json.JSONArray();
        for (java.util.Map<String, String> d : depts) {
            org.json.JSONObject obj = new org.json.JSONObject();
            obj.put("id", d.get("id"));
            obj.put("name", d.get("name"));
            json.put(obj);
        }
        return json.toString();
    }

    public String getAssetsByDepartment(String deptId) {
        if (deptId == null || deptId.isEmpty()) {
            return "[]";
        }
        com.ltnc.model.AssetDAO dao = new com.ltnc.model.AssetDAO();
        java.util.List<com.ltnc.model.Asset> assets = dao.getAssetsByDepartment(deptId);
        org.json.JSONArray json = new org.json.JSONArray();
        for (com.ltnc.model.Asset a : assets) {
            org.json.JSONObject obj = new org.json.JSONObject();
            obj.put("id", a.getId());
            obj.put("name", a.getName());
            obj.put("quantity", a.getCurrent_stock());
            obj.put("category", a.getAsset_category());
            json.put(obj);
        }
        return json.toString();
    }

    public String getLiquidationAssets(String deptId) {
        if (deptId == null || deptId.isEmpty()) {
            return "[]";
        }
        com.ltnc.model.AssetDAO dao = new com.ltnc.model.AssetDAO();
        java.util.List<java.util.Map<String, Object>> assets = dao.getLiquidationAssets(deptId);
        org.json.JSONArray json = new org.json.JSONArray();
        for (java.util.Map<String, Object> a : assets) {
            org.json.JSONObject obj = new org.json.JSONObject();
            obj.put("id", a.get("id"));
            obj.put("name", a.get("name"));
            obj.put("quantity", a.get("quantity"));
            obj.put("category", a.get("category"));
            obj.put("status", a.get("status")); // New field
            json.put(obj);
        }
        return json.toString();
    }

    public String getLiquidationItems(String assetId, String deptId, String status) {
        com.ltnc.model.AssetDAO dao = new com.ltnc.model.AssetDAO();
        java.util.List<java.util.Map<String, Object>> items = dao.getLiquidationItems(assetId, deptId, status);
        org.json.JSONArray json = new org.json.JSONArray();
        for (java.util.Map<String, Object> item : items) {
            org.json.JSONObject obj = new org.json.JSONObject();
            obj.put("id", item.get("id"));
            obj.put("serial", item.get("serial"));
            obj.put("status", item.get("status"));
            json.put(obj);
        }
        return json.toString();
    }

    public String submitLiquidation(String jsonString) {
        try {
            if (currentUser == null) {
                return "ERROR: User not logged in";
            }
            String userId = currentUser.getId();

            org.json.JSONObject jsonData = new org.json.JSONObject(jsonString);
            String deptId = jsonData.getString("departmentId");
            org.json.JSONArray items = jsonData.getJSONArray("items");

            com.ltnc.model.AssetDAO dao = new com.ltnc.model.AssetDAO();

            for (int i = 0; i < items.length(); i++) {
                org.json.JSONObject item = items.getJSONObject(i);
                String id = item.getString("id");
                int qty = item.getInt("qty");
                String reason = item.optString("reason", "L003");
                String note = item.optString("note", "");
                double price = item.optDouble("price", 0.0);

                org.json.JSONArray detailsJson = item.optJSONArray("details");

                if (detailsJson != null && detailsJson.length() > 0) {
                    // Fixed Asset
                    java.util.List<String> identifiers = new java.util.ArrayList<>();
                    for (int k = 0; k < detailsJson.length(); k++) {
                        identifiers.add(detailsJson.getString(k));
                    }
                    dao.liquidateFixedAsset(id, identifiers, price, reason, note, userId);
                } else {
                    // Tool
                    dao.liquidateTool(id, qty, price, reason, note, userId);
                }
            }
            log("Liquidation submitted successfully.");
            if (stage != null) {
                javafx.application.Platform.runLater(() -> stage.close());
            }
            return "SUCCESS";

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: " + e.getMessage();
        }
    }
}
