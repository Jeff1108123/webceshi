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
import com.medicalcoldchain.backend.dto.telemetry.LatestDeviceTelemetryResponse;
import com.medicalcoldchain.backend.entity.UserAccount;
import com.medicalcoldchain.backend.enums.UserRole;
import com.medicalcoldchain.backend.exception.BusinessException;
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
        DeviceSimulationService.SimulatedTelemetry previous = deviceSimulationService.simulateTelemetry("MCC-SMOOTH-001", start);

        for (int minute = 1; minute <= 24 * 60; minute++) {
            DeviceSimulationService.SimulatedTelemetry current = deviceSimulationService
                    .simulateTelemetry("MCC-SMOOTH-001", start.plusMinutes(minute));

            assertTrue(Math.abs(current.temperature() - previous.temperature()) <= 0.8,
                    "temperature jumped at minute " + minute);
            assertTrue(Math.abs(current.light() - previous.light()) <= 1.2,
                    "light jumped at minute " + minute);
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
