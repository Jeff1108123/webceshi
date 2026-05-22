package com.medicalcoldchain.backend.service;

import com.medicalcoldchain.backend.dto.location.DeviceLocationResponse;
import com.medicalcoldchain.backend.dto.telemetry.TelemetryPointResponse;
import com.medicalcoldchain.backend.entity.DeviceLocation;
import com.medicalcoldchain.backend.entity.DeviceThreshold;
import com.medicalcoldchain.backend.entity.TelemetryRecord;
import com.medicalcoldchain.backend.entity.TransportDevice;
import com.medicalcoldchain.backend.repository.DeviceLocationRepository;
import com.medicalcoldchain.backend.repository.TelemetryRecordRepository;
import com.medicalcoldchain.backend.repository.TransportDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TelemetryService {

    private static final int HISTORY_STEP_MINUTES = 1;
    private static final int HISTORY_WINDOW_HOURS = 24;

    private final TelemetryRecordRepository telemetryRecordRepository;
    private final DeviceLocationRepository deviceLocationRepository;
    private final TransportDeviceRepository transportDeviceRepository;
    private final DeviceSimulationService deviceSimulationService;

    @Transactional
    public void ensureDemoHistory(TransportDevice device) {
        ensureTimeline(device, LocalDateTime.now());
    }

    @Transactional
    public void generateBorrowHistory(TransportDevice device, LocalDateTime borrowTime) {
        LocalDateTime endTime = alignTime(LocalDateTime.now(), HISTORY_STEP_MINUTES);
        LocalDateTime startTime = alignTime(endTime.minusHours(HISTORY_WINDOW_HOURS), HISTORY_STEP_MINUTES);
        telemetryRecordRepository.deleteByDeviceIdAndRecordedAtGreaterThanEqual(device.getId(), startTime);
        deviceLocationRepository.deleteByDeviceIdAndRecordedAtGreaterThanEqual(device.getId(), startTime);
        saveTimeline(device, startTime, endTime, HISTORY_STEP_MINUTES);
    }

    @Transactional
    public void clearAllDemoHistory() {
        deviceLocationRepository.deleteAllBy();
        telemetryRecordRepository.deleteAllBy();
    }

    @Transactional
    public LatestSnapshot getLatestSnapshot(TransportDevice device) {
        ensureTimeline(device, LocalDateTime.now());
        TelemetryRecord telemetry = telemetryRecordRepository.findTopByDeviceIdOrderByRecordedAtDesc(device.getId()).orElse(null);
        DeviceLocation location = deviceLocationRepository.findTopByDeviceIdOrderByRecordedAtDesc(device.getId()).orElse(null);
        return new LatestSnapshot(telemetry, location);
    }

    @Transactional
    public TelemetryRecord getLatestRecord(TransportDevice device) {
        return getLatestSnapshot(device).telemetry();
    }

    @Transactional
    public DeviceLocation getLatestLocation(TransportDevice device) {
        return getLatestSnapshot(device).location();
    }

    @Transactional
    public List<TelemetryRecord> getHistoryRecords(TransportDevice device, int hours) {
        LocalDateTime end = alignTime(LocalDateTime.now(), HISTORY_STEP_MINUTES);
        ensureTimeline(device, end);
        int cappedHours = Math.max(1, Math.min(hours, HISTORY_WINDOW_HOURS));
        LocalDateTime start = end.minusHours(cappedHours);
        return telemetryRecordRepository.findByDeviceIdAndRecordedAtBetweenOrderByRecordedAtAsc(device.getId(), start, end);
    }

    public boolean isAlarm(TelemetryRecord record, DeviceThreshold threshold) {
        if (record == null || threshold == null) {
            return false;
        }
        return !Boolean.TRUE.equals(record.getSignalStatus())
                || record.getTemperature() < threshold.getTempMin()
                || record.getTemperature() > threshold.getTempMax()
                || record.getHumidity() < threshold.getHumidityMin()
                || record.getHumidity() > threshold.getHumidityMax()
                || record.getLight() > threshold.getLightMax();
    }

    public TelemetryPointResponse toPointResponse(TelemetryRecord record, DeviceThreshold threshold) {
        if (record == null) {
            return null;
        }
        return TelemetryPointResponse.builder()
                .recordedAt(record.getRecordedAt())
                .temperature(record.getTemperature())
                .humidity(record.getHumidity())
                .light(record.getLight())
                .batteryLevel(record.getBatteryLevel())
                .signalStatus(record.getSignalStatus())
                .alarm(isAlarm(record, threshold))
                .build();
    }

    public DeviceLocationResponse toLocationResponse(DeviceLocation location) {
        if (location == null) {
            return null;
        }
        return DeviceLocationResponse.builder()
                .recordedAt(location.getRecordedAt())
                .longitude(location.getLongitude())
                .latitude(location.getLatitude())
                .city(location.getCity())
                .address(location.getAddress())
                .build();
    }

    private void ensureTimeline(TransportDevice device, LocalDateTime targetTime) {
        LocalDateTime alignedTarget = alignTime(targetTime, HISTORY_STEP_MINUTES);
        TelemetryRecord latest = telemetryRecordRepository.findTopByDeviceIdOrderByRecordedAtDesc(device.getId()).orElse(null);

        if (latest == null) {
            LocalDateTime startTime = alignTime(alignedTarget.minusHours(HISTORY_WINDOW_HOURS), HISTORY_STEP_MINUTES);
            saveTimeline(device, startTime, alignedTarget, HISTORY_STEP_MINUTES);
            return;
        }
        if (!latest.getRecordedAt().isBefore(alignedTarget)) {
            syncDeviceSnapshot(device, latest);
            return;
        }
        saveTimeline(device, latest.getRecordedAt().plusMinutes(HISTORY_STEP_MINUTES), alignedTarget, HISTORY_STEP_MINUTES);
    }

    private void saveTimeline(TransportDevice device, LocalDateTime startTime, LocalDateTime endTime, int stepMinutes) {
        List<TelemetryRecord> telemetryRecords = new ArrayList<>();
        List<DeviceLocation> locations = new ArrayList<>();
        LocalDateTime nextTime = startTime;

        while (!nextTime.isAfter(endTime)) {
            DeviceSimulationService.SimulatedTelemetry telemetry = deviceSimulationService
                    .simulateTelemetry(device.getDeviceCode(), nextTime);
            DeviceSimulationService.SimulatedLocation location = deviceSimulationService
                    .simulateLocation(device.getDeviceCode(), device.getRouteName(), nextTime);

            telemetryRecords.add(TelemetryRecord.builder()
                    .device(device)
                    .temperature(telemetry.temperature())
                    .humidity(telemetry.humidity())
                    .light(telemetry.light())
                    .batteryLevel(telemetry.batteryLevel())
                    .signalStatus(telemetry.signalStatus())
                    .recordedAt(nextTime)
                    .build());

            locations.add(DeviceLocation.builder()
                    .device(device)
                    .longitude(location.longitude())
                    .latitude(location.latitude())
                    .city(location.city())
                    .address(location.address())
                    .recordedAt(nextTime)
                    .build());

            nextTime = nextTime.plusMinutes(stepMinutes);
        }

        if (!telemetryRecords.isEmpty()) {
            telemetryRecordRepository.saveAll(telemetryRecords);
            deviceLocationRepository.saveAll(locations);
            syncDeviceSnapshot(device, telemetryRecords.get(telemetryRecords.size() - 1));
        }
    }

    private void syncDeviceSnapshot(TransportDevice device, TelemetryRecord latest) {
        device.setBatteryLevel(latest.getBatteryLevel());
        device.setSignalStatus(latest.getSignalStatus());
        transportDeviceRepository.save(device);
    }

    private LocalDateTime alignTime(LocalDateTime time, int stepMinutes) {
        int minute = (time.getMinute() / stepMinutes) * stepMinutes;
        return time.withMinute(minute).truncatedTo(ChronoUnit.MINUTES);
    }

    public record LatestSnapshot(TelemetryRecord telemetry, DeviceLocation location) {
    }
}
