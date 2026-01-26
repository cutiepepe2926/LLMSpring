package com.example.LlmSpring.LogIn;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LogInResponseDTO {
    private boolean success;
    private String code;
    private String message;
    private String userName;

    public static LogInResponseDTO ok(String userName){
        return new LogInResponseDTO(true,"SUCCESS_LOGIN",userName+"님 환영합니다!", userName);
    }

    public static LogInResponseDTO fail(String code, String message){
        return new LogInResponseDTO(false, code, message, null);
    }
}
