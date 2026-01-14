package com.ltnc.controller;

public class StocktakeController {

    // =========================
    // WINDOW MANAGEMENT
    // =========================
    private javafx.stage.Stage stage;
    private com.ltnc.model.User currentUser;

    public void setStage(javafx.stage.Stage stage) {
        this.stage = stage;
    }

    public void setCurrentUser(com.ltnc.model.User user) {
        this.currentUser = user;
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

        String realId = assetId;
        String statusFilter = "NORMAL";
        if (assetId.endsWith("_DMG")) {
            realId = assetId.replace("_DMG", "");
            statusFilter = "DAMAGED";
        }

        java.util.List<java.util.Map<String, Object>> items = dao.getStocktakeItems(realId, statusFilter);
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
            if (currentUser == null)
                return "ERROR: User not logged in";
            String userId = currentUser.getId();
            java.util.Set<String> processedAssetIds = new java.util.HashSet<>();

            for (int i = 0; i < data.length(); i++) {
                org.json.JSONObject row = data.getJSONObject(i);
                String id = row.getString("id");
                boolean isDamagedRow = id.endsWith("_DMG");
                String realId = isDamagedRow ? id.replace("_DMG", "") : id;
                String type = row.getString("type");

                processedAssetIds.add(realId);

                if ("TOOL".equals(type) || "CCDC".equals(type)) {
                    int actual = row.getInt("actual");
                    int stock = row.optInt("stock", 0);
                    // Assume normal rows are IN_STOCK, damaged rows are DAMAGED
                    String status = isDamagedRow ? "DAMAGED" : "IN_STOCK";

                    dao.saveToolInventoryCheck(realId, actual, stock, userId);
                    // Update the actual quantity in the database with status awareness
                    dao.updateToolQuantityAfterStocktake(realId, actual, status, userId);
                } else {
                    // Fixed Asset: Expect 'items' array
                    // Check items status individually, no need to worry about row status
                    if (row.has("items")) {
                        org.json.JSONArray items = row.getJSONArray("items");
                        // int foundCount = 0;
                        for (int k = 0; k < items.length(); k++) {
                            org.json.JSONObject item = items.getJSONObject(k);
                            String itemId = item.getString("id");
                            if (itemId == null || itemId.isEmpty() || "null".equals(itemId))
                                continue; // skip dummy items

                            String status = item.getString("status"); // FOUND / MISSING / DAMAGED
                            dao.saveFixedAssetInventoryCheck(itemId, status, userId);
                        }
                    }
                }
            }

            // Recalculate totals for all touched assets
            for (String aid : processedAssetIds) {
                dao.recalculateAssetTotal(aid);
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
            if (currentUser == null)
                return "User not logged in";
            String userId = currentUser.getId();
            java.util.Set<String> processedAssetIds = new java.util.HashSet<>();

            java.util.List<java.util.Map<String, Object>> exportData = new java.util.ArrayList<>();

            for (int i = 0; i < data.length(); i++) {
                org.json.JSONObject row = data.getJSONObject(i);
                String id = row.getString("id"); // Can be _DMG
                boolean isDamagedRow = id.endsWith("_DMG");
                String realId = isDamagedRow ? id.replace("_DMG", "") : id;

                String name = row.optString("name", "");
                String assetCategory = row.optString("asset_category", "");
                String baseUnit = row.optString("base_unit", "");
                String type = row.getString("type");

                processedAssetIds.add(realId);

                if ("TOOL".equals(type) || "CCDC".equals(type)) {
                    int actual = row.getInt("actual");
                    int stock = row.optInt("stock", 0);
                    String status = isDamagedRow ? "DAMAGED" : "IN_STOCK";

                    dao.saveToolInventoryCheck(realId, actual, stock, userId);
                    // Update the actual quantity in the database
                    dao.updateToolQuantityAfterStocktake(realId, actual, status, userId);

                    // Add to export data
                    java.util.Map<String, Object> exportRow = new java.util.HashMap<>();
                    exportRow.put("id", realId + (isDamagedRow ? " (Hỏng)" : ""));
                    exportRow.put("name", name);
                    exportRow.put("asset_category", assetCategory);
                    exportRow.put("base_unit", baseUnit);
                    exportRow.put("book_quantity", stock);
                    exportRow.put("actual_quantity", actual);
                    exportData.add(exportRow);
                } else {
                    // Fixed Asset: Expect 'items' array
                    int foundCount = 0;
                    if (row.has("items")) {
                        org.json.JSONArray items = row.getJSONArray("items");
                        for (int k = 0; k < items.length(); k++) {
                            org.json.JSONObject item = items.getJSONObject(k);
                            String itemId = item.getString("id");
                            if (itemId == null || itemId.isEmpty() || "null".equals(itemId))
                                continue;

                            String status = item.getString("status"); // FOUND / MISSING / DAMAGED
                            dao.saveFixedAssetInventoryCheck(itemId, status, userId);
                            if ("FOUND".equals(status) || "DAMAGED".equals(status)) {
                                foundCount++;
                            }
                        }

                        // For fixed assets, add summary to export
                        java.util.Map<String, Object> exportRow = new java.util.HashMap<>();
                        exportRow.put("id", realId + (isDamagedRow ? " (Hỏng)" : ""));
                        exportRow.put("name", name);
                        exportRow.put("asset_category", assetCategory);
                        exportRow.put("base_unit", baseUnit);
                        exportRow.put("book_quantity", row.optInt("stock", 0)); // Based on row split
                        exportRow.put("actual_quantity", foundCount);
                        exportData.add(exportRow);
                    }
                }
            }

            // Recalculate totals
            for (String aid : processedAssetIds) {
                dao.recalculateAssetTotal(aid);
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
