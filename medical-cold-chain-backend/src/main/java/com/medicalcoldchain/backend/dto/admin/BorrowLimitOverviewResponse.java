package com.medicalcoldchain.backend.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BorrowLimitOverviewResponse {

    private Integer defaultLimit;
    private List<UserBorrowLimitResponse> users;
}
