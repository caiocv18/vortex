package br.com.vortex.authorization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {

    @NotBlank(message = "Email or username is required")
    @Size(max = 255, message = "Email or username must not exceed 255 characters")
    public String identifier;

    @NotBlank(message = "Password is required")
    @Size(min = 1, max = 128, message = "Password must be between 1 and 128 characters")
    public String password;

    public boolean rememberMe = false;
}