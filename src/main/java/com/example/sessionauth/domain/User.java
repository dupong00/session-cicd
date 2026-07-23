package com.example.sessionauth.domain;

public class User {
    private String username;
    private String passwordHashed;
    private String role;

    public User(String username, String passwordHashed, String role) {
        this.username = username;
        this.passwordHashed = passwordHashed;
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHashed() {
        return passwordHashed;
    }

    public String getRole() {
        return role;
    }
}
