package br.com.vortex.login.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class ForgotPasswordRequestDTO {
    @Email(message = "Email deve ter formato válido")
    @NotBlank(message = "Email é obrigatório")
    private String email;
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
}