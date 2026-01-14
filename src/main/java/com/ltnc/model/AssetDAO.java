package com.ltnc.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AssetDAO {

    // =========================
    // INIT & SCHEMA
    // =========================
    public void ensureDetailsTableExists() {
        // Database schema is managed externally. No auto-creation here.
    }

    // =========================
    // STOCKTAKE
    // =========================
    // =========================
    // STOCKTAKE
    // =========================
    public List<Asset> getStocktakeList() {
        return getAllAssets();
    }

    public List<Map<String, Object>> getStocktakeItems(String assetId) {
        List<Map<String, Object>> items = new ArrayList<>();
        String sql = "SELECT id, serial, status, department_id FROM fixed_asset_item WHERE asset_id = ? AND (status = 'IN_STOCK' OR status = 'IN_USE')";

        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, assetId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", rs.getString("id"));
                item.put("serial", rs.getString("serial"));
                item.put("status", rs.getString("status"));
                item.put("department_id", rs.getString("department_id"));
                items.add(item);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    public void saveToolInventoryCheck(String assetId, int actualQty, int bookQty, String userId) {
        String id = generateId("tool_inventory_check", "id", "TIC-");
        String sql = "INSERT INTO tool_inventory_check (id, asset_id, department_id, check_date, user_id, book_quantity, actual_quantity, difference) "
                +
                "VALUES (?, ?, 'qtvt', datetime('now'), ?, ?, ?, ?)";

        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.setString(2, assetId);
            pstmt.setString(3, userId);
            pstmt.setInt(4, bookQty);
            pstmt.setInt(5, actualQty);
            pstmt.setInt(6, actualQty - bookQty);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving tool inventory check: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void updateToolQuantityAfterStocktake(String assetId, int actualQty, String userId) {
        String updateToolSql = "UPDATE tool SET quantity = ? WHERE asset_id = ? AND department_id = 'qtvt'";
        String insertToolSql = "INSERT INTO tool (id, asset_id, department_id, quantity) VALUES (?, ?, 'qtvt', ?)";
        String updateAssetSql = "UPDATE asset SET total_quantity = ? WHERE id = ?";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Check if tool record exists for qtvt department
                String checkSql = "SELECT id FROM tool WHERE asset_id = ? AND department_id = 'qtvt'";
                boolean exists = false;
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setString(1, assetId);
                    ResultSet rs = checkStmt.executeQuery();
                    exists = rs.next();
                }

                // Update or insert tool record
                if (exists) {
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateToolSql)) {
                        updateStmt.setInt(1, actualQty);
                        updateStmt.setString(2, assetId);
                        updateStmt.executeUpdate();
                    }
                } else {
                    String toolId = generateNextId("TOOL", "tool", "id");
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertToolSql)) {
                        insertStmt.setString(1, toolId);
                        insertStmt.setString(2, assetId);
                        insertStmt.setInt(3, actualQty);
                        insertStmt.executeUpdate();
                    }
                }

                // Update asset total_quantity to match actual quantity
                try (PreparedStatement assetStmt = conn.prepareStatement(updateAssetSql)) {
                    assetStmt.setInt(1, actualQty);
                    assetStmt.setString(2, assetId);
                    assetStmt.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Error updating tool quantity after stocktake: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveFixedAssetInventoryCheck(String itemId, String status, String userId) {
        String id = generateId("fixed_asset_inventory_check", "id", "FIC-");
        String sql = "INSERT INTO fixed_asset_inventory_check (id, fixed_asset_item_id, check_date, user_id, actual_status) "
                +
                "VALUES (?, ?, datetime('now'), ?, ?)";

        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.setString(2, itemId);
            pstmt.setString(3, userId);
            pstmt.setString(4, status);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving fixed asset inventory check: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void updateFixedAssetQuantityAfterStocktake(String assetId, int actualQty) {
        String updateAssetSql = "UPDATE asset SET total_quantity = ? WHERE id = ?";

        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(updateAssetSql)) {
            pstmt.setInt(1, actualQty);
            pstmt.setString(2, assetId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating fixed asset quantity after stocktake: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================
    // READ
    // =========================
    // =========================
    // READ
    // =========================
    public List<Asset> getAllAssets() {
        ensureDetailsTableExists();
        List<Asset> assets = new ArrayList<>();
        // Query to fetch asset details + is_distributed flag + current_stock (in
        // 'qtvt')
        String sql = "SELECT a.id, a.name, a.asset_category, a.base_unit, a.total_quantity, " +
                "(EXISTS (SELECT 1 FROM tool t WHERE t.asset_id = a.id AND t.department_id != 'qtvt' AND t.quantity > 0) "
                +
                " OR " +
                " EXISTS (SELECT 1 FROM fixed_asset_item f WHERE f.asset_id = a.id AND f.department_id != 'qtvt' AND f.department_id IS NOT NULL)) AS is_distributed, "
                +
                "CASE " +
                "  WHEN a.asset_category = 'TOOL' THEN (SELECT COALESCE(SUM(quantity), 0) FROM tool WHERE asset_id = a.id AND department_id = 'qtvt' AND status = 'IN_STOCK') "
                +
                "  ELSE (SELECT COUNT(*) FROM fixed_asset_item WHERE asset_id = a.id AND (department_id = 'qtvt' OR department_id IS NULL) AND status != 'DAMAGED' AND status != 'LIQUIDATED') "
                +
                "END AS current_stock, " +
                "a.manufacturer " +
                "FROM asset a";

        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql);
                ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Asset asset = new Asset(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("asset_category"),
                        rs.getString("base_unit"),
                        rs.getInt("total_quantity"),
                        rs.getBoolean("is_distributed"),
                        rs.getInt("current_stock"),
                        rs.getString("manufacturer"));

                assets.add(asset);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching assets: " + e.getMessage());
            e.printStackTrace();
        }

        return assets;
    }

    public List<Map<String, Object>> getAssetUsage(String assetId) {
        List<Map<String, Object>> usageList = new ArrayList<>();
        // Check Category to decide which table to query
        // But since we don't have category passed in, we can query both or check asset
        // first.
        // Or simpler: Union logic or two simple queries.

        // 1. Check Tool Usage
        String toolSql = "SELECT d.name as dept_name, t.quantity " +
                "FROM tool t " +
                "JOIN department d ON t.department_id = d.id " +
                "WHERE t.asset_id = ? AND t.department_id != 'qtvt' AND t.quantity > 0";

        // 2. Check Fixed Asset Usage (Count items per dept)
        String fixedSql = "SELECT d.name as dept_name, COUNT(f.id) as quantity " +
                "FROM fixed_asset_item f " +
                "JOIN department d ON f.department_id = d.id " +
                "WHERE f.asset_id = ? AND f.department_id != 'qtvt' " +
                "GROUP BY d.name";

        try (Connection conn = Database.getConnection()) {

            // Try Tool first
            try (PreparedStatement days = conn.prepareStatement(toolSql)) {
                days.setString(1, assetId);
                ResultSet rs = days.executeQuery();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("department", rs.getString("dept_name"));
                    row.put("quantity", rs.getInt("quantity"));
                    usageList.add(row);
                }
            }

            // If empty, try Fixed Asset
            if (usageList.isEmpty()) {
                try (PreparedStatement fixedStmt = conn.prepareStatement(fixedSql)) {
                    fixedStmt.setString(1, assetId);
                    ResultSet rs2 = fixedStmt.executeQuery();
                    while (rs2.next()) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("department", rs2.getString("dept_name"));
                        row.put("quantity", rs2.getInt("quantity"));
                        usageList.add(row);
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return usageList;
    }

    public List<Asset> searchAssets(String query) {
        List<Asset> assets = new ArrayList<>();
        // Calculate Stock:
        // TSCD: Count Items in qtvt/NULL with IN_STOCK
        // CCDC: Sum Quantity in qtvt with IN_STOCK (or NULL status)
        String sql = "SELECT a.id, a.name, a.asset_category, a.base_unit, a.total_quantity, a.manufacturer, " +
                "CASE " +
                "  WHEN a.asset_category IN ('TSCD', 'FIXED_ASSET') THEN (" +
                "    SELECT COUNT(*) FROM fixed_asset_item f " +
                "    WHERE f.asset_id = a.id " +
                "    AND (f.department_id = 'qtvt' OR f.department_id IS NULL) " +
                "    AND f.status = 'IN_STOCK'" +
                "  ) " +
                "  ELSE (" +
                "    SELECT COALESCE(SUM(t.quantity), 0) FROM tool t " +
                "    WHERE t.asset_id = a.id " +
                "    AND (t.department_id = 'qtvt' OR t.department_id IS NULL) " + // tools in warehouse might strictly
                                                                                   // be 'qtvt'
                "    AND (t.status = 'IN_STOCK' OR t.status IS NULL)" +
                "  ) " +
                "END as current_stock " +
                "FROM asset a " +
                "WHERE a.name LIKE ? LIMIT 10";

        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, "%" + query + "%");
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Asset asset = new Asset(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("asset_category"),
                        rs.getString("base_unit"),
                        rs.getInt("total_quantity"),
                        false,
                        rs.getInt("current_stock"), // Populated from Query
                        rs.getString("manufacturer"));

                assets.add(asset);
            }
        } catch (SQLException e) {
            System.err.println("Error searching assets: " + e.getMessage());
            e.printStackTrace();
        }
        return assets;
    }

    public String getIdByName(String name) {
        String sql = "SELECT id FROM asset WHERE name = ?";
        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null; // Not found
    }

    // =========================
    // WRITE
    // =========================
    private String generateNextId(String prefix, String table, String idColumn) {
        String sql = "SELECT " + idColumn + " FROM " + table + " WHERE " + idColumn + " LIKE ? ORDER BY " + idColumn
                + " DESC LIMIT 1";
        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, prefix + "%");
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String lastId = rs.getString(1);
                // System.out.println("DEBUG: Last ID found for " + prefix + ": " + lastId);
                if (lastId.startsWith(prefix)) {
                    String numPart = lastId.substring(prefix.length() + 1); // AST-000001 -> 000001
                    // Handle "AST-000003" -> 3
                    try {
                        int currentNum = Integer.parseInt(numPart);
                        return String.format("%s-%06d", prefix, currentNum + 1);
                    } catch (NumberFormatException e) {
                        return prefix + "-000001-" + System.currentTimeMillis();
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return prefix + "-000001";
    }

    private String generateTransactionId() {
        return generateId("tool_transaction", "id", "TT-");
    }

    private String generateFixedAssetTransactionId() {
        return generateId("fixed_asset_transaction", "id", "FAT-");
    }

    private String generateId(String table, String col, String prefixBase) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd");
        String dateStr = sdf.format(new java.util.Date());
        String prefix = prefixBase + dateStr;

        String sql = "SELECT " + col + " FROM " + table + " WHERE " + col + " LIKE ? ORDER BY " + col + " DESC LIMIT 1";
        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, prefix + "%");
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String lastId = rs.getString(col);
                String[] parts = lastId.split("-");
                if (parts.length == 3) {
                    int validSeq = Integer.parseInt(parts[2]);
                    return String.format("%s-%03d", prefix, validSeq + 1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return prefix + "-001";
    }

    public void upsertAsset(Asset asset) {
        // Logic: Check Name AND Manufacturer
        String checkSql = "SELECT id, total_quantity, base_unit FROM asset WHERE name = ? AND (manufacturer = ? OR (manufacturer IS NULL AND ? IS NULL))";
        String updateSql = "UPDATE asset SET total_quantity = ? WHERE id = ?";
        String insertSql = "INSERT INTO asset(id, name, asset_category, base_unit, total_quantity, manufacturer) VALUES(?, ?, ?, ?, ?, ?)";

        System.out.println(
                "DEBUG: upsertAsset Check -> Name: [" + asset.getName() + "], Mfr: [" + asset.getManufacturer() + "]");

        try (Connection conn = Database.getConnection();
                PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {

            checkStmt.setString(1, asset.getName());
            checkStmt.setString(2, asset.getManufacturer());
            checkStmt.setString(3, asset.getManufacturer()); // For NULL check
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                String existingId = rs.getString("id");
                System.out.println("DEBUG: Found Existing Asset ID: " + existingId);
                int currentQty = rs.getInt("total_quantity");
                int newQty = currentQty + asset.getTotal_quantity();

                asset.setId(existingId); // IMPORTANT: Set ID back to object for further processing

                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setInt(1, newQty);
                    updateStmt.setString(2, existingId);
                    updateStmt.executeUpdate();
                }
            } else {
                String newId = generateNextId("AST", "asset", "id");
                System.out.println("DEBUG: Creating NEW Asset ID: " + newId);
                asset.setId(newId);

                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setString(1, newId);
                    insertStmt.setString(2, asset.getName());
                    String dbType = "TSCD".equals(asset.getAsset_category()) ? "FIXED_ASSET" : "TOOL";

                    insertStmt.setString(3, dbType);
                    insertStmt.setString(4, asset.getBase_unit());
                    insertStmt.setInt(5, asset.getTotal_quantity());
                    insertStmt.setString(6, asset.getManufacturer());
                    insertStmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.err.println("Error upserting asset: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String insertFixedAssetItem(String assetId, String serial, int manufactureYear, String note) {
        String sql = "INSERT INTO fixed_asset_item(id, asset_id, serial, manufacture_year, status, department_id, note) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?)";
        String newItemId = generateNextId("FAI", "fixed_asset_item", "id");

        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String defaultDept = "qtvt";
            String defaultStatus = "IN_STOCK";

            pstmt.setString(1, newItemId);
            pstmt.setString(2, assetId);
            pstmt.setString(3, serial);
            pstmt.setInt(4, manufactureYear);
            pstmt.setString(5, defaultStatus);
            pstmt.setString(6, defaultDept);
            pstmt.setString(7, note);

            pstmt.executeUpdate();
            return newItemId;
        } catch (SQLException e) {
            System.err.println("Error inserting fixed asset item: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public String isSerialAvailable(String assetId, String serial) {
        // Return Item ID if found and in stock, else null
        String sql = "SELECT id FROM fixed_asset_item WHERE asset_id = ? AND serial = ? AND status = 'IN_STOCK'";
        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, assetId);
            pstmt.setString(2, serial);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void logFixedAssetTransaction(String assetId, String itemId, String type, double price, String reason,
            String note, String userId) {
        String toDept = "qtvt";

        String sql = "INSERT INTO fixed_asset_transaction (id, asset_id, fixed_asset_item_id, transaction_type, transaction_date, "
                + "from_department_id, to_department_id, unit_price, disposal_price, user_id, reason, note) " +
                "VALUES (?, ?, ?, ?, datetime('now'), NULL, ?, ?, NULL, ?, ?, ?)";

        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String transId = generateFixedAssetTransactionId();

            pstmt.setString(1, transId);
            pstmt.setString(2, assetId);
            pstmt.setString(3, itemId);
            pstmt.setString(4, type);
            pstmt.setString(5, toDept);
            pstmt.setDouble(6, price);
            pstmt.setString(7, userId);
            pstmt.setString(8, reason);
            pstmt.setString(9, note);

            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error logging fixed asset transaction: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void transferFixedAsset(String assetId, String itemId, String toDept, String reason, String note,
            String userId) {
        // 1. Get current department
        String getFromDeptSql = "SELECT department_id FROM fixed_asset_item WHERE id = ?";
        String insertTransSql = "INSERT INTO fixed_asset_transaction (id, asset_id, fixed_asset_item_id, transaction_type, transaction_date, "
                + "from_department_id, to_department_id, unit_price, disposal_price, user_id, reason, note) " +
                "VALUES (?, ?, ?, 'HANDOVER', datetime('now'), ?, ?, 0, NULL, ?, ?, ?)";

        String updateItemSql = "UPDATE fixed_asset_item SET department_id = ?, status = 'IN_USE' WHERE id = ?";
        // NOTE: For Fixed Assets, handover creates a MOVEMENT of the item. Total Asset
        // Quantity (Ownership) remains same.
        // So we do NOT decrement 'asset.total_quantity'.

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Get From Dept
                String fromDept = "qtvt";
                try (PreparedStatement getStmt = conn.prepareStatement(getFromDeptSql)) {
                    getStmt.setString(1, itemId);
                    ResultSet rs = getStmt.executeQuery();
                    if (rs.next()) {
                        String dbDept = rs.getString("department_id");
                        if (dbDept != null)
                            fromDept = dbDept;
                    }
                }

                String transId = generateFixedAssetTransactionId();

                // 2. Log Transaction
                try (PreparedStatement transStmt = conn.prepareStatement(insertTransSql)) {
                    transStmt.setString(1, transId);
                    transStmt.setString(2, assetId);
                    transStmt.setString(3, itemId);
                    transStmt.setString(4, fromDept);
                    transStmt.setString(5, toDept);
                    transStmt.setString(6, userId);
                    transStmt.setString(7, reason);
                    transStmt.setString(8, note);
                    transStmt.executeUpdate();
                }

                // 3. Update Item Location/Status
                try (PreparedStatement itemStmt = conn.prepareStatement(updateItemSql)) {
                    itemStmt.setString(1, toDept);
                    itemStmt.setString(2, itemId);
                    itemStmt.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error transferring fixed asset: " + e.getMessage());
        }
    }

    public void upsertTool(String assetId, String departmentId, int quantity) {
        // Default to 'qtvt' and 'IN_STOCK' if not specified (Standard Import)
        String dept = (departmentId == null) ? "qtvt" : departmentId;
        String status = "IN_STOCK"; // Imported items are always IN_STOCK

        String checkSql = "SELECT id FROM tool WHERE asset_id = ? AND department_id = ? AND status = ?";
        String updateSql = "UPDATE tool SET quantity = quantity + ?, status = ? WHERE id = ?";
        String insertSql = "INSERT INTO tool (id, asset_id, department_id, quantity, status) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = Database.getConnection()) {
            // Check
            String existingId = null;
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, assetId);
                checkStmt.setString(2, dept);
                checkStmt.setString(3, status);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    existingId = rs.getString("id");
                }
            }

            if (existingId != null) {
                // Update
                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setInt(1, quantity);
                    updateStmt.setString(2, status);
                    updateStmt.setString(3, existingId);
                    updateStmt.executeUpdate();
                }
            } else {
                // Insert
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setString(1, generateNextId("TOOL", "tool", "id"));
                    insertStmt.setString(2, assetId);
                    insertStmt.setString(3, dept);
                    insertStmt.setInt(4, quantity);
                    insertStmt.setString(5, status);
                    insertStmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.err.println("Error upserting tool: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void logTransaction(String assetId, String type, int qty, double price, String note, String reason,
            String userId) {
        if ("IMPORT".equals(type)) {
            String transId = generateTransactionId();
            // IMPORT: From NULL -> To 'qtvt'
            String sql = "INSERT INTO tool_transaction (id, asset_id, transaction_type, transaction_date, quantity, " +
                    "from_department_id, to_department_id, unit_price, user_id, reason, note) " +
                    "VALUES (?, ?, ?, datetime('now'), ?, NULL, 'qtvt', ?, ?, ?, ?)";

            try (Connection conn = Database.getConnection();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, transId);
                pstmt.setString(2, assetId);
                pstmt.setString(3, type); // 'IMPORT'
                pstmt.setInt(4, qty);
                pstmt.setDouble(5, price);
                pstmt.setString(6, userId);
                pstmt.setString(7, reason);
                pstmt.setString(8, note);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                System.err.println("Error logging tool transaction (IMPORT): " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            // Deprecated or generic? Kept for backward compat if needed, but
            // logToolHandover supersedes for Handover.
            logToolHandover(assetId, qty, "qtvt", null, note, reason, userId);
        }
    }

    public void logToolHandover(String assetId, int qty, String fromDept, String toDept, String note, String reason,
            String userId) {
        String transId = generateTransactionId();
        String logSql = "INSERT INTO tool_transaction (id, asset_id, transaction_type, transaction_date, quantity, " +
                "from_department_id, to_department_id, unit_price, user_id, reason, note) " +
                "VALUES (?, ?, 'HANDOVER', datetime('now'), ?, ?, ?, 0, ?, ?, ?)";

        // Source = 'qtvt' (Warehouse) -> Status 'IN_STOCK'
        // Source = Other Dept -> Status 'IN_USE' (Usually handover is Warehouse ->
        // Dept)
        String sourceStatus = fromDept.equals("qtvt") ? "IN_STOCK" : "IN_USE";
        // Target = 'qtvt' (Return) -> 'IN_STOCK'
        // Target = Other Dept -> 'IN_USE'
        String targetStatus = toDept.equals("qtvt") ? "IN_STOCK" : "IN_USE";

        String selectSourceSql = "SELECT id, quantity FROM tool WHERE asset_id = ? AND department_id = ? AND status = ? AND quantity > 0 ORDER BY quantity ASC";
        String updateSourceSql = "UPDATE tool SET quantity = ? WHERE id = ?";

        String checkTargetSql = "SELECT id FROM tool WHERE asset_id = ? AND department_id = ? AND status = ?";
        String updateTargetSql = "UPDATE tool SET quantity = quantity + ? WHERE id = ?";
        String insertTargetSql = "INSERT INTO tool (id, asset_id, department_id, quantity, status) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement logStmt = conn.prepareStatement(logSql);
                    PreparedStatement selectStmt = conn.prepareStatement(selectSourceSql);
                    PreparedStatement updateSourceStmt = conn.prepareStatement(updateSourceSql)) {

                // 1. Log
                logStmt.setString(1, transId);
                logStmt.setString(2, assetId);
                logStmt.setInt(3, qty);
                logStmt.setString(4, fromDept);
                logStmt.setString(5, toDept);
                logStmt.setString(6, userId);
                logStmt.setString(7, reason);
                logStmt.setString(8, note);
                logStmt.executeUpdate();

                // 2. Reduce Source (Batch logic)
                selectStmt.setString(1, assetId);
                selectStmt.setString(2, fromDept);
                selectStmt.setString(3, sourceStatus);
                ResultSet rs = selectStmt.executeQuery();

                int remaining = qty;
                while (rs.next() && remaining > 0) {
                    String tId = rs.getString("id");
                    int currentQty = rs.getInt("quantity");

                    if (currentQty <= remaining) {
                        remaining -= currentQty;
                        updateSourceStmt.setInt(1, 0);
                        updateSourceStmt.setString(2, tId);
                        updateSourceStmt.executeUpdate();
                    } else {
                        updateSourceStmt.setInt(1, currentQty - remaining);
                        updateSourceStmt.setString(2, tId);
                        updateSourceStmt.executeUpdate();
                        remaining = 0;
                    }
                }
                rs.close();

                if (remaining > 0) {
                    throw new SQLException("Not enough quantity in source (Status: " + sourceStatus + ")");
                }

                // 3. Increase Target
                try (PreparedStatement checkTargetStmt = conn.prepareStatement(checkTargetSql)) {
                    checkTargetStmt.setString(1, assetId);
                    checkTargetStmt.setString(2, toDept);
                    checkTargetStmt.setString(3, targetStatus);
                    ResultSet rsTarget = checkTargetStmt.executeQuery();

                    if (rsTarget.next()) {
                        try (PreparedStatement updateTargetStmt = conn.prepareStatement(updateTargetSql)) {
                            updateTargetStmt.setInt(1, qty);
                            updateTargetStmt.setString(2, rsTarget.getString("id"));
                            updateTargetStmt.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement insertTargetStmt = conn.prepareStatement(insertTargetSql)) {
                            insertTargetStmt.setString(1, generateNextId("TOOL", "tool", "id"));
                            insertTargetStmt.setString(2, assetId);
                            insertTargetStmt.setString(3, toDept);
                            insertTargetStmt.setInt(4, qty);
                            insertTargetStmt.setString(5, targetStatus);
                            insertTargetStmt.executeUpdate();
                        }
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Handover failed: " + e.getMessage());
        }
    }

    public void transferTool(String assetId, String toDeptId, int qty, String reason, String note, String userId) {
        // Delegate to logToolHandover for consistent status logic (Source:
        // qtvt/IN_STOCK -> Target: Dept/IN_USE)
        logToolHandover(assetId, qty, "qtvt", toDeptId, note, reason, userId);
    }

    public List<Map<String, Object>> getTransactions(String assetId) {
        List<Map<String, Object>> history = new ArrayList<>();

        // 1. Determine Asset Type
        String typeSql = "SELECT asset_category FROM asset WHERE id = ?";
        String assetType = "";

        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(typeSql)) {
            pstmt.setString(1, assetId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                assetType = rs.getString("asset_category"); // "FIXED_ASSET" or "TOOL"
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return history; // Return empty on error
        }

        // 2. Select Query based on Type
        String sql;
        if ("FIXED_ASSET".equals(assetType) || "TSCD".equals(assetType)) {
            // Logic for Fixed Assets: Group by Transaction Batch (approximate by time and
            // reason)
            sql = "SELECT MIN(t.transaction_date) as transaction_date, t.transaction_type, COUNT(*) AS quantity, AVG(t.unit_price) as unit_price, t.reason, "
                    + "MAX(COALESCE(t.note, '')) AS note "
                    + "FROM fixed_asset_transaction t "
                    + "WHERE t.asset_id = ? "
                    + "GROUP BY t.transaction_type, t.reason, strftime('%Y-%m-%d %H:%M', t.transaction_date) "
                    + "ORDER BY transaction_date DESC";
        } else {
            // Logic for CCDC (Tools) - Existing Logic
            sql = "SELECT transaction_date, transaction_type, quantity, unit_price, reason, note, from_department_id, to_department_id "
                    + "FROM tool_transaction WHERE asset_id = ? ORDER BY transaction_date DESC";
        }

        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, assetId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Map<String, Object> record = new HashMap<>();
                record.put("date", rs.getString("transaction_date"));
                record.put("type", rs.getString("transaction_type"));
                record.put("qty", rs.getInt("quantity"));
                record.put("price", rs.getDouble("unit_price"));
                record.put("reason", rs.getString("reason"));
                record.put("note", rs.getString("note"));
                history.add(record);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching history: " + e.getMessage());
            e.printStackTrace();
        }
        return history;
    }

    public Map<String, Integer> getDashboardStats() {
        Map<String, Integer> stats = new HashMap<>();
        // 1. Total Stock (Sum of asset.total_quantity)
        String stockSql = "SELECT SUM(total_quantity) FROM asset";

        // 2. Handover (Sum of transactions)
        String toolHandoverSql = "SELECT SUM(quantity) FROM tool_transaction WHERE transaction_type = 'HANDOVER'";
        String tscdHandoverSql = "SELECT COUNT(*) FROM fixed_asset_transaction WHERE transaction_type = 'HANDOVER'";

        // 3. Damaged (Sum of transactions)
        String toolDamageSql = "SELECT SUM(quantity) FROM tool_transaction WHERE transaction_type = 'DAMAGE'";
        String tscdDamageSql = "SELECT COUNT(*) FROM fixed_asset_transaction WHERE transaction_type = 'DAMAGE'";

        try (Connection conn = Database.getConnection()) {

            // Stock
            try (PreparedStatement pst = conn.prepareStatement(stockSql); ResultSet rs = pst.executeQuery()) {
                if (rs.next())
                    stats.put("totalStock", rs.getInt(1));
            }

            // Handover
            int totalHandover = 0;
            try (PreparedStatement pst = conn.prepareStatement(toolHandoverSql); ResultSet rs = pst.executeQuery()) {
                if (rs.next())
                    totalHandover += rs.getInt(1);
            }
            try (PreparedStatement pst = conn.prepareStatement(tscdHandoverSql); ResultSet rs = pst.executeQuery()) {
                if (rs.next())
                    totalHandover += rs.getInt(1);
            }
            stats.put("totalHandover", totalHandover);

            // Damage
            int totalDamage = 0;
            try (PreparedStatement pst = conn.prepareStatement(toolDamageSql); ResultSet rs = pst.executeQuery()) {
                if (rs.next())
                    totalDamage += rs.getInt(1);
            }
            try (PreparedStatement pst = conn.prepareStatement(tscdDamageSql); ResultSet rs = pst.executeQuery()) {
                if (rs.next())
                    totalDamage += rs.getInt(1);
            }
            stats.put("totalDamage", totalDamage);

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return stats;
    }

    public List<Map<String, String>> getDepartments() {
        List<Map<String, String>> departments = new ArrayList<>();
        String sql = "SELECT id, name FROM department WHERE id != 'qtvt'";

        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql);
                ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Map<String, String> dept = new HashMap<>();
                dept.put("id", rs.getString("id"));
                dept.put("name", rs.getString("name"));
                departments.add(dept);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching departments: " + e.getMessage());
            e.printStackTrace();
        }
        return departments;
    }

    public List<Asset> getAssetsByDepartment(String deptId) {
        // Deprecated or keep for backward compatibility?
        // For now, redirect to paged with large limit or keep existing logic.
        // Let's keep existing logic to avoid breaking other things if any.
        List<Asset> assets = new ArrayList<>();
        // 1. Get Tools (Aggregated)
        String toolSql = "SELECT t.asset_id, a.name, a.asset_category, a.base_unit, SUM(t.quantity) as quantity, a.manufacturer "
                +
                "FROM tool t " +
                "JOIN asset a ON t.asset_id = a.id " +
                "WHERE t.department_id = ? AND t.quantity > 0 " +
                "GROUP BY t.asset_id, a.name, a.asset_category, a.base_unit, a.manufacturer";

        // 2. Get Fixed Assets
        String fixedSql = "SELECT f.asset_id, a.name, a.asset_category, a.base_unit, COUNT(f.id) as quantity, a.manufacturer "
                +
                "FROM fixed_asset_item f " +
                "JOIN asset a ON f.asset_id = a.id " +
                "WHERE f.department_id = ? AND f.status = 'IN_USE' " +
                "GROUP BY f.asset_id, a.name";

        try (Connection conn = Database.getConnection()) {
            // Tools
            try (PreparedStatement admin = conn.prepareStatement(toolSql)) {
                admin.setString(1, deptId);
                ResultSet rs = admin.executeQuery();
                while (rs.next()) {
                    assets.add(new Asset(
                            rs.getString("asset_id"),
                            rs.getString("name"),
                            rs.getString("asset_category"),
                            rs.getString("base_unit"),
                            0, // Total Stock irrelevant here
                            true, // isDistributed
                            rs.getInt("quantity"), // Current quantity in this dept
                            rs.getString("manufacturer")));
                }
            }

            // Fixed Assets
            try (PreparedStatement admin = conn.prepareStatement(fixedSql)) {
                admin.setString(1, deptId);
                ResultSet rs = admin.executeQuery();
                while (rs.next()) {
                    assets.add(new Asset(
                            rs.getString("asset_id"),
                            rs.getString("name"),
                            rs.getString("asset_category"),
                            rs.getString("base_unit"),
                            0,
                            true,
                            rs.getInt("quantity"),
                            rs.getString("manufacturer")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching assets by department: " + e.getMessage());
            e.printStackTrace();
        }
        return assets;
    }

    public List<Asset> getAssetsByDepartmentPaged(String deptId, int limit, int offset, String search) {
        List<Asset> assets = new ArrayList<>();
        String searchQuery = (search == null) ? "" : search.trim();
        boolean hasSearch = !searchQuery.isEmpty();

        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT * FROM (");

        // Tool Subquery
        sqlBuilder.append(
                "SELECT t.asset_id, a.name, a.asset_category, a.base_unit, SUM(t.quantity) as quantity, a.manufacturer ");
        sqlBuilder.append("FROM tool t JOIN asset a ON t.asset_id = a.id ");
        sqlBuilder.append("WHERE t.department_id = ? AND t.quantity > 0 ");
        if (hasSearch)
            sqlBuilder.append("AND (a.name LIKE ? OR t.asset_id LIKE ?) ");
        sqlBuilder.append("GROUP BY t.asset_id, a.name, a.asset_category, a.base_unit, a.manufacturer ");

        sqlBuilder.append("UNION ALL ");

        // Fixed Asset Subquery
        sqlBuilder.append(
                "SELECT f.asset_id, a.name, a.asset_category, a.base_unit, COUNT(f.id) as quantity, a.manufacturer ");
        sqlBuilder.append("FROM fixed_asset_item f JOIN asset a ON f.asset_id = a.id ");
        sqlBuilder.append("WHERE f.department_id = ? AND f.status = 'IN_USE' ");
        if (hasSearch)
            sqlBuilder.append("AND (a.name LIKE ? OR f.asset_id LIKE ?) ");
        sqlBuilder.append("GROUP BY f.asset_id, a.name, a.asset_category, a.base_unit, a.manufacturer ");

        sqlBuilder.append(") AS combined ");
        sqlBuilder.append("ORDER BY name LIMIT ? OFFSET ?");

        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sqlBuilder.toString())) {

            int paramIndex = 1;

            // Tool params
            pstmt.setString(paramIndex++, deptId);
            if (hasSearch) {
                pstmt.setString(paramIndex++, "%" + searchQuery + "%");
                pstmt.setString(paramIndex++, "%" + searchQuery + "%");
            }

            // Fixed Asset params
            pstmt.setString(paramIndex++, deptId);
            if (hasSearch) {
                pstmt.setString(paramIndex++, "%" + searchQuery + "%");
                pstmt.setString(paramIndex++, "%" + searchQuery + "%");
            }

            // Pagination
            pstmt.setInt(paramIndex++, limit);
            pstmt.setInt(paramIndex++, offset);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                assets.add(new Asset(
                        rs.getString("asset_id"),
                        rs.getString("name"),
                        rs.getString("asset_category"),
                        rs.getString("base_unit"),
                        0,
                        true,
                        rs.getInt("quantity"),
                        rs.getString("manufacturer")));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching assets paged: " + e.getMessage());
            e.printStackTrace();
        }
        return assets;
    }

    public int countAssetsByDepartment(String deptId, String search) {
        String searchQuery = (search == null) ? "" : search.trim();
        boolean hasSearch = !searchQuery.isEmpty();

        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT COUNT(*) FROM (");

        // Tool Subquery
        sqlBuilder.append("SELECT t.asset_id ");
        sqlBuilder.append("FROM tool t JOIN asset a ON t.asset_id = a.id ");
        sqlBuilder.append("WHERE t.department_id = ? AND t.quantity > 0 ");
        if (hasSearch)
            sqlBuilder.append("AND (a.name LIKE ? OR t.asset_id LIKE ?) ");
        sqlBuilder.append("GROUP BY t.asset_id "); // Only need to group to distinct assets

        sqlBuilder.append("UNION ALL ");

        // Fixed Asset Subquery
        sqlBuilder.append("SELECT f.asset_id ");
        sqlBuilder.append("FROM fixed_asset_item f JOIN asset a ON f.asset_id = a.id ");
        sqlBuilder.append("WHERE f.department_id = ? AND f.status = 'IN_USE' ");
        if (hasSearch)
            sqlBuilder.append("AND (a.name LIKE ? OR f.asset_id LIKE ?) ");
        sqlBuilder.append("GROUP BY f.asset_id ");

        sqlBuilder.append(") AS combined");

        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sqlBuilder.toString())) {

            int paramIndex = 1;

            // Tool params
            pstmt.setString(paramIndex++, deptId);
            if (hasSearch) {
                pstmt.setString(paramIndex++, "%" + searchQuery + "%");
                pstmt.setString(paramIndex++, "%" + searchQuery + "%");
            }

            // Fixed Asset params
            pstmt.setString(paramIndex++, deptId);
            if (hasSearch) {
                pstmt.setString(paramIndex++, "%" + searchQuery + "%");
                pstmt.setString(paramIndex++, "%" + searchQuery + "%");
            }

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Error counting assets: " + e.getMessage());
            e.printStackTrace();
        }
        return 0;
    }

    public void reportToolDamage(String assetId, String deptId, int qty, String reason, String note, String userId) {
        String transId = generateTransactionId();

        // New Logic: Transfer damaged tools from 'deptId' to 'qtvt' (Warehouse)
        // 1. Transaction Log: DAMAGE (from dept -> qtvt)
        String logSql = "INSERT INTO tool_transaction (id, asset_id, transaction_type, transaction_date, quantity, " +
                "from_department_id, to_department_id, unit_price, user_id, reason, note) " +
                "VALUES (?, ?, 'DAMAGE', datetime('now'), ?, ?, 'qtvt', 0, ?, ?, ?)";

        // 2. Reduce Tool Quantity in Source Dept (from IN_USE)
        // Source is usually a Department, so status is IN_USE.
        // If reporting damage from Warehouse (qtvt), status is IN_STOCK?
        // Let's assume most reports come from departments using tools (IN_USE).
        // If deptId == 'qtvt', consume IN_STOCK.
        String sourceStatus = deptId.equals("qtvt") ? "IN_STOCK" : "IN_USE";
        String selectToolsSql = "SELECT id, quantity FROM tool WHERE asset_id = ? AND department_id = ? AND status = ? AND quantity > 0 ORDER BY quantity ASC";
        String updateToolSql = "UPDATE tool SET quantity = ? WHERE id = ?";

        // 3. Increase Tool Quantity in Target Dept (qtvt) - Status DAMAGED
        String checkTargetSql = "SELECT id FROM tool WHERE asset_id = ? AND department_id = 'qtvt' AND status = 'DAMAGED'";
        String updateTargetSql = "UPDATE tool SET quantity = quantity + ? WHERE id = ?";
        String insertTargetSql = "INSERT INTO tool(id, asset_id, department_id, quantity, status) VALUES(?, ?, 'qtvt', ?, 'DAMAGED')";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement logStmt = conn.prepareStatement(logSql);
                    PreparedStatement selectStmt = conn.prepareStatement(selectToolsSql);
                    PreparedStatement updateStmt = conn.prepareStatement(updateToolSql)) {

                // 1. Log
                logStmt.setString(1, transId);
                logStmt.setString(2, assetId);
                logStmt.setInt(3, qty);
                logStmt.setString(4, deptId);
                logStmt.setString(5, userId);
                logStmt.setString(6, reason);
                logStmt.setString(7, note);
                logStmt.executeUpdate();

                // 2. Decrease Tool Quantity in Source (IN_USE)
                selectStmt.setString(1, assetId);
                selectStmt.setString(2, deptId);
                selectStmt.setString(3, sourceStatus);
                ResultSet rs = selectStmt.executeQuery();

                int remaining = qty;
                while (rs.next() && remaining > 0) {
                    String tId = rs.getString("id");
                    int currentQty = rs.getInt("quantity");

                    if (currentQty <= remaining) {
                        // Consume entire batch
                        remaining -= currentQty;
                        updateStmt.setInt(1, 0); // Set to 0
                        updateStmt.setString(2, tId);
                        updateStmt.executeUpdate();
                    } else {
                        // Partial consume
                        updateStmt.setInt(1, currentQty - remaining);
                        updateStmt.setString(2, tId);
                        updateStmt.executeUpdate();
                        remaining = 0;
                    }
                }
                rs.close();

                if (remaining > 0) {
                    throw new SQLException("Số lượng công cụ không đủ để báo hỏng (thiếu " + remaining + ").");
                }

                // 3. Increase Tool Quantity in Warehouse (qtvt) - DAMAGED
                try (PreparedStatement checkTargetStmt = conn.prepareStatement(checkTargetSql)) {
                    checkTargetStmt.setString(1, assetId);
                    ResultSet rsTarget = checkTargetStmt.executeQuery();
                    if (rsTarget.next()) {
                        try (PreparedStatement updateTargetStmt = conn.prepareStatement(updateTargetSql)) {
                            updateTargetStmt.setInt(1, qty);
                            updateTargetStmt.setString(2, rsTarget.getString("id"));
                            updateTargetStmt.executeUpdate();
                        }
                    } else {
                        String newToolId = generateNextId("TOOL", "tool", "id");
                        try (PreparedStatement insertTargetStmt = conn.prepareStatement(insertTargetSql)) {
                            insertTargetStmt.setString(1, newToolId);
                            insertTargetStmt.setString(2, assetId);
                            insertTargetStmt.setInt(3, qty);
                            insertTargetStmt.executeUpdate();
                        }
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Error reporting tool damage: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Lỗi báo hỏng công cụ: " + e.getMessage());
        }
    }

    public void reportFixedAssetDamage(String assetId, String deptId, List<String> identifiers, String reason,
            String note, String userId) {

        // 1. Transaction Log: DAMAGE (from dept -> qtvt)
        String logSql = "INSERT INTO fixed_asset_transaction (id, asset_id, transaction_type, transaction_date, "
                +
                "from_department_id, to_department_id, unit_price, user_id, reason, note, fixed_asset_item_id) " +
                "VALUES (?, ?, 'DAMAGE', datetime('now'), ?, 'qtvt', 0, ?, ?, ?, ?)";

        // 2. Update Fixed Asset Item: Move to qtvt, Status DAMAGED
        String findItemSql = "SELECT id, department_id, serial FROM fixed_asset_item WHERE asset_id = ? AND (id = ? OR serial = ?)";
        String updateItemSql = "UPDATE fixed_asset_item SET department_id = 'qtvt', status = 'DAMAGED' WHERE id = ?";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement logStmt = conn.prepareStatement(logSql);
                    PreparedStatement findStmt = conn.prepareStatement(findItemSql);
                    PreparedStatement updateStmt = conn.prepareStatement(updateItemSql)) {

                for (String ident : identifiers) {
                    // VALIDATION: Check if item exists AND belongs to the correct department
                    findStmt.setString(1, assetId);
                    findStmt.setString(2, ident);
                    findStmt.setString(3, ident);
                    ResultSet rs = findStmt.executeQuery();

                    String itemId = null;
                    String currentDept = null;
                    String serial = null;

                    if (rs.next()) {
                        itemId = rs.getString("id");
                        currentDept = rs.getString("department_id");
                        serial = rs.getString("serial");
                    }
                    rs.close();

                    if (itemId == null) {
                        throw new IllegalArgumentException("Không tìm thấy tài sản với ID/Serial: " + ident);
                    }

                    if (currentDept == null || !currentDept.equals(deptId)) {
                        throw new IllegalArgumentException("Tài sản " + serial + " (" + ident
                                + ") không thuộc phòng ban này (Đang ở: " + (currentDept == null ? "Kho" : currentDept)
                                + ").");
                    }

                    // Log Transaction
                    logStmt.setString(1, generateFixedAssetTransactionId());
                    logStmt.setString(2, assetId);
                    logStmt.setString(3, deptId);
                    logStmt.setString(4, userId);
                    logStmt.setString(5, reason);
                    logStmt.setString(6, note);
                    logStmt.setString(7, itemId);
                    logStmt.executeUpdate();

                    // Update Item
                    updateStmt.setString(1, itemId);
                    updateStmt.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Error reporting fixed asset damage: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Lỗi báo hỏng tài sản cố định: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getLiquidationItems(String assetId, String deptId, String status) {
        System.out.println("DEBUG: getLiquidationItems called with assetId=" + assetId + ", deptId=" + deptId
                + ", status=" + status);
        List<Map<String, Object>> items = new ArrayList<>();
        // Fetch items: Filter by Status if provided
        // Base Query
        String sql = "SELECT id, serial, status, department_id FROM fixed_asset_item " +
                "WHERE asset_id = ? AND (department_id = ? OR (? = 'qtvt' AND department_id IS NULL)) ";

        // Append Status Filter
        if (status != null && !status.isEmpty()) {
            sql += "AND status = ? ";
        } else {
            sql += "AND status IN ('IN_STOCK', 'DAMAGED', 'BROKEN') ";
        }

        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            int paramIdx = 1;
            pstmt.setString(paramIdx++, assetId);
            pstmt.setString(paramIdx++, deptId);
            pstmt.setString(paramIdx++, deptId);

            if (status != null && !status.isEmpty()) {
                pstmt.setString(paramIdx++, status);
            }

            // System.out.println("DEBUG: Executing Query: " + sql);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", rs.getString("id"));
                item.put("serial", rs.getString("serial"));
                item.put("status", rs.getString("status"));
                items.add(item);
            }
            System.out.println("DEBUG: Total items found: " + items.size());
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    public List<Map<String, Object>> getLiquidationAssets(String deptId) {
        List<Map<String, Object>> assets = new ArrayList<>();
        // 1. Tools (Group by Asset + Status)
        String toolSql = "SELECT t.asset_id, a.name, a.asset_category, a.base_unit, COALESCE(t.status, 'IN_STOCK') as status, SUM(t.quantity) as quantity, a.manufacturer "
                + "FROM tool t "
                + "JOIN asset a ON t.asset_id = a.id "
                + "WHERE t.department_id = ? AND t.quantity > 0 "
                + "GROUP BY t.asset_id, a.name, a.asset_category, a.base_unit, a.manufacturer, COALESCE(t.status, 'IN_STOCK')";

        // 2. Fixed Assets (Group by Asset + Status)
        String fixedSql = "SELECT f.asset_id, a.name, a.asset_category, a.base_unit, f.status, COUNT(f.id) as quantity, a.manufacturer "
                + "FROM fixed_asset_item f "
                + "JOIN asset a ON f.asset_id = a.id "
                + "WHERE (f.department_id = ? OR (? = 'qtvt' AND f.department_id IS NULL)) "
                + "AND f.status IN ('IN_STOCK', 'DAMAGED', 'BROKEN') "
                + "GROUP BY f.asset_id, a.name, a.asset_category, a.base_unit, a.manufacturer, f.status";

        try (Connection conn = Database.getConnection()) {
            // Tools
            try (PreparedStatement start = conn.prepareStatement(toolSql)) {
                start.setString(1, deptId);
                ResultSet rs = start.executeQuery();
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", rs.getString("asset_id"));
                    map.put("name", rs.getString("name"));
                    map.put("category", rs.getString("asset_category"));
                    map.put("unit", rs.getString("base_unit"));
                    map.put("status", rs.getString("status"));
                    map.put("quantity", rs.getInt("quantity"));
                    assets.add(map);
                }
            }
            // Fixed Assets
            try (PreparedStatement start = conn.prepareStatement(fixedSql)) {
                start.setString(1, deptId);
                start.setString(2, deptId);
                ResultSet rs = start.executeQuery();
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", rs.getString("asset_id"));
                    map.put("name", rs.getString("name"));
                    map.put("category", rs.getString("asset_category"));
                    map.put("unit", rs.getString("base_unit"));
                    map.put("status", rs.getString("status")); // Already set in DB for Fixed Assets
                    map.put("quantity", rs.getInt("quantity"));
                    assets.add(map);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return assets;
    }

    public void liquidateTool(String assetId, int qty, double price, String reason, String note, String userId) {
        String transId = generateTransactionId();
        String logSql = "INSERT INTO tool_transaction (id, asset_id, transaction_type, transaction_date, quantity, " +
                "from_department_id, to_department_id, unit_price, user_id, reason, note) " +
                "VALUES (?, ?, 'DISPOSAL', datetime('now'), ?, 'qtvt', NULL, ?, ?, ?, ?)"; // From qtvt to NULL

        String updateAssetSql = "UPDATE asset SET total_quantity = total_quantity - ? WHERE id = ?";

        // Allow liquidating from IN_STOCK, DAMAGED, or NULL
        String selectToolsSql = "SELECT id, quantity FROM tool WHERE asset_id = ? AND department_id = 'qtvt' " +
                "AND (status = 'IN_STOCK' OR status = 'DAMAGED' OR status IS NULL) " +
                "AND quantity > 0 ORDER BY quantity ASC";
        String updateToolSql = "UPDATE tool SET quantity = ? WHERE id = ?";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement logStmt = conn.prepareStatement(logSql);
                    PreparedStatement assetStmt = conn.prepareStatement(updateAssetSql);
                    PreparedStatement selectStmt = conn.prepareStatement(selectToolsSql);
                    PreparedStatement updateStmt = conn.prepareStatement(updateToolSql)) {

                // 1. Log Transaction
                logStmt.setString(1, transId);
                logStmt.setString(2, assetId);
                logStmt.setInt(3, qty);
                logStmt.setDouble(4, price);
                logStmt.setString(5, userId);
                logStmt.setString(6, reason);
                logStmt.setString(7, note);
                logStmt.executeUpdate();

                // 2. Reduce Master Quantity
                assetStmt.setInt(1, qty);
                assetStmt.setString(2, assetId);
                assetStmt.executeUpdate();

                // 3. Reduce Stock in Warehouse (qtvt)
                selectStmt.setString(1, assetId);
                ResultSet rs = selectStmt.executeQuery();

                int remaining = qty;
                while (rs.next() && remaining > 0) {
                    String tId = rs.getString("id");
                    int currentQty = rs.getInt("quantity");

                    if (currentQty <= remaining) {
                        remaining -= currentQty;
                        updateStmt.setInt(1, 0); // Deplete row
                        updateStmt.setString(2, tId);
                        updateStmt.executeUpdate();
                    } else {
                        updateStmt.setInt(1, currentQty - remaining);
                        updateStmt.setString(2, tId);
                        updateStmt.executeUpdate();
                        remaining = 0;
                    }
                }
                rs.close();

                if (remaining > 0) {
                    throw new SQLException("Không đủ số lượng trong kho để thanh lý (thiếu " + remaining + ").");
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Lỗi thanh lý công cụ: " + e.getMessage());
        }
    }

    public void liquidateFixedAsset(String assetId, List<String> identifiers, double price, String reason, String note,
            String userId) {
        String logSql = "INSERT INTO fixed_asset_transaction (id, asset_id, transaction_type, transaction_date, "
                + "from_department_id, to_department_id, unit_price, user_id, reason, note, fixed_asset_item_id) " +
                "VALUES (?, ?, 'DISPOSAL', datetime('now'), 'qtvt', NULL, ?, ?, ?, ?, ?)"; // From qtvt to NULL

        String findItemSql = "SELECT id, department_id, serial FROM fixed_asset_item WHERE asset_id = ? AND (id = ? OR serial = ?)";
        String updateItemSql = "UPDATE fixed_asset_item SET status = 'LIQUIDATED' WHERE id = ?";
        String updateAssetSql = "UPDATE asset SET total_quantity = total_quantity - 1 WHERE id = ?";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement logStmt = conn.prepareStatement(logSql);
                    PreparedStatement findStmt = conn.prepareStatement(findItemSql);
                    PreparedStatement updateStmt = conn.prepareStatement(updateItemSql);
                    PreparedStatement assetStmt = conn.prepareStatement(updateAssetSql)) {

                for (String ident : identifiers) {
                    findStmt.setString(1, assetId);
                    findStmt.setString(2, ident);
                    findStmt.setString(3, ident);
                    ResultSet rs = findStmt.executeQuery();

                    String itemId = null;
                    String currentDept = null;
                    String serial = null;

                    if (rs.next()) {
                        itemId = rs.getString("id");
                        currentDept = rs.getString("department_id");
                        serial = rs.getString("serial");
                    }
                    rs.close();

                    if (itemId == null) {
                        throw new IllegalArgumentException("Không tìm thấy tài sản (TSCD) với ID/Serial: " + ident);
                    }
                    // Optional: Ensure it's in qtvt (Warehouse) before liquidating?
                    // Currently assume users select logical items (which filter query provides).
                    // If strict: verify currentDept == 'qtvt'.

                    // 1. Log Transaction
                    logStmt.setString(1, generateFixedAssetTransactionId());
                    logStmt.setString(2, assetId);
                    logStmt.setDouble(3, price);
                    logStmt.setString(4, userId);
                    logStmt.setString(5, reason);
                    logStmt.setString(6, note);
                    logStmt.setString(7, itemId);
                    logStmt.executeUpdate();

                    // 2. Update Item Status
                    updateStmt.setString(1, itemId);
                    updateStmt.executeUpdate();

                    // 3. Reduce Master Quantity
                    assetStmt.setString(1, assetId);
                    assetStmt.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Lỗi thanh lý tài sản cố định: " + e.getMessage());
        }
    }

    // Removed dead code (adjustToolStock, logAnalysisNote)
}
