package com.example.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "username is required")
    @Size(min = 3, max = 48, message = "username length must be between 3 and 48")
    private String username;

    @NotBlank(message = "password is required")
    @Size(min = 8, max = 128, message = "password length must be between 8 and 128")
    private String password;

    public RegisterRequest() {}

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