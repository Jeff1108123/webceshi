package com.medicalcoldchain.backend.dto.location;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DeviceLocationResponse {

    private LocalDateTime recordedAt;
    private Double longitude;
    private Double latitude;
    private String city;
    private String address;
}
