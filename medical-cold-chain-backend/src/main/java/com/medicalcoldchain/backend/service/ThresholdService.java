package com.medicalcoldchain.backend.service;

import com.medicalcoldchain.backend.dto.device.ThresholdRequest;
import com.medicalcoldchain.backend.dto.device.ThresholdResponse;
import com.medicalcoldchain.backend.entity.DeviceBorrowRecord;
import com.medicalcoldchain.backend.entity.TransportDevice;
import com.medicalcoldchain.backend.entity.UserAccount;
import com.medicalcoldchain.backend.exception.BusinessException;
import com.medicalcoldchain.backend.repository.DeviceBorrowRecordRepository;
import com.medicalcoldchain.backend.repository.TransportDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ThresholdService {

    private final DeviceBorrowRecordRepository deviceBorrowRecordRepository;
    private final TransportDeviceRepository transportDeviceRepository;

    @Transactional
    public DeviceBorrowRecord ensureThreshold(UserAccount user, TransportDevice device) {
        return deviceBorrowRecordRepository
                .findTopByDeviceIdAndBorrowerIdAndReturnTimeIsNullOrderByBorrowTimeDesc(device.getId(), user.getId())
                .orElseGet(() -> deviceBorrowRecordRepository.save(DeviceBorrowRecord.builder()
                        .device(device)
                        .borrower(user)
                        .borrowTime(device.getBorrowedAt() == null ? LocalDateTime.now() : device.getBorrowedAt())
                        .build()));
    }

    @Transactional
    public ThresholdResponse getThreshold(UserAccount user, Long deviceId) {
        TransportDevice device = getOwnedDevice(user, deviceId);
        return toResponse(ensureThreshold(user, device));
    }

    @Transactional
    public ThresholdResponse saveThreshold(UserAccount user, Long deviceId, ThresholdRequest request) {
        validateRequest(request);
        TransportDevice device = getOwnedDevice(user, deviceId);
        DeviceBorrowRecord threshold = ensureThreshold(user, device);
        threshold.setTempMin(request.getTempMin());
        threshold.setTempMax(request.getTempMax());
        threshold.setHumidityMin(request.getHumidityMin());
        threshold.setHumidityMax(request.getHumidityMax());
        threshold.setLightMax(request.getLightMax());
        return toResponse(deviceBorrowRecordRepository.save(threshold));
    }

    @Transactional
    public Map<Long, DeviceBorrowRecord> getThresholdMap(UserAccount user, List<TransportDevice> devices) {
        Map<Long, DeviceBorrowRecord> thresholdMap = new HashMap<>();
        if (devices.isEmpty()) {
            return thresholdMap;
        }

        List<Long> deviceIds = devices.stream().map(TransportDevice::getId).toList();
        deviceBorrowRecordRepository.findByBorrowerIdAndReturnTimeIsNullAndDevice_IdIn(user.getId(), deviceIds)
                .forEach(record -> thresholdMap.put(record.getDevice().getId(), record));

        for (TransportDevice device : devices) {
            thresholdMap.computeIfAbsent(device.getId(), key -> ensureThreshold(user, device));
        }
        return thresholdMap;
    }

    public ThresholdResponse toResponse(DeviceBorrowRecord threshold) {
        return ThresholdResponse.builder()
                .id(threshold.getId())
                .deviceId(threshold.getDevice().getId())
                .tempMin(threshold.getTempMin())
                .tempMax(threshold.getTempMax())
                .humidityMin(threshold.getHumidityMin())
                .humidityMax(threshold.getHumidityMax())
                .lightMax(threshold.getLightMax())
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
