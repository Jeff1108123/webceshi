package com.medicalcoldchain.backend.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BorrowLimitRequest {

    @NotNull(message = "借用上限不能为空")
    @Min(value = 1, message = "借用上限至少为 1")
    private Integer limit;
}
