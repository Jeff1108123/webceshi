package com.medicalcoldchain.backend.service;

import com.medicalcoldchain.backend.entity.TransportDevice;
import com.medicalcoldchain.backend.entity.UserAccount;
import com.medicalcoldchain.backend.enums.DeviceStatus;
import com.medicalcoldchain.backend.enums.UserRole;
import com.medicalcoldchain.backend.repository.TransportDeviceRepository;
import com.medicalcoldchain.backend.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializationService implements ApplicationRunner {

    private static final int INITIAL_DEVICE_COUNT = 60;
    private static final List<String> MEDICINE_NAMES = List.of("疫苗冷藏箱", "血液运输箱", "生物制剂运输箱", "试剂冷链箱");
    private static final List<String> ROUTE_NAMES = List.of("三明市第一医院专线", "三明市中西医结合医院专线", "沙县区总医院专线", "永安市立医院专线");

    private final UserAccountRepository userAccountRepository;
    private final TransportDeviceRepository transportDeviceRepository;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.super-admin-phone:18800000000}")
    private String superAdminPhone;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        dropDeprecatedThresholdTable();
        initUsers();
        initDevices();
    }

    private void dropDeprecatedThresholdTable() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS device_threshold");
    }

    private void initUsers() {
        ensureAdminAccount();
        ensureDemoDispatcher();
        normalizeExistingUsers();
    }

    private void ensureAdminAccount() {
        userAccountRepository.findByPhone(superAdminPhone)
                .map(existing -> {
                    existing.setName("超级管理员");
                    existing.setOrganization("医疗冷链总控中心");
                    existing.setRole(UserRole.ADMIN);
                    return userAccountRepository.save(existing);
                })
                .orElseGet(() -> userAccountRepository.save(UserAccount.builder()
                        .phone(superAdminPhone)
                        .name("超级管理员")
                        .organization("医疗冷链总控中心")
                        .role(UserRole.ADMIN)
                        .build()));
    }

    private void ensureDemoDispatcher() {
        userAccountRepository.findByPhone("15059048933")
                .map(existing -> {
                    if (existing.getRole() == null) {
                        existing.setRole(UserRole.USER);
                        return userAccountRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> userAccountRepository.save(UserAccount.builder()
                        .phone("15059048933")
                        .name("演示调度员")
                        .organization("华东医疗冷链调度中心")
                        .role(UserRole.USER)
                        .build()));
    }

    private void normalizeExistingUsers() {
        userAccountRepository.findAll().forEach(user -> {
            UserRole targetRole = user.getPhone().equals(superAdminPhone) ? UserRole.ADMIN : UserRole.USER;
            if (user.getRole() == targetRole) {
                return;
            }
            user.setRole(targetRole);
            if (targetRole == UserRole.ADMIN) {
                user.setName("超级管理员");
                user.setOrganization("医疗冷链总控中心");
            }
            userAccountRepository.save(user);
        });
    }

    private void initDevices() {
        for (int index = 1; index <= INITIAL_DEVICE_COUNT; index++) {
            int deviceNumber = index;
            String deviceCode = String.format("MCC-%03d", deviceNumber);
            int templateIndex = Math.floorMod(deviceNumber - 1, MEDICINE_NAMES.size());
            transportDeviceRepository.findByDeviceCode(deviceCode)
                    .orElseGet(() -> transportDeviceRepository.save(TransportDevice.builder()
                            .deviceCode(deviceCode)
                            .deviceName("冷链设备-" + String.format("%03d", deviceNumber))
                            .medicineName(MEDICINE_NAMES.get(templateIndex))
                            .routeName(ROUTE_NAMES.get(templateIndex))
                            .status(DeviceStatus.AVAILABLE)
                            .batteryLevel(100)
                            .signalStatus(true)
                            .build()));
        }
    }
}
