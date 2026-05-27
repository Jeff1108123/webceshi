package com.medicalcoldchain.backend.service;

import com.medicalcoldchain.backend.entity.TelemetryRecord;
import com.medicalcoldchain.backend.entity.TransportDevice;
import com.medicalcoldchain.backend.enums.DeviceStatus;
import com.medicalcoldchain.backend.repository.TelemetryRecordRepository;
import com.medicalcoldchain.backend.repository.TransportDeviceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TelemetryServiceMinuteRefreshTest {

    @Autowired
    private TelemetryService telemetryService;

    @Autowired
    private TelemetryRecordRepository telemetryRecordRepository;

    @Autowired
    private TransportDeviceRepository transportDeviceRepository;

    @Test
    void historyQueriesDoNotGenerateTelemetryForEmptyDevice() {
        TransportDevice device = createDevice("TEST-EMPTY-");

        assertThat(telemetryService.getLatestRecord(device)).isNull();
        assertThat(telemetryService.getHistoryRecords(device, 1)).isEmpty();
    }

    @Test
    void borrowedDeviceHistoryIsGeneratedAndCappedAtTwentyFourHours() {
        TransportDevice device = createDevice("TEST-BORROW-");
        LocalDateTime oldRecordedAt = LocalDateTime.now().minusHours(25).truncatedTo(ChronoUnit.MINUTES);
        telemetryRecordRepository.save(TelemetryRecord.builder()
                .device(device)
                .temperature(22.0)
                .humidity(55.0)
                .light(10.0)
                .batteryLevel(80)
                .signalStatus(true)
                .recordedAt(oldRecordedAt)
                .build());

        LocalDateTime beforeGeneration = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        telemetryService.generateBorrowHistory(device, LocalDateTime.now());
        List<TelemetryRecord> records = telemetryService.getHistoryRecords(device, 72);
        LocalDateTime afterGeneration = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        assertThat(records).isNotEmpty();
        assertThat(records)
                .extracting(TelemetryRecord::getRecordedAt)
                .doesNotContain(oldRecordedAt);
        assertThat(records.get(records.size() - 1).getRecordedAt())
                .isBetween(beforeGeneration, afterGeneration);
    }

    @Test
    void refreshHistoryToCurrentTimeGeneratesTelemetryForEmptyDevice() {
        TransportDevice device = createDevice("TEST-REFRESH-");

        assertThat(telemetryService.getHistoryRecords(device, 24)).isEmpty();

        LocalDateTime beforeRefresh = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        telemetryService.refreshHistoryToCurrentTime(device);
        LocalDateTime afterRefresh = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        List<TelemetryRecord> records = telemetryService.getHistoryRecords(device, 24);
        TelemetryRecord latest = telemetryRecordRepository
                .findTopByDeviceIdOrderByRecordedAtDescIdDesc(device.getId())
                .orElseThrow();

        assertThat(records).isNotEmpty();
        assertThat(latest.getRecordedAt()).isBetween(beforeRefresh, afterRefresh);
    }

    private TransportDevice createDevice(String codePrefix) {
        return transportDeviceRepository.save(TransportDevice.builder()
                .deviceCode(codePrefix + System.nanoTime())
                .deviceName("测试冷链箱")
                .medicineName("测试药品")
                .routeName("测试线路")
                .status(DeviceStatus.IN_USE)
                .batteryLevel(100)
                .signalStatus(true)
                .build());
    }
}
