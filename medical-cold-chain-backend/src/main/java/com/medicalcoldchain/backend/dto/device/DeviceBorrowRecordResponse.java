package com.medicalcoldchain.backend.dto.device;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DeviceBorrowRecordResponse {

    private Long recordId;
    private Long deviceId;
    private String deviceCode;
    private String deviceName;
    private String medicineName;
    private String routeName;
    private String borrowerName;
    private String borrowerPhone;
    private LocalDateTime borrowTime;
    private LocalDateTime returnTime;
    private String status;
    private ThresholdResponse threshold;
}
