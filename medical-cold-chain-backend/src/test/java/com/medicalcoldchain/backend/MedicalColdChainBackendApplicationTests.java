package com.medicalcoldchain.backend;

import com.medicalcoldchain.backend.dto.admin.AdminForceReturnRequest;
import com.medicalcoldchain.backend.dto.admin.AdminUserSummaryResponse;
import com.medicalcoldchain.backend.dto.admin.BorrowLimitOverviewResponse;
import com.medicalcoldchain.backend.dto.admin.BorrowLimitRequest;
import com.medicalcoldchain.backend.dto.admin.UserBorrowLimitRequest;
import com.medicalcoldchain.backend.dto.auth.LoginRequest;
import com.medicalcoldchain.backend.dto.auth.LoginResponse;
import com.medicalcoldchain.backend.dto.auth.SendCodeRequest;
import com.medicalcoldchain.backend.dto.auth.SendCodeResponse;
import com.medicalcoldchain.backend.dto.device.ApplyDeviceRequest;
import com.medicalcoldchain.backend.dto.device.DeviceBorrowRecordResponse;
import com.medicalcoldchain.backend.dto.device.ThresholdResponse;
import com.medicalcoldchain.backend.dto.telemetry.HistoryResponse;
import com.medicalcoldchain.backend.dto.telemetry.LatestDeviceTelemetryResponse;
import com.medicalcoldchain.backend.dto.telemetry.ManualTelemetryRequest;
import com.medicalcoldchain.backend.dto.telemetry.TelemetryPointResponse;
import com.medicalcoldchain.backend.entity.TelemetryRecord;
import com.medicalcoldchain.backend.entity.TransportDevice;
import com.medicalcoldchain.backend.entity.UserAccount;
import com.medicalcoldchain.backend.enums.UserRole;
import com.medicalcoldchain.backend.exception.BusinessException;
import com.medicalcoldchain.backend.repository.DeviceBorrowRecordRepository;
import com.medicalcoldchain.backend.repository.DeviceLocationRepository;
import com.medicalcoldchain.backend.repository.TelemetryRecordRepository;
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
    private TelemetryRecordRepository telemetryRecordRepository;

    @Autowired
    private DeviceBorrowRecordRepository deviceBorrowRecordRepository;

    @Autowired
    private DeviceLocationRepository deviceLocationRepository;

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
    void repeatedLoginShouldRequireKickConfirmationBeforeInvalidatingPreviousSession() {
        String phone = "138" + String.format("%08d", System.nanoTime() % 100000000L);
        userAccountRepository.save(UserAccount.builder()
                .phone(phone)
                .name("单点登录测试调度员")
                .organization("测试调度中心")
                .role(UserRole.USER)
                .build());

        SendCodeResponse firstCode = sendLoginCode(phone);
        LoginResponse firstLogin = authService.login(loginRequest(phone, firstCode.getCode(), false));
        assertNotNull(authService.requireUser("Bearer " + firstLogin.getToken()));

        SendCodeResponse secondCode = sendLoginCode(phone);
        BusinessException conflict = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest(phone, secondCode.getCode(), false)));
        assertEquals("该账号已在其他地方登录，是否踢下线并继续登录？", conflict.getMessage());
        assertNotNull(authService.requireUser("Bearer " + firstLogin.getToken()));

        LoginResponse secondLogin = authService.login(loginRequest(phone, secondCode.getCode(), true));
        assertNotNull(authService.requireUser("Bearer " + secondLogin.getToken()));

        BusinessException expired = assertThrows(BusinessException.class,
                () -> authService.requireUser("Bearer " + firstLogin.getToken()));
        assertEquals("登录状态已失效，请重新登录", expired.getMessage());
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
        DeviceBorrowRecordResponse dispatcherRecord = records.stream()
                .filter(record -> dispatcher.getPhone().equals(record.getBorrowerPhone()))
                .findFirst()
                .orElseThrow();
        assertEquals("BORROWED", dispatcherRecord.getStatus());
        assertNotNull(dispatcherRecord.getBorrowTime());
        assertNotNull(dispatcherRecord.getThreshold());
        assertEquals(20D, dispatcherRecord.getThreshold().getTempMin());
        assertEquals(30D, dispatcherRecord.getThreshold().getTempMax());
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

            assertTrue(previous.temperature() >= 20 && previous.temperature() <= 30,
                    "temperature out of expected range at minute 0 for " + deviceCode + ": " + previous.temperature());
            assertTrue(previous.humidity() >= 40 && previous.humidity() <= 70,
                    "humidity out of expected range at minute 0 for " + deviceCode + ": " + previous.humidity());
            assertTrue(previous.light() >= 7 && previous.light() <= 13,
                    "light out of expected range at minute 0 for " + deviceCode + ": " + previous.light());
            assertTrue(previous.signalStatus(), "simulated signal should stay normal for " + deviceCode);

            for (int minute = 1; minute <= 6 * 60; minute++) {
                DeviceSimulationService.SimulatedTelemetry current = deviceSimulationService
                        .simulateTelemetry(deviceCode, start.plusMinutes(minute));

                assertTrue(Math.abs(current.temperature() - previous.temperature()) <= 0.25,
                        "temperature jumped at minute " + minute + " for " + deviceCode);
                assertTrue(Math.abs(current.humidity() - previous.humidity()) <= 0.4,
                        "humidity jumped at minute " + minute + " for " + deviceCode);
                assertTrue(Math.abs(current.light() - previous.light()) <= 0.25,
                        "light jumped at minute " + minute + " for " + deviceCode);

                assertTrue(current.temperature() >= 20 && current.temperature() <= 30,
                        "temperature out of expected range at minute " + minute + " for " + deviceCode + ": " + current.temperature());
                assertTrue(current.humidity() >= 40 && current.humidity() <= 70,
                        "humidity out of expected range at minute " + minute + " for " + deviceCode + ": " + current.humidity());
                assertTrue(current.light() >= 7 && current.light() <= 13,
                        "light out of expected range at minute " + minute + " for " + deviceCode + ": " + current.light());
                assertTrue(current.signalStatus(), "simulated signal should stay normal for " + deviceCode);

                previous = current;
            }
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
    void newBorrowRecordThresholdShouldUseDefaultRange() {
        UserAccount dispatcher = createBorrowLimitTestUser("128", "默认阈值测试调度员");

        deviceService.applyDevices(dispatcher, applyRequest(1));
        Long deviceId = deviceService.listMyDevices(dispatcher).get(0).getId();
        ThresholdResponse threshold = deviceService.getThreshold(dispatcher, deviceId);
        var borrowRecord = deviceBorrowRecordRepository
                .findTopByDeviceIdAndBorrowerIdAndReturnTimeIsNullOrderByBorrowTimeDesc(deviceId, dispatcher.getId())
                .orElseThrow();

        assertEquals(borrowRecord.getId(), threshold.getId());
        assertEquals(20D, threshold.getTempMin());
        assertEquals(30D, threshold.getTempMax());
        assertEquals(40D, threshold.getHumidityMin());
        assertEquals(70D, threshold.getHumidityMax());
        assertEquals(13D, threshold.getLightMax());
        assertEquals(threshold.getTempMin(), borrowRecord.getTempMin());
        assertEquals(threshold.getTempMax(), borrowRecord.getTempMax());
    }

    @Test
    void historyShouldReturnBackendAggregatedPointsForRequestedStep() {
        UserAccount dispatcher = createBorrowLimitTestUser("124", "历史密度测试调度员");
        deviceService.applyDevices(dispatcher, applyRequest(1));
        Long deviceId = deviceService.listMyDevices(dispatcher).get(0).getId();

        HistoryResponse history = deviceService.getHistory(dispatcher, deviceId, 24, 15);

        assertEquals(24, history.getHours());
        assertEquals(15, history.getStepMinutes());
        assertNotNull(history.getPoints());
        assertFalse(history.getPoints().isEmpty());
        assertTrue(history.getPoints().size() < 24 * 60);
        assertTrue(history.getPoints().size() <= 24 * 4 + 2);
    }

    @Test
    void manualTelemetryRecordShouldBeVisibleInLatestMonitorAndHistory() {
        UserAccount dispatcher = createBorrowLimitTestUser("123", "手动实时数据测试调度员");
        deviceService.applyDevices(dispatcher, applyRequest(1));
        Long deviceId = deviceService.listMyDevices(dispatcher).get(0).getId();
        TransportDevice device = transportDeviceRepository.findById(deviceId).orElseThrow();
        LocalDateTime manualRecordedAt = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

        telemetryRecordRepository.save(TelemetryRecord.builder()
                .device(device)
                .temperature(2.34)
                .humidity(66.7)
                .light(12.5)
                .batteryLevel(88)
                .signalStatus(false)
                .recordedAt(manualRecordedAt)
                .build());

        long beforeLatestQueryCount = telemetryRecordRepository.count();
        LatestDeviceTelemetryResponse latest = deviceService.getLatestTelemetry(dispatcher).get(0);
        long afterLatestQueryCount = telemetryRecordRepository.count();
        assertEquals(beforeLatestQueryCount + 1, afterLatestQueryCount);
        assertNotNull(latest.getTelemetry().getId());
        assertTrue(telemetryRecordRepository.existsById(latest.getTelemetry().getId()));
        assertNotNull(latest.getTelemetry().getRecordedAt());
        assertFalse(manualRecordedAt.equals(latest.getTelemetry().getRecordedAt()));

        long beforeMonitorQueryCount = telemetryRecordRepository.count();
        LatestDeviceTelemetryResponse monitor = deviceService.getMonitorTelemetry(dispatcher, deviceId);
        long afterMonitorQueryCount = telemetryRecordRepository.count();
        assertEquals(beforeMonitorQueryCount + 1, afterMonitorQueryCount);
        assertNotNull(monitor.getTelemetry().getId());
        assertTrue(telemetryRecordRepository.existsById(monitor.getTelemetry().getId()));
        assertNotNull(monitor.getTelemetry().getRecordedAt());

        HistoryResponse history = deviceService.getHistory(dispatcher, deviceId, 1, 1);
        assertTrue(history.getPoints().stream().anyMatch(point -> manualRecordedAt.equals(point.getRecordedAt())
                && Double.valueOf(2.34).equals(point.getTemperature())
                && Double.valueOf(66.7).equals(point.getHumidity())
                && Double.valueOf(12.5).equals(point.getLight())
                && Integer.valueOf(88).equals(point.getBatteryLevel())
                && Boolean.FALSE.equals(point.getSignalStatus())));
    }

    @Test
    void adminLatestTelemetryShouldRefreshAllInUseDevices() {
        UserAccount admin = userAccountRepository.findByPhone("18800000000").orElseThrow();
        UserAccount firstDispatcher = createBorrowLimitTestUser("122", "管理员实时刷新测试调度员A");
        UserAccount secondDispatcher = createBorrowLimitTestUser("121", "管理员实时刷新测试调度员B");
        deviceService.applyDevices(firstDispatcher, applyRequest(1));
        deviceService.applyDevices(secondDispatcher, applyRequest(1));

        List<Long> activeDeviceIds = transportDeviceRepository.findByStatusOrderByDeviceCodeAsc(com.medicalcoldchain.backend.enums.DeviceStatus.IN_USE)
                .stream()
                .map(TransportDevice::getId)
                .toList();
        long beforeLatestQueryCount = telemetryRecordRepository.count();

        List<LatestDeviceTelemetryResponse> latestTelemetry = deviceService.getLatestTelemetry(admin);

        assertEquals(activeDeviceIds.size(), latestTelemetry.size());
        assertEquals(beforeLatestQueryCount + activeDeviceIds.size(), telemetryRecordRepository.count());
        assertTrue(latestTelemetry.stream().map(LatestDeviceTelemetryResponse::getDeviceId).toList().containsAll(activeDeviceIds));
    }

    @Test
    void adminMonitorShouldRefreshAnyInUseDevice() {
        UserAccount admin = userAccountRepository.findByPhone("18800000000").orElseThrow();
        UserAccount dispatcher = createBorrowLimitTestUser("120", "管理员单设备实时刷新测试调度员");
        deviceService.applyDevices(dispatcher, applyRequest(1));
        Long deviceId = deviceService.listMyDevices(dispatcher).get(0).getId();
        long beforeMonitorQueryCount = telemetryRecordRepository.count();

        LatestDeviceTelemetryResponse monitor = deviceService.getMonitorTelemetry(admin, deviceId);

        assertEquals(deviceId, monitor.getDeviceId());
        assertNotNull(monitor.getTelemetry());
        assertNotNull(monitor.getLocation());
        assertEquals(beforeMonitorQueryCount + 1, telemetryRecordRepository.count());
    }

    @Test
    void returnDevicesShouldClearHistoryAndResetTelemetryIdWhenAllDevicesReturned() {
        forceReturnAllInUseDevices();
        deviceLocationRepository.deleteAllBy();
        telemetryRecordRepository.deleteAllBy();
        UserAccount dispatcher = createBorrowLimitTestUser("119", "归还清理历史测试调度员");

        deviceService.applyDevices(dispatcher, applyRequest(1));
        Long deviceId = deviceService.listMyDevices(dispatcher).get(0).getId();
        assertTrue(telemetryRecordRepository.findTopByDeviceIdOrderByRecordedAtDescIdDesc(deviceId).isPresent());
        assertTrue(deviceLocationRepository.findTopByDeviceIdOrderByRecordedAtDesc(deviceId).isPresent());

        deviceService.returnDevices(dispatcher, null);

        assertTrue(deviceService.listMyDevices(dispatcher).isEmpty());
        assertTrue(telemetryRecordRepository.findTopByDeviceIdOrderByRecordedAtDescIdDesc(deviceId).isEmpty());
        assertTrue(deviceLocationRepository.findTopByDeviceIdOrderByRecordedAtDesc(deviceId).isEmpty());
        assertEquals(0, telemetryRecordRepository.count());

        deviceService.applyDevices(dispatcher, applyRequest(1));
        Long firstTelemetryId = telemetryRecordRepository.findAll().stream()
                .map(TelemetryRecord::getId)
                .min(Long::compareTo)
                .orElseThrow();
        assertEquals(1L, firstTelemetryId);
    }

    @Test
    void forceReturnDevicesShouldClearReturnedDeviceHistory() {
        UserAccount admin = userAccountRepository.findByPhone("18800000000").orElseThrow();
        UserAccount dispatcher = createBorrowLimitTestUser("118", "强制归还清理历史测试调度员");
        deviceService.applyDevices(dispatcher, applyRequest(1));
        Long deviceId = deviceService.listMyDevices(dispatcher).get(0).getId();
        assertTrue(authService.isAdmin(admin));
        assertTrue(telemetryRecordRepository.findTopByDeviceIdOrderByRecordedAtDescIdDesc(deviceId).isPresent());

        AdminForceReturnRequest request = new AdminForceReturnRequest();
        request.setDeviceIds(List.of(deviceId));
        deviceService.forceReturnDevices(request);

        assertTrue(deviceService.listMyDevices(dispatcher).isEmpty());
        assertTrue(telemetryRecordRepository.findTopByDeviceIdOrderByRecordedAtDescIdDesc(deviceId).isEmpty());
        assertTrue(deviceLocationRepository.findTopByDeviceIdOrderByRecordedAtDesc(deviceId).isEmpty());
    }

    @Test
    void userAndAdminShouldManuallyAddHistoryForInUseDevices() {
        UserAccount admin = userAccountRepository.findByPhone("18800000000").orElseThrow();
        UserAccount owner = createBorrowLimitTestUser("117", "用户补录历史测试调度员");
        UserAccount anotherOwner = createBorrowLimitTestUser("116", "管理员补录历史测试调度员");
        deviceService.applyDevices(owner, applyRequest(1));
        deviceService.applyDevices(anotherOwner, applyRequest(1));
        Long ownedDeviceId = deviceService.listMyDevices(owner).get(0).getId();
        Long adminTargetDeviceId = deviceService.listMyDevices(anotherOwner).get(0).getId();

        LocalDateTime userRecordedAt = LocalDateTime.now().plusMinutes(1).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        TelemetryPointResponse userPoint = deviceService.addManualHistory(owner, ownedDeviceId,
                manualTelemetryRequest(userRecordedAt, 25.6, 58.2, 10.8, 86, false));

        TelemetryRecord userRecord = telemetryRecordRepository.findById(userPoint.getId()).orElseThrow();
        assertEquals(userRecordedAt, userRecord.getRecordedAt());
        assertEquals(25.6, userRecord.getTemperature());
        assertEquals(58.2, userRecord.getHumidity());
        assertEquals(10.8, userRecord.getLight());
        assertEquals(86, userRecord.getBatteryLevel());
        assertEquals(false, userRecord.getSignalStatus());
        assertTrue(deviceLocationRepository.findTopByDeviceIdOrderByRecordedAtDesc(ownedDeviceId).isPresent());

        LocalDateTime adminRecordedAt = LocalDateTime.now().plusMinutes(2).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        TelemetryPointResponse adminPoint = deviceService.addManualHistory(admin, adminTargetDeviceId,
                manualTelemetryRequest(adminRecordedAt, 24.2, 51.5, 9.6, 91, true));

        TelemetryRecord adminRecord = telemetryRecordRepository.findById(adminPoint.getId()).orElseThrow();
        assertEquals(adminRecordedAt, adminRecord.getRecordedAt());
        assertEquals(24.2, adminRecord.getTemperature());
        assertEquals(51.5, adminRecord.getHumidity());
        assertEquals(9.6, adminRecord.getLight());
        assertEquals(91, adminRecord.getBatteryLevel());
        assertEquals(true, adminRecord.getSignalStatus());
    }

    @Test
    void manualHistoryShouldRejectUnauthorizedOrReturnedDevices() {
        UserAccount admin = userAccountRepository.findByPhone("18800000000").orElseThrow();
        UserAccount owner = createBorrowLimitTestUser("115", "补录权限测试调度员A");
        UserAccount anotherOwner = createBorrowLimitTestUser("114", "补录权限测试调度员B");
        deviceService.applyDevices(owner, applyRequest(1));
        deviceService.applyDevices(anotherOwner, applyRequest(1));
        Long ownedDeviceId = deviceService.listMyDevices(owner).get(0).getId();
        Long otherDeviceId = deviceService.listMyDevices(anotherOwner).get(0).getId();

        BusinessException unauthorized = assertThrows(BusinessException.class,
                () -> deviceService.addManualHistory(owner, otherDeviceId,
                        manualTelemetryRequest(LocalDateTime.now(), 25, 55, 10, 90, true)));
        assertEquals("设备不存在或不属于当前用户", unauthorized.getMessage());

        deviceService.returnDevices(owner, null);

        BusinessException returned = assertThrows(BusinessException.class,
                () -> deviceService.addManualHistory(admin, ownedDeviceId,
                        manualTelemetryRequest(LocalDateTime.now(), 25, 55, 10, 90, true)));
        assertEquals("设备不存在或不属于当前用户", returned.getMessage());
    }

    @Test
    void adminShouldDeleteOrdinaryUserWithoutBorrowedDevices() {
        UserAccount dispatcher = createBorrowLimitTestUser("127", "删除用户测试调度员");

        BorrowLimitOverviewResponse overview = borrowLimitService.deleteUser(dispatcher.getId());

        assertFalse(userAccountRepository.existsById(dispatcher.getId()));
        assertTrue(overview.getUsers().stream().noneMatch(user -> dispatcher.getId().equals(user.getUserId())));
    }

    @Test
    void adminShouldDeleteOrdinaryUserAfterReturnedBorrowRecords() {
        UserAccount dispatcher = createBorrowLimitTestUser("125", "归还后删除测试调度员");
        deviceService.applyDevices(dispatcher, applyRequest(1));
        deviceService.returnDevices(dispatcher, null);

        assertTrue(deviceBorrowRecordRepository.countByBorrowerId(dispatcher.getId()) > 0);

        BorrowLimitOverviewResponse overview = borrowLimitService.deleteUser(dispatcher.getId());

        assertFalse(userAccountRepository.existsById(dispatcher.getId()));
        assertEquals(0, deviceBorrowRecordRepository.countByBorrowerId(dispatcher.getId()));
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

    private SendCodeResponse sendLoginCode(String phone) {
        SendCodeRequest request = new SendCodeRequest();
        request.setPhone(phone);
        request.setCheckOnly(true);
        return authService.sendCode(request);
    }

    private LoginRequest loginRequest(String phone, String code, boolean forceLogin) {
        LoginRequest request = new LoginRequest();
        request.setPhone(phone);
        request.setCode(code);
        request.setForceLogin(forceLogin);
        return request;
    }

    private ManualTelemetryRequest manualTelemetryRequest(
            LocalDateTime recordedAt,
            double temperature,
            double humidity,
            double light,
            int batteryLevel,
            boolean signalStatus) {
        ManualTelemetryRequest request = new ManualTelemetryRequest();
        request.setRecordedAt(recordedAt);
        request.setTemperature(temperature);
        request.setHumidity(humidity);
        request.setLight(light);
        request.setBatteryLevel(batteryLevel);
        request.setSignalStatus(signalStatus);
        return request;
    }

    private void forceReturnAllInUseDevices() {
        List<Long> deviceIds = transportDeviceRepository
                .findByStatusOrderByDeviceCodeAsc(com.medicalcoldchain.backend.enums.DeviceStatus.IN_USE)
                .stream()
                .map(TransportDevice::getId)
                .toList();
        if (deviceIds.isEmpty()) {
            return;
        }
        AdminForceReturnRequest request = new AdminForceReturnRequest();
        request.setDeviceIds(deviceIds);
        deviceService.forceReturnDevices(request);
    }
}
