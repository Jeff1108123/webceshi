# Super Admin Delete User Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a super-admin-only delete-user action that refuses to delete administrators or users who still have active borrowed devices.

**Architecture:** Reuse the existing super-admin borrow limit management page as the user management surface. Add a backend service method behind the existing `DeviceController` admin guard, return a refreshed `BorrowLimitOverviewResponse`, and update the Vue page state from that response.

**Tech Stack:** Spring Boot 4, Java 17, Spring Data JPA, JUnit 5, Vue 2, Axios.

---

## File Structure

- Modify `medical-cold-chain-backend/src/test/java/com/medicalcoldchain/backend/MedicalColdChainBackendApplicationTests.java`: add service-level integration tests for eligible deletion and rejection paths.
- Modify `medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/service/BorrowLimitService.java`: add `deleteUser(Long userId)` and keep overview-building as the refreshed response path.
- Modify `medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/controller/DeviceController.java`: add `DELETE /api/devices/users/{userId}` protected by existing admin checks.
- Modify `src/api/medicalColdChain.js`: add `deleteAdminUser(userId)` using Axios delete.
- Modify `src/components/adminBorrowLimitPage.vue`: add delete action, client-side force-return-first prompt, confirmation, and row styling.

## Task 1: Backend deletion service tests

**Files:**
- Modify: `vue_kgy_frontend-history-axis-granularity/medical-cold-chain-backend/src/test/java/com/medicalcoldchain/backend/MedicalColdChainBackendApplicationTests.java`

- [ ] **Step 1: Add repository import**

Add this import with the existing repository imports:

```java
import com.medicalcoldchain.backend.repository.TransportDeviceRepository;
```

- [ ] **Step 2: Autowire transport device repository**

Add this field after `UserAccountRepository userAccountRepository`:

```java
    @Autowired
    private TransportDeviceRepository transportDeviceRepository;
```

- [ ] **Step 3: Add failing tests before helper methods**

Insert these tests before `private boolean isAlarm(...)`:

```java
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
```

- [ ] **Step 4: Run targeted backend tests and verify they fail because the method is missing**

Run from `vue_kgy_frontend-history-axis-granularity/medical-cold-chain-backend`:

```bash
./mvnw -Dtest=MedicalColdChainBackendApplicationTests#adminShouldDeleteOrdinaryUserWithoutBorrowedDevices test
```

Expected: compilation fails with `cannot find symbol` for `deleteUser(Long)` in `BorrowLimitService`.

## Task 2: Backend service implementation

**Files:**
- Modify: `vue_kgy_frontend-history-axis-granularity/medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/service/BorrowLimitService.java`

- [ ] **Step 1: Add the delete service method**

Insert this method after `updateUserBorrowLimit(...)`:

```java
    @Transactional
    public BorrowLimitOverviewResponse deleteUser(Long userId) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (resolveRole(user) == UserRole.ADMIN) {
            throw new BusinessException("不能删除超级管理员");
        }
        if (!transportDeviceRepository.findByCurrentUserIdOrderByDeviceCodeAsc(userId).isEmpty()) {
            throw new BusinessException("该用户仍有在用设备，请先强制归还后再删除");
        }
        userAccountRepository.delete(user);
        return buildOverview(getDefaultLimit());
    }
```

- [ ] **Step 2: Run backend deletion service tests**

Run from `vue_kgy_frontend-history-axis-granularity/medical-cold-chain-backend`:

```bash
./mvnw -Dtest=MedicalColdChainBackendApplicationTests#adminShouldDeleteOrdinaryUserWithoutBorrowedDevices,MedicalColdChainBackendApplicationTests#adminShouldNotDeleteMissingUser,MedicalColdChainBackendApplicationTests#adminShouldNotDeleteSuperAdminUser,MedicalColdChainBackendApplicationTests#adminShouldNotDeleteUserWithBorrowedDevices test
```

Expected: all four tests pass.

## Task 3: Backend controller endpoint

**Files:**
- Modify: `vue_kgy_frontend-history-axis-granularity/medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/controller/DeviceController.java`

- [ ] **Step 1: Add DeleteMapping import**

Add this import near the other Spring mapping imports:

```java
import org.springframework.web.bind.annotation.DeleteMapping;
```

- [ ] **Step 2: Add admin delete endpoint**

Insert this method after `updateUserBorrowLimit(...)` and before `apply(...)`:

```java
    @DeleteMapping("/users/{userId}")
    public ApiResponse<BorrowLimitOverviewResponse> deleteUser(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long userId) {
        UserAccount user = authService.requireUser(authorization);
        authService.requireAdmin(user);
        return ApiResponse.ok("用户已删除", borrowLimitService.deleteUser(userId));
    }
```

- [ ] **Step 3: Run backend test suite**

Run from `vue_kgy_frontend-history-axis-granularity/medical-cold-chain-backend`:

```bash
./mvnw test
```

Expected: the backend test suite passes.

## Task 4: Frontend API helper

**Files:**
- Modify: `vue_kgy_frontend-history-axis-granularity/src/api/medicalColdChain.js`

- [ ] **Step 1: Add delete API helper**

Insert this function after `updateUserBorrowLimit(...)`:

```js
export function deleteAdminUser(userId) {
  return request.delete(`/devices/users/${userId}`)
}
```

- [ ] **Step 2: Run frontend lint to catch syntax issues**

Run from `vue_kgy_frontend-history-axis-granularity`:

```bash
npm run lint
```

Expected: no new lint errors from `src/api/medicalColdChain.js`.

## Task 5: Frontend delete action

**Files:**
- Modify: `vue_kgy_frontend-history-axis-granularity/src/components/adminBorrowLimitPage.vue`

- [ ] **Step 1: Update API imports**

Replace the current API import with:

```js
import {
  deleteAdminUser,
  fetchBorrowLimits,
  updateDefaultBorrowLimit,
  updateUserBorrowLimit
} from '../api/medicalColdChain'
```

- [ ] **Step 2: Add delete button in the operation column**

In the `<td class="actions">` block, after the “清除覆盖” button, add:

```vue
                <button
                  class="danger-btn"
                  :disabled="loading || isAdminRole(item.role)"
                  :title="isAdminRole(item.role) ? '不能删除超级管理员' : '删除用户'"
                  @click="deleteUser(item)"
                >
                  删除用户
                </button>
```

- [ ] **Step 3: Add role helper and delete method**

Replace `roleLabel(role)` with these methods at the start of `methods`:

```js
    isAdminRole(role) {
      return String(role || '').toUpperCase() === 'ADMIN'
    },
    roleLabel(role) {
      return this.isAdminRole(role) ? '超级管理员' : '普通用户'
    },
```

Add this method after `clearUserLimit(item)`:

```js
    async deleteUser(item) {
      if (this.isAdminRole(item.role)) {
        this.$message.error('不能删除超级管理员')
        return
      }
      if (Number(item.currentBorrowCount || 0) > 0) {
        this.$message.error('该用户仍有在用设备，请先强制归还后再删除')
        return
      }
      const displayName = item.name || item.phone || '该用户'
      if (!window.confirm(`确定删除 ${displayName} 吗？此操作不可撤销。`)) {
        return
      }
      this.loading = true
      try {
        const payload = await deleteAdminUser(item.userId)
        this.syncForms(payload || {})
        this.$message.success('用户已删除')
      } catch (error) {
        this.$message.error(error.message)
      } finally {
        this.loading = false
      }
    }
```

Keep the comma between Vue methods valid: `clearUserLimit` must end with `},` before `deleteUser`, and `deleteUser` can be the final method without a trailing comma.

- [ ] **Step 4: Add danger button styles**

Insert after `.ghost-btn` styles:

```css
.danger-btn {
  color: #ffe7e7;
  border-color: rgba(248, 113, 113, 0.46);
  background: linear-gradient(135deg, rgba(185, 28, 28, 0.34), rgba(127, 29, 29, 0.26));
  box-shadow: 0 10px 24px rgba(248, 113, 113, 0.1), inset 0 1px 0 rgba(255, 255, 255, 0.05);
}

.danger-btn:hover:not(:disabled) {
  border-color: rgba(248, 113, 113, 0.72);
  box-shadow: 0 12px 28px rgba(248, 113, 113, 0.16);
}
```

- [ ] **Step 5: Run frontend lint**

Run from `vue_kgy_frontend-history-axis-granularity`:

```bash
npm run lint
```

Expected: lint passes or only reports pre-existing unrelated warnings. There should be no syntax errors in `adminBorrowLimitPage.vue`.

## Task 6: Final verification

**Files:**
- Verify all modified files.

- [ ] **Step 1: Run backend tests**

Run from `vue_kgy_frontend-history-axis-granularity/medical-cold-chain-backend`:

```bash
./mvnw test
```

Expected: backend tests pass.

- [ ] **Step 2: Run frontend lint**

Run from `vue_kgy_frontend-history-axis-granularity`:

```bash
npm run lint
```

Expected: frontend lint passes.

- [ ] **Step 3: Run frontend build**

Run from `vue_kgy_frontend-history-axis-granularity`:

```bash
npm run build
```

Expected: production build completes successfully.

- [ ] **Step 4: Review changed files**

Run from `vue_kgy_frontend-history-axis-granularity`:

```bash
git status --short
git diff -- medical-cold-chain-backend/src/test/java/com/medicalcoldchain/backend/MedicalColdChainBackendApplicationTests.java medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/service/BorrowLimitService.java medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/controller/DeviceController.java src/api/medicalColdChain.js src/components/adminBorrowLimitPage.vue docs/superpowers/specs/2026-05-22-super-admin-delete-user-design.md docs/superpowers/plans/2026-05-22-super-admin-delete-user.md
```

Expected: diff contains only the delete-user feature, design spec, and implementation plan.

## Self-Review

- Spec coverage: backend endpoint, backend safety checks, frontend button, force-return-first prompt, confirmation, refreshed table, and tests are all covered.
- Placeholder scan: no placeholder tasks remain; every code-changing step includes concrete code.
- Type consistency: frontend uses `currentBorrowCount`, `role`, `userId`, and the existing `BorrowLimitOverviewResponse.users` shape. Backend uses existing `BorrowLimitOverviewResponse`, `BusinessException`, `UserRole.ADMIN`, and repository methods already present in the codebase.

## Execution Note

Do not create a git commit unless the user explicitly asks for one. The plan includes verification checkpoints instead of commit steps to comply with the session git safety rules.
