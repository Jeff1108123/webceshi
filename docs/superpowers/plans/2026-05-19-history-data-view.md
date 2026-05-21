# History Data View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the history data page so users can analyze device trends faster with clearer summaries, better filtering, and more reliable display logic.

**Architecture:** Keep the existing `/history` route and `fetchHistory()` backend contract, but restructure `src/components/sixthPage.vue` into a richer analysis page that derives summary cards, alarm counts, and chart data from the existing `HistoryResponse.points` payload. Reuse the existing `TrendChartCard` chart component and date formatting utility while improving frontend state transitions, range switching, and empty/loading handling locally in the history page.

**Tech Stack:** Vue 2 Options API, Pinia, existing Axios API wrapper, existing TrendChartCard component, Spring Boot history endpoint (read-only reuse)

---

## File Structure

- **Modify:** `src/components/sixthPage.vue`
  - Expand the page from a simple three-chart list into a history analysis screen with toolbar, summary cards, chart cards, alarm list, loading/empty states, and derived computed display logic.
- **Modify:** `src/components/common/TrendChartCard.vue`
  - Add support for chart subtitles / helper text and optional highlighted alarm points only if the existing component cannot express the required visual cues cleanly.
- **Modify:** `src/utils/dateTime.js`
  - Add any small formatting helper needed by the history page, such as short date labels or human-readable time spans, only if existing formatting helpers are insufficient.
- **Read/verify only:** `src/api/medicalColdChain.js`
  - Reuse `fetchHistory(deviceId, hours)` without changing the backend contract.
- **Read/verify only:** `medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/dto/telemetry/HistoryResponse.java`
  - Confirm the frontend plan stays aligned with the existing response shape.

---

### Task 1: Add failing frontend expectations for derived history display logic

**Files:**
- Modify: `src/components/sixthPage.vue`
- Test: no existing test harness is evident in this repo, so use a manual red/green verification pass on the page itself after wiring temporary visible outputs.

- [ ] **Step 1: Read the existing history page and identify the exact gaps to close**

Review these current areas in `src/components/sixthPage.vue`:

```js
computed: {
  points() {
    return this.historyData && Array.isArray(this.historyData.points) ? this.historyData.points : []
  },
  pointLabels() {
    return this.points.map(item => formatMonthDayTime(item.recordedAt))
  },
  temperatureSeries() {
    return this.points.map(item => item.temperature)
  },
  humiditySeries() {
    return this.points.map(item => item.humidity)
  },
  lightSeries() {
    return this.points.map(item => item.light)
  }
}
```

Expected finding: the page currently renders only three trend charts and has no summary, loading state, anomaly extraction, last-update summary, or graceful handling of missing point sets.

- [ ] **Step 2: Define the desired derived outputs before implementation**

Plan to surface these exact computed outputs from `historyData.points`:

```js
summaryCards = [
  { key: 'temperature', label: '温度区间', value: 'min ~ max °C', alarmCount: n },
  { key: 'humidity', label: '湿度区间', value: 'min ~ max %', alarmCount: n },
  { key: 'light', label: '最高光照', value: 'max Lux', alarmCount: n },
  { key: 'sampling', label: '采样点', value: 'xx 条', alarmCount: 0 }
]

alarmMoments = [
  { recordedAt, type: '温度异常', value: '9.4°C', detail: '阈值 2 ~ 8°C' }
]

latestSnapshot = {
  recordedAt,
  temperature,
  humidity,
  light,
  batteryLevel,
  signalStatus,
  alarm
}
```

Expected: these outputs are enough to power the UI without changing the backend payload.

- [ ] **Step 3: Add temporary placeholder sections in the template that will clearly fail until the logic exists**

Insert placeholders in `src/components/sixthPage.vue` below the toolbar:

```vue
<div v-else-if="historyData" class="summary-grid">
  <article v-for="card in summaryCards" :key="card.key" class="summary-card">
    <span>{{ card.label }}</span>
    <strong>{{ card.value }}</strong>
    <small>{{ card.alarmCount }} 次异常</small>
  </article>
</div>
```

Expected: the page should fail to compile because `summaryCards` does not exist yet. This is the manual “red” step in the absence of an automated frontend test harness.

- [ ] **Step 4: Run the frontend build to verify the missing computed values fail as expected**

Run:

```bash
cd "D:/web作业/vue_kgy_frontend" && npm run build
```

Expected: FAIL with a Vue compile/runtime reference complaint about `summaryCards` not existing.

- [ ] **Step 5: Commit the red-state checkpoint if you are working on a branch with explicit checkpoints**

```bash
git add src/components/sixthPage.vue
git commit -m "test: expose missing history summary outputs"
```

Expected: a small checkpoint commit capturing the intended UI contract.

---

### Task 2: Implement richer history-page state and derived display logic

**Files:**
- Modify: `src/components/sixthPage.vue`

- [ ] **Step 1: Expand the page state to track loading, refresh time, and selected metric context**

Update `data()` in `src/components/sixthPage.vue` to:

```js
data() {
  return {
    deviceStore: useDeviceStore(),
    selectedDeviceId: null,
    hours: 24,
    historyData: null,
    loading: false,
    loadedAt: '',
    activeMetric: 'temperature'
  }
}
```

Expected: the page can now distinguish initial empty state from active loading and can remember which metric is emphasized.

- [ ] **Step 2: Replace the basic computed block with derived series, summaries, and anomalies**

Use this computed structure in `src/components/sixthPage.vue`:

```js
computed: {
  points() {
    return this.historyData && Array.isArray(this.historyData.points) ? this.historyData.points : []
  },
  hasPoints() {
    return this.points.length > 0
  },
  pointLabels() {
    return this.points.map(item => formatMonthDayTime(item.recordedAt))
  },
  temperatureSeries() {
    return this.points.map(item => item.temperature)
  },
  humiditySeries() {
    return this.points.map(item => item.humidity)
  },
  lightSeries() {
    return this.points.map(item => item.light)
  },
  latestSnapshot() {
    return this.points.length ? this.points[this.points.length - 1] : null
  },
  alarmMoments() {
    if (!this.historyData || !this.historyData.threshold) return []
    const { tempMin, tempMax, humidityMin, humidityMax, lightMax } = this.historyData.threshold

    return this.points.flatMap(point => {
      const alarms = []
      if (point.temperature < tempMin || point.temperature > tempMax) {
        alarms.push({
          recordedAt: point.recordedAt,
          type: '温度异常',
          value: `${point.temperature}°C`,
          detail: `阈值 ${tempMin} ~ ${tempMax}°C`
        })
      }
      if (point.humidity < humidityMin || point.humidity > humidityMax) {
        alarms.push({
          recordedAt: point.recordedAt,
          type: '湿度异常',
          value: `${point.humidity}%`,
          detail: `阈值 ${humidityMin} ~ ${humidityMax}%`
        })
      }
      if (point.light > lightMax) {
        alarms.push({
          recordedAt: point.recordedAt,
          type: '光照异常',
          value: `${point.light}Lux`,
          detail: `阈值 ≤ ${lightMax}Lux`
        })
      }
      if (point.signalStatus === false) {
        alarms.push({
          recordedAt: point.recordedAt,
          type: '信号异常',
          value: '信号中断',
          detail: '设备上报信号状态异常'
        })
      }
      return alarms
    })
  },
  summaryCards() {
    if (!this.hasPoints || !this.historyData || !this.historyData.threshold) return []
    const temperatures = this.temperatureSeries
    const humidities = this.humiditySeries
    const lights = this.lightSeries

    return [
      {
        key: 'temperature',
        label: '温度区间',
        value: `${Math.min(...temperatures)} ~ ${Math.max(...temperatures)}°C`,
        alarmCount: this.alarmMoments.filter(item => item.type === '温度异常').length
      },
      {
        key: 'humidity',
        label: '湿度区间',
        value: `${Math.min(...humidities)} ~ ${Math.max(...humidities)}%`,
        alarmCount: this.alarmMoments.filter(item => item.type === '湿度异常').length
      },
      {
        key: 'light',
        label: '最高光照',
        value: `${Math.max(...lights)}Lux`,
        alarmCount: this.alarmMoments.filter(item => item.type === '光照异常').length
      },
      {
        key: 'sampling',
        label: '采样点',
        value: `${this.points.length} 条`,
        alarmCount: this.alarmMoments.length
      }
    ]
  }
}
```

Expected: the page can now summarize trends and enumerate anomaly moments entirely from the existing response.

- [ ] **Step 3: Make `loadHistory()` robust for repeated refreshes and state transitions**

Replace the method with:

```js
async loadHistory() {
  if (!this.selectedDeviceId) return
  this.loading = true
  try {
    this.historyData = await fetchHistory(this.selectedDeviceId, this.hours)
    this.loadedAt = formatDateTime()
  } catch (error) {
    this.$message.error(error.message)
  } finally {
    this.loading = false
  }
}
```

Also update imports:

```js
import { formatDateTime, formatMonthDayTime } from '../utils/dateTime'
```

Expected: refreshes show a reliable “updated at” timestamp and no longer silently blur loading vs. no-data states.

- [ ] **Step 4: Run the frontend build to verify the new computed logic compiles**

Run:

```bash
cd "D:/web作业/vue_kgy_frontend" && npm run build
```

Expected: PASS, proving the missing summary outputs are now implemented.

- [ ] **Step 5: Commit the derived-logic implementation**

```bash
git add src/components/sixthPage.vue
git commit -m "feat: derive richer history summaries and alarms"
```

---

### Task 3: Redesign the history page layout for readability

**Files:**
- Modify: `src/components/sixthPage.vue`

- [ ] **Step 1: Replace the current template body with a structured analysis layout**

Replace the content inside the panel with this structure:

```vue
<section class="page-section panel">
  <div class="toolbar">
    <div class="toolbar-group">
      <select v-if="deviceStore.devices.length" v-model="selectedDeviceId">
        <option v-for="item in deviceStore.devices" :key="item.id" :value="item.id">
          {{ item.deviceName }} ({{ item.deviceCode }})
        </option>
      </select>
      <select v-model="hours">
        <option :value="6">近 6 小时</option>
        <option :value="12">近 12 小时</option>
        <option :value="24">近 24 小时</option>
        <option :value="48">近 48 小时</option>
        <option :value="72">近 72 小时</option>
      </select>
    </div>
    <div class="toolbar-group toolbar-meta">
      <span>{{ loadedAt ? `更新时间：${loadedAt}` : '等待加载历史数据' }}</span>
      <button :disabled="!selectedDeviceId || loading" @click="loadHistory">{{ loading ? '加载中...' : '刷新' }}</button>
    </div>
  </div>

  <div v-if="!deviceStore.devices.length" class="empty-state">暂无借用中的设备，请先申请创建设备。</div>

  <div v-else-if="loading && !historyData" class="empty-state">历史数据加载中...</div>

  <div v-else-if="historyData" class="history-layout">
    <section class="summary-grid">
      <article v-for="card in summaryCards" :key="card.key" :class="['summary-card', { active: activeMetric === card.key }]" @click="activeMetric = card.key">
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
        <small>{{ card.alarmCount }} 次异常</small>
      </article>
    </section>

    <section v-if="latestSnapshot" class="snapshot-card">
      <div>
        <span>最新采样</span>
        <strong>{{ formatMonthDayTime(latestSnapshot.recordedAt) }}</strong>
      </div>
      <div>
        <span>温度</span>
        <strong>{{ latestSnapshot.temperature }}°C</strong>
      </div>
      <div>
        <span>湿度</span>
        <strong>{{ latestSnapshot.humidity }}%</strong>
      </div>
      <div>
        <span>光照</span>
        <strong>{{ latestSnapshot.light }}Lux</strong>
      </div>
    </section>

    <section class="trend-list">
      <TrendChartCard
        title="温度趋势"
        unit="°C"
        color="#0f7bff"
        :labels="pointLabels"
        :values="temperatureSeries"
        :threshold-min="historyData.threshold.tempMin"
        :threshold-max="historyData.threshold.tempMax"
      />
      <TrendChartCard
        title="湿度趋势"
        unit="%"
        color="#1f9d66"
        :labels="pointLabels"
        :values="humiditySeries"
        :threshold-min="historyData.threshold.humidityMin"
        :threshold-max="historyData.threshold.humidityMax"
      />
      <TrendChartCard
        title="光照趋势"
        unit="Lux"
        color="#d97706"
        :labels="pointLabels"
        :values="lightSeries"
        :threshold-max="historyData.threshold.lightMax"
      />
    </section>

    <section class="alarm-panel">
      <div class="panel-header">
        <h3>异常时刻</h3>
        <span>{{ alarmMoments.length }} 条</span>
      </div>
      <div v-if="!alarmMoments.length" class="alarm-empty">当前时间范围内没有异常记录。</div>
      <ul v-else class="alarm-list">
        <li v-for="(item, index) in alarmMoments.slice(0, 12)" :key="`${item.recordedAt}-${item.type}-${index}`">
          <strong>{{ item.type }}</strong>
          <span>{{ formatMonthDayTime(item.recordedAt) }} · {{ item.value }}</span>
          <small>{{ item.detail }}</small>
        </li>
      </ul>
    </section>
  </div>
</section>
```

Expected: the page now reads like an analysis dashboard instead of a raw chart stack.

- [ ] **Step 2: Add matching scoped styles for the new layout**

Extend the `<style scoped>` block in `src/components/sixthPage.vue` with:

```css
.toolbar {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.toolbar-group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.toolbar-meta {
  color: var(--text-muted);
}

.history-layout {
  display: grid;
  gap: 16px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-card,
.snapshot-card,
.alarm-panel {
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.9);
}

.summary-card {
  padding: 16px;
  display: grid;
  gap: 8px;
  cursor: pointer;
}

.summary-card.active {
  border-color: rgba(15, 123, 255, 0.35);
  box-shadow: 0 10px 30px rgba(15, 123, 255, 0.08);
}

.summary-card span,
.summary-card small,
.snapshot-card span,
.alarm-panel span,
.alarm-panel small {
  color: var(--text-muted);
}

.summary-card strong,
.snapshot-card strong {
  font-size: 24px;
}

.snapshot-card {
  padding: 18px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.trend-list {
  display: grid;
  gap: 12px;
}

.alarm-panel {
  padding: 18px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.alarm-list {
  display: grid;
  gap: 10px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.alarm-list li {
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(214, 69, 69, 0.06);
  display: grid;
  gap: 4px;
}

.alarm-empty {
  color: var(--text-muted);
}

@media (max-width: 980px) {
  .summary-grid,
  .snapshot-card {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .toolbar,
  .toolbar-group,
  .summary-grid,
  .snapshot-card {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: stretch;
  }

  select {
    min-width: 0;
    width: 100%;
  }
}
```

Expected: the page becomes responsive and information hierarchy becomes obvious.

- [ ] **Step 3: Keep the existing chart component unless a concrete limitation appears**

Do not edit `src/components/common/TrendChartCard.vue` yet unless the redesigned page reveals a missing prop that blocks the visual layout. YAGNI applies here.

Expected: chart reuse keeps this change focused and lowers regression risk.

- [ ] **Step 4: Run the frontend build to validate the redesigned template and styles**

Run:

```bash
cd "D:/web作业/vue_kgy_frontend" && npm run build
```

Expected: PASS.

- [ ] **Step 5: Commit the layout redesign**

```bash
git add src/components/sixthPage.vue
git commit -m "feat: redesign history analysis page layout"
```

---

### Task 4: Tighten history-page interaction logic and navigation behavior

**Files:**
- Modify: `src/components/sixthPage.vue`

- [ ] **Step 1: Prevent unnecessary reload churn in watchers**

Update the watchers to guard empty state and invalid syncs explicitly:

```js
watch: {
  selectedDeviceId(value) {
    if (!value) {
      this.historyData = null
      return
    }
    const selectedDevice = this.deviceStore.setSelectedDevice(value)
    if (!selectedDevice || selectedDevice.id !== value) {
      this.selectedDeviceId = selectedDevice ? selectedDevice.id : null
      return
    }
    this.loadHistory()
  },
  hours() {
    if (!this.selectedDeviceId) return
    this.loadHistory()
  }
}
```

Expected: switching ranges or devices does not leave stale charts onscreen for invalid selections.

- [ ] **Step 2: Ensure initial creation loads both devices and first history payload cleanly**

Use this `created()` block:

```js
async created() {
  try {
    await this.deviceStore.refreshDevices()
    const selectedDevice = this.deviceStore.syncSelectedDevice()
    this.selectedDeviceId = selectedDevice ? selectedDevice.id : null
    if (this.selectedDeviceId) {
      await this.loadHistory()
    }
  } catch (error) {
    this.$message.error(error.message)
  }
}
```

Expected: the page opens directly into usable history data instead of waiting for a second interaction.

- [ ] **Step 3: Reset active metric when the data range changes substantially**

Add this line inside `loadHistory()` after successful fetch:

```js
if (!['temperature', 'humidity', 'light'].includes(this.activeMetric)) {
  this.activeMetric = 'temperature'
}
```

Expected: metric highlight state stays valid even if future UI changes add non-chart summary cards.

- [ ] **Step 4: Run a full frontend build and a manual page walkthrough**

Run:

```bash
cd "D:/web作业/vue_kgy_frontend" && npm run build
```

Then manually verify in the browser:
- Enter the history page from a device card
- Switch device selector
- Switch 6 / 12 / 24 / 48 / 72 hour ranges
- Confirm summary cards, latest snapshot, charts, and alarm list all update together
- Confirm empty-state copy appears when no borrowed devices exist

Expected: the history page behaves consistently with no dead states.

- [ ] **Step 5: Commit the interaction-logic cleanup**

```bash
git add src/components/sixthPage.vue
git commit -m "fix: stabilize history page refresh behavior"
```

---

### Task 5: Final verification and cleanup

**Files:**
- Modify only if needed: `src/components/sixthPage.vue`
- Verify: `src/components/common/TrendChartCard.vue`, `src/api/medicalColdChain.js`, `src/utils/dateTime.js`

- [ ] **Step 1: Review the final implementation against the goal**

Check that the final page now includes all of these:

```text
- device selector
- hour-range selector including 72h
- refresh timestamp
- loading state
- no-device empty state
- summary cards
- latest snapshot panel
- three trend charts
- anomaly list
- stable refresh/watcher behavior
```

Expected: no planned feature is missing.

- [ ] **Step 2: Scan the changed file for dead code and redundant state**

Specifically verify that `src/components/sixthPage.vue` does not keep unused members such as:

```js
activeMetric // keep only if actually used by summary-card highlighting
loadedAt     // keep only if rendered
historyData  // should be the single raw payload source
```

Expected: the page remains focused and avoids accidental complexity.

- [ ] **Step 3: Run the final production build**

Run:

```bash
cd "D:/web作业/vue_kgy_frontend" && npm run build
```

Expected: PASS with the usual Vue production build output.

- [ ] **Step 4: Commit the verified final slice**

```bash
git add src/components/sixthPage.vue src/components/common/TrendChartCard.vue src/utils/dateTime.js
git commit -m "feat: improve history data analysis experience"
```

If only `src/components/sixthPage.vue` changed, stage only that file instead.

- [ ] **Step 5: Summarize the user-visible result for handoff**

Use this structure in the final handoff note:

```text
历史数据页现在支持：
1. 更清晰的时间范围切换
2. 汇总卡片和最新采样概览
3. 异常时刻列表
4. 更稳定的刷新和空态显示
```

Expected: the next reviewer or agent can understand the delivered scope instantly.

---

## Self-Review

- **Spec coverage:** The plan covers both requested dimensions: interface optimization (summary layout, snapshot, alarm panel, responsive styling) and display-logic optimization (derived anomaly extraction, range refresh behavior, cleaner loading/empty handling).
- **Placeholder scan:** No `TODO`, `TBD`, or “implement later” markers remain; each task includes exact files, code, commands, and expected outcomes.
- **Type consistency:** The plan uses existing names from `HistoryResponse` and `TelemetryPointResponse` (`threshold`, `points`, `temperature`, `humidity`, `light`, `signalStatus`, `recordedAt`) consistently across all tasks.
