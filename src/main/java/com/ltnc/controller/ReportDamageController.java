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

    public String getAssetsByDepartmentPaged(String jsonArgs) {
        // Expected JSON: { "deptId": "...", "page": 1, "limit": 10, "search": "" }
        try {
            org.json.JSONObject args = new org.json.JSONObject(jsonArgs);
            String deptId = args.getString("deptId");
            int page = args.optInt("page", 1);
            int limit = args.optInt("limit", 10);
            String search = args.optString("search", "");

            if (page < 1)
                page = 1;
            int offset = (page - 1) * limit;

            com.ltnc.model.AssetDAO dao = new com.ltnc.model.AssetDAO();

            // Get Data
            java.util.List<com.ltnc.model.Asset> assets = dao.getAssetsByDepartmentPaged(deptId, limit, offset, search);
            int totalCount = dao.countAssetsByDepartment(deptId, search);
            int totalPages = (int) Math.ceil((double) totalCount / limit);

            // Construct Response
            org.json.JSONObject result = new org.json.JSONObject();
            org.json.JSONArray assetsJson = new org.json.JSONArray();

            for (com.ltnc.model.Asset a : assets) {
                org.json.JSONObject obj = new org.json.JSONObject();
                obj.put("id", a.getId());
                obj.put("name", a.getName());
                obj.put("quantity", a.getCurrent_stock());
                obj.put("category", a.getAsset_category());
                assetsJson.put(obj);
            }

            result.put("assets", assetsJson);
            result.put("total", totalCount);
            result.put("page", page);
            result.put("totalPages", totalPages);

            return result.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return new org.json.JSONObject().put("error", e.getMessage()).toString();
        }
    }

    // Kept for backward compatibility if needed, but logic delegated to new method
    // or simple empty wrapper
    public String getAssetsByDepartment(String deptId) {
        // Redirect to page 1, distinct logic
        return getAssetsByDepartmentPaged(new org.json.JSONObject()
                .put("deptId", deptId)
                .put("page", 1)
                .put("limit", 1000) // All items simulation
                .toString());
    }

    private com.ltnc.model.User currentUser;

    public void setCurrentUser(com.ltnc.model.User user) {
        this.currentUser = user;
    }

    public String submitDamageReport(String jsonString) {
        try {
            if (currentUser == null)
                return "ERROR: User not logged in";
            String userId = currentUser.getId();

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

                // Check for details (TSCD)
                org.json.JSONArray detailsJson = item.optJSONArray("details");

                if (detailsJson != null && detailsJson.length() > 0) {
                    // It's a Fixed Asset (TSCD)
                    java.util.List<String> identifiers = new java.util.ArrayList<>();
                    for (int k = 0; k < detailsJson.length(); k++) {
                        identifiers.add(detailsJson.getString(k));
                    }
                    dao.reportFixedAssetDamage(id, deptId, identifiers, reason, "", userId);
                } else {
                    // It's a Tool (CCDC)
                    dao.reportToolDamage(id, deptId, qty, reason, "", userId);
                }
            }
            log("Damage report submitted successfully for department: " + deptId);
            return "SUCCESS";
        } catch (IllegalArgumentException e) {
            return "Thông tin không hợp lệ: " + e.getMessage();
        } catch (RuntimeException e) {
            // Extract inner message if possible
            return "Lỗi: " + e.getMessage();
        } catch (Exception e) {
            e.printStackTrace();
            return "Lỗi không xác định: " + e.getMessage();
        }
    }
}
