package com.ltnc.controller;

public class LiquidationController {

    public void log(String message) {
        System.out.println("[Liquidation JS] " + message);
    }

    private javafx.stage.Stage stage;

    public void setStage(javafx.stage.Stage stage) {
        this.stage = stage;
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
        java.util.List<com.ltnc.model.Asset> assets = dao.getLiquidationAssets(deptId);
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

    public String getLiquidationItems(String assetId, String deptId) {
        com.ltnc.model.AssetDAO dao = new com.ltnc.model.AssetDAO();
        java.util.List<java.util.Map<String, Object>> items = dao.getLiquidationItems(assetId, deptId);
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

    public void submitLiquidation(String jsonString) {
        try {
            org.json.JSONObject jsonData = new org.json.JSONObject(jsonString);
            String deptId = jsonData.getString("departmentId");
            // String date = jsonData.getString("date");
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
                    dao.reportFixedAssetLiquidation(id, deptId, identifiers, price, reason, note);
                } else {
                    // Tool
                    dao.reportToolLiquidation(id, deptId, qty, price, reason, note);
                }
            }
            log("Liquidation submitted successfully.");
            if (stage != null) {
                javafx.application.Platform.runLater(() -> stage.close());
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error submitting liquidation: " + e.getMessage());
        }
    }
}
