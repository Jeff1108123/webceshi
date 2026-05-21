package com.medicalcoldchain.backend.service;

import com.medicalcoldchain.backend.entity.UserAccount;
import com.medicalcoldchain.backend.enums.UserRole;
import com.medicalcoldchain.backend.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DataInitializationService implements ApplicationRunner {

    private final UserAccountRepository userAccountRepository;

    @Value("${app.super-admin-phone:18800000000}")
    private String superAdminPhone;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initUsers();
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
}
