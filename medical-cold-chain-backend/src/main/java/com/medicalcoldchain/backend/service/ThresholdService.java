package com.medicalcoldchain.backend.service;

import com.medicalcoldchain.backend.dto.device.ThresholdRequest;
import com.medicalcoldchain.backend.dto.device.ThresholdResponse;
import com.medicalcoldchain.backend.entity.DeviceThreshold;
import com.medicalcoldchain.backend.entity.TransportDevice;
import com.medicalcoldchain.backend.entity.UserAccount;
import com.medicalcoldchain.backend.exception.BusinessException;
import com.medicalcoldchain.backend.repository.DeviceThresholdRepository;
import com.medicalcoldchain.backend.repository.TransportDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ThresholdService {

    private static final double DEFAULT_TEMP_MIN = 2D;
    private static final double DEFAULT_TEMP_MAX = 8D;
    private static final double DEFAULT_HUMIDITY_MIN = 35D;
    private static final double DEFAULT_HUMIDITY_MAX = 75D;
    private static final double DEFAULT_LIGHT_MAX = 10D;
    private static final int DEFAULT_DURATION_LIMIT_HOURS = 8;

    private final DeviceThresholdRepository deviceThresholdRepository;
    private final TransportDeviceRepository transportDeviceRepository;

    @Transactional
    public DeviceThreshold ensureThreshold(UserAccount user, TransportDevice device) {
        return deviceThresholdRepository.findByUserIdAndDeviceId(user.getId(), device.getId())
                .orElseGet(() -> deviceThresholdRepository.save(DeviceThreshold.builder()
                        .user(user)
                        .device(device)
                        .tempMin(DEFAULT_TEMP_MIN)
                        .tempMax(DEFAULT_TEMP_MAX)
                        .humidityMin(DEFAULT_HUMIDITY_MIN)
                        .humidityMax(DEFAULT_HUMIDITY_MAX)
                        .lightMax(DEFAULT_LIGHT_MAX)
                        .durationLimitHours(DEFAULT_DURATION_LIMIT_HOURS)
                        .build()));
    }

    @Transactional
    public ThresholdResponse getThreshold(UserAccount user, Long deviceId) {
        TransportDevice device = getOwnedDevice(user, deviceId);
        return toResponse(deviceThresholdRepository.findByUserIdAndDeviceId(user.getId(), device.getId())
                .orElseGet(() -> ensureThreshold(user, device)));
    }

    @Transactional
    public ThresholdResponse saveThreshold(UserAccount user, Long deviceId, ThresholdRequest request) {
        validateRequest(request);
        TransportDevice device = getOwnedDevice(user, deviceId);
        DeviceThreshold threshold = ensureThreshold(user, device);
        threshold.setTempMin(request.getTempMin());
        threshold.setTempMax(request.getTempMax());
        threshold.setHumidityMin(request.getHumidityMin());
        threshold.setHumidityMax(request.getHumidityMax());
        threshold.setLightMax(request.getLightMax());
        threshold.setDurationLimitHours(request.getDurationLimitHours());
        return toResponse(deviceThresholdRepository.save(threshold));
    }

    @Transactional
    public Map<Long, DeviceThreshold> getThresholdMap(UserAccount user, List<TransportDevice> devices) {
        Map<Long, DeviceThreshold> thresholdMap = new HashMap<>();
        if (devices.isEmpty()) {
            return thresholdMap;
        }

        List<Long> deviceIds = devices.stream().map(TransportDevice::getId).toList();
        deviceThresholdRepository.findByUserIdAndDeviceIdIn(user.getId(), deviceIds)
                .forEach(threshold -> thresholdMap.put(threshold.getDevice().getId(), threshold));

        for (TransportDevice device : devices) {
            thresholdMap.computeIfAbsent(device.getId(), key -> ensureThreshold(user, device));
        }
        return thresholdMap;
    }

    public ThresholdResponse toResponse(DeviceThreshold threshold) {
        return ThresholdResponse.builder()
                .id(threshold.getId())
                .deviceId(threshold.getDevice().getId())
                .tempMin(threshold.getTempMin())
                .tempMax(threshold.getTempMax())
                .humidityMin(threshold.getHumidityMin())
                .humidityMax(threshold.getHumidityMax())
                .lightMax(threshold.getLightMax())
                .durationLimitHours(threshold.getDurationLimitHours())
                .build();
    }

    private TransportDevice getOwnedDevice(UserAccount user, Long deviceId) {
        return transportDeviceRepository.findByIdAndCurrentUserId(deviceId, user.getId())
                .orElseThrow(() -> new BusinessException("设备不存在或不属于当前用户"));
    }

    private void validateRequest(ThresholdRequest request) {
        if (request.getTempMin() >= request.getTempMax()) {
            throw new BusinessException("温度下限必须小于上限");
        }
        if (request.getHumidityMin() >= request.getHumidityMax()) {
            throw new BusinessException("湿度下限必须小于上限");
        }
    }
}
