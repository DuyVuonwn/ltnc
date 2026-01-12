package com.ltnc.controller;

import com.ltnc.model.Asset;
import com.ltnc.model.AssetDAO;
import org.json.JSONArray;
import org.json.JSONObject;

public class HandoverWindowController {

    private AssetDAO assetDAO;
    private javafx.stage.Stage stage;

    public void setStage(javafx.stage.Stage stage) {
        this.stage = stage;
    }

    public void closeWindow() {
        if (this.stage != null) {
            javafx.application.Platform.runLater(() -> this.stage.close());
        }
    }

    public HandoverWindowController() {
        this.assetDAO = new AssetDAO();
    }

    public void log(String message) {
        System.out.println("[Handover JS] " + message);
    }

    public String searchAssets(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "[]";
        }
        java.util.List<Asset> assets = assetDAO.searchAssets(query);
        JSONArray json = new JSONArray();
        for (Asset a : assets) {
            JSONObject obj = new JSONObject();
            obj.put("id", a.getId());
            obj.put("name", a.getName());
            obj.put("type", a.getAsset_category());
            obj.put("unit", a.getBase_unit());
            obj.put("quantity", a.getTotal_quantity());
            obj.put("manufacturer", a.getManufacturer());
            json.put(obj);

        }
        return json.toString();
    }

    public String getDepartments() {
        java.util.List<java.util.Map<String, String>> depts = assetDAO.getDepartments();
        JSONArray json = new JSONArray();
        for (java.util.Map<String, String> d : depts) {
            JSONObject obj = new JSONObject();
            obj.put("id", d.get("id"));
            obj.put("name", d.get("name"));
            json.put(obj);
        }
        return json.toString();
    }

    public String checkSerial(String assetId, String serial) {
        String itemId = assetDAO.isSerialAvailable(assetId, serial);
        if (itemId != null) {
            return itemId;
        }
        return ""; // Not found
    }

    public String saveHandover(String jsonPayload) {
        log("Saving handover: " + jsonPayload);
        try {
            JSONObject root = new JSONObject(jsonPayload);
            String decisionNo = root.optString("decisionNo", "");
            String deptId = root.optString("departmentId", "");
            // String handoverDate = root.optString("handoverDate", ""); // Can be used for
            // custom date if DB schema supports it, currently using NOW()

            JSONArray items = root.getJSONArray("items");

            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String name = item.getString("name");
                int qty = item.getInt("qty");
                String assetId = item.optString("id", "");
                String type = item.optString("type", "");

                if (assetId.isEmpty())
                    assetId = assetDAO.getIdByName(name);
                if (assetId == null || assetId.isEmpty())
                    return "Lỗi: Không tìm thấy tài sản '" + name + "'";

                String reason = decisionNo; // Simple reason string

                if ("TSCD".equals(type) || "FIXED_ASSET".equals(type)) {
                    // Handle Fixed Asset
                    JSONArray details = item.optJSONArray("details");
                    if (details == null || details.length() != qty) {
                        return "Lỗi: Chưa nhập đủ chi tiết cho TSCD '" + name + "'";
                    }
                    for (int j = 0; j < details.length(); j++) {
                        JSONObject detail = details.getJSONObject(j);
                        String itemId = detail.optString("itemId", "");
                        if (itemId.isEmpty())
                            return "Lỗi: Mã định danh (ItemID) thiếu cho serial " + detail.optString("serial");

                        assetDAO.transferFixedAsset(assetId, itemId, deptId, reason, "");
                    }
                } else {
                    // Handle CCDC / Tool
                    assetDAO.transferTool(assetId, deptId, qty, reason, "");
                }
            }
            return "SUCCESS";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }
}