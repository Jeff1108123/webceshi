package com.medicalcoldchain.backend.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserInfoResponse {

    private Long id;
    private String phone;
    private String name;
    private String organization;
    private String role;
}
