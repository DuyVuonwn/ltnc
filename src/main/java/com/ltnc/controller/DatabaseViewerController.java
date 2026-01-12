package com.ltnc.controller;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.ltnc.model.Database;
import org.json.JSONArray;
import org.json.JSONObject;

public class DatabaseViewerController {

    public String getTables() {
        List<String> tables = new ArrayList<>();
        String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"; // Exclude system
                                                                                                       // tables

        try (Connection conn = Database.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                tables.add(rs.getString("name"));
            }

        } catch (Exception e) {
            e.printStackTrace();
            return new JSONArray().put("Error: " + e.getMessage()).toString();
        }
        return new JSONArray(tables).toString();
    }

    public String getTableData(String tableName) {
        if (tableName == null || tableName.isEmpty() || !tableName.matches("[a-zA-Z0-9_]+")) {
            return new JSONObject().put("error", "Invalid table name").toString();
        }

        String sql = "SELECT * FROM " + tableName + " LIMIT 500";
        JSONObject result = new JSONObject();

        try (Connection conn = Database.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            JSONArray columns = new JSONArray();

            for (int i = 1; i <= columnCount; i++) {
                columns.put(metaData.getColumnName(i));
            }

            JSONArray data = new JSONArray();
            while (rs.next()) {
                JSONObject row = new JSONObject();
                for (int i = 1; i <= columnCount; i++) {
                    String colName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    row.put(colName, value == null ? JSONObject.NULL : value);
                }
                data.put(row);
            }

            result.put("columns", columns);
            result.put("data", data);

        } catch (Exception e) {
            e.printStackTrace();
            return new JSONObject().put("error", e.getMessage()).toString();
        }
        return result.toString();
    }

    public String executeQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new JSONObject().put("error", "Empty query").toString();
        }

        // Basic safety: only allow SELECT for now if generic execution is exposed
        if (!query.trim().toUpperCase().startsWith("SELECT")) {
            return new JSONObject().put("error", "Only SELECT queries are allowed in this viewer.").toString();
        }

        JSONObject result = new JSONObject();
        try (Connection conn = Database.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            JSONArray columns = new JSONArray();

            for (int i = 1; i <= columnCount; i++) {
                columns.put(metaData.getColumnName(i));
            }

            JSONArray data = new JSONArray();
            while (rs.next()) {
                JSONObject row = new JSONObject();
                for (int i = 1; i <= columnCount; i++) {
                    String colName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    row.put(colName, value == null ? JSONObject.NULL : value);
                }
                data.put(row);
            }

            result.put("columns", columns);
            result.put("data", data);

        } catch (Exception e) {
            return new JSONObject().put("error", e.getMessage()).toString();
        }
        return result.toString();
    }
}
