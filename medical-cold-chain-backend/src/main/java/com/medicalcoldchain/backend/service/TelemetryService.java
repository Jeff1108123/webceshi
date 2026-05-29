package com.medicalcoldchain.backend.service;

import com.medicalcoldchain.backend.dto.location.DeviceLocationResponse;
import com.medicalcoldchain.backend.dto.telemetry.ManualTelemetryRequest;
import com.medicalcoldchain.backend.dto.telemetry.TelemetryPointResponse;
import com.medicalcoldchain.backend.entity.DeviceBorrowRecord;
import com.medicalcoldchain.backend.entity.DeviceLocation;
import com.medicalcoldchain.backend.entity.TelemetryRecord;
import com.medicalcoldchain.backend.entity.TransportDevice;
import com.medicalcoldchain.backend.repository.DeviceLocationRepository;
import com.medicalcoldchain.backend.repository.TelemetryRecordRepository;
import com.medicalcoldchain.backend.repository.TransportDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TelemetryService {

    private static final int HISTORY_GENERATION_STEP_MINUTES = 1;
    private static final int HISTORY_RESPONSE_MIN_STEP_MINUTES = 1;
    private static final int HISTORY_WINDOW_HOURS = 24;

    private final TelemetryRecordRepository telemetryRecordRepository;
    private final DeviceLocationRepository deviceLocationRepository;
    private final TransportDeviceRepository transportDeviceRepository;
    private final DeviceSimulationService deviceSimulationService;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Transactional
    public void generateBorrowHistory(TransportDevice device, LocalDateTime borrowTime) {
        LocalDateTime endTime = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime startTime = getBorrowStartMinute(device, borrowTime);
        if (startTime.isAfter(endTime)) {
            endTime = startTime;
        }
        telemetryRecordRepository.deleteByDeviceIdAndRecordedAtGreaterThanEqual(device.getId(), startTime);
        deviceLocationRepository.deleteByDeviceIdAndRecordedAtGreaterThanEqual(device.getId(), startTime);
        saveTimeline(device, startTime, endTime, HISTORY_GENERATION_STEP_MINUTES);
    }

    @Transactional
    public void refreshHistoryToCurrentTime(TransportDevice device) {
        LocalDateTime endTime = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime borrowStart = getBorrowStartMinute(device, endTime);
        if (borrowStart.isAfter(endTime)) {
            endTime = borrowStart;
        }
        TelemetryRecord latest = telemetryRecordRepository
                .findTopByDeviceIdOrderByRecordedAtDescIdDesc(device.getId())
                .orElse(null);

        if (latest == null || latest.getRecordedAt() == null) {
            saveTimeline(device, borrowStart, endTime, HISTORY_GENERATION_STEP_MINUTES);
            return;
        }

        LocalDateTime latestMinute = latest.getRecordedAt().truncatedTo(ChronoUnit.MINUTES);
        if (!latestMinute.isBefore(endTime)) {
            syncDeviceSnapshot(device, latest);
            return;
        }

        LocalDateTime windowStart = endTime.minusHours(HISTORY_WINDOW_HOURS);
        LocalDateTime startTime = latestMinute.plusMinutes(HISTORY_GENERATION_STEP_MINUTES);
        if (startTime.isBefore(windowStart)) {
            startTime = windowStart;
        }
        if (startTime.isBefore(borrowStart)) {
            startTime = borrowStart;
        }
        if (startTime.isAfter(endTime)) {
            startTime = endTime;
        }
        saveTimeline(device, startTime, endTime, HISTORY_GENERATION_STEP_MINUTES);
    }

    @Transactional
    public void clearAllDemoHistory() {
        deviceLocationRepository.deleteAllBy();
        telemetryRecordRepository.deleteAllBy();
    }

    @Transactional
    public void clearDeviceHistory(TransportDevice device) {
        deviceLocationRepository.deleteByDeviceId(device.getId());
        telemetryRecordRepository.deleteByDeviceId(device.getId());
    }

    @Transactional
    public void resetTelemetryRecordAutoIncrementIfEmpty() {
        if (telemetryRecordRepository.count() > 0) {
            return;
        }
        String databaseProductName = getDatabaseProductName().toLowerCase();
        if (databaseProductName.contains("mysql") || databaseProductName.contains("mariadb")) {
            jdbcTemplate.execute("ALTER TABLE telemetry_record AUTO_INCREMENT = 1");
            return;
        }
        if (databaseProductName.contains("h2")) {
            jdbcTemplate.execute("ALTER TABLE telemetry_record ALTER COLUMN id RESTART WITH 1");
        }
    }

    @Transactional
    public LatestSnapshot recordManualHistory(TransportDevice device, ManualTelemetryRequest request) {
        LocalDateTime recordedAt = request.getRecordedAt() == null
                ? LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
                : request.getRecordedAt().truncatedTo(ChronoUnit.MILLIS);
        DeviceSimulationService.SimulatedLocation location = deviceSimulationService
                .simulateLocation(device.getDeviceCode(), device.getRouteName(), recordedAt);

        TelemetryRecord savedTelemetry = telemetryRecordRepository.save(TelemetryRecord.builder()
                .device(device)
                .temperature(request.getTemperature())
                .humidity(request.getHumidity())
                .light(request.getLight())
                .batteryLevel(request.getBatteryLevel())
                .signalStatus(request.getSignalStatus())
                .recordedAt(recordedAt)
                .build());

        DeviceLocation savedLocation = deviceLocationRepository.save(DeviceLocation.builder()
                .device(device)
                .longitude(location.longitude())
                .latitude(location.latitude())
                .city(location.city())
                .address(location.address())
                .recordedAt(recordedAt)
                .build());

        syncDeviceSnapshot(device, savedTelemetry);
        return new LatestSnapshot(savedTelemetry, savedLocation);
    }

    @Transactional
    public LatestSnapshot recordRealtimeSnapshot(TransportDevice device) {
        LocalDateTime recordedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        DeviceSimulationService.SimulatedTelemetry telemetry = deviceSimulationService
                .simulateTelemetry(device.getDeviceCode(), recordedAt);
        DeviceSimulationService.SimulatedLocation location = deviceSimulationService
                .simulateLocation(device.getDeviceCode(), device.getRouteName(), recordedAt);

        TelemetryRecord savedTelemetry = telemetryRecordRepository.save(TelemetryRecord.builder()
                .device(device)
                .temperature(telemetry.temperature())
                .humidity(telemetry.humidity())
                .light(telemetry.light())
                .batteryLevel(telemetry.batteryLevel())
                .signalStatus(telemetry.signalStatus())
                .recordedAt(recordedAt)
                .build());

        DeviceLocation savedLocation = deviceLocationRepository.save(DeviceLocation.builder()
                .device(device)
                .longitude(location.longitude())
                .latitude(location.latitude())
                .city(location.city())
                .address(location.address())
                .recordedAt(recordedAt)
                .build());

        syncDeviceSnapshot(device, savedTelemetry);
        return new LatestSnapshot(savedTelemetry, savedLocation);
    }

    @Transactional
    public LatestSnapshot getLatestSnapshot(TransportDevice device) {
        TelemetryRecord telemetry = telemetryRecordRepository.findTopByDeviceIdOrderByRecordedAtDescIdDesc(device.getId()).orElse(null);
        DeviceLocation location = deviceLocationRepository.findTopByDeviceIdOrderByRecordedAtDesc(device.getId()).orElse(null);
        if (telemetry != null) {
            syncDeviceSnapshot(device, telemetry);
        }
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
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime alignedEnd = now.truncatedTo(ChronoUnit.MINUTES);
        int cappedHours = Math.max(1, Math.min(hours, HISTORY_WINDOW_HOURS));
        LocalDateTime start = alignedEnd.minusHours(cappedHours);
        return telemetryRecordRepository.findByDeviceIdAndRecordedAtBetweenOrderByRecordedAtAsc(device.getId(), start, now);
    }

    @Transactional
    public List<TelemetryPointResponse> getHistoryPoints(
            TransportDevice device,
            int hours,
            int stepMinutes,
            DeviceBorrowRecord threshold) {
        List<TelemetryRecord> records = getHistoryRecords(device, hours);
        int safeStepMinutes = Math.max(HISTORY_RESPONSE_MIN_STEP_MINUTES, Math.min(stepMinutes, 60));
        if (safeStepMinutes <= HISTORY_RESPONSE_MIN_STEP_MINUTES) {
            return records.stream()
                    .map(record -> toPointResponse(record, threshold))
                    .toList();
        }
        return aggregateHistoryPoints(records, safeStepMinutes, threshold);
    }

    public boolean isAlarm(TelemetryRecord record, DeviceBorrowRecord threshold) {
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

    public TelemetryPointResponse toPointResponse(TelemetryRecord record, DeviceBorrowRecord threshold) {
        if (record == null) {
            return null;
        }
        return TelemetryPointResponse.builder()
                .id(record.getId())
                .recordedAt(record.getRecordedAt())
                .temperature(record.getTemperature())
                .humidity(record.getHumidity())
                .light(record.getLight())
                .batteryLevel(record.getBatteryLevel())
                .signalStatus(record.getSignalStatus())
                .alarm(isAlarm(record, threshold))
                .build();
    }

    private List<TelemetryPointResponse> aggregateHistoryPoints(
            List<TelemetryRecord> records,
            int stepMinutes,
            DeviceBorrowRecord threshold) {
        if (records.isEmpty()) {
            return List.of();
        }

        List<TelemetryPointResponse> points = new ArrayList<>();
        HistoryBucket bucket = null;
        LocalDateTime bucketStart = null;

        for (TelemetryRecord record : records) {
            LocalDateTime recordBucketStart = alignTime(record.getRecordedAt(), stepMinutes);
            if (bucket == null || !recordBucketStart.equals(bucketStart)) {
                if (bucket != null) {
                    points.add(bucket.toResponse());
                }
                bucketStart = recordBucketStart;
                bucket = new HistoryBucket();
            }
            bucket.add(record, isAlarm(record, threshold));
        }

        if (bucket != null) {
            points.add(bucket.toResponse());
        }
        return points;
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

    private LocalDateTime getBorrowStartMinute(TransportDevice device, LocalDateTime fallback) {
        LocalDateTime borrowedAt = device.getBorrowedAt() == null ? fallback : device.getBorrowedAt();
        if (borrowedAt == null) {
            return LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        }
        return borrowedAt.truncatedTo(ChronoUnit.MILLIS);
    }

    private void saveTimeline(TransportDevice device, LocalDateTime startTime, LocalDateTime endTime, int stepMinutes) {
        List<TelemetryRecord> telemetryRecords = new ArrayList<>();
        List<DeviceLocation> locations = new ArrayList<>();
        LocalDateTime nextTime = startTime;
        LocalDateTime lastGeneratedAt = null;

        while (!nextTime.isAfter(endTime)) {
            appendTimelinePoint(device, nextTime, telemetryRecords, locations);
            lastGeneratedAt = nextTime;
            nextTime = nextTime.plusMinutes(stepMinutes);
        }

        if (lastGeneratedAt != null && endTime.truncatedTo(ChronoUnit.MINUTES).isAfter(lastGeneratedAt.truncatedTo(ChronoUnit.MINUTES))) {
            appendTimelinePoint(device, endTime, telemetryRecords, locations);
        }

        if (!telemetryRecords.isEmpty()) {
            telemetryRecordRepository.saveAll(telemetryRecords);
            deviceLocationRepository.saveAll(locations);
            syncDeviceSnapshot(device, telemetryRecords.get(telemetryRecords.size() - 1));
        }
    }

    private void appendTimelinePoint(
            TransportDevice device,
            LocalDateTime recordedAt,
            List<TelemetryRecord> telemetryRecords,
            List<DeviceLocation> locations) {
        DeviceSimulationService.SimulatedTelemetry telemetry = deviceSimulationService
                .simulateTelemetry(device.getDeviceCode(), recordedAt);
        DeviceSimulationService.SimulatedLocation location = deviceSimulationService
                .simulateLocation(device.getDeviceCode(), device.getRouteName(), recordedAt);

        telemetryRecords.add(TelemetryRecord.builder()
                .device(device)
                .temperature(telemetry.temperature())
                .humidity(telemetry.humidity())
                .light(telemetry.light())
                .batteryLevel(telemetry.batteryLevel())
                .signalStatus(telemetry.signalStatus())
                .recordedAt(recordedAt)
                .build());

        locations.add(DeviceLocation.builder()
                .device(device)
                .longitude(location.longitude())
                .latitude(location.latitude())
                .city(location.city())
                .address(location.address())
                .recordedAt(recordedAt)
                .build());
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

    private String getDatabaseProductName() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName();
        } catch (SQLException exception) {
            return "";
        }
    }

    private static double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static class HistoryBucket {

        private LocalDateTime recordedAt;
        private double temperatureSum;
        private double humiditySum;
        private double lightSum;
        private int batterySum;
        private int count;
        private boolean signalStatus = true;
        private boolean alarm;

        private void add(TelemetryRecord record, boolean recordAlarm) {
            recordedAt = record.getRecordedAt();
            temperatureSum += record.getTemperature();
            humiditySum += record.getHumidity();
            lightSum += record.getLight();
            batterySum += record.getBatteryLevel();
            count++;
            signalStatus = signalStatus && Boolean.TRUE.equals(record.getSignalStatus());
            alarm = alarm || recordAlarm;
        }

        private TelemetryPointResponse toResponse() {
            return TelemetryPointResponse.builder()
                    .recordedAt(recordedAt)
                    .temperature(roundToTwoDecimals(temperatureSum / count))
                    .humidity(roundToTwoDecimals(humiditySum / count))
                    .light(roundToTwoDecimals(lightSum / count))
                    .batteryLevel(Math.round((float) batterySum / count))
                    .signalStatus(signalStatus)
                    .alarm(alarm)
                    .build();
        }
    }

    public record LatestSnapshot(TelemetryRecord telemetry, DeviceLocation location) {
    }
}
