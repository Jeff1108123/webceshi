package com.medicalcoldchain.backend.service;

import com.medicalcoldchain.backend.entity.TransportDevice;
import com.medicalcoldchain.backend.entity.TelemetryRecord;
import com.medicalcoldchain.backend.enums.DeviceStatus;
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
    private TransportDeviceRepository transportDeviceRepository;

    @Test
    void historyTimelineCanExtendToCurrentMinute() {
        TransportDevice device = transportDeviceRepository.save(TransportDevice.builder()
                .deviceCode("TEST-MINUTE-" + System.nanoTime())
                .deviceName("测试冷链箱")
                .medicineName("测试药品")
                .routeName("测试线路")
                .status(DeviceStatus.IN_USE)
                .batteryLevel(100)
                .signalStatus(true)
                .build());

        telemetryService.generateBorrowHistory(device, LocalDateTime.now().minusHours(1));
        List<TelemetryRecord> records = telemetryService.getHistoryRecords(device, 1);

        assertThat(records).isNotEmpty();
        LocalDateTime lastRecordedAt = records.get(records.size() - 1).getRecordedAt();
        LocalDateTime currentMinute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        assertThat(lastRecordedAt).isEqualTo(currentMinute);
    }

    @Test
    void historyTimelineCoversLongestSelectableRange() {
        TransportDevice device = transportDeviceRepository.save(TransportDevice.builder()
                .deviceCode("TEST-HISTORY-RANGE-" + System.nanoTime())
                .deviceName("测试冷链箱")
                .medicineName("测试药品")
                .routeName("测试线路")
                .status(DeviceStatus.IN_USE)
                .batteryLevel(100)
                .signalStatus(true)
                .build());

        List<TelemetryRecord> records = telemetryService.getHistoryRecords(device, 72);

        assertThat(records).isNotEmpty();
        LocalDateTime currentMinute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        assertThat(records.get(0).getRecordedAt()).isEqualTo(currentMinute.minusHours(72));
        assertThat(records.get(records.size() - 1).getRecordedAt()).isEqualTo(currentMinute);
    }
}
