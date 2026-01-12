package com.ltnc.controller;

public class ReportDamageController {

    public void log(String message) {
        System.out.println("[ReportDamage JS] " + message);
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

    public void submitDamageReport(String jsonString) {
        try {
            org.json.JSONObject jsonData = new org.json.JSONObject(jsonString);
            String deptId = jsonData.getString("departmentId");
            org.json.JSONArray items = jsonData.getJSONArray("items");

            com.ltnc.model.AssetDAO dao = new com.ltnc.model.AssetDAO();

            for (int i = 0; i < items.length(); i++) {
                org.json.JSONObject item = items.getJSONObject(i);
                String id = item.getString("id");
                int qty = item.getInt("qty");
                String reason = item.optString("reason", "Hỏng");
                // Note is not currently sent, but can be added if needed
                // String note = item.optString("note", "");

                // Simplistic dispatch logic: We check the DAO or cache, but for now
                // we assume usage of reportToolDamage if it succeeds, or check type if passed.
                // Or better: pass type from frontend.
                // Let's assume frontend passes 'type' or we fetch it.
                // For this task, we focus on CCDC (TOOL). The user asked for CCDC logic.
                // We'll treat all as tool damage for now or try to be safer.
                // Ideally, get type from DB.

                // Check for details (TSCD)
                org.json.JSONArray detailsJson = item.optJSONArray("details");

                if (detailsJson != null && detailsJson.length() > 0) {
                    // It's a Fixed Asset (TSCD)
                    java.util.List<String> identifiers = new java.util.ArrayList<>();
                    for (int k = 0; k < detailsJson.length(); k++) {
                        identifiers.add(detailsJson.getString(k));
                    }
                    dao.reportFixedAssetDamage(id, deptId, identifiers, reason, "");
                } else {
                    // It's a Tool (CCDC)
                    dao.reportToolDamage(id, deptId, qty, reason, "");
                }
            }
            log("Damage report submitted successfully for department: " + deptId);
            closeWindow();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error submitting damage report: " + e.getMessage());
        }
    }
}
