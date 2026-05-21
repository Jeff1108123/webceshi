package com.medicalcoldchain.backend.dto.device;

import lombok.Data;

import java.util.List;

@Data
public class ReturnDeviceRequest {

    private List<Long> deviceIds;
}
