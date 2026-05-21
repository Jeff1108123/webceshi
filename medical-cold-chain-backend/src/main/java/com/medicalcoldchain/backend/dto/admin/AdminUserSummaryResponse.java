package com.medicalcoldchain.backend.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AdminUserSummaryResponse {

    private Long userId;
    private String phone;
    private String name;
    private String organization;
    private String role;
    private Integer inUseDeviceCount;
    private List<String> activeDeviceCodes;
    private LocalDateTime latestBorrowTime;
}
