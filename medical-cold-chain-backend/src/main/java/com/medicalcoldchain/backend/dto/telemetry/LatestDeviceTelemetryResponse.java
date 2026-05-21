package com.medicalcoldchain.backend.dto.telemetry;

import com.medicalcoldchain.backend.dto.device.ThresholdResponse;
import com.medicalcoldchain.backend.dto.location.DeviceLocationResponse;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LatestDeviceTelemetryResponse {

    private Long deviceId;
    private String deviceCode;
    private String deviceName;
    private String medicineName;
    private String routeName;
    private ThresholdResponse threshold;
    private TelemetryPointResponse telemetry;
    private DeviceLocationResponse location;
}
