package com.ltnc.controller;

public class StocktakeController {

    // =========================
    // WINDOW MANAGEMENT
    // =========================
    private javafx.stage.Stage stage;

    public void setStage(javafx.stage.Stage stage) {
        this.stage = stage;
    }

    public void closeWindow() {
        if (this.stage != null) {
            javafx.application.Platform.runLater(() -> this.stage.close());
        }
    }

    // =========================
    // DATA METHODS
    // =========================
    // =========================
    // DATA METHODS
    // =========================
    public String getAssets() {
        com.ltnc.model.AssetDAO dao = new com.ltnc.model.AssetDAO();
        java.util.List<com.ltnc.model.Asset> assets = dao.getStocktakeList();

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < assets.size(); i++) {
            com.ltnc.model.Asset a = assets.get(i);
            json.append("{")
                    .append("\"id\":\"").append(escape(a.getId())).append("\",")
                    .append("\"name\":\"").append(escape(a.getName())).append("\",")
                    .append("\"asset_category\":\"").append(escape(a.getAsset_category())).append("\",")
                    .append("\"base_unit\":\"").append(escape(a.getBase_unit())).append("\",")
                    .append("\"total_quantity\":").append(a.getTotal_quantity()).append(",")
                    .append("\"current_stock\":").append(a.getCurrent_stock())
                    .append("}");
            if (i < assets.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
        return json.toString();
    }

    public String getStocktakeDetails(String assetId) {
        com.ltnc.model.AssetDAO dao = new com.ltnc.model.AssetDAO();
        java.util.List<java.util.Map<String, Object>> items = dao.getStocktakeItems(assetId);
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

    private String escape(String s) {
        if (s == null)
            return "";
        return s.replace("\"", "\\\"").replace("\n", "\\n");
    }

    public void log(String message) {
        System.out.println("[Stocktake JS] " + message);
    }

    public String completeStocktake(String jsonStr) {
        try {
            org.json.JSONArray data = new org.json.JSONArray(jsonStr);
            com.ltnc.model.AssetDAO dao = new com.ltnc.model.AssetDAO();
            String userId = "nv001"; // Hardcoded

            for (int i = 0; i < data.length(); i++) {
                org.json.JSONObject row = data.getJSONObject(i);
                String id = row.getString("id");
                String type = row.getString("type");

                if ("TOOL".equals(type) || "CCDC".equals(type)) {
                    int actual = row.getInt("actual");
                    int stock = row.optInt("stock", 0);
                    dao.saveToolInventoryCheck(id, actual, stock, userId);
                    // Update the actual quantity in the database
                    dao.updateToolQuantityAfterStocktake(id, actual);
                } else {
                    // Fixed Asset: Expect 'items' array
                    if (row.has("items")) {
                        org.json.JSONArray items = row.getJSONArray("items");
                        int foundCount = 0;
                        for (int k = 0; k < items.length(); k++) {
                            org.json.JSONObject item = items.getJSONObject(k);
                            String itemId = item.getString("id");
                            String status = item.getString("status"); // FOUND / MISSING / DAMAGED
                            dao.saveFixedAssetInventoryCheck(itemId, status, userId);
                            if ("FOUND".equals(status)) {
                                foundCount++;
                            }
                        }
                        // Update the actual quantity (count of found items)
                        dao.updateFixedAssetQuantityAfterStocktake(id, foundCount);
                    }
                }
            }
            return "SUCCESS";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    public String completeStocktakeAndExport(String jsonStr) {
        try {
            org.json.JSONArray data = new org.json.JSONArray(jsonStr);
            com.ltnc.model.AssetDAO dao = new com.ltnc.model.AssetDAO();
            String userId = "nv001"; // Hardcoded

            java.util.List<java.util.Map<String, Object>> exportData = new java.util.ArrayList<>();

            for (int i = 0; i < data.length(); i++) {
                org.json.JSONObject row = data.getJSONObject(i);
                String id = row.getString("id");
                String name = row.optString("name", "");
                String assetCategory = row.optString("asset_category", "");
                String baseUnit = row.optString("base_unit", "");
                String type = row.getString("type");

                if ("TOOL".equals(type) || "CCDC".equals(type)) {
                    int actual = row.getInt("actual");
                    int stock = row.optInt("stock", 0);
                    dao.saveToolInventoryCheck(id, actual, stock, userId);
                    // Update the actual quantity in the database
                    dao.updateToolQuantityAfterStocktake(id, actual);

                    // Add to export data
                    java.util.Map<String, Object> exportRow = new java.util.HashMap<>();
                    exportRow.put("id", id);
                    exportRow.put("name", name);
                    exportRow.put("asset_category", assetCategory);
                    exportRow.put("base_unit", baseUnit);
                    exportRow.put("book_quantity", stock);
                    exportRow.put("actual_quantity", actual);
                    exportData.add(exportRow);
                } else {
                    // Fixed Asset: Expect 'items' array
                    if (row.has("items")) {
                        org.json.JSONArray items = row.getJSONArray("items");
                        int foundCount = 0;
                        for (int k = 0; k < items.length(); k++) {
                            org.json.JSONObject item = items.getJSONObject(k);
                            String itemId = item.getString("id");
                            String status = item.getString("status"); // FOUND / MISSING / DAMAGED
                            dao.saveFixedAssetInventoryCheck(itemId, status, userId);
                            if ("FOUND".equals(status)) {
                                foundCount++;
                            }
                        }
                        // Update the actual quantity (count of found items)
                        dao.updateFixedAssetQuantityAfterStocktake(id, foundCount);

                        // For fixed assets, add summary to export
                        java.util.Map<String, Object> exportRow = new java.util.HashMap<>();
                        exportRow.put("id", id);
                        exportRow.put("name", name);
                        exportRow.put("asset_category", assetCategory);
                        exportRow.put("base_unit", baseUnit);
                        exportRow.put("book_quantity", items.length());
                        exportRow.put("actual_quantity", foundCount);
                        exportData.add(exportRow);
                    }
                }
            }

            // Export to Excel
            String filePath = com.ltnc.util.ExcelExporter.exportStocktakeToExcel(exportData, stage);
            return "SUCCESS|" + filePath;
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }
}
