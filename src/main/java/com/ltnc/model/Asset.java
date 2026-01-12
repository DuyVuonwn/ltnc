package com.ltnc.model;

public class Asset {
    private String id;
    private String name;
    private String asset_category;
    private String base_unit;
    private String manufacturer;
    private int total_quantity;
    private boolean isDistributed;
    private int current_stock; // Available in Warehouse (qtvt)

    public Asset(String id, String name, String asset_category, String base_unit, int total_quantity) {
        this(id, name, asset_category, base_unit, total_quantity, false, 0, null);
    }

    public Asset(String id, String name, String asset_category, String base_unit, int total_quantity,
            String manufacturer) {
        this(id, name, asset_category, base_unit, total_quantity, false, 0, manufacturer);
    }

    public Asset(String id, String name, String asset_category, String base_unit, int total_quantity,
            boolean isDistributed) {
        this(id, name, asset_category, base_unit, total_quantity, isDistributed, 0, null);
    }

    public Asset(String id, String name, String asset_category, String base_unit, int total_quantity,
            boolean isDistributed, int current_stock) {
        this(id, name, asset_category, base_unit, total_quantity, isDistributed, current_stock, null);
    }

    public Asset(String id, String name, String asset_category, String base_unit, int total_quantity,
            boolean isDistributed, int current_stock, String manufacturer) {
        this.id = id;
        this.name = name;
        this.asset_category = asset_category;
        this.base_unit = base_unit;
        this.total_quantity = total_quantity;
        this.isDistributed = isDistributed;
        this.current_stock = current_stock;
        this.manufacturer = manufacturer;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAsset_category() {
        return asset_category;
    }

    public void setAsset_category(String asset_category) {
        this.asset_category = asset_category;
    }

    public String getBase_unit() {
        return base_unit;
    }

    public void setBase_unit(String base_unit) {
        this.base_unit = base_unit;
    }

    public int getTotal_quantity() {
        return total_quantity;
    }

    public void setTotal_quantity(int total_quantity) {
        this.total_quantity = total_quantity;
    }

    public boolean isDistributed() {
        return isDistributed;
    }

    public void setDistributed(boolean distributed) {
        isDistributed = distributed;
    }

    public int getCurrent_stock() {
        return current_stock;
    }

    public void setCurrent_stock(int current_stock) {
        this.current_stock = current_stock;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

}
