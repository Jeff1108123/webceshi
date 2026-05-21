package com.medicalcoldchain.backend.dto.device;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeviceOverviewResponse {

    private long availableCount;
    private long inUseCount;
    private long myDeviceCount;
    private long alarmCount;
}
