package com.medicalcoldchain.backend.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserBorrowLimitResponse {

    private Long userId;
    private String phone;
    private String name;
    private String organization;
    private String role;
    private Integer currentBorrowCount;
    private Integer borrowLimitOverride;
    private Integer effectiveBorrowLimit;
}
