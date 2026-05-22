package com.medicalcoldchain.backend;

import com.medicalcoldchain.backend.dto.admin.AdminForceReturnRequest;
import com.medicalcoldchain.backend.dto.admin.AdminUserSummaryResponse;
import com.medicalcoldchain.backend.dto.admin.BorrowLimitOverviewResponse;
import com.medicalcoldchain.backend.dto.admin.BorrowLimitRequest;
import com.medicalcoldchain.backend.dto.admin.UserBorrowLimitRequest;
import com.medicalcoldchain.backend.dto.auth.SendCodeRequest;
import com.medicalcoldchain.backend.dto.auth.SendCodeResponse;
import com.medicalcoldchain.backend.dto.device.ApplyDeviceRequest;
import com.medicalcoldchain.backend.dto.device.DeviceBorrowRecordResponse;
import com.medicalcoldchain.backend.dto.device.ThresholdResponse;
import com.medicalcoldchain.backend.dto.telemetry.LatestDeviceTelemetryResponse;
import com.medicalcoldchain.backend.entity.UserAccount;
import com.medicalcoldchain.backend.enums.UserRole;
import com.medicalcoldchain.backend.exception.BusinessException;
import com.medicalcoldchain.backend.repository.TransportDeviceRepository;
import com.medicalcoldchain.backend.repository.UserAccountRepository;
import com.medicalcoldchain.backend.service.AuthService;
import com.medicalcoldchain.backend.service.BorrowLimitService;
import com.medicalcoldchain.backend.service.DeviceService;
import com.medicalcoldchain.backend.service.DeviceSimulationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class MedicalColdChainBackendApplicationTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private BorrowLimitService borrowLimitService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private TransportDeviceRepository transportDeviceRepository;

    @Autowired
    private DeviceSimulationService deviceSimulationService;

    @Test
    void newUserShouldContinueRegistrationAfterConfirmation() {
        String phone = "139" + String.format("%08d", System.currentTimeMillis() % 100000000L);

        SendCodeRequest checkOnlyRequest = new SendCodeRequest();
        checkOnlyRequest.setPhone(phone);
        checkOnlyRequest.setCheckOnly(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.sendCode(checkOnlyRequest));
        assertEquals("NEW_USER", exception.getMessage());
        assertFalse(userAccountRepository.existsByPhone(phone));

        SendCodeRequest confirmRegistrationRequest = new SendCodeRequest();
        confirmRegistrationRequest.setPhone(phone);
        confirmRegistrationRequest.setCheckOnly(false);

        SendCodeResponse response = authService.sendCode(confirmRegistrationRequest);
        assertNotNull(response);
        assertEquals(phone, response.getPhone());
        assertNotNull(response.getCode());
        assertTrue(response.getCode().matches("\\d{6}"));
        assertTrue(userAccountRepository.existsByPhone(phone));
    }

    @Test
    void adminShouldSeeBorrowRecords() {
        UserAccount admin = userAccountRepository.findByPhone("18800000000").orElseThrow();
        String phone = "137" + String.format("%08d", System.currentTimeMillis() % 100000000L);
        UserAccount dispatcher = userAccountRepository.save(UserAccount.builder()
                .phone(phone)
                .name("测试调度员")
                .organization("测试调度中心")
                .role(UserRole.USER)
                .build());

        assertTrue(authService.isAdmin(admin));

        ApplyDeviceRequest request = new ApplyDeviceRequest();
        request.setCount(1);
        deviceService.applyDevices(dispatcher, request);

        List<DeviceBorrowRecordResponse> records = deviceService.listBorrowRecords(null, "BORROWED");
        assertFalse(records.isEmpty());
        assertTrue(records.stream().anyMatch(record ->
                dispatcher.getPhone().equals(record.getBorrowerPhone())
                        && "BORROWED".equals(record.getStatus())
                        && record.getBorrowTime() != null));
    }

    @Test
    void adminShouldSeeCurrentUsersAndForceReturnDevices() {
        UserAccount admin = userAccountRepository.findByPhone("18800000000").orElseThrow();
        String phone = "136" + String.format("%08d", System.currentTimeMillis() % 100000000L);
        UserAccount dispatcher = userAccountRepository.save(UserAccount.builder()
                .phone(phone)
                .name("强制归还测试调度员")
                .organization("测试调度中心")
                .role(UserRole.USER)
                .build());

        assertTrue(authService.isAdmin(admin));

        ApplyDeviceRequest request = new ApplyDeviceRequest();
        request.setCount(2);
        deviceService.applyDevices(dispatcher, request);

        List<AdminUserSummaryResponse> users = deviceService.listUserSummaries();
        AdminUserSummaryResponse dispatcherSummary = users.stream()
                .filter(item -> dispatcher.getPhone().equals(item.getPhone()))
                .findFirst()
                .orElseThrow();

        assertEquals(2, dispatcherSummary.getInUseDeviceCount());
        assertEquals(2, dispatcherSummary.getActiveDeviceCodes().size());
        assertNotNull(dispatcherSummary.getLatestBorrowTime());

        AdminForceReturnRequest forceReturnRequest = new AdminForceReturnRequest();
        forceReturnRequest.setUserId(dispatcher.getId());

        String result = deviceService.forceReturnDevices(forceReturnRequest);
        assertEquals("已强制归还 2 台设备", result);
        assertTrue(deviceService.listMyDevices(dispatcher).isEmpty());

        List<DeviceBorrowRecordResponse> returnedRecords = deviceService.listBorrowRecords(dispatcher.getPhone(), "RETURNED");
        assertTrue(returnedRecords.stream().anyMatch(record ->
                dispatcher.getPhone().equals(record.getBorrowerPhone())
                        && "RETURNED".equals(record.getStatus())
                        && record.getReturnTime() != null));
    }

    @Test
    void latestTelemetryAndMonitorShouldReturnSnapshotAfterApply() {
        String phone = "135" + String.format("%08d", System.currentTimeMillis() % 100000000L);
        UserAccount dispatcher = userAccountRepository.save(UserAccount.builder()
                .phone(phone)
                .name("实时监测测试调度员")
                .organization("测试调度中心")
                .role(UserRole.USER)
                .build());

        ApplyDeviceRequest request = new ApplyDeviceRequest();
        request.setCount(1);
        deviceService.applyDevices(dispatcher, request);

        List<LatestDeviceTelemetryResponse> latestTelemetry = deviceService.getLatestTelemetry(dispatcher);
        assertEquals(1, latestTelemetry.size());

        LatestDeviceTelemetryResponse latest = latestTelemetry.get(0);
        assertNotNull(latest.getTelemetry());
        assertNotNull(latest.getLocation());
        assertNotNull(latest.getThreshold());
        assertNotNull(latest.getTelemetry().getRecordedAt());
        assertNotNull(latest.getLocation().getRecordedAt());

        LatestDeviceTelemetryResponse monitor = deviceService.getMonitorTelemetry(dispatcher, latest.getDeviceId());
        assertEquals(latest.getDeviceId(), monitor.getDeviceId());
        assertNotNull(monitor.getTelemetry());
        assertNotNull(monitor.getLocation());
        assertNotNull(monitor.getThreshold());
    }

    @Test
    void applyDevicesShouldRespectDefaultBorrowLimit() {
        UserAccount dispatcher = createBorrowLimitTestUser("134", "默认上限测试调度员");

        assertEquals(3, borrowLimitService.getDefaultLimit());
        assertEquals(3, deviceService.applyDevices(dispatcher, applyRequest(3)).size());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> deviceService.applyDevices(dispatcher, applyRequest(1)));
        assertEquals("当前最多可借 3 台设备，已借 3 台，本次最多还能申请 0 台", exception.getMessage());
    }

    @Test
    void adminShouldUpdateGlobalBorrowLimit() {
        UserAccount dispatcher = createBorrowLimitTestUser("133", "全局上限测试调度员");

        BorrowLimitRequest limitRequest = new BorrowLimitRequest();
        limitRequest.setLimit(4);
        BorrowLimitOverviewResponse overview = borrowLimitService.updateDefaultLimit(limitRequest);

        assertEquals(4, overview.getDefaultLimit());
        assertEquals(4, deviceService.applyDevices(dispatcher, applyRequest(4)).size());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> deviceService.applyDevices(dispatcher, applyRequest(1)));
        assertEquals("当前最多可借 4 台设备，已借 4 台，本次最多还能申请 0 台", exception.getMessage());

        limitRequest.setLimit(3);
        borrowLimitService.updateDefaultLimit(limitRequest);
    }

    @Test
    void adminShouldSetUserBorrowLimitOverride() {
        UserAccount dispatcher = createBorrowLimitTestUser("132", "用户覆盖上限测试调度员");

        UserBorrowLimitRequest overrideRequest = new UserBorrowLimitRequest();
        overrideRequest.setLimit(2);
        BorrowLimitOverviewResponse overview = borrowLimitService.updateUserBorrowLimit(dispatcher.getId(), overrideRequest);

        assertTrue(overview.getUsers().stream().anyMatch(user ->
                dispatcher.getId().equals(user.getUserId())
                        && Integer.valueOf(2).equals(user.getBorrowLimitOverride())
                        && user.getEffectiveBorrowLimit() == 2));

        assertEquals(2, deviceService.applyDevices(dispatcher, applyRequest(2)).size());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> deviceService.applyDevices(dispatcher, applyRequest(1)));
        assertEquals("当前最多可借 2 台设备，已借 2 台，本次最多还能申请 0 台", exception.getMessage());
    }

    @Test
    void adminShouldClearUserBorrowLimitOverride() {
        UserAccount dispatcher = createBorrowLimitTestUser("131", "清除覆盖上限测试调度员");

        UserBorrowLimitRequest overrideRequest = new UserBorrowLimitRequest();
        overrideRequest.setLimit(1);
        borrowLimitService.updateUserBorrowLimit(dispatcher.getId(), overrideRequest);

        UserBorrowLimitRequest clearRequest = new UserBorrowLimitRequest();
        clearRequest.setLimit(null);
        BorrowLimitOverviewResponse overview = borrowLimitService.updateUserBorrowLimit(dispatcher.getId(), clearRequest);

        assertTrue(overview.getUsers().stream().anyMatch(user ->
                dispatcher.getId().equals(user.getUserId())
                        && user.getBorrowLimitOverride() == null
                        && user.getEffectiveBorrowLimit() == borrowLimitService.getDefaultLimit()));

        assertEquals(3, deviceService.applyDevices(dispatcher, applyRequest(3)).size());
    }

    @Test
    void overviewShouldReturnRemainingBorrowableDeviceCount() {
        UserAccount dispatcher = createBorrowLimitTestUser("130", "剩余可借数量测试调度员");

        assertEquals(3, deviceService.getOverview(dispatcher).getAvailableCount());

        deviceService.applyDevices(dispatcher, applyRequest(1));

        assertEquals(2, deviceService.getOverview(dispatcher).getAvailableCount());
    }

    @Test
    void simulatedTelemetryShouldChangeSmoothlyBetweenMinutes() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 21, 0, 0);
        List<String> deviceCodes = List.of("MCC-SMOOTH-001", "MCC-001", "MCC-002", "MCC-003");

        for (String deviceCode : deviceCodes) {
            DeviceSimulationService.SimulatedTelemetry previous = deviceSimulationService.simulateTelemetry(deviceCode, start);
            int failedSignalCount = previous.signalStatus() ? 0 : 1;
            int temperatureAlarmCount = isTemperatureAlarm(previous) ? 1 : 0;
            int humidityAlarmCount = isHumidityAlarm(previous) ? 1 : 0;
            int lightAlarmCount = isLightAlarm(previous) ? 1 : 0;
            int alarmCount = isAlarm(previous) ? 1 : 0;
            int sampleCount = 1;

            assertTrue(previous.temperature() >= 0.5 && previous.temperature() <= 10.5,
                    "temperature out of realistic cold-chain range at minute 0 for " + deviceCode + ": " + previous.temperature());
            assertTrue(previous.humidity() >= 42 && previous.humidity() <= 76,
                    "humidity out of realistic cold-chain range at minute 0 for " + deviceCode + ": " + previous.humidity());
            assertTrue(previous.light() >= 0 && previous.light() <= 18,
                    "light out of realistic cold-chain range at minute 0 for " + deviceCode + ": " + previous.light());

            for (int minute = 1; minute <= 6 * 60; minute++) {
                DeviceSimulationService.SimulatedTelemetry current = deviceSimulationService
                        .simulateTelemetry(deviceCode, start.plusMinutes(minute));
                sampleCount++;
                if (!current.signalStatus()) {
                    failedSignalCount++;
                }
                if (isTemperatureAlarm(current)) {
                    temperatureAlarmCount++;
                }
                if (isHumidityAlarm(current)) {
                    humidityAlarmCount++;
                }
                if (isLightAlarm(current)) {
                    lightAlarmCount++;
                }
                if (isAlarm(current)) {
                    alarmCount++;
                }

                assertTrue(Math.abs(current.temperature() - previous.temperature()) <= 0.9,
                        "temperature jumped at minute " + minute + " for " + deviceCode);
                assertTrue(Math.abs(current.humidity() - previous.humidity()) <= 0.9,
                        "humidity jumped at minute " + minute + " for " + deviceCode);
                assertTrue(Math.abs(current.light() - previous.light()) <= 1.6,
                        "light jumped at minute " + minute + " for " + deviceCode);

                assertTrue(current.temperature() >= 0.5 && current.temperature() <= 10.5,
                        "temperature out of realistic cold-chain range at minute " + minute + " for " + deviceCode + ": " + current.temperature());
                assertTrue(current.humidity() >= 42 && current.humidity() <= 76,
                        "humidity out of realistic cold-chain range at minute " + minute + " for " + deviceCode + ": " + current.humidity());
                assertTrue(current.light() >= 0 && current.light() <= 18,
                        "light out of realistic cold-chain range at minute " + minute + " for " + deviceCode + ": " + current.light());

                previous = current;
            }

            assertTrue(failedSignalCount > 0, "expected occasional simulated signal failures for " + deviceCode);
            assertTrue(failedSignalCount < sampleCount * 0.10,
                    "signal failures should remain sparse for " + deviceCode + ", but got " + failedSignalCount + " of " + sampleCount);
            assertTrue(temperatureAlarmCount < sampleCount * 0.08,
                    "temperature alarms should remain uncommon for " + deviceCode + ", but got " + temperatureAlarmCount + " of " + sampleCount);
            assertTrue(humidityAlarmCount < sampleCount * 0.08,
                    "humidity alarms should remain uncommon for " + deviceCode + ", but got " + humidityAlarmCount + " of " + sampleCount);
            assertTrue(lightAlarmCount < sampleCount * 0.06,
                    "light alarms should remain uncommon for " + deviceCode + ", but got " + lightAlarmCount + " of " + sampleCount);
            assertTrue(alarmCount < sampleCount * 0.12,
                    "overall alarm moments should remain uncommon for " + deviceCode + ", but got " + alarmCount + " of " + sampleCount);
        }
    }

    @Test
    void simulatedLocationShouldKeepPrecisionAndMoveSmoothly() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 21, 0, 0);
        DeviceSimulationService.SimulatedLocation previous = deviceSimulationService
                .simulateLocation("MCC-LOC-001", "测试线路", start);

        assertTrue(Math.abs(previous.longitude() - roundToTwoDecimals(previous.longitude())) > 0.00001,
                "longitude should retain more than two decimal places");
        assertTrue(Math.abs(previous.latitude() - roundToTwoDecimals(previous.latitude())) > 0.00001,
                "latitude should retain more than two decimal places");

        for (int minute = 1; minute <= 6 * 60; minute++) {
            DeviceSimulationService.SimulatedLocation current = deviceSimulationService
                    .simulateLocation("MCC-LOC-001", "测试线路", start.plusMinutes(minute));

            assertTrue(Math.abs(current.longitude() - previous.longitude()) <= 0.001,
                    "longitude moved too abruptly at minute " + minute);
            assertTrue(Math.abs(current.latitude() - previous.latitude()) <= 0.001,
                    "latitude moved too abruptly at minute " + minute);

            previous = current;
        }
    }

    @Test
    void overviewShouldRestoreBorrowableCountAfterReturningAllDevices() {
        UserAccount dispatcher = createBorrowLimitTestUser("129", "归还恢复可借数量测试调度员");

        deviceService.applyDevices(dispatcher, applyRequest(3));
        assertEquals(0, deviceService.getOverview(dispatcher).getAvailableCount());

        deviceService.returnDevices(dispatcher, null);

        assertEquals(3, deviceService.getOverview(dispatcher).getAvailableCount());
    }

    @Test
    void newDeviceThresholdShouldUseNarrowDefaultRange() {
        UserAccount dispatcher = createBorrowLimitTestUser("128", "默认阈值测试调度员");

        deviceService.applyDevices(dispatcher, applyRequest(1));
        Long deviceId = deviceService.listMyDevices(dispatcher).get(0).getId();
        ThresholdResponse threshold = deviceService.getThreshold(dispatcher, deviceId);

        assertEquals(3D, threshold.getTempMin());
        assertEquals(7D, threshold.getTempMax());
        assertEquals(45D, threshold.getHumidityMin());
        assertEquals(70D, threshold.getHumidityMax());
        assertEquals(9D, threshold.getLightMax());
    }

    @Test
    void adminShouldDeleteOrdinaryUserWithoutBorrowedDevices() {
        UserAccount dispatcher = createBorrowLimitTestUser("127", "删除用户测试调度员");

        BorrowLimitOverviewResponse overview = borrowLimitService.deleteUser(dispatcher.getId());

        assertFalse(userAccountRepository.existsById(dispatcher.getId()));
        assertTrue(overview.getUsers().stream().noneMatch(user -> dispatcher.getId().equals(user.getUserId())));
    }

    @Test
    void adminShouldNotDeleteMissingUser() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> borrowLimitService.deleteUser(-1L));

        assertEquals("用户不存在", exception.getMessage());
    }

    @Test
    void adminShouldNotDeleteSuperAdminUser() {
        UserAccount admin = userAccountRepository.findByPhone("18800000000").orElseThrow();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> borrowLimitService.deleteUser(admin.getId()));

        assertEquals("不能删除超级管理员", exception.getMessage());
        assertTrue(userAccountRepository.existsById(admin.getId()));
    }

    @Test
    void adminShouldNotDeleteUserWithBorrowedDevices() {
        UserAccount dispatcher = createBorrowLimitTestUser("126", "有设备删除拦截测试调度员");
        deviceService.applyDevices(dispatcher, applyRequest(1));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> borrowLimitService.deleteUser(dispatcher.getId()));

        assertEquals("该用户仍有在用设备，请先强制归还后再删除", exception.getMessage());
        assertTrue(userAccountRepository.existsById(dispatcher.getId()));
        assertEquals(1, transportDeviceRepository.findByCurrentUserIdOrderByDeviceCodeAsc(dispatcher.getId()).size());
    }

    private boolean isAlarm(DeviceSimulationService.SimulatedTelemetry telemetry) {
        return isTemperatureAlarm(telemetry)
                || isHumidityAlarm(telemetry)
                || isLightAlarm(telemetry)
                || !telemetry.signalStatus();
    }

    private boolean isTemperatureAlarm(DeviceSimulationService.SimulatedTelemetry telemetry) {
        return telemetry.temperature() < 3 || telemetry.temperature() > 7;
    }

    private boolean isHumidityAlarm(DeviceSimulationService.SimulatedTelemetry telemetry) {
        return telemetry.humidity() < 45 || telemetry.humidity() > 70;
    }

    private boolean isLightAlarm(DeviceSimulationService.SimulatedTelemetry telemetry) {
        return telemetry.light() > 9;
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private UserAccount createBorrowLimitTestUser(String prefix, String name) {
        String phone = prefix + String.format("%08d", System.nanoTime() % 100000000L);
        return userAccountRepository.save(UserAccount.builder()
                .phone(phone)
                .name(name)
                .organization("借用限制测试中心")
                .role(UserRole.USER)
                .build());
    }

    private ApplyDeviceRequest applyRequest(int count) {
        ApplyDeviceRequest request = new ApplyDeviceRequest();
        request.setCount(count);
        return request;
    }
}
