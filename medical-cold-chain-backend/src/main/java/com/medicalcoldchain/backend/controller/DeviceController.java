package com.medicalcoldchain.backend.controller;

import com.medicalcoldchain.backend.common.ApiResponse;
import com.medicalcoldchain.backend.dto.admin.AdminForceReturnRequest;
import com.medicalcoldchain.backend.dto.admin.AdminUserSummaryResponse;
import com.medicalcoldchain.backend.dto.admin.BorrowLimitOverviewResponse;
import com.medicalcoldchain.backend.dto.admin.BorrowLimitRequest;
import com.medicalcoldchain.backend.dto.admin.UserBorrowLimitRequest;
import com.medicalcoldchain.backend.dto.device.ApplyDeviceRequest;
import com.medicalcoldchain.backend.dto.device.DeviceBorrowRecordResponse;
import com.medicalcoldchain.backend.dto.device.DeviceCardResponse;
import com.medicalcoldchain.backend.dto.device.DeviceOverviewResponse;
import com.medicalcoldchain.backend.dto.device.ReturnDeviceRequest;
import com.medicalcoldchain.backend.dto.device.ThresholdRequest;
import com.medicalcoldchain.backend.dto.device.ThresholdResponse;
import com.medicalcoldchain.backend.dto.location.DeviceLocationResponse;
import com.medicalcoldchain.backend.dto.telemetry.HistoryResponse;
import com.medicalcoldchain.backend.dto.telemetry.LatestDeviceTelemetryResponse;
import com.medicalcoldchain.backend.entity.UserAccount;
import com.medicalcoldchain.backend.service.AuthService;
import com.medicalcoldchain.backend.service.BorrowLimitService;
import com.medicalcoldchain.backend.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/devices")
public class DeviceController {

    private final AuthService authService;
    private final DeviceService deviceService;
    private final BorrowLimitService borrowLimitService;

    @GetMapping("/overview")
    public ApiResponse<DeviceOverviewResponse> overview(@RequestHeader("Authorization") String authorization) {
        UserAccount user = authService.requireUser(authorization);
        return ApiResponse.ok(deviceService.getOverview(user));
    }

    @GetMapping("/mine")
    public ApiResponse<List<DeviceCardResponse>> mine(@RequestHeader("Authorization") String authorization) {
        UserAccount user = authService.requireUser(authorization);
        return ApiResponse.ok(deviceService.listMyDevices(user));
    }

    @GetMapping("/users")
    public ApiResponse<List<AdminUserSummaryResponse>> users(@RequestHeader("Authorization") String authorization) {
        UserAccount user = authService.requireUser(authorization);
        authService.requireAdmin(user);
        return ApiResponse.ok(deviceService.listUserSummaries());
    }

    @GetMapping("/borrow-records")
    public ApiResponse<List<DeviceBorrowRecordResponse>> borrowRecords(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        UserAccount user = authService.requireUser(authorization);
        authService.requireAdmin(user);
        return ApiResponse.ok(deviceService.listBorrowRecords(keyword, status));
    }

    @GetMapping("/borrow-limits")
    public ApiResponse<BorrowLimitOverviewResponse> borrowLimits(@RequestHeader("Authorization") String authorization) {
        UserAccount user = authService.requireUser(authorization);
        authService.requireAdmin(user);
        return ApiResponse.ok(borrowLimitService.getOverview());
    }

    @PutMapping("/borrow-limits/default")
    public ApiResponse<BorrowLimitOverviewResponse> updateDefaultBorrowLimit(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody BorrowLimitRequest request) {
        UserAccount user = authService.requireUser(authorization);
        authService.requireAdmin(user);
        return ApiResponse.ok("全局借用上限已保存", borrowLimitService.updateDefaultLimit(request));
    }

    @PutMapping("/users/{userId}/borrow-limit")
    public ApiResponse<BorrowLimitOverviewResponse> updateUserBorrowLimit(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long userId,
            @Valid @RequestBody UserBorrowLimitRequest request) {
        UserAccount user = authService.requireUser(authorization);
        authService.requireAdmin(user);
        return ApiResponse.ok("用户借用上限已保存", borrowLimitService.updateUserBorrowLimit(userId, request));
    }

    @PostMapping("/apply")
    public ApiResponse<List<DeviceCardResponse>> apply(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ApplyDeviceRequest request) {
        UserAccount user = authService.requireUser(authorization);
        return ApiResponse.ok("设备申请成功", deviceService.applyDevices(user, request));
    }

    @PostMapping("/return")
    public ApiResponse<List<DeviceCardResponse>> returnDevices(
            @RequestHeader("Authorization") String authorization,
            @RequestBody(required = false) ReturnDeviceRequest request) {
        UserAccount user = authService.requireUser(authorization);
        String message = deviceService.returnDevices(user, request);
        return ApiResponse.ok(message, deviceService.listMyDevices(user));
    }

    @PostMapping("/force-return")
    public ApiResponse<String> forceReturnDevices(
            @RequestHeader("Authorization") String authorization,
            @RequestBody(required = false) AdminForceReturnRequest request) {
        UserAccount user = authService.requireUser(authorization);
        authService.requireAdmin(user);
        String message = deviceService.forceReturnDevices(request);
        return ApiResponse.ok(message, message);
    }

    @GetMapping("/{deviceId}/threshold")
    public ApiResponse<ThresholdResponse> getThreshold(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long deviceId) {
        UserAccount user = authService.requireUser(authorization);
        return ApiResponse.ok(deviceService.getThreshold(user, deviceId));
    }

    @PutMapping("/{deviceId}/threshold")
    public ApiResponse<ThresholdResponse> saveThreshold(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long deviceId,
            @Valid @RequestBody ThresholdRequest request) {
        UserAccount user = authService.requireUser(authorization);
        return ApiResponse.ok("阈值保存成功", deviceService.saveThreshold(user, deviceId, request));
    }

    @GetMapping("/latest")
    public ApiResponse<List<LatestDeviceTelemetryResponse>> latest(
            @RequestHeader("Authorization") String authorization) {
        UserAccount user = authService.requireUser(authorization);
        return ApiResponse.ok(deviceService.getLatestTelemetry(user));
    }

    @GetMapping("/{deviceId}/monitor")
    public ApiResponse<LatestDeviceTelemetryResponse> monitor(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long deviceId) {
        UserAccount user = authService.requireUser(authorization);
        return ApiResponse.ok(deviceService.getMonitorTelemetry(user, deviceId));
    }

    @GetMapping("/{deviceId}/history")
    public ApiResponse<HistoryResponse> history(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long deviceId,
            @RequestParam(required = false) Integer hours) {
        UserAccount user = authService.requireUser(authorization);
        return ApiResponse.ok(deviceService.getHistory(user, deviceId, hours));
    }

    @GetMapping("/{deviceId}/location")
    public ApiResponse<DeviceLocationResponse> location(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long deviceId) {
        UserAccount user = authService.requireUser(authorization);
        return ApiResponse.ok(deviceService.getLatestLocation(user, deviceId));
    }
}
