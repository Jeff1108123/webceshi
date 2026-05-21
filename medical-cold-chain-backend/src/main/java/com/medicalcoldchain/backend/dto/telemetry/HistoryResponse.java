package com.medicalcoldchain.backend.dto.telemetry;

import com.medicalcoldchain.backend.dto.device.ThresholdResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class HistoryResponse {

    private Long deviceId;
    private String deviceCode;
    private String deviceName;
    private Integer hours;
    private ThresholdResponse threshold;
    private List<TelemetryPointResponse> points;
}
