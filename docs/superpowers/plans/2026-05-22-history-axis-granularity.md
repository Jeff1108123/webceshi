# History Axis Granularity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a single-metric history trend chart with professional axes, wheel-controlled display density, full-range aggregation, and more natural simulated telemetry data.

**Architecture:** The frontend history page keeps the existing `/history` route and `fetchHistory(deviceId, hours)` API but replaces the combined three-series chart with one focused metric chart driven by `activeMetric`. The chart component receives already-aggregated points from `sixthPage.vue`, renders the current metric with real units, threshold references, min/max/latest markers, and handles wheel events by requesting a granularity step change from the parent. Backend telemetry simulation is improved inside `DeviceSimulationService` without changing API contracts.

**Tech Stack:** Vue 2.6, Vue CLI 5, SVG chart rendering, Java/Spring Boot backend, JUnit/Maven tests.

---

## File Structure

- Create: `src/components/common/SingleMetricTrendChart.vue`
  - Responsibility: render one metric's trend with SVG axes, threshold bands/lines, markers, accessible labels, and wheel interaction.
- Modify: `src/components/sixthPage.vue`
  - Responsibility: manage active metric, display granularity, full-range time-bucket aggregation, and pass one metric into the new chart.
- Modify: `medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/service/DeviceSimulationService.java`
  - Responsibility: generate more realistic but smooth simulated telemetry.
- Modify: `medical-cold-chain-backend/src/test/java/com/medicalcoldchain/backend/MedicalColdChainBackendApplicationTests.java`
  - Responsibility: verify optimized simulation stays smooth and realistic.
- Keep existing API: `src/api/medicalColdChain.js` remains unchanged.
- Do not delete `src/components/common/CombinedTrendChartCard.vue` unless a final search proves it is unused and the reviewer approves deletion.

---

### Task 1: Add Single Metric Trend Chart Component

**Files:**
- Create: `src/components/common/SingleMetricTrendChart.vue`

- [ ] **Step 1: Create the component with props and accessible SVG skeleton**

Write `src/components/common/SingleMetricTrendChart.vue` with this structure:

```vue
<template>
  <article class="single-trend-card" @wheel.prevent="handleWheel">
    <div class="chart-head">
      <div>
        <span class="eyebrow">{{ metric.label }} Trend Channel</span>
        <h4>{{ metric.label }}历史趋势</h4>
        <p>{{ granularityLabel }} · 滚轮调整数据密集程度</p>
      </div>
      <div class="axis-badge">
        <strong>{{ latestText }}</strong>
        <span>{{ metric.unit }}</span>
      </div>
    </div>

    <div v-if="hasPoints" class="chart-shell">
      <svg
        class="trend-svg"
        :viewBox="`0 0 ${width} ${height}`"
        preserveAspectRatio="none"
        role="img"
        :aria-label="chartDescription"
      >
        <title>{{ chartTitle }}</title>
        <desc>{{ chartDescription }}</desc>
        <defs>
          <linearGradient :id="lineGradientId" x1="0" x2="1" y1="0" y2="0">
            <stop offset="0%" :stop-color="metric.color" stop-opacity="0.3" />
            <stop offset="45%" :stop-color="metric.color" stop-opacity="1" />
            <stop offset="100%" :stop-color="metric.color" stop-opacity="0.72" />
          </linearGradient>
          <linearGradient :id="areaGradientId" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" :stop-color="metric.color" stop-opacity="0.24" />
            <stop offset="100%" :stop-color="metric.color" stop-opacity="0" />
          </linearGradient>
        </defs>

        <rect v-if="safeBand" class="safe-band" :x="padding.left" :y="safeBand.y" :width="chartWidth" :height="safeBand.height" />
        <line
          v-for="grid in horizontalGrid"
          :key="grid.key"
          class="grid-line horizontal"
          :x1="padding.left"
          :x2="width - padding.right"
          :y1="grid.y"
          :y2="grid.y"
        />
        <line
          v-for="grid in verticalGrid"
          :key="grid.key"
          class="grid-line vertical"
          :x1="grid.x"
          :x2="grid.x"
          :y1="padding.top"
          :y2="height - padding.bottom"
        />
        <line v-if="thresholdMaxLine" class="threshold-line" :x1="padding.left" :x2="width - padding.right" :y1="thresholdMaxLine" :y2="thresholdMaxLine" />
        <line v-if="thresholdMinLine" class="threshold-line" :x1="padding.left" :x2="width - padding.right" :y1="thresholdMinLine" :y2="thresholdMinLine" />

        <path class="trend-area" :d="areaPath" :fill="`url(#${areaGradientId})`" />
        <path class="trend-line" :d="linePath" :stroke="`url(#${lineGradientId})`" />

        <g v-for="marker in markers" :key="marker.key" class="marker">
          <circle :cx="marker.x" :cy="marker.y" r="5" :class="marker.type" />
          <text :x="marker.x" :y="marker.labelY" text-anchor="middle">{{ marker.label }}</text>
        </g>

        <g v-for="tick in yTicks" :key="tick.key">
          <text class="axis-text y-axis" :x="padding.left - 10" :y="tick.y + 4" text-anchor="end">{{ tick.label }}</text>
        </g>
        <g v-for="tick in xTicks" :key="tick.key">
          <text class="axis-text x-axis" :x="tick.x" :y="height - 18" :text-anchor="tick.anchor">{{ tick.label }}</text>
        </g>
      </svg>
    </div>
    <div v-else class="empty-state">暂无历史数据</div>

    <div class="chart-footer">
      <span>最低 {{ minText }}</span>
      <span>最高 {{ maxText }}</span>
      <span>{{ thresholdText }}</span>
    </div>
  </article>
</template>

<script>
export default {
  name: 'SingleMetricTrendChart',
  props: {
    metric: {
      type: Object,
      required: true
    },
    points: {
      type: Array,
      default: () => []
    },
    granularityMinutes: {
      type: Number,
      required: true
    }
  },
  data() {
    return {
      width: 820,
      height: 360,
      padding: {
        top: 34,
        right: 34,
        bottom: 62,
        left: 72
      }
    }
  },
  computed: {
    chartWidth() {
      return this.width - this.padding.left - this.padding.right
    },
    chartHeight() {
      return this.height - this.padding.top - this.padding.bottom
    },
    hasPoints() {
      return this.validPoints.length > 0
    },
    validPoints() {
      return this.points
        .map((point, index) => ({ ...point, value: Number(point.value), index }))
        .filter(point => Number.isFinite(point.value))
    },
    values() {
      return this.validPoints.map(point => point.value)
    },
    minValue() {
      return this.values.length ? Math.min(...this.values) : null
    },
    maxValue() {
      return this.values.length ? Math.max(...this.values) : null
    },
    latestPoint() {
      return this.validPoints.length ? this.validPoints[this.validPoints.length - 1] : null
    },
    latestText() {
      return this.formatValue(this.latestPoint ? this.latestPoint.value : null)
    },
    minText() {
      return this.formatValue(this.minValue)
    },
    maxText() {
      return this.formatValue(this.maxValue)
    },
    granularityLabel() {
      return `展示粒度：${this.granularityMinutes} 分钟/点`
    },
    scale() {
      const candidates = [this.minValue, this.maxValue, this.metric.thresholdMin, this.metric.thresholdMax].filter(Number.isFinite)
      if (!candidates.length) return { min: 0, max: 1 }
      const rawMin = Math.min(...candidates)
      const rawMax = Math.max(...candidates)
      const range = Math.max(rawMax - rawMin, 1)
      const padding = Math.max(range * 0.16, this.metric.unit === 'Lux' ? 1 : 0.5)
      return {
        min: rawMin - padding,
        max: rawMax + padding
      }
    },
    coordinates() {
      const denominator = Math.max(this.validPoints.length - 1, 1)
      const valueRange = Math.max(this.scale.max - this.scale.min, 1)
      return this.validPoints.map((point, index) => ({
        ...point,
        x: this.padding.left + (this.chartWidth * index) / denominator,
        y: this.padding.top + this.chartHeight - ((point.value - this.scale.min) / valueRange) * this.chartHeight
      }))
    },
    linePath() {
      return this.toSmoothPath(this.coordinates)
    },
    areaPath() {
      if (!this.coordinates.length) return ''
      const baseline = this.height - this.padding.bottom
      return `${this.linePath} L ${this.coordinates[this.coordinates.length - 1].x} ${baseline} L ${this.coordinates[0].x} ${baseline} Z`
    },
    horizontalGrid() {
      return this.yTicks.map(tick => ({ key: `h-${tick.key}`, y: tick.y }))
    },
    verticalGrid() {
      return this.xTicks.map(tick => ({ key: `v-${tick.key}`, x: tick.x }))
    },
    yTicks() {
      const tickCount = 5
      return Array.from({ length: tickCount }, (_, index) => {
        const ratio = index / (tickCount - 1)
        const value = this.scale.max - (this.scale.max - this.scale.min) * ratio
        return {
          key: `y-${index}`,
          y: this.padding.top + this.chartHeight * ratio,
          label: this.formatValue(value)
        }
      })
    },
    xTicks() {
      if (!this.validPoints.length) return []
      const maxTicks = 6
      const lastIndex = this.validPoints.length - 1
      const step = Math.max(1, Math.ceil(this.validPoints.length / maxTicks))
      const indexes = []
      for (let index = 0; index <= lastIndex; index += step) indexes.push(index)
      if (!indexes.includes(lastIndex)) indexes.push(lastIndex)
      const denominator = Math.max(lastIndex, 1)
      return indexes.map((pointIndex, index) => ({
        key: `x-${index}-${pointIndex}`,
        x: this.padding.left + (this.chartWidth * pointIndex) / denominator,
        label: this.validPoints[pointIndex].label,
        anchor: pointIndex === 0 ? 'start' : (pointIndex === lastIndex ? 'end' : 'middle')
      }))
    },
    thresholdMinLine() {
      return Number.isFinite(this.metric.thresholdMin) ? this.valueToY(this.metric.thresholdMin) : null
    },
    thresholdMaxLine() {
      return Number.isFinite(this.metric.thresholdMax) ? this.valueToY(this.metric.thresholdMax) : null
    },
    safeBand() {
      if (!Number.isFinite(this.metric.thresholdMin) || !Number.isFinite(this.metric.thresholdMax)) return null
      const top = this.valueToY(this.metric.thresholdMax)
      const bottom = this.valueToY(this.metric.thresholdMin)
      return {
        y: top,
        height: Math.max(bottom - top, 1)
      }
    },
    markers() {
      if (!this.coordinates.length) return []
      const latest = this.coordinates[this.coordinates.length - 1]
      const min = this.coordinates.reduce((result, point) => point.value < result.value ? point : result, this.coordinates[0])
      const max = this.coordinates.reduce((result, point) => point.value > result.value ? point : result, this.coordinates[0])
      return [
        { ...min, key: 'min', type: 'min', label: '低', labelY: Math.min(min.y + 22, this.height - this.padding.bottom - 6) },
        { ...max, key: 'max', type: 'max', label: '高', labelY: Math.max(max.y - 12, this.padding.top + 12) },
        { ...latest, key: 'latest', type: 'latest', label: '新', labelY: Math.max(latest.y - 12, this.padding.top + 12) }
      ]
    },
    thresholdText() {
      if (Number.isFinite(this.metric.thresholdMin) && Number.isFinite(this.metric.thresholdMax)) {
        return `安全区间 ${this.metric.thresholdMin} ~ ${this.metric.thresholdMax}${this.metric.unit}`
      }
      if (Number.isFinite(this.metric.thresholdMax)) {
        return `警戒上限 ${this.metric.thresholdMax}${this.metric.unit}`
      }
      return '暂无阈值参考'
    },
    chartTitle() {
      return `${this.metric.label}历史趋势图`
    },
    chartDescription() {
      const first = this.validPoints[0] ? this.validPoints[0].label : '起始时间'
      const last = this.latestPoint ? this.latestPoint.label : '结束时间'
      return `${this.chartTitle}，时间从${first}到${last}，${this.granularityLabel}，最低${this.minText}，最高${this.maxText}，最新${this.latestText}。`
    },
    lineGradientId() {
      return `line-gradient-${this.metric.key}`
    },
    areaGradientId() {
      return `area-gradient-${this.metric.key}`
    }
  },
  methods: {
    handleWheel(event) {
      this.$emit('density-change', event.deltaY < 0 ? 'denser' : 'sparser')
    },
    valueToY(value) {
      const valueRange = Math.max(this.scale.max - this.scale.min, 1)
      return this.padding.top + this.chartHeight - ((value - this.scale.min) / valueRange) * this.chartHeight
    },
    toSmoothPath(points) {
      if (!points.length) return ''
      if (points.length === 1) return `M ${points[0].x} ${points[0].y}`
      return points.reduce((path, point, index, allPoints) => {
        if (index === 0) return `M ${point.x} ${point.y}`
        const previous = allPoints[index - 1]
        const controlOffset = (point.x - previous.x) * 0.36
        return `${path} C ${previous.x + controlOffset} ${previous.y}, ${point.x - controlOffset} ${point.y}, ${point.x} ${point.y}`
      }, '')
    },
    formatValue(value) {
      if (!Number.isFinite(value)) return '--'
      return `${value.toFixed(this.metric.unit === 'Lux' ? 0 : 1)}${this.metric.unit}`
    }
  }
}
</script>
```

- [ ] **Step 2: Add scoped styling to the component**

Append this `<style scoped>` block to the same file:

```vue
<style scoped>
.single-trend-card {
  position: relative;
  overflow: hidden;
  padding: 22px;
  border: 1px solid rgba(34, 211, 238, 0.18);
  border-radius: 24px;
  background:
    radial-gradient(circle at 18% 10%, rgba(34, 211, 238, 0.14), transparent 30%),
    linear-gradient(145deg, rgba(10, 25, 47, 0.96), rgba(2, 8, 23, 0.9));
  box-shadow: var(--card-shadow), inset 0 1px 0 rgba(255, 255, 255, 0.05);
  display: grid;
  gap: 18px;
  backdrop-filter: blur(18px);
}

.chart-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.eyebrow {
  color: rgba(103, 232, 249, 0.7);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.chart-head h4 {
  margin: 8px 0 8px;
  color: var(--text-strong);
  font-size: 22px;
}

.chart-head p {
  margin: 0;
  color: var(--text-muted);
}

.axis-badge {
  min-width: 118px;
  padding: 12px 14px;
  border: 1px solid rgba(103, 232, 249, 0.2);
  border-radius: 16px;
  background: rgba(8, 22, 43, 0.78);
  text-align: right;
}

.axis-badge strong {
  display: block;
  color: #ecfeff;
  font-size: 22px;
}

.axis-badge span {
  color: var(--text-muted);
  font-size: 12px;
}

.chart-shell {
  min-height: 360px;
  cursor: ns-resize;
}

.trend-svg {
  width: 100%;
  height: 360px;
  display: block;
}

.safe-band {
  fill: rgba(34, 197, 94, 0.08);
  stroke: rgba(34, 197, 94, 0.18);
  stroke-width: 1;
}

.grid-line {
  stroke: rgba(148, 163, 184, 0.14);
  stroke-width: 1;
}

.grid-line.vertical {
  stroke-dasharray: 4 8;
  opacity: 0.72;
}

.threshold-line {
  stroke: rgba(251, 191, 36, 0.72);
  stroke-width: 1.5;
  stroke-dasharray: 8 6;
}

.trend-area {
  opacity: 0.9;
}

.trend-line {
  fill: none;
  stroke-width: 3.2;
  stroke-linecap: round;
  stroke-linejoin: round;
  filter: drop-shadow(0 0 10px rgba(34, 211, 238, 0.2));
}

.marker circle {
  stroke: rgba(2, 8, 23, 0.92);
  stroke-width: 3;
}

.marker circle.min {
  fill: #60a5fa;
}

.marker circle.max {
  fill: #f97316;
}

.marker circle.latest {
  fill: #22d3ee;
}

.marker text,
.axis-text {
  fill: #9fb8d6;
  font-size: 12px;
  font-weight: 700;
}

.y-axis {
  letter-spacing: 0.02em;
}

.chart-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.chart-footer span {
  padding: 8px 11px;
  border: 1px solid rgba(113, 206, 255, 0.15);
  border-radius: 999px;
  background: rgba(8, 22, 43, 0.72);
  color: var(--text-muted);
  font-size: 13px;
}

.empty-state {
  padding: 56px 0;
  color: var(--text-muted);
  text-align: center;
}

@media (max-width: 760px) {
  .chart-head {
    flex-direction: column;
  }

  .axis-badge {
    width: 100%;
    text-align: left;
  }

  .single-trend-card {
    padding: 16px;
  }
}
</style>
```

- [ ] **Step 3: Commit component creation**

Run:

```bash
git add src/components/common/SingleMetricTrendChart.vue
git commit -m "feat: add single metric history chart"
```

Expected: commit succeeds.

---

### Task 2: Wire Single-Metric Chart and Wheel Granularity in History Page

**Files:**
- Modify: `src/components/sixthPage.vue`

- [ ] **Step 1: Replace the combined chart import and registration**

Change:

```js
import CombinedTrendChartCard from './common/CombinedTrendChartCard.vue'
```

to:

```js
import SingleMetricTrendChart from './common/SingleMetricTrendChart.vue'
```

Change component registration from:

```js
components: {
  AppShell,
  CombinedTrendChartCard
},
```

to:

```js
components: {
  AppShell,
  SingleMetricTrendChart
},
```

- [ ] **Step 2: Replace the chart template block**

Replace the existing `<CombinedTrendChartCard ... />` inside `.trend-list` with:

```vue
<SingleMetricTrendChart
  :metric="activeMetricConfig"
  :points="aggregatedMetricPoints"
  :granularity-minutes="displayGranularityMinutes"
  @density-change="handleDensityChange"
/>
```

- [ ] **Step 3: Add granularity state**

In `data()`, after `activeMetric: 'temperature'`, add:

```js
      granularityOptions: [1, 2, 5, 10, 15, 30, 60],
      manualGranularityMinutes: null
```

The resulting end of `data()` should include:

```js
      loadedAt: '',
      activeMetric: 'temperature',
      granularityOptions: [1, 2, 5, 10, 15, 30, 60],
      manualGranularityMinutes: null
```

- [ ] **Step 4: Add metric config and aggregation computed properties**

Inside `computed`, after `lightSeries()`, add:

```js
    metricConfigs() {
      return {
        temperature: {
          key: 'temperature',
          label: '温度',
          unit: '°C',
          color: '#38bdf8',
          field: 'temperature',
          thresholdMin: this.historyData && this.historyData.threshold ? this.historyData.threshold.tempMin : null,
          thresholdMax: this.historyData && this.historyData.threshold ? this.historyData.threshold.tempMax : null
        },
        humidity: {
          key: 'humidity',
          label: '湿度',
          unit: '%',
          color: '#22c55e',
          field: 'humidity',
          thresholdMin: this.historyData && this.historyData.threshold ? this.historyData.threshold.humidityMin : null,
          thresholdMax: this.historyData && this.historyData.threshold ? this.historyData.threshold.humidityMax : null
        },
        light: {
          key: 'light',
          label: '光照',
          unit: 'Lux',
          color: '#facc15',
          field: 'light',
          thresholdMin: null,
          thresholdMax: this.historyData && this.historyData.threshold ? this.historyData.threshold.lightMax : null
        }
      }
    },
    activeMetricConfig() {
      return this.metricConfigs[this.activeMetric] || this.metricConfigs.temperature
    },
    defaultGranularityMinutes() {
      if (this.hours <= 6) return 5
      if (this.hours <= 12) return 10
      if (this.hours <= 24) return 15
      if (this.hours <= 48) return 30
      return 60
    },
    displayGranularityMinutes() {
      return this.manualGranularityMinutes || this.defaultGranularityMinutes
    },
    aggregatedMetricPoints() {
      return this.aggregateMetricPoints(this.points, this.activeMetricConfig, this.displayGranularityMinutes)
    },
```

- [ ] **Step 5: Reset manual granularity when time range changes**

Change the `hours()` watcher from:

```js
    hours() {
      if (!this.selectedDeviceId) return
      this.loadHistory()
    }
```

to:

```js
    hours() {
      this.manualGranularityMinutes = null
      if (!this.selectedDeviceId) return
      this.loadHistory()
    }
```

- [ ] **Step 6: Add density and aggregation methods**

Inside `methods`, before `async loadHistory()`, add:

```js
    handleDensityChange(direction) {
      const currentIndex = this.granularityOptions.indexOf(this.displayGranularityMinutes)
      if (currentIndex === -1) {
        this.manualGranularityMinutes = this.defaultGranularityMinutes
        return
      }
      const nextIndex = direction === 'denser'
        ? Math.max(0, currentIndex - 1)
        : Math.min(this.granularityOptions.length - 1, currentIndex + 1)
      this.manualGranularityMinutes = this.granularityOptions[nextIndex]
    },
    aggregateMetricPoints(points, metric, granularityMinutes) {
      if (!Array.isArray(points) || !points.length || !metric) return []
      const bucketMs = granularityMinutes * 60 * 1000
      const buckets = []

      points.forEach(point => {
        const timestamp = new Date(point.recordedAt).getTime()
        const value = Number(point[metric.field])
        if (!Number.isFinite(timestamp) || !Number.isFinite(value)) return

        const bucketStart = Math.floor(timestamp / bucketMs) * bucketMs
        let bucket = buckets[buckets.length - 1]
        if (!bucket || bucket.start !== bucketStart) {
          bucket = {
            start: bucketStart,
            end: bucketStart + bucketMs,
            sum: 0,
            count: 0,
            alarm: false,
            firstTime: point.recordedAt,
            lastTime: point.recordedAt
          }
          buckets.push(bucket)
        }

        bucket.sum += value
        bucket.count += 1
        bucket.alarm = bucket.alarm || Boolean(point.alarm)
        bucket.lastTime = point.recordedAt
      })

      return buckets
        .filter(bucket => bucket.count > 0)
        .map(bucket => ({
          recordedAt: bucket.lastTime,
          label: formatMonthDayTime(bucket.lastTime),
          value: Math.round((bucket.sum / bucket.count) * 100) / 100,
          alarm: bucket.alarm,
          count: bucket.count
        }))
    },
```

This aggregation does not truncate the range because it iterates over every returned point, creates partial buckets for the beginning/end as needed, and emits every non-empty bucket including the last partial bucket.

- [ ] **Step 7: Make sampling card show displayed points**

In `summaryCards()`, change the sampling card value from:

```js
          value: `${this.points.length} 条`,
```

to:

```js
          value: `${this.aggregatedMetricPoints.length} / ${this.points.length} 点`,
```

- [ ] **Step 8: Commit history page wiring**

Run:

```bash
git add src/components/sixthPage.vue
git commit -m "feat: add wheel-controlled history granularity"
```

Expected: commit succeeds.

---

### Task 3: Improve Simulated Telemetry Generation

**Files:**
- Modify: `medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/service/DeviceSimulationService.java`

- [ ] **Step 1: Replace the telemetry formulas inside `simulateTelemetry`**

In `simulateTelemetry`, keep these lines:

```java
long minutes = Duration.between(BASE_TIME, recordedAt).toMinutes();
int seed = Math.abs(deviceCode.hashCode());
double phase = (minutes + seed % 360) / 60.0;
```

Replace the existing `temperature`, `humidity`, `light`, `batteryLevel`, and `signalStatus` calculations with:

```java
double handoffPulse = smoothCyclicPulse((recordedAt.getMinute() + seed) % 180, 24, 180, 9.5);
double doorOpenPulse = smoothCyclicPulse((recordedAt.getMinute() + seed) % 95, 88, 95, 4.8);
double routeDrift = Math.sin((minutes + seed % 720) / 360.0) * 0.65;

double temperature = 4.6
        + Math.sin(phase * 0.54) * 1.35
        + Math.cos(phase * 0.18 + seed % 11) * 0.48
        + routeDrift
        + handoffPulse * 1.65
        + doorOpenPulse * 0.72;

double humidity = 59
        + Math.sin(phase * 0.34 + 1.5) * 6.4
        + Math.cos(phase * 0.12 + seed % 9) * 2.1
        - handoffPulse * 1.6
        + routeDrift * 1.8;

double lightExposure = smoothCyclicPulse((recordedAt.getMinute() + seed) % 160, 154, 160, 3.8);
double ambientLeak = Math.max(0, Math.sin(phase * 0.72 + seed % 5)) * 1.4;
double light = 3.2 + ambientLeak + doorOpenPulse * 4.2 + lightExposure * 8.5;

int batteryLevel = (int) Math.round(clamp(
        96 - ((minutes % (72 * 60)) / 60.0) * 0.48 - (seed % 7) + Math.sin(phase * 0.18) * 1.2,
        34,
        99));
boolean signalStatus = (recordedAt.getMinute() + seed) % 41 != 0;
```

- [ ] **Step 2: Commit simulation change**

Run:

```bash
git add medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/service/DeviceSimulationService.java
git commit -m "feat: refine simulated cold-chain telemetry"
```

Expected: commit succeeds.

---

### Task 4: Update Backend Simulation Test Thresholds

**Files:**
- Modify: `medical-cold-chain-backend/src/test/java/com/medicalcoldchain/backend/MedicalColdChainBackendApplicationTests.java`

- [ ] **Step 1: Inspect the existing smoothness test**

Find the test named:

```java
simulatedTelemetryShouldChangeSmoothlyBetweenMinutes
```

It currently asserts temperature and light minute-to-minute jumps.

- [ ] **Step 2: Update assertions to match the refined realistic pulses**

Inside that test, keep the loop structure and replace the assertions with:

```java
assertTrue(Math.abs(current.temperature() - previous.temperature()) <= 0.9,
        "temperature jumped at minute " + minute);
assertTrue(Math.abs(current.humidity() - previous.humidity()) <= 0.9,
        "humidity jumped at minute " + minute);
assertTrue(Math.abs(current.light() - previous.light()) <= 1.6,
        "light jumped at minute " + minute);
assertTrue(current.temperature() >= 0.5 && current.temperature() <= 10.5,
        "temperature out of realistic cold-chain range at minute " + minute);
assertTrue(current.humidity() >= 42 && current.humidity() <= 76,
        "humidity out of realistic range at minute " + minute);
assertTrue(current.light() >= 0 && current.light() <= 18,
        "light out of realistic range at minute " + minute);
```

- [ ] **Step 3: Run the targeted backend test**

Run:

```bash
cd "D:/web作业/vue_kgy_frontend/medical-cold-chain-backend" && ./mvnw test -Dtest=MedicalColdChainBackendApplicationTests#simulatedTelemetryShouldChangeSmoothlyBetweenMinutes
```

Expected: the targeted test passes. If Windows cannot execute `./mvnw`, run the equivalent Maven wrapper command supported by the environment:

```bash
cd "D:/web作业/vue_kgy_frontend/medical-cold-chain-backend" && mvnw.cmd test -Dtest=MedicalColdChainBackendApplicationTests#simulatedTelemetryShouldChangeSmoothlyBetweenMinutes
```

- [ ] **Step 4: Commit test update**

Run:

```bash
git add medical-cold-chain-backend/src/test/java/com/medicalcoldchain/backend/MedicalColdChainBackendApplicationTests.java
git commit -m "test: cover refined telemetry simulation"
```

Expected: commit succeeds.

---

### Task 5: Frontend Build Verification and Commit Design Artifacts

**Files:**
- Modify: `docs/superpowers/specs/2026-05-22-history-axis-granularity-design.md`
- Modify: `docs/superpowers/plans/2026-05-22-history-axis-granularity.md`

- [ ] **Step 1: Run frontend build**

Run:

```bash
cd "D:/web作业/vue_kgy_frontend" && npm run build
```

Expected: output includes `DONE  Compiled successfully` and exit code 0.

- [ ] **Step 2: Run backend history-related tests**

Run:

```bash
cd "D:/web作业/vue_kgy_frontend/medical-cold-chain-backend" && ./mvnw test -Dtest=MedicalColdChainBackendApplicationTests,TelemetryServiceMinuteRefreshTest
```

Expected: Maven test run exits 0. If `./mvnw` is not executable in this shell, use:

```bash
cd "D:/web作业/vue_kgy_frontend/medical-cold-chain-backend" && mvnw.cmd test -Dtest=MedicalColdChainBackendApplicationTests,TelemetryServiceMinuteRefreshTest
```

- [ ] **Step 3: Commit spec and plan docs**

Run:

```bash
git add docs/superpowers/specs/2026-05-22-history-axis-granularity-design.md docs/superpowers/plans/2026-05-22-history-axis-granularity.md
git commit -m "docs: document history chart granularity redesign"
```

Expected: commit succeeds.

---

### Task 6: Final Review, Squash Decision, and Push

**Files:**
- Review all changed files from Tasks 1-5.

- [ ] **Step 1: Show status and recent commits**

Run:

```bash
git status --short
git log --oneline -6
```

Expected: only `.serena/` may remain untracked; task commits are visible.

- [ ] **Step 2: Run final verification**

Run frontend build again:

```bash
cd "D:/web作业/vue_kgy_frontend" && npm run build
```

Run backend tests again:

```bash
cd "D:/web作业/vue_kgy_frontend/medical-cold-chain-backend" && ./mvnw test -Dtest=MedicalColdChainBackendApplicationTests,TelemetryServiceMinuteRefreshTest
```

Expected: both commands exit 0.

- [ ] **Step 3: Push to GitHub using remembered identity only if a new commit is needed**

If commits already exist and only push is needed, run:

```bash
git push origin master
```

If a final fix commit is needed and Git identity is missing, do not edit git config. Use:

```bash
GIT_AUTHOR_NAME='Jeff1108123' GIT_AUTHOR_EMAIL='2823871364@qq.com' GIT_COMMITTER_NAME='Jeff1108123' GIT_COMMITTER_EMAIL='2823871364@qq.com' git commit -m "fix: polish history chart granularity"
```

Then push:

```bash
git push origin master
```

Expected: remote `origin/master` advances successfully.

---

## Self-Review

- Spec coverage: single-metric switching is covered in Task 2; advanced axis and chart rendering in Task 1; wheel-controlled density and no truncation aggregation in Task 2; backend simulation in Task 3; tests and verification in Tasks 4-6.
- Placeholder scan: no TBD/TODO placeholders remain.
- Type consistency: `metric.key`, `metric.label`, `metric.unit`, `metric.field`, `thresholdMin`, `thresholdMax`, `granularityMinutes`, and `density-change` are consistently defined and consumed.
