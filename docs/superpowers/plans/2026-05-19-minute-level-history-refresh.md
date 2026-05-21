# Minute-Level History Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make history refresh advance to the current minute instead of waiting for the next 5-minute time bucket.

**Architecture:** Keep the existing history API shape and frontend refresh behavior, but change the backend telemetry timeline generation so history data is aligned and extended at minute granularity. The work stays centered in `TelemetryService`, because that service owns both initial history seeding and on-demand timeline extension for history/monitor pages.

**Tech Stack:** Spring Boot, Spring Data JPA, MySQL/H2, existing Vue history page consuming `/api/devices/{deviceId}/history`

---

## File Structure

- **Modify:** `medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/service/TelemetryService.java`
  - Change history timeline generation from 5/15-minute bucket logic to minute-level logic while preserving snapshot sync and existing DTO output.
- **Verify only:** `medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/service/DeviceService.java`
  - Reuse existing `getHistory(...)` and `getMonitorTelemetry(...)` flows unchanged.
- **Verify only:** `src/components/sixthPage.vue`
  - No functional API contract change required; manual refresh should begin showing later timestamps automatically after backend change.

---

### Task 1: Prove the current behavior is bucketed at 5-minute granularity

**Files:**
- Modify: `medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/service/TelemetryService.java`
- Test: `medical-cold-chain-backend/src/test/java/com/medicalcoldchain/backend/MedicalColdChainBackendApplicationTests.java` or a new targeted test file if needed

- [ ] **Step 1: Inspect the existing bucket logic in `TelemetryService.java`**

Confirm these current lines exist:

```java
LocalDateTime endTime = alignTime(LocalDateTime.now(), 5);
LocalDateTime startTime = alignTime(endTime.minusHours(24), 15);
```

and:

```java
LocalDateTime end = alignTime(LocalDateTime.now(), 5);
```

and:

```java
LocalDateTime alignedTarget = alignTime(targetTime, 5);
```

Expected: the root cause is explicit — history refresh is currently capped to the previous 5-minute boundary.

- [ ] **Step 2: Add a focused failing backend test that describes minute-level expectation**

Create a new test file if none exists for telemetry behavior:

`medical-cold-chain-backend/src/test/java/com/medicalcoldchain/backend/service/TelemetryServiceMinuteRefreshTest.java`

Use this test shape:

```java
@SpringBootTest
class TelemetryServiceMinuteRefreshTest {

    @Autowired
    private TelemetryService telemetryService;

    @Autowired
    private TransportDeviceRepository transportDeviceRepository;

    @Test
    void historyTimelineCanExtendToCurrentMinute() {
        TransportDevice device = transportDeviceRepository.save(TransportDevice.builder()
                .deviceCode("TEST-MINUTE-001")
                .deviceName("测试冷链箱")
                .medicineName("测试药品")
                .routeName("测试线路")
                .status(DeviceStatus.IN_USE)
                .batteryLevel(100)
                .signalStatus(true)
                .build());

        telemetryService.generateBorrowHistory(device, LocalDateTime.now().minusHours(1));
        List<TelemetryRecord> records = telemetryService.getHistoryRecords(device, 1);

        assertThat(records).isNotEmpty();
        LocalDateTime lastRecordedAt = records.get(records.size() - 1).getRecordedAt();
        LocalDateTime currentMinute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        assertThat(lastRecordedAt).isEqualTo(currentMinute);
    }
}
```

Expected: this test should fail before the implementation because the existing logic truncates to 5-minute buckets.

- [ ] **Step 3: Run the targeted backend test and watch it fail for the right reason**

Run:

```bash
cd "D:/web作业/vue_kgy_frontend/medical-cold-chain-backend" && ./mvnw.cmd -q -Dtest=TelemetryServiceMinuteRefreshTest test
```

Expected: FAIL because `lastRecordedAt` lags behind `currentMinute` when current time is not on a 5-minute boundary.

- [ ] **Step 4: Keep the failure focused on time alignment, not setup noise**

If the test fails for uniqueness or unrelated setup noise, change the device code to include a nanoTime suffix:

```java
String code = "TEST-MINUTE-" + System.nanoTime();
```

Expected: the red state is specifically about time bucketing.

- [ ] **Step 5: Commit the failing test if git is available**

```bash
git add src/test/java/com/medicalcoldchain/backend/service/TelemetryServiceMinuteRefreshTest.java
git commit -m "test: capture minute-level history refresh expectation"
```

If the directory is not a git repo, skip commit and continue.

---

### Task 2: Change timeline generation to minute-level alignment

**Files:**
- Modify: `medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/service/TelemetryService.java`

- [ ] **Step 1: Introduce explicit constants for history granularity**

Near the top of `TelemetryService.java`, add:

```java
private static final int HISTORY_STEP_MINUTES = 1;
private static final int HISTORY_WINDOW_HOURS = 24;
```

Expected: the minute-level behavior is declared once instead of hard-coded in multiple methods.

- [ ] **Step 2: Update `generateBorrowHistory(...)` to seed a minute-level timeline**

Replace:

```java
LocalDateTime endTime = alignTime(LocalDateTime.now(), 5);
LocalDateTime startTime = alignTime(endTime.minusHours(24), 15);
telemetryRecordRepository.deleteByDeviceIdAndRecordedAtGreaterThanEqual(device.getId(), startTime);
deviceLocationRepository.deleteByDeviceIdAndRecordedAtGreaterThanEqual(device.getId(), startTime);
saveTimeline(device, startTime, endTime, 15);
```

with:

```java
LocalDateTime endTime = alignTime(LocalDateTime.now(), HISTORY_STEP_MINUTES);
LocalDateTime startTime = alignTime(endTime.minusHours(HISTORY_WINDOW_HOURS), HISTORY_STEP_MINUTES);
telemetryRecordRepository.deleteByDeviceIdAndRecordedAtGreaterThanEqual(device.getId(), startTime);
deviceLocationRepository.deleteByDeviceIdAndRecordedAtGreaterThanEqual(device.getId(), startTime);
saveTimeline(device, startTime, endTime, HISTORY_STEP_MINUTES);
```

Expected: newly created devices immediately get minute-granularity history.

- [ ] **Step 3: Update `getHistoryRecords(...)` to request the current minute, not a 5-minute bucket**

Replace:

```java
LocalDateTime end = alignTime(LocalDateTime.now(), 5);
```

with:

```java
LocalDateTime end = alignTime(LocalDateTime.now(), HISTORY_STEP_MINUTES);
```

Expected: the history endpoint now asks for the latest current-minute cutoff.

- [ ] **Step 4: Update `ensureTimeline(...)` so incremental filling also runs minute-by-minute**

Replace:

```java
LocalDateTime alignedTarget = alignTime(targetTime, 5);
```

with:

```java
LocalDateTime alignedTarget = alignTime(targetTime, HISTORY_STEP_MINUTES);
```

Replace:

```java
LocalDateTime startTime = alignTime(alignedTarget.minusHours(24), 15);
saveTimeline(device, startTime, alignedTarget, 15);
```

with:

```java
LocalDateTime startTime = alignTime(alignedTarget.minusHours(HISTORY_WINDOW_HOURS), HISTORY_STEP_MINUTES);
saveTimeline(device, startTime, alignedTarget, HISTORY_STEP_MINUTES);
```

Replace:

```java
saveTimeline(device, latest.getRecordedAt().plusMinutes(5), alignedTarget, 5);
```

with:

```java
saveTimeline(device, latest.getRecordedAt().plusMinutes(HISTORY_STEP_MINUTES), alignedTarget, HISTORY_STEP_MINUTES);
```

Expected: every history refresh fills the missing minutes up to “now” consistently.

- [ ] **Step 5: Run the targeted minute-refresh test again**

Run:

```bash
cd "D:/web作业/vue_kgy_frontend/medical-cold-chain-backend" && ./mvnw.cmd -q -Dtest=TelemetryServiceMinuteRefreshTest test
```

Expected: PASS.

---

### Task 3: Verify the change does not break the rest of telemetry behavior

**Files:**
- Modify only if needed: `medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/service/TelemetryService.java`
- Verify: `medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/service/DeviceService.java`

- [ ] **Step 1: Run the full backend test suite**

Run:

```bash
cd "D:/web作业/vue_kgy_frontend/medical-cold-chain-backend" && ./mvnw.cmd -q test
```

Expected: PASS.

- [ ] **Step 2: Compile the frontend to ensure the existing history page contract still works**

Run:

```bash
cd "D:/web作业/vue_kgy_frontend" && npm run build
```

Expected: PASS, because the API response shape has not changed.

- [ ] **Step 3: Manually verify the refresh behavior in the browser**

Use this exact flow:

```text
1. 打开历史数据页
2. 记录“数据更新到”时间
3. 等待 1 分钟跨过新的分钟边界
4. 点击“刷新到当前时间”
5. 确认“数据更新到”推进到当前分钟
6. 确认“当前历史截止点”和图表最后一个点一起前进
```

Expected: the page visibly advances after a 1-minute boundary instead of waiting for 5 minutes.

- [ ] **Step 4: Verify startup and borrowing still work after the telemetry change**

Run:

```bash
cd "D:/web作业/vue_kgy_frontend/medical-cold-chain-backend" && ./mvnw.cmd -q -DskipTests compile
```

Then manually confirm:
- Backend still starts against MySQL
- Borrowing a device still creates history records
- History page still loads for that device

Expected: the change is isolated to granularity, not functionality.

- [ ] **Step 5: Commit the minute-level refresh change if git is available**

```bash
git add src/main/java/com/medicalcoldchain/backend/service/TelemetryService.java src/test/java/com/medicalcoldchain/backend/service/TelemetryServiceMinuteRefreshTest.java
git commit -m "feat: refresh history data at minute granularity"
```

If the directory is not a git repo, skip commit and note that limitation in the handoff.

---

## Self-Review

- **Spec coverage:** The plan covers all affected layers of the 5-minute bucket problem: initial seed, incremental timeline fill, history query cutoff, and user-visible refresh verification.
- **Placeholder scan:** No TODO/TBD markers remain; all steps include exact files, commands, code, and expected outcomes.
- **Type consistency:** The plan consistently uses existing names (`getHistoryRecords`, `generateBorrowHistory`, `ensureTimeline`, `saveTimeline`, `alignTime`) and introduces only `HISTORY_STEP_MINUTES` and `HISTORY_WINDOW_HOURS` as new constants.
