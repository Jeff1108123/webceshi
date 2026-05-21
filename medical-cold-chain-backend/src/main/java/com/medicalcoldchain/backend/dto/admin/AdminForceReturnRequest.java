package com.medicalcoldchain.backend.dto.admin;

import lombok.Data;

import java.util.List;

@Data
public class AdminForceReturnRequest {

    private Long userId;
    private List<Long> deviceIds;
}
