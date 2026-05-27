package com.medicalcoldchain.backend.dto.device;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ThresholdResponse {

    private Long id;
    private Long deviceId;
    private Double tempMin;
    private Double tempMax;
    private Double humidityMin;
    private Double humidityMax;
    private Double lightMax;
}
