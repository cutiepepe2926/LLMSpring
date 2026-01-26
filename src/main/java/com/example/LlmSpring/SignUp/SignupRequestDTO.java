package com.example.LlmSpring.SignUp;

import lombok.Data;

@Data
public class SignupRequestDTO {
    private String userId;

    private String email;

    private String password;
}
