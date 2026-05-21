# Admin Force Return and Borrow Limits Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add super-admin forced device return from the UI and enforce configurable per-user device borrow limits with a global default of 3.

**Architecture:** The backend remains the source of truth: Spring services persist global/user limit settings and enforce the effective limit during device application. The frontend adds admin-only screens/actions that call protected backend endpoints and display backend business errors without duplicating policy.

**Tech Stack:** Vue 2 Options API, Vue Router 3, Pinia, Axios, Spring Boot 4, Spring Data JPA, MySQL, JUnit 5.

---

## Scope and File Structure

This plan implements one cohesive full-stack feature: borrowing policy plus admin operations. It is small enough for one plan because all behavior centers on the existing device borrowing subsystem.

### Backend files

- Modify: `vue_kgy_frontend/medical-cold-chain-backend/src/test/java/com/medicalcoldchain/backend/MedicalColdChainBackendApplicationTests.java`
  - Adds service-level tests for default limit, global limit, user override, and clearing overrides.
- Modify: `vue_kgy_frontend/medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/entity/UserAccount.java`
  - Adds nullable `borrowLimitOverride`.
- Create: `vue_kgy_frontend/medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/entity/AppSetting.java`
  - Stores global key/value settings.
- Create: `vue_kgy_frontend/medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/repository/AppSettingRepository.java`
  - Loads settings by key.
- Create: `vue_kgy_frontend/medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/dto/admin/BorrowLimitRequest.java`
  - Request body for setting a positive global limit.
- Create: `vue_kgy_frontend/medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/dto/admin/UserBorrowLimitRequest.java`
  - Request body for setting or clearing a user override.
- Create: `vue_kgy_frontend/medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/dto/admin/UserBorrowLimitResponse.java`
  - One user row in the admin borrow-limit page.
- Create: `vue_kgy_frontend/medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/dto/admin/BorrowLimitOverviewResponse.java`
  - Full borrow-limit page payload.
- Create: `vue_kgy_frontend/medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/service/BorrowLimitService.java`
  - Encapsulates global setting, effective limit calculation, user override updates, and application validation.
- Modify: `vue_kgy_frontend/medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/service/DeviceService.java`
  - Calls `BorrowLimitService` before creating devices.
- Modify: `vue_kgy_frontend/medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/controller/DeviceController.java`
  - Adds super-admin endpoints for borrow-limit management.
- Modify: `vue_kgy_frontend/medical-cold-chain-backend/database/medical_cold_chain.sql`
  - Adds SQL schema for `app_setting` and `user_account.borrow_limit_override`.

### Frontend files

- Modify: `vue_kgy_frontend/src/api/medicalColdChain.js`
  - Adds borrow-limit API functions; reuses existing `forceReturnDevices`.
- Modify: `vue_kgy_frontend/src/router/index.js`
  - Adds `/admin/borrow-limits` route.
- Modify: `vue_kgy_frontend/src/config/navigation.js`
  - Adds admin-only “借用限制管理” navigation item.
- Create: `vue_kgy_frontend/src/components/adminBorrowLimitPage.vue`
  - Admin UI for global default and per-user overrides.
- Modify: `vue_kgy_frontend/src/components/adminDeviceBorrowPage.vue`
  - Adds force-return action for borrowed records.

---

## Task 1: Backend Failing Tests for Borrow Limits

**Files:**
- Modify: `vue_kgy_frontend/medical-cold-chain-backend/src/test/java/com/medicalcoldchain/backend/MedicalColdChainBackendApplicationTests.java`

- [ ] **Step 1: Add imports and autowired service reference**

Open `MedicalColdChainBackendApplicationTests.java` and add these imports next to the existing imports:

```java
import com.medicalcoldchain.backend.dto.admin.BorrowLimitOverviewResponse;
import com.medicalcoldchain.backend.dto.admin.BorrowLimitRequest;
import com.medicalcoldchain.backend.dto.admin.UserBorrowLimitRequest;
```

Add this field after the existing `DeviceService deviceService;` field:

```java
    @Autowired
    private BorrowLimitService borrowLimitService;
```

Add this import for the service:

```java
import com.medicalcoldchain.backend.service.BorrowLimitService;
```

- [ ] **Step 2: Add test helper methods**

Add these helper methods inside `MedicalColdChainBackendApplicationTests`, before the final closing brace:

```java
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
```

- [ ] **Step 3: Add default limit test**

Add this test method inside `MedicalColdChainBackendApplicationTests`:

```java
    @Test
    void applyDevicesShouldRespectDefaultBorrowLimit() {
        UserAccount dispatcher = createBorrowLimitTestUser("134", "默认上限测试调度员");

        assertEquals(3, borrowLimitService.getDefaultLimit());
        assertEquals(3, deviceService.applyDevices(dispatcher, applyRequest(3)).size());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> deviceService.applyDevices(dispatcher, applyRequest(1)));
        assertEquals("当前最多可借 3 台设备，已借 3 台，本次最多还能申请 0 台", exception.getMessage());
    }
```

- [ ] **Step 4: Add global limit test**

Add this test method inside `MedicalColdChainBackendApplicationTests`:

```java
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
```

- [ ] **Step 5: Add user override test**

Add this test method inside `MedicalColdChainBackendApplicationTests`:

```java
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
```

- [ ] **Step 6: Add clear override test**

Add this test method inside `MedicalColdChainBackendApplicationTests`:

```java
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
```

- [ ] **Step 7: Run tests to verify they fail**

Run from `vue_kgy_frontend/medical-cold-chain-backend/`:

```bash
./mvnw test -Dtest=MedicalColdChainBackendApplicationTests
```

Expected: compilation fails because `BorrowLimitService`, `BorrowLimitRequest`, `UserBorrowLimitRequest`, and `BorrowLimitOverviewResponse` do not exist yet.

- [ ] **Step 8: Checkpoint**

If this is inside a git repository, run:

```bash
git status --short
```

Expected in this current project: `fatal: not a git repository` if run from `D:/web作业/vue_kgy_frontend`. In a future git-backed copy, commit the failing tests before implementation.

---

## Task 2: Backend Borrow-Limit Model, DTOs, Repository, and Service

**Files:**
- Modify: `vue_kgy_frontend/medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/entity/UserAccount.java`
- Create: `vue_kgy_frontend/medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/entity/AppSetting.java`
- Create: `vue_kgy_frontend/medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/repository/AppSettingRepository.java`
- Create: `vue_kgy_frontend/medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/dto/admin/BorrowLimitRequest.java`
- Create: `vue_kgy_frontend/medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/dto/admin/UserBorrowLimitRequest.java`
- Create: `vue_kgy_frontend/medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/dto/admin/UserBorrowLimitResponse.java`
- Create: `vue_kgy_frontend/medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/dto/admin/BorrowLimitOverviewResponse.java`
- Create: `vue_kgy_frontend/medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/service/BorrowLimitService.java`

- [ ] **Step 1: Add user override column to entity**

In `UserAccount.java`, add this field after `private UserRole role;`:

```java
    @Column
    private Integer borrowLimitOverride;
```

- [ ] **Step 2: Create AppSetting entity**

Create `AppSetting.java` with this content:

```java
package com.medicalcoldchain.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "app_setting")
public class AppSetting extends AbstractAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String settingKey;

    @Column(nullable = false, length = 200)
    private String settingValue;
}
```

- [ ] **Step 3: Create AppSettingRepository**

Create `AppSettingRepository.java` with this content:

```java
package com.medicalcoldchain.backend.repository;

import com.medicalcoldchain.backend.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppSettingRepository extends JpaRepository<AppSetting, Long> {
    Optional<AppSetting> findBySettingKey(String settingKey);
}
```

- [ ] **Step 4: Create request DTOs**

Create `BorrowLimitRequest.java` with this content:

```java
package com.medicalcoldchain.backend.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BorrowLimitRequest {

    @NotNull(message = "借用上限不能为空")
    @Min(value = 1, message = "借用上限至少为 1")
    private Integer limit;
}
```

Create `UserBorrowLimitRequest.java` with this content:

```java
package com.medicalcoldchain.backend.dto.admin;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UserBorrowLimitRequest {

    @Min(value = 1, message = "借用上限至少为 1")
    private Integer limit;
}
```

- [ ] **Step 5: Create response DTOs**

Create `UserBorrowLimitResponse.java` with this content:

```java
package com.medicalcoldchain.backend.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserBorrowLimitResponse {

    private Long userId;
    private String phone;
    private String name;
    private String organization;
    private String role;
    private Integer currentBorrowCount;
    private Integer borrowLimitOverride;
    private Integer effectiveBorrowLimit;
}
```

Create `BorrowLimitOverviewResponse.java` with this content:

```java
package com.medicalcoldchain.backend.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BorrowLimitOverviewResponse {

    private Integer defaultLimit;
    private List<UserBorrowLimitResponse> users;
}
```

- [ ] **Step 6: Create BorrowLimitService**

Create `BorrowLimitService.java` with this content:

```java
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

    @Transactional(readOnly = true)
    public int getEffectiveLimit(UserAccount user) {
        if (user.getBorrowLimitOverride() != null) {
            return user.getBorrowLimitOverride();
        }
        return getDefaultLimit();
    }

    @Transactional(readOnly = true)
    public void validateCanApply(UserAccount user, int requestedCount) {
        int currentCount = transportDeviceRepository.findByCurrentUserIdOrderByDeviceCodeAsc(user.getId()).size();
        int effectiveLimit = getEffectiveLimit(user);
        int remainingCount = Math.max(effectiveLimit - currentCount, 0);

        if (requestedCount > remainingCount) {
            throw new BusinessException("当前最多可借 " + effectiveLimit + " 台设备，已借 "
                    + currentCount + " 台，本次最多还能申请 " + remainingCount + " 台");
        }
    }

    @Transactional(readOnly = true)
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
```

- [ ] **Step 7: Run tests to verify expected remaining failure**

Run from `vue_kgy_frontend/medical-cold-chain-backend/`:

```bash
./mvnw test -Dtest=MedicalColdChainBackendApplicationTests
```

Expected: tests still fail because `DeviceService.applyDevices()` does not yet call `BorrowLimitService.validateCanApply()`.

---

## Task 3: Enforce Borrow Limits in DeviceService

**Files:**
- Modify: `vue_kgy_frontend/medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/service/DeviceService.java`

- [ ] **Step 1: Inject BorrowLimitService**

In `DeviceService.java`, add this field after `private final TelemetryService telemetryService;`:

```java
    private final BorrowLimitService borrowLimitService;
```

- [ ] **Step 2: Enforce before creating devices**

In `DeviceService.applyDevices(UserAccount user, ApplyDeviceRequest request)`, replace the beginning of the method:

```java
        int count = request.getCount();
        LocalDateTime borrowTime = LocalDateTime.now();
```

with:

```java
        int count = request.getCount();
        borrowLimitService.validateCanApply(user, count);
        LocalDateTime borrowTime = LocalDateTime.now();
```

- [ ] **Step 3: Run targeted tests**

Run from `vue_kgy_frontend/medical-cold-chain-backend/`:

```bash
./mvnw test -Dtest=MedicalColdChainBackendApplicationTests
```

Expected: tests compile and pass. If any older test now fails because previous tests changed the global default limit, ensure the global-limit test resets the limit back to 3 at the end exactly as written in Task 1.

- [ ] **Step 4: Checkpoint**

If this is inside a git repository, run:

```bash
git add src/test/java/com/medicalcoldchain/backend/MedicalColdChainBackendApplicationTests.java src/main/java/com/medicalcoldchain/backend/entity/UserAccount.java src/main/java/com/medicalcoldchain/backend/entity/AppSetting.java src/main/java/com/medicalcoldchain/backend/repository/AppSettingRepository.java src/main/java/com/medicalcoldchain/backend/dto/admin/BorrowLimitRequest.java src/main/java/com/medicalcoldchain/backend/dto/admin/UserBorrowLimitRequest.java src/main/java/com/medicalcoldchain/backend/dto/admin/UserBorrowLimitResponse.java src/main/java/com/medicalcoldchain/backend/dto/admin/BorrowLimitOverviewResponse.java src/main/java/com/medicalcoldchain/backend/service/BorrowLimitService.java src/main/java/com/medicalcoldchain/backend/service/DeviceService.java
git commit -m "feat: enforce configurable device borrow limits"
```

Expected in this current project: skip commit because the app directory is not a git repository.

---

## Task 4: Add Borrow-Limit Controller Endpoints and SQL Schema

**Files:**
- Modify: `vue_kgy_frontend/medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/controller/DeviceController.java`
- Modify: `vue_kgy_frontend/medical-cold-chain-backend/database/medical_cold_chain.sql`

- [ ] **Step 1: Add controller imports**

In `DeviceController.java`, add these imports with the other admin/device DTO imports:

```java
import com.medicalcoldchain.backend.dto.admin.BorrowLimitOverviewResponse;
import com.medicalcoldchain.backend.dto.admin.BorrowLimitRequest;
import com.medicalcoldchain.backend.dto.admin.UserBorrowLimitRequest;
import com.medicalcoldchain.backend.service.BorrowLimitService;
```

- [ ] **Step 2: Inject BorrowLimitService**

In `DeviceController.java`, add this field after `private final DeviceService deviceService;`:

```java
    private final BorrowLimitService borrowLimitService;
```

- [ ] **Step 3: Add borrow-limit endpoints**

In `DeviceController.java`, insert these methods after the existing `borrowRecords(...)` method and before `apply(...)`:

```java
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
```

- [ ] **Step 4: Update SQL schema**

In `database/medical_cold_chain.sql`, insert this table after the `login_code` table block:

```sql
CREATE TABLE IF NOT EXISTS app_setting (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  setting_key VARCHAR(100) NOT NULL UNIQUE,
  setting_value VARCHAR(200) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
);
```

Add these statements after the existing `ALTER TABLE user_account ADD COLUMN IF NOT EXISTS role VARCHAR(20) NULL;` statement:

```sql
ALTER TABLE user_account
  ADD COLUMN IF NOT EXISTS borrow_limit_override INT NULL;

INSERT INTO app_setting (setting_key, setting_value, created_at, updated_at)
SELECT 'device.borrow.limit.default', '3', NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM app_setting WHERE setting_key = 'device.borrow.limit.default'
);
```

- [ ] **Step 5: Run backend tests**

Run from `vue_kgy_frontend/medical-cold-chain-backend/`:

```bash
./mvnw test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Checkpoint**

If this is inside a git repository, run:

```bash
git add src/main/java/com/medicalcoldchain/backend/controller/DeviceController.java database/medical_cold_chain.sql
git commit -m "feat: expose borrow limit admin endpoints"
```

Expected in this current project: skip commit because the app directory is not a git repository.

---

## Task 5: Frontend API, Routing, and Navigation for Borrow-Limit Page

**Files:**
- Modify: `vue_kgy_frontend/src/api/medicalColdChain.js`
- Modify: `vue_kgy_frontend/src/router/index.js`
- Modify: `vue_kgy_frontend/src/config/navigation.js`

- [ ] **Step 1: Add API functions**

In `src/api/medicalColdChain.js`, add these functions after `fetchAllDeviceBorrows(...)`:

```js
export function fetchBorrowLimits() {
  return request.get('/devices/borrow-limits')
}

export function updateDefaultBorrowLimit(limit) {
  return request.put('/devices/borrow-limits/default', { limit })
}

export function updateUserBorrowLimit(userId, limit) {
  return request.put(`/devices/users/${userId}/borrow-limit`, { limit })
}
```

- [ ] **Step 2: Add router lazy import**

In `src/router/index.js`, add this constant after `AdminDeviceBorrowPage`:

```js
const AdminBorrowLimitPage = () => import(/* webpackChunkName: "admin-borrow-limits" */ '../components/adminBorrowLimitPage.vue')
```

- [ ] **Step 3: Add router entry**

In the `routes` array in `src/router/index.js`, replace the current admin route line:

```js
    { path: '/admin/device-borrows', name: 'AdminDeviceBorrows', component: AdminDeviceBorrowPage, meta: { requiresAuth: true, requiresSuperAdmin: true, title: '设备借用总览' } }
```

with these two entries:

```js
    { path: '/admin/device-borrows', name: 'AdminDeviceBorrows', component: AdminDeviceBorrowPage, meta: { requiresAuth: true, requiresSuperAdmin: true, title: '设备借用总览' } },
    { path: '/admin/borrow-limits', name: 'AdminBorrowLimits', component: AdminBorrowLimitPage, meta: { requiresAuth: true, requiresSuperAdmin: true, title: '借用限制管理' } }
```

- [ ] **Step 4: Add navigation item**

In `src/config/navigation.js`, replace the exported array with:

```js
export const navigationItems = [
  { label: '设备管理', path: '/device' },
  { label: '阈值设置', path: '/threshold' },
  { label: '实时数据', path: '/realdata' },
  { label: '实时监测', path: '/monitor' },
  { label: '历史数据', path: '/history' },
  { label: '实时位置', path: '/location' },
  { label: '设备借用总览', path: '/admin/device-borrows', requiresSuperAdmin: true },
  { label: '借用限制管理', path: '/admin/borrow-limits', requiresSuperAdmin: true }
]
```

- [ ] **Step 5: Run frontend lint to expose missing component error**

Run from `vue_kgy_frontend/`:

```bash
npm run lint
```

Expected: lint/build tooling may fail because `src/components/adminBorrowLimitPage.vue` does not exist yet. If lint does not resolve lazy imports, this step may pass; continue to Task 6 either way.

---

## Task 6: Create Admin Borrow-Limit Page

**Files:**
- Create: `vue_kgy_frontend/src/components/adminBorrowLimitPage.vue`

- [ ] **Step 1: Create component**

Create `src/components/adminBorrowLimitPage.vue` with this content:

```vue
<template>
  <AppShell title="借用限制管理">
    <section class="page-section panel default-panel">
      <div>
        <p class="eyebrow">Global Limit</p>
        <h2>全局默认借用上限</h2>
        <p>未单独配置的用户默认最多可同时借用 {{ defaultLimit || 3 }} 台设备。</p>
      </div>
      <label>
        默认上限
        <input v-model.number="defaultLimitForm" type="number" min="1" />
      </label>
      <button class="primary-btn" :disabled="loading" @click="saveDefaultLimit">保存全局上限</button>
    </section>

    <section class="page-section panel">
      <div class="toolbar">
        <input v-model.trim="keyword" placeholder="搜索姓名 / 手机号 / 机构" />
        <button :disabled="loading" @click="loadBorrowLimits">刷新</button>
      </div>

      <div v-if="loading" class="empty-state">加载中...</div>
      <div v-else-if="!filteredUsers.length" class="empty-state">暂无用户。</div>

      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>用户</th>
              <th>手机号</th>
              <th>机构</th>
              <th>角色</th>
              <th>当前借用</th>
              <th>覆盖上限</th>
              <th>有效上限</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredUsers" :key="item.userId">
              <td>{{ item.name || '--' }}</td>
              <td>{{ item.phone || '--' }}</td>
              <td>{{ item.organization || '--' }}</td>
              <td>{{ roleLabel(item.role) }}</td>
              <td>{{ item.currentBorrowCount }}</td>
              <td>
                <input
                  v-model.number="limitForms[item.userId]"
                  class="limit-input"
                  type="number"
                  min="1"
                  :placeholder="String(defaultLimit || 3)"
                />
              </td>
              <td>{{ item.effectiveBorrowLimit }}</td>
              <td class="actions">
                <button :disabled="loading" @click="saveUserLimit(item)">保存</button>
                <button class="ghost-btn" :disabled="loading || item.borrowLimitOverride === null" @click="clearUserLimit(item)">
                  清除覆盖
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </AppShell>
</template>

<script>
import AppShell from './common/AppShell.vue'
import { fetchBorrowLimits, updateDefaultBorrowLimit, updateUserBorrowLimit } from '../api/medicalColdChain'

function toPositiveInteger(value) {
  const numberValue = Number(value)
  if (!Number.isInteger(numberValue) || numberValue < 1) return null
  return numberValue
}

export default {
  name: 'AdminBorrowLimitPage',
  components: {
    AppShell
  },
  data() {
    return {
      loading: false,
      defaultLimit: 3,
      defaultLimitForm: 3,
      users: [],
      limitForms: {},
      keyword: ''
    }
  },
  computed: {
    filteredUsers() {
      const normalizedKeyword = this.keyword.trim().toLowerCase()
      if (!normalizedKeyword) return this.users
      return this.users.filter(item => [item.name, item.phone, item.organization]
        .some(value => String(value || '').toLowerCase().includes(normalizedKeyword)))
    }
  },
  created() {
    this.loadBorrowLimits()
  },
  methods: {
    roleLabel(role) {
      return String(role || '').toUpperCase() === 'ADMIN' ? '超级管理员' : '普通用户'
    },
    syncForms(payload) {
      this.defaultLimit = payload.defaultLimit || 3
      this.defaultLimitForm = this.defaultLimit
      this.users = Array.isArray(payload.users) ? payload.users : []
      const forms = {}
      this.users.forEach(item => {
        forms[item.userId] = item.borrowLimitOverride
      })
      this.limitForms = forms
    },
    async loadBorrowLimits() {
      this.loading = true
      try {
        const payload = await fetchBorrowLimits()
        this.syncForms(payload || {})
      } catch (error) {
        this.$message.error(error.message)
      } finally {
        this.loading = false
      }
    },
    async saveDefaultLimit() {
      const limit = toPositiveInteger(this.defaultLimitForm)
      if (!limit) {
        this.$message.error('全局借用上限必须是正整数')
        return
      }
      this.loading = true
      try {
        const payload = await updateDefaultBorrowLimit(limit)
        this.syncForms(payload || {})
        this.$message.success('全局借用上限已保存')
      } catch (error) {
        this.$message.error(error.message)
      } finally {
        this.loading = false
      }
    },
    async saveUserLimit(item) {
      const limit = toPositiveInteger(this.limitForms[item.userId])
      if (!limit) {
        this.$message.error('用户借用上限必须是正整数')
        return
      }
      this.loading = true
      try {
        const payload = await updateUserBorrowLimit(item.userId, limit)
        this.syncForms(payload || {})
        this.$message.success('用户借用上限已保存')
      } catch (error) {
        this.$message.error(error.message)
      } finally {
        this.loading = false
      }
    },
    async clearUserLimit(item) {
      this.loading = true
      try {
        const payload = await updateUserBorrowLimit(item.userId, null)
        this.syncForms(payload || {})
        this.$message.success('已清除用户覆盖上限')
      } catch (error) {
        this.$message.error(error.message)
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.panel {
  padding: 20px;
}

.default-panel {
  display: grid;
  grid-template-columns: 1fr 160px 140px;
  gap: 14px;
  align-items: end;
  margin-bottom: 14px;
}

.eyebrow {
  margin-bottom: 6px;
  color: var(--primary);
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

h2 {
  margin-bottom: 8px;
  font-size: 20px;
}

p,
label {
  color: var(--text-muted);
}

label {
  display: grid;
  gap: 6px;
}

.toolbar {
  display: grid;
  grid-template-columns: 1fr 80px;
  gap: 10px;
  margin-bottom: 14px;
}

input,
button {
  height: 40px;
  border-radius: 10px;
}

input {
  padding: 0 12px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: #fff;
}

button {
  border: none;
  color: var(--primary-dark);
  background: rgba(15, 123, 255, 0.08);
  cursor: pointer;
}

.primary-btn {
  color: #fff;
  background: var(--primary);
}

.ghost-btn {
  color: var(--text-muted);
  background: rgba(148, 163, 184, 0.12);
}

.table-wrap {
  overflow: auto;
}

table {
  width: 100%;
  min-width: 1100px;
  border-collapse: collapse;
}

th,
td {
  padding: 12px;
  text-align: left;
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
}

th {
  color: var(--text-muted);
  font-size: 13px;
  background: rgba(248, 250, 252, 0.9);
}

.limit-input {
  width: 110px;
}

.actions {
  display: flex;
  gap: 8px;
}

.empty-state {
  padding: 30px 0;
  text-align: center;
  color: var(--text-muted);
}

button:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

@media (max-width: 760px) {
  .default-panel,
  .toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
```

- [ ] **Step 2: Run frontend lint**

Run from `vue_kgy_frontend/`:

```bash
npm run lint
```

Expected: lint passes. If ESLint reports a Vue parsing issue on `v-model.number="limitForms[item.userId]"`, replace it with `:value="limitForms[item.userId]" @input="$set(limitForms, item.userId, $event.target.value ? Number($event.target.value) : null)"` and rerun lint.

- [ ] **Step 3: Checkpoint**

If this is inside a git repository, run:

```bash
git add src/api/medicalColdChain.js src/router/index.js src/config/navigation.js src/components/adminBorrowLimitPage.vue
git commit -m "feat: add admin borrow limit management page"
```

Expected in this current project: skip commit because the app directory is not a git repository.

---

## Task 7: Add Frontend Force Return on Admin Borrow Overview

**Files:**
- Modify: `vue_kgy_frontend/src/components/adminDeviceBorrowPage.vue`

- [ ] **Step 1: Import forceReturnDevices**

In `adminDeviceBorrowPage.vue`, replace this import:

```js
import { fetchAllDeviceBorrows } from '../api/medicalColdChain'
```

with:

```js
import { fetchAllDeviceBorrows, forceReturnDevices } from '../api/medicalColdChain'
```

- [ ] **Step 2: Preserve deviceId in normalized records**

In `normalizeRecord`, add `deviceId` after `recordId`:

```js
    deviceId: raw.deviceId || null,
```

The start of the return object should become:

```js
  return {
    recordId: raw.recordId || raw.id || `${raw.deviceId || 'device'}-${index}`,
    deviceId: raw.deviceId || null,
    deviceName: raw.deviceName || '--',
```

- [ ] **Step 3: Add operation table header**

In the table header, add this column after the `状态` column:

```html
              <th>操作</th>
```

- [ ] **Step 4: Add force-return button cell**

In the table body row, add this cell after the status `<td>` block:

```html
              <td>
                <button
                  v-if="item.status === 'BORROWED'"
                  class="danger-action"
                  :disabled="loading || !item.deviceId"
                  @click="handleForceReturn(item)"
                >
                  强制归还
                </button>
                <span v-else class="muted">--</span>
              </td>
```

- [ ] **Step 5: Add method**

In the `methods` object, add this method after `loadRecords()`:

```js
    async handleForceReturn(item) {
      if (!item.deviceId) {
        this.$message.error('缺少设备信息，无法强制归还')
        return
      }
      this.loading = true
      try {
        await forceReturnDevices({ deviceIds: [item.deviceId] })
        await this.loadRecords()
        this.$message.success('强制归还成功')
      } catch (error) {
        this.$message.error(error.message)
      } finally {
        this.loading = false
      }
    }
```

Ensure the preceding method has a trailing comma. The end of the `methods` object should contain both `loadRecords` and `handleForceReturn`.

- [ ] **Step 6: Add styles**

Add these styles before `.empty-state`:

```css
.danger-action {
  color: #fff;
  background: var(--danger);
}

.muted {
  color: var(--text-muted);
}
```

- [ ] **Step 7: Run frontend lint**

Run from `vue_kgy_frontend/`:

```bash
npm run lint
```

Expected: lint passes.

- [ ] **Step 8: Checkpoint**

If this is inside a git repository, run:

```bash
git add src/components/adminDeviceBorrowPage.vue
git commit -m "feat: add admin force return action"
```

Expected in this current project: skip commit because the app directory is not a git repository.

---

## Task 8: Full Verification

**Files:**
- Verify all modified frontend and backend files.

- [ ] **Step 1: Run backend test suite**

Run from `vue_kgy_frontend/medical-cold-chain-backend/`:

```bash
./mvnw test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run frontend lint and build**

Run from `vue_kgy_frontend/`:

```bash
npm run lint
npm run build
```

Expected: lint passes and production build completes successfully.

- [ ] **Step 3: Start backend for manual verification**

Run from `vue_kgy_frontend/medical-cold-chain-backend/`:

```bash
./mvnw spring-boot:run
```

Expected: backend starts on port `8080` and connects to local MySQL `medical_cold_chain`.

- [ ] **Step 4: Start frontend for manual verification**

Run from `vue_kgy_frontend/` in a second terminal:

```bash
npm run serve
```

Expected: frontend starts. Use `http://127.0.0.1:8081` if Vue CLI reports that port, matching the project README.

- [ ] **Step 5: Verify normal-user default limit**

In the browser:

1. Log in as a non-admin phone.
2. Open “设备管理”.
3. Apply for 3 devices.
4. Apply for 1 more device.

Expected: the second application fails with `当前最多可借 3 台设备，已借 3 台，本次最多还能申请 0 台`.

- [ ] **Step 6: Verify global limit**

In the browser:

1. Log out.
2. Log in as super admin phone `18800000000`.
3. Open “借用限制管理”.
4. Set global default limit to 4.
5. Log in as a new non-admin phone.
6. Apply for 4 devices.
7. Apply for 1 more device.

Expected: the second application fails with `当前最多可借 4 台设备，已借 4 台，本次最多还能申请 0 台`.

- [ ] **Step 7: Verify user override and clear**

In the browser:

1. Log in as super admin phone `18800000000`.
2. Open “借用限制管理”.
3. Find the test user from Step 6.
4. Set that user’s override limit to 2.
5. Force return that user’s devices from “设备借用总览” or use the user account’s own return action.
6. Log in as that user.
7. Apply for 2 devices.
8. Apply for 1 more device.

Expected: the second application fails with `当前最多可借 2 台设备，已借 2 台，本次最多还能申请 0 台`.

Then:

1. Log in as super admin.
2. Open “借用限制管理”.
3. Clear the same user’s override.

Expected: the user row shows blank override and effective limit equal to the global default.

- [ ] **Step 8: Verify force return from borrow overview**

In the browser:

1. Log in as super admin phone `18800000000`.
2. Open “设备借用总览”.
3. Filter status to “借用中”.
4. Click “强制归还” on a borrowed record.

Expected: success message appears, the record refreshes to returned or disappears from the “借用中” filtered view, and the borrower’s device count decreases.

- [ ] **Step 9: Restore demo-friendly global default**

Before ending manual verification, set global default limit back to `3` in “借用限制管理”.

Expected: “全局借用上限已保存”.

---

## Self-Review Result

- Spec coverage: covered backend enforcement, global default setting, user override setting/clearing, admin endpoints, SQL schema, admin navigation/page, force-return UI, and manual verification.
- Placeholder scan: no unresolved placeholders or vague implementation steps remain.
- Type consistency: DTO names, service names, endpoint paths, frontend function names, and route paths are consistent across tasks.
