package com.medicalcoldchain.backend.service;

import com.medicalcoldchain.backend.dto.admin.BorrowLimitOverviewResponse;
import com.medicalcoldchain.backend.dto.admin.BorrowLimitRequest;
import com.medicalcoldchain.backend.dto.admin.UserBorrowLimitRequest;
import com.medicalcoldchain.backend.dto.admin.UserBorrowLimitResponse;
import com.medicalcoldchain.backend.entity.AppSetting;
import com.medicalcoldchain.backend.entity.TransportDevice;
import com.medicalcoldchain.backend.entity.UserAccount;
import com.medicalcoldchain.backend.enums.DeviceStatus;
import com.medicalcoldchain.backend.enums.UserRole;
import com.medicalcoldchain.backend.exception.BusinessException;
import com.medicalcoldchain.backend.repository.AppSettingRepository;
import com.medicalcoldchain.backend.repository.TransportDeviceRepository;
import com.medicalcoldchain.backend.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BorrowLimitService {

    private static final String DEFAULT_LIMIT_KEY = "device.borrow.limit.default";
    private static final int FALLBACK_DEFAULT_LIMIT = 3;

    private final AppSettingRepository appSettingRepository;
    private final UserAccountRepository userAccountRepository;
    private final TransportDeviceRepository transportDeviceRepository;

    @Transactional
    public int getDefaultLimit() {
        return appSettingRepository.findBySettingKey(DEFAULT_LIMIT_KEY)
                .map(AppSetting::getSettingValue)
                .map(this::parseLimit)
                .orElseGet(this::createDefaultLimit);
    }

    @Transactional
    public int getEffectiveLimit(UserAccount user) {
        UserAccount currentUser = userAccountRepository.findById(user.getId()).orElse(user);
        if (currentUser.getBorrowLimitOverride() != null) {
            return currentUser.getBorrowLimitOverride();
        }
        return getDefaultLimit();
    }

    @Transactional
    public void validateCanApply(UserAccount user, int requestedCount) {
        int currentCount = transportDeviceRepository.findByCurrentUserIdOrderByDeviceCodeAsc(user.getId()).size();
        int effectiveLimit = getEffectiveLimit(user);
        int remainingCount = Math.max(effectiveLimit - currentCount, 0);

        if (requestedCount > remainingCount) {
            throw new BusinessException("当前最多可借 " + effectiveLimit + " 台设备，已借 "
                    + currentCount + " 台，本次最多还能申请 " + remainingCount + " 台");
        }
    }

    @Transactional
    public BorrowLimitOverviewResponse getOverview() {
        return buildOverview(getDefaultLimit());
    }

    @Transactional
    public BorrowLimitOverviewResponse updateDefaultLimit(BorrowLimitRequest request) {
        int limit = requirePositiveLimit(request == null ? null : request.getLimit());
        AppSetting setting = appSettingRepository.findBySettingKey(DEFAULT_LIMIT_KEY)
                .orElseGet(() -> AppSetting.builder().settingKey(DEFAULT_LIMIT_KEY).build());
        setting.setSettingValue(String.valueOf(limit));
        appSettingRepository.save(setting);
        return buildOverview(limit);
    }

    @Transactional
    public BorrowLimitOverviewResponse updateUserBorrowLimit(Long userId, UserBorrowLimitRequest request) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        Integer limit = request == null ? null : request.getLimit();
        if (limit != null) {
            requirePositiveLimit(limit);
        }
        user.setBorrowLimitOverride(limit);
        userAccountRepository.save(user);
        return buildOverview(getDefaultLimit());
    }

    private BorrowLimitOverviewResponse buildOverview(int defaultLimit) {
        List<UserAccount> users = new ArrayList<>(userAccountRepository.findAllByOrderByCreatedAtAsc());
        users.sort(Comparator
                .comparing((UserAccount user) -> resolveRole(user) != UserRole.ADMIN)
                .thenComparing(UserAccount::getCreatedAt)
                .thenComparing(UserAccount::getPhone));

        Map<Long, Integer> currentCounts = new HashMap<>();
        for (TransportDevice device : transportDeviceRepository.findAllByOrderByDeviceCodeAsc()) {
            if (device.getCurrentUser() != null && device.getStatus() == DeviceStatus.IN_USE) {
                currentCounts.merge(device.getCurrentUser().getId(), 1, Integer::sum);
            }
        }

        List<UserBorrowLimitResponse> userResponses = users.stream()
                .map(user -> UserBorrowLimitResponse.builder()
                        .userId(user.getId())
                        .phone(user.getPhone())
                        .name(user.getName())
                        .organization(user.getOrganization())
                        .role(resolveRole(user).name())
                        .currentBorrowCount(currentCounts.getOrDefault(user.getId(), 0))
                        .borrowLimitOverride(user.getBorrowLimitOverride())
                        .effectiveBorrowLimit(user.getBorrowLimitOverride() == null
                                ? defaultLimit
                                : user.getBorrowLimitOverride())
                        .build())
                .toList();

        return BorrowLimitOverviewResponse.builder()
                .defaultLimit(defaultLimit)
                .users(userResponses)
                .build();
    }

    private int createDefaultLimit() {
        AppSetting setting = AppSetting.builder()
                .settingKey(DEFAULT_LIMIT_KEY)
                .settingValue(String.valueOf(FALLBACK_DEFAULT_LIMIT))
                .build();
        appSettingRepository.save(setting);
        return FALLBACK_DEFAULT_LIMIT;
    }

    private int parseLimit(String value) {
        try {
            return requirePositiveLimit(Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return FALLBACK_DEFAULT_LIMIT;
        }
    }

    private int requirePositiveLimit(Integer limit) {
        if (limit == null || limit < 1) {
            throw new BusinessException("借用上限至少为 1");
        }
        return limit;
    }

    private UserRole resolveRole(UserAccount user) {
        return user.getRole() == null ? UserRole.USER : user.getRole();
    }
}
