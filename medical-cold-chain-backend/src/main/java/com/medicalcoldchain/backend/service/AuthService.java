package com.medicalcoldchain.backend.service;

import com.medicalcoldchain.backend.dto.auth.LoginRequest;
import com.medicalcoldchain.backend.dto.auth.LoginResponse;
import com.medicalcoldchain.backend.dto.auth.SendCodeRequest;
import com.medicalcoldchain.backend.dto.auth.SendCodeResponse;
import com.medicalcoldchain.backend.dto.auth.UserInfoResponse;
import com.medicalcoldchain.backend.entity.LoginCode;
import com.medicalcoldchain.backend.entity.UserAccount;
import com.medicalcoldchain.backend.enums.UserRole;
import com.medicalcoldchain.backend.exception.BusinessException;
import com.medicalcoldchain.backend.repository.LoginCodeRepository;
import com.medicalcoldchain.backend.repository.UserAccountRepository;
import com.medicalcoldchain.backend.util.PhoneUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final LoginCodeRepository loginCodeRepository;

    @Value("${app.super-admin-phone:18800000000}")
    private String superAdminPhone;

    private final Map<String, Long> tokenStore = new ConcurrentHashMap<>();
    private final Map<Long, String> activeTokenStore = new ConcurrentHashMap<>();

    @Transactional
    public SendCodeResponse sendCode(SendCodeRequest request) {
        if (!PhoneUtil.isValidPhone(request.getPhone())) {
            throw new BusinessException("请输入正确的手机号");
        }

        boolean isNewUser = !userAccountRepository.existsByPhone(request.getPhone());
        boolean requireConfirmation = request.getCheckOnly() == null || Boolean.TRUE.equals(request.getCheckOnly());

        if (isNewUser) {
            if (requireConfirmation) {
                throw new BusinessException("NEW_USER");
            }
            createUserAccount(request.getPhone());
        }

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        LoginCode loginCode = LoginCode.builder()
                .phone(request.getPhone())
                .code(code)
                .expiresAt(expiresAt)
                .used(false)
                .build();
        loginCodeRepository.save(loginCode);

        return SendCodeResponse.builder()
                .phone(request.getPhone())
                .code(code)
                .expiresAt(expiresAt)
                .tip("演示环境直接返回验证码，正式部署时可接入短信平台。")
                .build();
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        LoginCode loginCode = loginCodeRepository
                .findTopByPhoneAndCodeAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        request.getPhone(), request.getCode(), LocalDateTime.now())
                .orElseThrow(() -> new BusinessException("验证码错误或已过期"));

        UserAccount user = userAccountRepository.findByPhone(request.getPhone())
                .map(this::normalizeUserRole)
                .orElseGet(() -> createUserAccount(request.getPhone()));

        String currentToken = activeTokenStore.get(user.getId());
        if (currentToken != null && tokenStore.containsKey(currentToken) && !Boolean.TRUE.equals(request.getForceLogin())) {
            throw new BusinessException(HttpStatus.CONFLICT, "该账号已在其他地方登录，是否踢下线并继续登录？");
        }

        loginCode.setUsed(true);

        String token = UUID.randomUUID().toString().replace("-", "");
        String previousToken = activeTokenStore.put(user.getId(), token);
        if (previousToken != null) {
            tokenStore.remove(previousToken);
        }
        tokenStore.put(token, user.getId());

        return LoginResponse.builder()
                .token(token)
                .user(UserInfoResponse.builder()
                        .id(user.getId())
                        .phone(user.getPhone())
                        .name(user.getName())
                        .organization(user.getOrganization())
                        .role(resolveRole(user).name())
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public UserAccount requireUser(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        Long userId = tokenStore.get(token);
        if (userId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        String activeToken = activeTokenStore.get(userId);
        if (!token.equals(activeToken)) {
            tokenStore.remove(token);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        return userAccountRepository.findById(userId)
                .map(this::normalizeUserRole)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "用户不存在，请重新登录"));
    }

    public void requireAdmin(UserAccount user) {
        if (!isAdmin(user)) {
            throw new BusinessException("只有超级管理员可以查看全部设备借用明细");
        }
    }

    public boolean isAdmin(UserAccount user) {
        return resolveRole(user) == UserRole.ADMIN;
    }

    private String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "缺少登录凭证");
        }
        if (authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7).trim();
        }
        return authorizationHeader.trim();
    }

    private UserAccount createUserAccount(String phone) {
        UserRole role = phone.equals(superAdminPhone) ? UserRole.ADMIN : UserRole.USER;
        String name = role == UserRole.ADMIN ? "超级管理员" : "调度员" + phone.substring(7);
        String organization = role == UserRole.ADMIN ? "医疗冷链总控中心" : "华东医疗冷链调度中心";

        return userAccountRepository.save(UserAccount.builder()
                .phone(phone)
                .name(name)
                .organization(organization)
                .role(role)
                .build());
    }

    private UserAccount normalizeUserRole(UserAccount user) {
        UserRole targetRole = user.getPhone().equals(superAdminPhone) ? UserRole.ADMIN : UserRole.USER;
        if (user.getRole() == targetRole) {
            return user;
        }
        user.setRole(targetRole);
        if (targetRole == UserRole.ADMIN) {
            user.setName("超级管理员");
            user.setOrganization("医疗冷链总控中心");
        }
        return userAccountRepository.save(user);
    }

    private UserRole resolveRole(UserAccount user) {
        if (user == null) {
            return UserRole.USER;
        }
        if (user.getRole() != null) {
            return user.getRole();
        }
        return user.getPhone().equals(superAdminPhone) ? UserRole.ADMIN : UserRole.USER;
    }
}
