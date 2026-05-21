package com.medicalcoldchain.backend.dto.device;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApplyDeviceRequest {

    @NotNull(message = "申请数量不能为空")
    @Min(value = 1, message = "申请数量至少为 1")
    @Max(value = 10, message = "单次最多申请 10 台设备")
    private Integer count;
}
