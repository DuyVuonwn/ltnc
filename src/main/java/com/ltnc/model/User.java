package com.ltnc.model;

public class User {
    private String id;
    private String name;
    private String phone;
    private String email;
    private String departmentId;
    private String departmentName;
    private String username;
    // We might not want to keep password in memory for long, but for login flow
    // it's typical.
    // Ideally we wouldn't store it in the object after auth, but simple pojo is
    // fine.
    private String password;

    public User() {
    }

    public User(String id, String name, String phone, String email, String departmentId, String departmentName,
            String username,
            String password) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.username = username;
        this.password = password;
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
