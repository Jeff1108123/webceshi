package com.medicalcoldchain.backend.dto.admin;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UserBorrowLimitRequest {

    @Min(value = 1, message = "借用上限至少为 1")
    private Integer limit;
}
