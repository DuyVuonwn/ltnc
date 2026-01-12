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

    public void updateToolQuantityAfterStocktake(String assetId, int actualQty) {
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
                "  WHEN a.asset_category = 'TOOL' THEN (SELECT COALESCE(SUM(quantity), 0) FROM tool WHERE asset_id = a.id AND department_id = 'qtvt') "
                +
                "  ELSE (SELECT COUNT(*) FROM fixed_asset_item WHERE asset_id = a.id AND (department_id = 'qtvt' OR department_id IS NULL)) "
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
        String sql = "SELECT id, name, asset_category, base_unit, total_quantity, manufacturer FROM asset WHERE name LIKE ? LIMIT 10";

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
                        0,
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
        String sql = "SELECT " + idColumn + " FROM " + table + " ORDER BY " + idColumn + " DESC LIMIT 1";
        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql);
                ResultSet rs = pstmt.executeQuery()) {

            if (rs.next()) {
                String lastId = rs.getString(1);
                if (lastId.startsWith(prefix)) {
                    String numPart = lastId.substring(prefix.length() + 1);
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
        return generateId("fixed_asset_transaction", "transaction_id", "FAT-");
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

        try (Connection conn = Database.getConnection();
                PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {

            checkStmt.setString(1, asset.getName());
            checkStmt.setString(2, asset.getManufacturer());
            checkStmt.setString(3, asset.getManufacturer()); // For NULL check
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                String existingId = rs.getString("id");
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
            String note) {
        String userId = "nv001";
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

    public void transferFixedAsset(String assetId, String itemId, String toDept, String reason, String note) {
        String userId = "nv001";
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

    public void upsertTool(String assetId, String deptId, int addQty) {
        String targetDept = "qtvt";

        String checkSql = "SELECT id, quantity FROM tool WHERE asset_id = ? AND department_id = ?";
        String updateSql = "UPDATE tool SET quantity = quantity + ? WHERE id = ?";
        String insertSql = "INSERT INTO tool(id, asset_id, department_id, quantity) VALUES(?, ?, ?, ?)";

        try (Connection conn = Database.getConnection();
                PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {

            checkStmt.setString(1, assetId);
            checkStmt.setString(2, targetDept);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                String id = rs.getString("id");
                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setInt(1, addQty);
                    updateStmt.setString(2, id);
                    updateStmt.executeUpdate();
                }
            } else {
                String newId = generateNextId("TOOL", "tool", "id");
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setString(1, newId);
                    insertStmt.setString(2, assetId);
                    insertStmt.setString(3, targetDept);
                    insertStmt.setInt(4, addQty);
                    insertStmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.err.println("Error upserting tool: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void logTransaction(String assetId, String type, int qty, double price, String note, String reason) {
        if ("IMPORT".equals(type)) {
            String userId = "nv001";
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
            logToolHandover(assetId, qty, "qtvt", null, note, reason);
        }
    }

    public void logToolHandover(String assetId, int qty, String fromDept, String toDept, String note, String reason) {
        String userId = "nv001"; // Hardcoded user for now
        String transId = generateTransactionId();

        String sql = "INSERT INTO tool_transaction (id, asset_id, transaction_type, transaction_date, quantity, " +
                "from_department_id, to_department_id, unit_price, user_id, reason, note) " +
                "VALUES (?, ?, 'HANDOVER', datetime('now'), ?, ?, ?, 0, ?, ?, ?)"; // Price 0 for handover? Or fetch?
                                                                                   // Assuming 0 or handled elsewhere.

        // Update Stock Logic:
        // Handover implies moving FROM one dept TO another.
        // If fromDept is 'qtvt' (Warehouse), we decrement `asset.total_quantity`
        // (Available Stock).
        // If we tracked per-department stock in `tool` table, we'd update that too.
        // For now, simpler logic: Decrement Global Stock in `asset` table.
        String updateStockSql = "UPDATE asset SET total_quantity = total_quantity - ? WHERE id = ?";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false); // Transaction start

            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                    PreparedStatement updateStmt = conn.prepareStatement(updateStockSql)) {

                // 1. Log Transaction
                pstmt.setString(1, transId);
                pstmt.setString(2, assetId);
                pstmt.setInt(3, qty);
                pstmt.setString(4, fromDept);
                pstmt.setString(5, toDept);
                pstmt.setString(6, userId);
                pstmt.setString(7, reason); // e.g. "Decision No..."
                pstmt.setString(8, note);
                pstmt.executeUpdate();

                // 2. Decrement Stock
                updateStmt.setInt(1, qty);
                updateStmt.setString(2, assetId);
                int rows = updateStmt.executeUpdate();

                if (rows == 0) {
                    throw new SQLException("Asset ID not found for stock update: " + assetId);
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e; // Re-throw to handle in controller
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Error processing tool handover: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Database error: " + e.getMessage());
        }
    }

    public void transferTool(String assetId, String toDeptId, int qty, String reason, String note) {
        String userId = "nv001";
        String fromDept = "qtvt";
        String transId = generateTransactionId();

        String insertTransSql = "INSERT INTO tool_transaction (id, asset_id, transaction_type, transaction_date, quantity, "
                +
                "from_department_id, to_department_id, unit_price, user_id, reason, note) " +
                "VALUES (?, ?, 'HANDOVER', datetime('now'), ?, ?, ?, 0, ?, ?, ?)";

        String updateSourceToolSql = "UPDATE tool SET quantity = quantity - ? WHERE asset_id = ? AND department_id = ?";
        // String updateSourceAssetSql = "UPDATE asset SET total_quantity =
        // total_quantity - ? WHERE id = ?"; // REMOVED: Keep Total Global

        String checkDestToolSql = "SELECT id FROM tool WHERE asset_id = ? AND department_id = ?";
        String updateDestToolSql = "UPDATE tool SET quantity = quantity + ? WHERE id = ?";
        String insertDestToolSql = "INSERT INTO tool(id, asset_id, department_id, quantity) VALUES(?, ?, ?, ?)";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement transStmt = conn.prepareStatement(insertTransSql);
                    PreparedStatement decToolStmt = conn.prepareStatement(updateSourceToolSql);
                    // PreparedStatement decAssetStmt = conn.prepareStatement(updateSourceAssetSql);
                    PreparedStatement checkDestStmt = conn.prepareStatement(checkDestToolSql)) {

                // 1. Log Transaction
                transStmt.setString(1, transId);
                transStmt.setString(2, assetId);
                transStmt.setInt(3, qty);
                transStmt.setString(4, fromDept);
                transStmt.setString(5, toDeptId);
                transStmt.setString(6, userId);
                transStmt.setString(7, reason);
                transStmt.setString(8, note);
                transStmt.executeUpdate();

                // 2. Decrement Source (Warehouse)
                // 2a. Decrement tool record for QTVT (if tracked per dept)
                decToolStmt.setInt(1, qty);
                decToolStmt.setString(2, assetId);
                decToolStmt.setString(3, fromDept);
                decToolStmt.executeUpdate();

                // 2b. Decrement Global Asset Stock -> REMOVED

                // 3. Increment Destination
                checkDestStmt.setString(1, assetId);
                checkDestStmt.setString(2, toDeptId);
                ResultSet rs = checkDestStmt.executeQuery();

                if (rs.next()) {
                    // Update existing
                    String destToolId = rs.getString("id");
                    try (PreparedStatement updateDestStmt = conn.prepareStatement(updateDestToolSql)) {
                        updateDestStmt.setInt(1, qty);
                        updateDestStmt.setString(2, destToolId);
                        updateDestStmt.executeUpdate();
                    }
                } else {
                    // Insert new
                    String newId = generateNextId("TOOL", "tool", "id");
                    try (PreparedStatement insertDestStmt = conn.prepareStatement(insertDestToolSql)) {
                        insertDestStmt.setString(1, newId);
                        insertDestStmt.setString(2, assetId);
                        insertDestStmt.setString(3, toDeptId);
                        insertDestStmt.setInt(4, qty);
                        insertDestStmt.executeUpdate();
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
            System.err.println("Error transferring tool: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error transferring tool: " + e.getMessage());
        }
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

    public void reportToolDamage(String assetId, String deptId, int qty, String reason, String note) {
        String userId = "nv001";
        String transId = generateTransactionId();

        // 1. Transaction Log: DAMAGE
        String logSql = "INSERT INTO tool_transaction (id, asset_id, transaction_type, transaction_date, quantity, " +
                "from_department_id, to_department_id, unit_price, user_id, reason, note) " +
                "VALUES (?, ?, 'DAMAGE', datetime('now'), ?, ?, 'qtvt', 0, ?, ?, ?)";

        // 2. Reduce Global Asset Quantity
        String updateAssetSql = "UPDATE asset SET total_quantity = total_quantity - ? WHERE id = ?";

        // 3. Select Tool Batches/Lots
        String selectToolsSql = "SELECT id, quantity FROM tool WHERE asset_id = ? AND department_id = ? AND quantity > 0 ORDER BY quantity ASC";
        String updateToolSql = "UPDATE tool SET quantity = ? WHERE id = ?";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement logStmt = conn.prepareStatement(logSql);
                    PreparedStatement assetStmt = conn.prepareStatement(updateAssetSql);
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

                // 2. Update Asset
                assetStmt.setInt(1, qty);
                assetStmt.setString(2, assetId);
                assetStmt.executeUpdate();

                // 3. Decrease Tool Quantity (across multiple batches if needed)
                selectStmt.setString(1, assetId);
                selectStmt.setString(2, deptId);
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
                    throw new SQLException("Available tool quantity is insufficient for the requested amount ("
                            + remaining + " missing).");
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
            throw new RuntimeException("Error reporting damage: " + e.getMessage());
        }
    }

    public void reportFixedAssetDamage(String assetId, String deptId, List<String> identifiers, String reason,
            String note) {
        String userId = "nv001";

        // 1. Transaction Log: DAMAGE
        String logSql = "INSERT INTO fixed_asset_transaction (transaction_id, asset_id, transaction_type, transaction_date, quantity, "
                +
                "from_department_id, to_department_id, unit_price, user_id, reason, note, fixed_asset_item_id) " +
                "VALUES (?, ?, 'DAMAGE', datetime('now'), 1, ?, 'qtvt', 0, ?, ?, ?, ?)";

        // 2. Update Fixed Asset Item Status
        // Try ID first, then Serial Number. Scope to Asset and Department.
        String findItemSql = "SELECT id FROM fixed_asset_item WHERE asset_id = ? AND department_id = ? AND (id = ? OR serial_number = ?)";
        String updateItemSql = "UPDATE fixed_asset_item SET status = 'DAMAGED' WHERE id = ?";

        // 3. Reduce Global Asset Quantity
        // Actually, for fixed assets, total_quantity is usually count of items.
        // If it's damaged but exists, do we reduce total_quantity?
        // Usually NO, unless liquidated. If status is DAMAGED, it's still an asset,
        // just broken.
        // However, for TOOL, we reduced total_quantity because TOOL doesn't track
        // status individually well in 'asset' table sum?
        // Wait, for TOOL, user requested "Reduce asset.total_quantity".
        // For TSCD, user requested "Update fixed_asset_item: status = 'DAMAGED'".
        // User did NOT ask to reduce total_quantity for TSCD.
        // So I will ONLY update status and log transaction.

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement logStmt = conn.prepareStatement(logSql);
                    PreparedStatement findStmt = conn.prepareStatement(findItemSql);
                    PreparedStatement updateStmt = conn.prepareStatement(updateItemSql)) {

                for (String ident : identifiers) {
                    // Find Item ID
                    findStmt.setString(1, assetId);
                    findStmt.setString(2, deptId);
                    findStmt.setString(3, ident);
                    findStmt.setString(4, ident);
                    ResultSet rs = findStmt.executeQuery();

                    String itemId = null;
                    if (rs.next()) {
                        itemId = rs.getString("id");
                    }
                    rs.close();

                    if (itemId != null) {
                        // Log Transaction with new ID for each item
                        logStmt.setString(1, generateFixedAssetTransactionId());
                        logStmt.setString(2, assetId);
                        logStmt.setString(3, deptId);
                        logStmt.setString(4, userId);
                        logStmt.setString(5, reason);
                        logStmt.setString(6, note);
                        logStmt.setString(7, itemId);
                        logStmt.executeUpdate();

                        // Update Status
                        updateStmt.setString(1, itemId);
                        updateStmt.executeUpdate();
                    } else {
                        throw new SQLException("Fixed Asset Item not found with ID/Serial: " + ident);
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
            System.err.println("Error reporting fixed asset damage: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error reporting damage: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getLiquidationItems(String assetId, String deptId) {
        System.out.println("DEBUG: getLiquidationItems called with assetId=" + assetId + ", deptId=" + deptId);
        List<Map<String, Object>> items = new ArrayList<>();
        // Fetch items: IN_STOCK, DAMAGED, BROKEN.
        // For qtvt, include NULL department_id.
        String sql = "SELECT id, serial, status, department_id FROM fixed_asset_item " +
                "WHERE asset_id = ? AND (department_id = ? OR (? = 'qtvt' AND department_id IS NULL)) " +
                "AND status IN ('IN_STOCK', 'DAMAGED', 'BROKEN')";

        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, assetId);
            pstmt.setString(2, deptId);
            pstmt.setString(3, deptId);

            // System.out.println("DEBUG: Executing Query: " + sql);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                System.out.println("DEBUG: Found Item: " + rs.getString("id") + " Status: " + rs.getString("status")
                        + " Dept: " + rs.getString("department_id"));
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

    public List<Asset> getLiquidationAssets(String deptId) {
        List<Asset> assets = new ArrayList<>();
        // 1. Tools
        String toolSql = "SELECT t.asset_id, a.name, a.asset_category, a.base_unit, SUM(t.quantity) as quantity, a.manufacturer "
                + "FROM tool t "
                + "JOIN asset a ON t.asset_id = a.id "
                + "WHERE t.department_id = ? AND t.quantity > 0 "
                + "GROUP BY t.asset_id, a.name, a.asset_category, a.base_unit, a.manufacturer";

        // 2. Fixed Assets (Include IN_STOCK, DAMAGED, BROKEN)
        String fixedSql = "SELECT f.asset_id, a.name, a.asset_category, a.base_unit, COUNT(f.id) as quantity, a.manufacturer "
                + "FROM fixed_asset_item f "
                + "JOIN asset a ON f.asset_id = a.id "
                + "WHERE (f.department_id = ? OR (? = 'qtvt' AND f.department_id IS NULL)) "
                + "AND f.status IN ('IN_STOCK', 'DAMAGED', 'BROKEN') "
                + "GROUP BY f.asset_id, a.name";

        try (Connection conn = Database.getConnection()) {
            // Tools
            try (PreparedStatement start = conn.prepareStatement(toolSql)) {
                start.setString(1, deptId);
                ResultSet rs = start.executeQuery();
                while (rs.next()) {
                    assets.add(new Asset(
                            rs.getString("asset_id"),
                            rs.getString("name"),
                            rs.getString("asset_category"),
                            rs.getString("base_unit"),
                            0, true, rs.getInt("quantity"), rs.getString("manufacturer")));
                }
            }
            // Fixed Assets
            try (PreparedStatement start = conn.prepareStatement(fixedSql)) {
                start.setString(1, deptId);
                start.setString(2, deptId);
                ResultSet rs = start.executeQuery();
                while (rs.next()) {
                    assets.add(new Asset(
                            rs.getString("asset_id"),
                            rs.getString("name"),
                            rs.getString("asset_category"),
                            rs.getString("base_unit"),
                            0, true, rs.getInt("quantity"), rs.getString("manufacturer")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return assets;
    }

    public void reportToolLiquidation(String assetId, String deptId, int qty, double price, String reason,
            String note) {
        String userId = "nv001";
        String transId = generateTransactionId();
        String logSql = "INSERT INTO tool_transaction (id, asset_id, transaction_type, transaction_date, quantity, " +
                "from_department_id, to_department_id, unit_price, user_id, reason, note) " +
                "VALUES (?, ?, 'LIQUIDATION', datetime('now'), ?, ?, 'qtvt', ?, ?, ?, ?)";
        String updateAssetSql = "UPDATE asset SET total_quantity = total_quantity - ? WHERE id = ?";
        String selectToolsSql = "SELECT id, quantity FROM tool WHERE asset_id = ? AND department_id = ? AND quantity > 0 ORDER BY quantity ASC";
        String updateToolSql = "UPDATE tool SET quantity = ? WHERE id = ?";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement logStmt = conn.prepareStatement(logSql);
                    PreparedStatement assetStmt = conn.prepareStatement(updateAssetSql);
                    PreparedStatement selectStmt = conn.prepareStatement(selectToolsSql);
                    PreparedStatement updateStmt = conn.prepareStatement(updateToolSql)) {
                logStmt.setString(1, transId);
                logStmt.setString(2, assetId);
                logStmt.setInt(3, qty);
                logStmt.setString(4, deptId);
                logStmt.setDouble(5, price);
                logStmt.setString(6, userId);
                logStmt.setString(7, reason);
                logStmt.setString(8, note);
                logStmt.executeUpdate();

                assetStmt.setInt(1, qty);
                assetStmt.setString(2, assetId);
                assetStmt.executeUpdate();

                selectStmt.setString(1, assetId);
                selectStmt.setString(2, deptId);
                ResultSet rs = selectStmt.executeQuery();
                int remaining = qty;
                while (rs.next() && remaining > 0) {
                    String tId = rs.getString("id");
                    int currentQty = rs.getInt("quantity");
                    if (currentQty <= remaining) {
                        remaining -= currentQty;
                        updateStmt.setInt(1, 0);
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
                if (remaining > 0)
                    throw new SQLException("Insufficient quantity.");
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error: " + e.getMessage());
        }
    }

    public void reportFixedAssetLiquidation(String assetId, String deptId, List<String> identifiers, double price,
            String reason, String note) {
        String userId = "nv001";
        String logSql = "INSERT INTO fixed_asset_transaction (transaction_id, asset_id, transaction_type, transaction_date, quantity, "
                + "from_department_id, to_department_id, unit_price, user_id, reason, note, fixed_asset_item_id) " +
                "VALUES (?, ?, 'LIQUIDATION', datetime('now'), 1, ?, 'qtvt', ?, ?, ?, ?, ?)";
        String findItemSql = "SELECT id FROM fixed_asset_item WHERE asset_id = ? AND department_id = ? AND (id = ? OR serial_number = ?)";
        String updateItemSql = "UPDATE fixed_asset_item SET status = 'LIQUIDATED' WHERE id = ?";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement logStmt = conn.prepareStatement(logSql);
                    PreparedStatement findStmt = conn.prepareStatement(findItemSql);
                    PreparedStatement updateStmt = conn.prepareStatement(updateItemSql)) {
                for (String ident : identifiers) {
                    findStmt.setString(1, assetId);
                    findStmt.setString(2, deptId);
                    findStmt.setString(3, ident);
                    findStmt.setString(4, ident);
                    ResultSet rs = findStmt.executeQuery();
                    String itemId = null;
                    if (rs.next())
                        itemId = rs.getString("id");
                    rs.close();

                    if (itemId != null) {
                        logStmt.setString(1, generateFixedAssetTransactionId());
                        logStmt.setString(2, assetId);
                        logStmt.setString(3, deptId);
                        logStmt.setDouble(4, price);
                        logStmt.setString(5, userId);
                        logStmt.setString(6, reason);
                        logStmt.setString(7, note);
                        logStmt.setString(8, itemId);
                        logStmt.executeUpdate();

                        updateStmt.setString(1, itemId);
                        updateStmt.executeUpdate();
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
            throw new RuntimeException("Error: " + e.getMessage());
        }
    }

    public void adjustToolStock(String assetId, int newQty) {
        // 1. Calculate Difference
        // We need current global stock to act? Or just force update?
        // Logic:
        // We assume 'qtvt' is the main warehouse where we adjust buffers.
        // But if total_quantity != newQty, where do we add/remove?
        // Default: Adjust 'qtvt' quantity.

        String getQtvtQtySql = "SELECT quantity FROM tool WHERE asset_id = ? AND department_id = 'qtvt'";
        String updateToolSql = "UPDATE tool SET quantity = ? WHERE asset_id = ? AND department_id = 'qtvt'";
        String insertToolSql = "INSERT INTO tool(id, asset_id, department_id, quantity) VALUES(?, ?, 'qtvt', ?)";
        String updateAssetSql = "UPDATE asset SET total_quantity = ? WHERE id = ?";

        // To update asset.total_quantity properly, we need sum of all depts + qtvt.
        // Easier:
        // old_total = (qtvt_old + distributed)
        // new_total = (qtvt_new + distributed)
        // diff = new_total - old_total = qtvt_new - qtvt_old
        // So just need qtvt_old.

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int oldQtvtQty = 0;
                boolean existsInQtvt = false;

                try (PreparedStatement getStmt = conn.prepareStatement(getQtvtQtySql)) {
                    getStmt.setString(1, assetId);
                    ResultSet rs = getStmt.executeQuery();
                    if (rs.next()) {
                        oldQtvtQty = rs.getInt("quantity");
                        existsInQtvt = true;
                    }
                }

                // Wait. newQty passed from UI is "Total Actual Stock" (GLOBAL).
                // UI shows "SL Sổ Sách" which is `asset.total_quantity`.
                // User enters "SL Thực Tế" which is NEW `asset.total_quantity`.

                // So NEW_TOTAL = newQty.
                // We know Distributed = TOTAL - QTVT.
                // So NEW_QTVT = NEW_TOTAL - Distributed = newQty - (Old_Total - Old_Qtvt).

                int currentTotal = 0;
                String getTotalSql = "SELECT total_quantity FROM asset WHERE id = ?";
                try (PreparedStatement p = conn.prepareStatement(getTotalSql)) {
                    p.setString(1, assetId);
                    ResultSet rs = p.executeQuery();
                    if (rs.next())
                        currentTotal = rs.getInt(1);
                }

                int distributed = currentTotal - oldQtvtQty;
                int newQtvtQty = newQty - distributed;

                if (newQtvtQty < 0) {
                    // Theoretically possible if textfield input is smaller than distributed amount.
                    // In valid scenario, this implies we lost distributed items?
                    // For simplicity, let's clamp QTVT to 0 and reduce distributed?
                    // No, just refuse or set QTVT to negative (warn)?
                    // Let's set QTVT to newQtvtQty even if negative to reflect missing stock, or
                    // clamp 0.
                    // Better: Set to newQtvtQty.
                }

                if (existsInQtvt) {
                    try (PreparedStatement up = conn.prepareStatement(updateToolSql)) {
                        up.setInt(1, newQtvtQty);
                        up.setString(2, assetId);
                        up.executeUpdate();
                    }
                } else {
                    String newId = generateNextId("TOOL", "tool", "id");
                    try (PreparedStatement in = conn.prepareStatement(insertToolSql)) {
                        in.setString(1, newId);
                        in.setString(2, assetId);
                        in.setInt(3, newQtvtQty);
                        in.executeUpdate();
                    }
                }

                try (PreparedStatement upA = conn.prepareStatement(updateAssetSql)) {
                    upA.setInt(1, newQty);
                    upA.setString(2, assetId);
                    upA.executeUpdate();
                }

                // Log Transaction (Adjustment)
                String transType = "STOCKTAKE_ADJUST";
                int adjustment = newQty - currentTotal;
                if (adjustment != 0) {
                    logTransaction(assetId, transType, Math.abs(adjustment), 0, "Stocktake Adjustment",
                            "System Auto-Correction");
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
            System.err.println("Error adjusting tool stock: " + e.getMessage());
        }
    }

    public void logAnalysisNote(String assetId, String type, String content) {
        // Since we don't have a generic "Log" table visible here, we can use a print or
        // a specific log file
        // or abuse the transaction table with 0 quantity.
        // Let's use transaction table to record the discrepancy note.
        logTransaction(assetId, "STOCK_NOTE", 0, 0, content, type);
    }
}
