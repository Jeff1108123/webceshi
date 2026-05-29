package com.medicalcoldchain.backend.dto.telemetry;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TelemetryPointResponse {

    private Long id;
    private LocalDateTime recordedAt;
    private Double temperature;
    private Double humidity;
    private Double light;
    private Integer batteryLevel;
    private Boolean signalStatus;
    private Boolean alarm;
}
