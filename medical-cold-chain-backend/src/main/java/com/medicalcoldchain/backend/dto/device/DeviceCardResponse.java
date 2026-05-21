package com.medicalcoldchain.backend.dto.device;

import com.medicalcoldchain.backend.dto.location.DeviceLocationResponse;
import com.medicalcoldchain.backend.dto.telemetry.TelemetryPointResponse;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeviceCardResponse {

    private Long id;
    private String deviceCode;
    private String deviceName;
    private String medicineName;
    private String routeName;
    private String status;
    private Integer batteryLevel;
    private Boolean signalStatus;
    private Boolean alarm;
    private ThresholdResponse threshold;
    private TelemetryPointResponse latestTelemetry;
    private DeviceLocationResponse latestLocation;
}
