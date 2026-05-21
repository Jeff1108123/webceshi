# Combined History Chart and Thresholds Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the three separate history trend charts with one combined real-value chart and narrow default thresholds for newly created device thresholds.

**Architecture:** Backend keeps telemetry generation and threshold defaults authoritative; tests verify new default threshold values and smooth telemetry output. Frontend adds a focused combined chart component that independently scales temperature, humidity, and light into one SVG frame while displaying real values in legends and stats.

**Tech Stack:** Vue 2 Options API, SVG, Spring Boot 4, JUnit 5, Maven, Vue CLI.

---

## File Structure

- Create: `src/components/common/CombinedTrendChartCard.vue`
  - Renders one SVG card with three real-value series, independent y scaling per metric, shared x-axis, smoothed paths, legend, and per-series latest/min/max values.
- Modify: `src/components/sixthPage.vue`
  - Replaces three `TrendChartCard` instances with one `CombinedTrendChartCard` and passes existing series/threshold data.
- Modify: `medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/service/ThresholdService.java`
  - Changes only default constants for newly created thresholds.
- Modify: `medical-cold-chain-backend/src/test/java/com/medicalcoldchain/backend/MedicalColdChainBackendApplicationTests.java`
  - Adds a regression test for default threshold values.

---

## Task 1: Backend Default Threshold Regression Test

**Files:**
- Modify: `medical-cold-chain-backend/src/test/java/com/medicalcoldchain/backend/MedicalColdChainBackendApplicationTests.java`

- [x] **Step 1: Write failing test**

Add this test before helper methods near the end of the test class:

```java
    @Test
    void newDeviceThresholdShouldUseNarrowDefaultRange() {
        UserAccount dispatcher = createBorrowLimitTestUser("128", "默认阈值测试调度员");

        deviceService.applyDevices(dispatcher, applyRequest(1));
        Long deviceId = deviceService.listMyDevices(dispatcher).get(0).getId();
        ThresholdResponse threshold = deviceService.getThreshold(dispatcher, deviceId);

        assertEquals(3D, threshold.getTempMin());
        assertEquals(7D, threshold.getTempMax());
        assertEquals(45D, threshold.getHumidityMin());
        assertEquals(70D, threshold.getHumidityMax());
        assertEquals(9D, threshold.getLightMax());
    }
```

If `ThresholdResponse` is not imported, add:

```java
import com.medicalcoldchain.backend.dto.device.ThresholdResponse;
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd "D:/web作业/vue_kgy_frontend/medical-cold-chain-backend" && ./mvnw test -Dtest=MedicalColdChainBackendApplicationTests#newDeviceThresholdShouldUseNarrowDefaultRange
```

Expected: FAIL because current defaults are `2~8°C`, `35~75%`, and `10 Lux`.

---

## Task 2: Narrow Backend Default Thresholds

**Files:**
- Modify: `medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/service/ThresholdService.java`

- [x] **Step 1: Change default constants**

Replace:

```java
    private static final double DEFAULT_TEMP_MIN = 2D;
    private static final double DEFAULT_TEMP_MAX = 8D;
    private static final double DEFAULT_HUMIDITY_MIN = 35D;
    private static final double DEFAULT_HUMIDITY_MAX = 75D;
    private static final double DEFAULT_LIGHT_MAX = 10D;
```

with:

```java
    private static final double DEFAULT_TEMP_MIN = 3D;
    private static final double DEFAULT_TEMP_MAX = 7D;
    private static final double DEFAULT_HUMIDITY_MIN = 45D;
    private static final double DEFAULT_HUMIDITY_MAX = 70D;
    private static final double DEFAULT_LIGHT_MAX = 9D;
```

- [x] **Step 2: Run targeted test**

Run:

```bash
cd "D:/web作业/vue_kgy_frontend/medical-cold-chain-backend" && ./mvnw test -Dtest=MedicalColdChainBackendApplicationTests#newDeviceThresholdShouldUseNarrowDefaultRange
```

Expected: PASS.

- [x] **Step 3: Run backend suite**

Run:

```bash
cd "D:/web作业/vue_kgy_frontend/medical-cold-chain-backend" && ./mvnw test
```

Expected: BUILD SUCCESS.

---

## Task 3: Create Combined Trend Chart Component

**Files:**
- Create: `src/components/common/CombinedTrendChartCard.vue`

- [x] **Step 1: Create component**

Create `CombinedTrendChartCard.vue` with a Vue 2 component that accepts:

```js
props: {
  labels: { type: Array, default: () => [] },
  temperatureValues: { type: Array, default: () => [] },
  humidityValues: { type: Array, default: () => [] },
  lightValues: { type: Array, default: () => [] },
  threshold: { type: Object, default: () => ({}) }
}
```

Implement these metric definitions in computed state:

```js
metricConfigs() {
  return [
    {
      key: 'temperature',
      label: '温度',
      unit: '°C',
      color: '#0f7bff',
      values: this.temperatureValues,
      thresholdMin: this.threshold.tempMin,
      thresholdMax: this.threshold.tempMax
    },
    {
      key: 'humidity',
      label: '湿度',
      unit: '%',
      color: '#1f9d66',
      values: this.humidityValues,
      thresholdMin: this.threshold.humidityMin,
      thresholdMax: this.threshold.humidityMax
    },
    {
      key: 'light',
      label: '光照',
      unit: 'Lux',
      color: '#d97706',
      values: this.lightValues,
      thresholdMin: null,
      thresholdMax: this.threshold.lightMax
    }
  ]
}
```

Use one SVG with `viewBox="0 0 720 300"`. For each metric, compute finite points, scale min/max from that metric's values and thresholds only, then generate a smoothed cubic path using the same control-point approach currently used in `TrendChartCard.vue`.

Template must include:

```vue
<article class="combined-trend-card">
  <div class="combined-head">
    <div>
      <h4>综合趋势</h4>
      <p>温度、湿度、光照三项指标独立缩放后合并展示</p>
    </div>
    <div class="legend">
      <span v-for="series in chartSeries" :key="series.key">
        <i :style="{ background: series.color }"></i>{{ series.label }} {{ formatMetric(series.latest, series.unit) }}
      </span>
    </div>
  </div>
  <div v-if="hasPoints" class="chart-shell">
    <svg class="trend-svg" viewBox="0 0 720 300" preserveAspectRatio="none">
      <line v-for="gridLine in gridLines" :key="gridLine.key" class="grid-line" :x1="padding.left" :y1="gridLine.y" :x2="width - padding.right" :y2="gridLine.y" />
      <path v-for="series in chartSeries" :key="series.key" class="trend-line" :d="series.path" :style="{ stroke: series.color }" />
      <circle v-for="series in chartSeries" :key="`${series.key}-last`" :cx="series.lastPoint.x" :cy="series.lastPoint.y" r="4" :style="{ fill: series.color }" />
      <text v-for="tick in xTicks" :key="tick.key" class="axis-text" :x="tick.x" :y="height - 16" :text-anchor="tick.anchor">{{ tick.label }}</text>
    </svg>
  </div>
  <div v-else class="empty-state">暂无历史数据</div>
  <div class="metric-stats">
    <div v-for="series in chartSeries" :key="`${series.key}-stats`" class="stat-row">
      <strong :style="{ color: series.color }">{{ series.label }}</strong>
      <span>最新 {{ formatMetric(series.latest, series.unit) }}</span>
      <span>最低 {{ formatMetric(series.min, series.unit) }}</span>
      <span>最高 {{ formatMetric(series.max, series.unit) }}</span>
      <small>{{ thresholdText(series) }}</small>
    </div>
  </div>
</article>
```

Add scoped styles matching the existing card language: white translucent panel, rounded corners, muted grid lines, colored legend dots, and responsive stacking.

- [x] **Step 2: Run frontend lint**

Run:

```bash
cd "D:/web作业/vue_kgy_frontend" && npm run lint
```

Expected: no lint errors.

---

## Task 4: Replace Three History Charts With Combined Chart

**Files:**
- Modify: `src/components/sixthPage.vue`

- [x] **Step 1: Update import and registration**

Replace:

```js
import TrendChartCard from './common/TrendChartCard.vue'
```

with:

```js
import CombinedTrendChartCard from './common/CombinedTrendChartCard.vue'
```

Replace component registration:

```js
TrendChartCard
```

with:

```js
CombinedTrendChartCard
```

- [x] **Step 2: Replace chart template**

Replace the whole `<section class="trend-list">...</section>` containing the three `TrendChartCard` entries with:

```vue
        <section class="trend-list">
          <CombinedTrendChartCard
            :labels="pointLabels"
            :temperature-values="temperatureSeries"
            :humidity-values="humiditySeries"
            :light-values="lightSeries"
            :threshold="historyData.threshold"
          />
        </section>
```

- [x] **Step 3: Run frontend lint and build**

Run:

```bash
cd "D:/web作业/vue_kgy_frontend" && npm run lint
cd "D:/web作业/vue_kgy_frontend" && npm run build
```

Expected: lint passes and production build compiles successfully.

---

## Task 5: Final Verification and Commit

**Files:**
- All changed files.

- [x] **Step 1: Run backend tests**

Run:

```bash
cd "D:/web作业/vue_kgy_frontend/medical-cold-chain-backend" && ./mvnw test
```

Expected: BUILD SUCCESS.

- [x] **Step 2: Run frontend checks**

Run:

```bash
cd "D:/web作业/vue_kgy_frontend" && npm run lint
cd "D:/web作业/vue_kgy_frontend" && npm run build
```

Expected: no lint errors and compiled successfully.

- [ ] **Step 3: Commit and push**

Run:

```bash
git -C "D:/web作业/vue_kgy_frontend" status --short
git -C "D:/web作业/vue_kgy_frontend" add medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/service/ThresholdService.java medical-cold-chain-backend/src/test/java/com/medicalcoldchain/backend/MedicalColdChainBackendApplicationTests.java src/components/common/CombinedTrendChartCard.vue src/components/sixthPage.vue docs/superpowers/plans/2026-05-21-combined-history-chart-thresholds.md
git -C "D:/web作业/vue_kgy_frontend" -c user.name="Jeff1108123" -c user.email="2823871364@qq.com" commit -m "$(cat <<'EOF'
Combine history trends and narrow thresholds

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
git -C "D:/web作业/vue_kgy_frontend" push
```

Expected: commit succeeds and pushes to `origin/master`.

---

## Self-Review Result

- Spec coverage: combined chart, independent metric scaling, smoothed paths, default thresholds, existing data not migrated, and verification are covered.
- Placeholder scan: no TBD/TODO or vague implementation-only steps remain.
- Type consistency: prop names in plan match the intended Vue kebab-case bindings and camelCase component props.
