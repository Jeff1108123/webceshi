package com.medicalcoldchain.backend.dto.telemetry;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ManualTelemetryRequest {

    private LocalDateTime recordedAt;

    @NotNull(message = "温度不能为空")
    @DecimalMin(value = "-80.0", message = "温度不能低于 -80°C")
    @DecimalMax(value = "80.0", message = "温度不能高于 80°C")
    private Double temperature;

    @NotNull(message = "湿度不能为空")
    @DecimalMin(value = "0.0", message = "湿度不能低于 0%")
    @DecimalMax(value = "100.0", message = "湿度不能高于 100%")
    private Double humidity;

    @NotNull(message = "光照不能为空")
    @DecimalMin(value = "0.0", message = "光照不能低于 0Lux")
    private Double light;

    @NotNull(message = "电量不能为空")
    @Min(value = 0, message = "电量不能低于 0%")
    @Max(value = 100, message = "电量不能高于 100%")
    private Integer batteryLevel;

    @NotNull(message = "信号状态不能为空")
    private Boolean signalStatus;
}
