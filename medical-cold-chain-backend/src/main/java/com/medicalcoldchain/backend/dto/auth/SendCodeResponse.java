package com.medicalcoldchain.backend.dto.auth;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SendCodeResponse {

    private String phone;
    private String code;
    private LocalDateTime expiresAt;
    private String tip;

    private boolean isNewUser;
}
