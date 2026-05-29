package com.medicalcoldchain.backend.service;

import com.medicalcoldchain.backend.dto.admin.AdminForceReturnRequest;
import com.medicalcoldchain.backend.dto.admin.AdminUserSummaryResponse;
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
import com.medicalcoldchain.backend.dto.telemetry.ManualTelemetryRequest;
import com.medicalcoldchain.backend.dto.telemetry.TelemetryPointResponse;
import com.medicalcoldchain.backend.entity.DeviceBorrowRecord;
import com.medicalcoldchain.backend.entity.DeviceLocation;
import com.medicalcoldchain.backend.entity.TelemetryRecord;
import com.medicalcoldchain.backend.entity.TransportDevice;
import com.medicalcoldchain.backend.entity.UserAccount;
import com.medicalcoldchain.backend.enums.DeviceStatus;
import com.medicalcoldchain.backend.enums.UserRole;
import com.medicalcoldchain.backend.exception.BusinessException;
import com.medicalcoldchain.backend.repository.DeviceBorrowRecordRepository;
import com.medicalcoldchain.backend.repository.TransportDeviceRepository;
import com.medicalcoldchain.backend.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private static final DateTimeFormatter DEVICE_CODE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final List<String> MEDICINE_NAMES = List.of("疫苗冷藏箱", "血液运输箱", "生物制剂运输箱", "试剂冷链箱");
    private static final List<String> ROUTE_NAMES = List.of("北京医院专线", "上海疾控专线", "广州仓储专线", "成都配送专线");

    private final TransportDeviceRepository transportDeviceRepository;
    private final DeviceBorrowRecordRepository deviceBorrowRecordRepository;
    private final UserAccountRepository userAccountRepository;
    private final ThresholdService thresholdService;
    private final TelemetryService telemetryService;
    private final BorrowLimitService borrowLimitService;

    @Transactional
    public DeviceOverviewResponse getOverview(UserAccount user) {
        List<TransportDevice> myDevices = transportDeviceRepository.findByCurrentUserIdOrderByDeviceCodeAsc(user.getId());
        Map<Long, DeviceBorrowRecord> thresholdMap = thresholdService.getThresholdMap(user, myDevices);

        long alarmCount = 0;
        for (TransportDevice device : myDevices) {
            TelemetryService.LatestSnapshot snapshot = telemetryService.getLatestSnapshot(device);
            if (telemetryService.isAlarm(snapshot.telemetry(), thresholdMap.get(device.getId()))) {
                alarmCount++;
            }
        }

        return DeviceOverviewResponse.builder()
                .availableCount(Math.max(borrowLimitService.getEffectiveLimit(user) - myDevices.size(), 0))
                .inUseCount(transportDeviceRepository.countByStatus(DeviceStatus.IN_USE))
                .myDeviceCount(myDevices.size())
                .alarmCount(alarmCount)
                .build();
    }

    @Transactional
    public List<DeviceCardResponse> listMyDevices(UserAccount user) {
        List<TransportDevice> devices = transportDeviceRepository.findByCurrentUserIdOrderByDeviceCodeAsc(user.getId());
        return buildDeviceCards(user, devices);
    }

    @Transactional(readOnly = true)
    public List<AdminUserSummaryResponse> listUserSummaries() {
        List<UserAccount> users = new ArrayList<>(userAccountRepository.findAllByOrderByCreatedAtAsc());
        users.sort(Comparator
                .comparing((UserAccount user) -> resolveRole(user) != UserRole.ADMIN)
                .thenComparing(UserAccount::getCreatedAt)
                .thenComparing(UserAccount::getPhone));

        Map<Long, List<TransportDevice>> devicesByUserId = new HashMap<>();
        for (TransportDevice device : transportDeviceRepository.findAllByOrderByDeviceCodeAsc()) {
            if (!isBorrowedDevice(device)) {
                continue;
            }
            devicesByUserId.computeIfAbsent(device.getCurrentUser().getId(), key -> new ArrayList<>()).add(device);
        }

        List<AdminUserSummaryResponse> result = new ArrayList<>();
        for (UserAccount user : users) {
            List<TransportDevice> activeDevices = devicesByUserId.getOrDefault(user.getId(), List.of());
            List<String> activeDeviceCodes = new ArrayList<>();
            LocalDateTime latestBorrowTime = null;

            for (TransportDevice device : activeDevices) {
                activeDeviceCodes.add(device.getDeviceCode());
                if (device.getBorrowedAt() != null
                        && (latestBorrowTime == null || device.getBorrowedAt().isAfter(latestBorrowTime))) {
                    latestBorrowTime = device.getBorrowedAt();
                }
            }

            result.add(AdminUserSummaryResponse.builder()
                    .userId(user.getId())
                    .phone(user.getPhone())
                    .name(user.getName())
                    .organization(user.getOrganization())
                    .role(resolveRole(user).name())
                    .inUseDeviceCount(activeDevices.size())
                    .activeDeviceCodes(activeDeviceCodes)
                    .latestBorrowTime(latestBorrowTime)
                    .build());
        }

        return result;
    }

    @Transactional
    public List<DeviceCardResponse> applyDevices(UserAccount user, ApplyDeviceRequest request) {
        int count = request.getCount();
        borrowLimitService.validateCanApply(user, count);
        LocalDateTime borrowTime = LocalDateTime.now();
        List<TransportDevice> createdDevices = new ArrayList<>();
        List<DeviceBorrowRecord> records = new ArrayList<>();

        for (int index = 0; index < count; index++) {
            TransportDevice device = createBorrowedDevice(user, borrowTime, index);
            createdDevices.add(device);
            records.add(DeviceBorrowRecord.builder()
                    .device(device)
                    .borrower(user)
                    .borrowTime(borrowTime)
                    .build());
        }

        deviceBorrowRecordRepository.saveAll(records);

        for (TransportDevice device : createdDevices) {
            thresholdService.ensureThreshold(user, device);
            telemetryService.generateBorrowHistory(device, borrowTime);
        }

        return listMyDevices(user);
    }

    @Transactional
    public String returnDevices(UserAccount user, ReturnDeviceRequest request) {
        List<TransportDevice> allMyDevices = transportDeviceRepository.findByCurrentUserIdOrderByDeviceCodeAsc(user.getId());
        if (allMyDevices.isEmpty()) {
            throw new BusinessException("当前没有可归还的设备");
        }

        List<Long> deviceIds = request == null ? null : request.getDeviceIds();
        List<TransportDevice> targetDevices = new ArrayList<>();

        if (deviceIds == null || deviceIds.isEmpty()) {
            targetDevices.addAll(allMyDevices);
        } else {
            for (TransportDevice device : allMyDevices) {
                if (deviceIds.contains(device.getId())) {
                    targetDevices.add(device);
                }
            }
        }

        if (targetDevices.isEmpty()) {
            throw new BusinessException("未找到需要归还的设备");
        }

        int returnedCount = releaseDevices(targetDevices);

        return "已归还 " + returnedCount + " 台设备";
    }

    @Transactional
    public String forceReturnDevices(AdminForceReturnRequest request) {
        List<TransportDevice> targetDevices = resolveForceReturnTargets(request);
        int returnedCount = releaseDevices(targetDevices);
        return "已强制归还 " + returnedCount + " 台设备";
    }

    @Transactional
    public ThresholdResponse getThreshold(UserAccount user, Long deviceId) {
        return thresholdService.getThreshold(user, deviceId);
    }

    @Transactional
    public ThresholdResponse saveThreshold(UserAccount user, Long deviceId, ThresholdRequest request) {
        return thresholdService.saveThreshold(user, deviceId, request);
    }

    @Transactional
    public List<LatestDeviceTelemetryResponse> getLatestTelemetry(UserAccount user) {
        List<TransportDevice> devices = isAdmin(user)
                ? transportDeviceRepository.findByStatusOrderByDeviceCodeAsc(DeviceStatus.IN_USE)
                : transportDeviceRepository.findByCurrentUserIdOrderByDeviceCodeAsc(user.getId());
        Map<Long, DeviceBorrowRecord> thresholdMap = isAdmin(user)
                ? getActiveThresholdMap(devices)
                : thresholdService.getThresholdMap(user, devices);

        return devices.stream()
                .map(device -> buildLatestTelemetryResponse(device, thresholdMap.get(device.getId())))
                .toList();
    }

    @Transactional
    public LatestDeviceTelemetryResponse getMonitorTelemetry(UserAccount user, Long deviceId) {
        TransportDevice device = isAdmin(user) ? getInUseDevice(deviceId) : getOwnedDevice(user, deviceId);
        DeviceBorrowRecord threshold = isAdmin(user)
                ? getActiveThreshold(device)
                : thresholdService.ensureThreshold(user, device);
        return buildLatestTelemetryResponse(device, threshold);
    }

    @Transactional
    public HistoryResponse getHistory(UserAccount user, Long deviceId, Integer hours) {
        return getHistory(user, deviceId, hours, null);
    }

    @Transactional
    public HistoryResponse getHistory(UserAccount user, Long deviceId, Integer hours, Integer stepMinutes) {
        int safeHours = hours == null ? 24 : Math.max(1, Math.min(hours, 24));
        int safeStepMinutes = resolveHistoryStepMinutes(safeHours, stepMinutes);
        TransportDevice device = getOwnedDevice(user, deviceId);
        DeviceBorrowRecord threshold = thresholdService.ensureThreshold(user, device);
        List<TelemetryPointResponse> points = telemetryService.getHistoryPoints(device, safeHours, safeStepMinutes, threshold);

        return HistoryResponse.builder()
                .deviceId(device.getId())
                .deviceCode(device.getDeviceCode())
                .deviceName(device.getDeviceName())
                .hours(safeHours)
                .stepMinutes(safeStepMinutes)
                .threshold(thresholdService.toResponse(threshold))
                .points(points)
                .build();
    }

    @Transactional
    public HistoryResponse refreshHistory(UserAccount user, Long deviceId, Integer hours, Integer stepMinutes) {
        int safeHours = hours == null ? 24 : Math.max(1, Math.min(hours, 24));
        int safeStepMinutes = resolveHistoryStepMinutes(safeHours, stepMinutes);
        TransportDevice device = getOwnedDevice(user, deviceId);
        telemetryService.refreshHistoryToCurrentTime(device);
        DeviceBorrowRecord threshold = thresholdService.ensureThreshold(user, device);
        List<TelemetryPointResponse> points = telemetryService.getHistoryPoints(device, safeHours, safeStepMinutes, threshold);

        return HistoryResponse.builder()
                .deviceId(device.getId())
                .deviceCode(device.getDeviceCode())
                .deviceName(device.getDeviceName())
                .hours(safeHours)
                .stepMinutes(safeStepMinutes)
                .threshold(thresholdService.toResponse(threshold))
                .points(points)
                .build();
    }

    @Transactional
    public TelemetryPointResponse addManualHistory(UserAccount user, Long deviceId, ManualTelemetryRequest request) {
        TransportDevice device = isAdmin(user) ? getInUseDevice(deviceId) : getOwnedDevice(user, deviceId);
        if (!isBorrowedDevice(device)) {
            throw new BusinessException("只能给正在使用的设备添加历史记录");
        }
        DeviceBorrowRecord threshold = isAdmin(user)
                ? getActiveThreshold(device)
                : thresholdService.ensureThreshold(user, device);
        TelemetryService.LatestSnapshot snapshot = telemetryService.recordManualHistory(device, request);
        return telemetryService.toPointResponse(snapshot.telemetry(), threshold);
    }

    @Transactional
    public DeviceLocationResponse getLatestLocation(UserAccount user, Long deviceId) {
        TransportDevice device = getOwnedDevice(user, deviceId);
        DeviceLocation location = telemetryService.getLatestSnapshot(device).location();
        return telemetryService.toLocationResponse(location);
    }

    @Transactional(readOnly = true)
    public List<DeviceBorrowRecordResponse> listBorrowRecords(String keyword, String status) {
        List<DeviceBorrowRecord> records = deviceBorrowRecordRepository.findAllByOrderByBorrowTimeDesc();
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();

        return records.stream()
                .map(this::toBorrowRecordResponse)
                .filter(record -> normalizedKeyword.isBlank() || matchesKeyword(record, normalizedKeyword))
                .filter(record -> normalizedStatus.isBlank() || normalizedStatus.equals(record.getStatus()))
                .toList();
    }

    private List<DeviceCardResponse> buildDeviceCards(UserAccount user, List<TransportDevice> devices) {
        Map<Long, DeviceBorrowRecord> thresholdMap = thresholdService.getThresholdMap(user, devices);
        List<DeviceCardResponse> result = new ArrayList<>();

        for (TransportDevice device : devices) {
            DeviceBorrowRecord threshold = thresholdMap.get(device.getId());
            TelemetryService.LatestSnapshot snapshot = telemetryService.getLatestSnapshot(device);

            result.add(DeviceCardResponse.builder()
                    .id(device.getId())
                    .deviceCode(device.getDeviceCode())
                    .deviceName(device.getDeviceName())
                    .medicineName(device.getMedicineName())
                    .routeName(device.getRouteName())
                    .status(device.getStatus().name())
                    .batteryLevel(device.getBatteryLevel())
                    .signalStatus(device.getSignalStatus())
                    .alarm(telemetryService.isAlarm(snapshot.telemetry(), threshold))
                    .threshold(thresholdService.toResponse(threshold))
                    .latestTelemetry(telemetryService.toPointResponse(snapshot.telemetry(), threshold))
                    .latestLocation(telemetryService.toLocationResponse(snapshot.location()))
                    .build());
        }
        return result;
    }

    private LatestDeviceTelemetryResponse buildLatestTelemetryResponse(TransportDevice device, DeviceBorrowRecord threshold) {
        TelemetryService.LatestSnapshot snapshot = telemetryService.recordRealtimeSnapshot(device);

        return LatestDeviceTelemetryResponse.builder()
                .deviceId(device.getId())
                .deviceCode(device.getDeviceCode())
                .deviceName(device.getDeviceName())
                .medicineName(device.getMedicineName())
                .routeName(device.getRouteName())
                .threshold(thresholdService.toResponse(threshold))
                .telemetry(telemetryService.toPointResponse(snapshot.telemetry(), threshold))
                .location(telemetryService.toLocationResponse(snapshot.location()))
                .build();
    }

    private DeviceBorrowRecordResponse toBorrowRecordResponse(DeviceBorrowRecord record) {
        return DeviceBorrowRecordResponse.builder()
                .recordId(record.getId())
                .deviceId(record.getDevice().getId())
                .deviceCode(record.getDevice().getDeviceCode())
                .deviceName(record.getDevice().getDeviceName())
                .medicineName(record.getDevice().getMedicineName())
                .routeName(record.getDevice().getRouteName())
                .borrowerName(record.getBorrower().getName())
                .borrowerPhone(record.getBorrower().getPhone())
                .borrowTime(record.getBorrowTime())
                .returnTime(record.getReturnTime())
                .status(record.getReturnTime() == null ? "BORROWED" : "RETURNED")
                .threshold(thresholdService.toResponse(record))
                .build();
    }

    private boolean matchesKeyword(DeviceBorrowRecordResponse record, String keyword) {
        return contains(record.getDeviceCode(), keyword)
                || contains(record.getDeviceName(), keyword)
                || contains(record.getMedicineName(), keyword)
                || contains(record.getRouteName(), keyword)
                || contains(record.getBorrowerName(), keyword)
                || contains(record.getBorrowerPhone(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private List<TransportDevice> resolveForceReturnTargets(AdminForceReturnRequest request) {
        if (request == null) {
            throw new BusinessException("请选择需要强制归还的设备或用户");
        }

        List<Long> deviceIds = request.getDeviceIds();
        Long userId = request.getUserId();

        if ((deviceIds == null || deviceIds.isEmpty()) && userId == null) {
            throw new BusinessException("请选择需要强制归还的设备或用户");
        }

        List<TransportDevice> targetDevices = new ArrayList<>();
        if (deviceIds != null && !deviceIds.isEmpty()) {
            targetDevices.addAll(transportDeviceRepository.findAllById(deviceIds));
            if (userId != null) {
                targetDevices.removeIf(device -> device.getCurrentUser() == null
                        || !userId.equals(device.getCurrentUser().getId()));
            }
        } else {
            userAccountRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException("用户不存在"));
            targetDevices.addAll(transportDeviceRepository.findByCurrentUserIdOrderByDeviceCodeAsc(userId));
        }

        targetDevices.removeIf(device -> !isBorrowedDevice(device));

        if (targetDevices.isEmpty()) {
            throw new BusinessException(userId != null ? "该用户当前没有在用设备" : "未找到需要强制归还的设备");
        }

        return targetDevices;
    }

    private int releaseDevices(List<TransportDevice> targetDevices) {
        LocalDateTime returnTime = LocalDateTime.now();

        for (TransportDevice device : targetDevices) {
            deviceBorrowRecordRepository
                    .findTopByDeviceIdAndReturnTimeIsNullOrderByBorrowTimeDesc(device.getId())
                    .ifPresent(record -> record.setReturnTime(returnTime));
            telemetryService.clearDeviceHistory(device);
            device.setCurrentUser(null);
            device.setStatus(DeviceStatus.AVAILABLE);
            device.setBorrowedAt(null);
        }

        transportDeviceRepository.saveAll(targetDevices);
        if (transportDeviceRepository.countByStatus(DeviceStatus.IN_USE) == 0) {
            telemetryService.resetTelemetryRecordAutoIncrementIfEmpty();
        }
        return targetDevices.size();
    }

    private TransportDevice createBorrowedDevice(UserAccount user, LocalDateTime borrowTime, int index) {
        int templateIndex = Math.floorMod(index, MEDICINE_NAMES.size());
        String deviceCode = generateDeviceCode();
        String deviceName = "冷链设备-" + deviceCode.substring(deviceCode.length() - 6);

        return transportDeviceRepository.save(TransportDevice.builder()
                .deviceCode(deviceCode)
                .deviceName(deviceName)
                .medicineName(MEDICINE_NAMES.get(templateIndex))
                .routeName(ROUTE_NAMES.get(templateIndex))
                .status(DeviceStatus.IN_USE)
                .currentUser(user)
                .borrowedAt(borrowTime)
                .batteryLevel(100)
                .signalStatus(true)
                .build());
    }

    private String generateDeviceCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = "MCC" + DEVICE_CODE_TIME_FORMAT.format(LocalDateTime.now())
                    + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
            if (transportDeviceRepository.findByDeviceCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new BusinessException("设备编码生成失败，请稍后重试");
    }

    private boolean isBorrowedDevice(TransportDevice device) {
        return device != null && device.getCurrentUser() != null && device.getStatus() == DeviceStatus.IN_USE;
    }

    private Map<Long, DeviceBorrowRecord> getActiveThresholdMap(List<TransportDevice> devices) {
        Map<Long, DeviceBorrowRecord> thresholdMap = new HashMap<>();
        for (TransportDevice device : devices) {
            thresholdMap.put(device.getId(), getActiveThreshold(device));
        }
        return thresholdMap;
    }

    private DeviceBorrowRecord getActiveThreshold(TransportDevice device) {
        return deviceBorrowRecordRepository
                .findTopByDeviceIdAndReturnTimeIsNullOrderByBorrowTimeDesc(device.getId())
                .orElseGet(() -> thresholdService.ensureThreshold(device.getCurrentUser(), device));
    }

    private TransportDevice getInUseDevice(Long deviceId) {
        TransportDevice device = transportDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new BusinessException("设备不存在或不属于当前用户"));
        if (!isBorrowedDevice(device)) {
            throw new BusinessException("设备不存在或不属于当前用户");
        }
        return device;
    }

    private boolean isAdmin(UserAccount user) {
        return resolveRole(user) == UserRole.ADMIN;
    }

    private UserRole resolveRole(UserAccount user) {
        return user.getRole() == null ? UserRole.USER : user.getRole();
    }

    private int resolveHistoryStepMinutes(int hours, Integer stepMinutes) {
        if (stepMinutes != null) {
            return Math.max(1, Math.min(stepMinutes, 60));
        }
        if (hours <= 6) {
            return 5;
        }
        if (hours <= 12) {
            return 10;
        }
        return 15;
    }

    private TransportDevice getOwnedDevice(UserAccount user, Long deviceId) {
        return transportDeviceRepository.findByIdAndCurrentUserId(deviceId, user.getId())
                .orElseThrow(() -> new BusinessException("设备不存在或不属于当前用户"));
    }
}
