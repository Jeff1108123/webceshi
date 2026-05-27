<template>
  <AppShell title="历史数据">
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
          </select>
        </div>
        <div class="toolbar-group toolbar-meta">
          <div class="status-copy">
            <strong>{{ dataCurrentAt ? `数据更新到：${formatDateTime(dataCurrentAt)}` : '等待加载历史数据' }}</strong>
            <small>{{ loadedAt ? `页面刷新时间：${loadedAt} · 后端粒度：${displayGranularityMinutes}分钟/点` : '点击刷新可拉取当前时间范围内的最新历史数据' }}</small>
          </div>
          <button :disabled="!selectedDeviceId || loading" @click="loadHistory">{{ loading ? '加载中...' : '刷新到当前时间' }}</button>
        </div>
      </div>

      <div v-if="!deviceStore.devices.length" class="empty-state">暂无借用中的设备，请先申请创建设备。</div>
      <div v-else-if="loading && !historyData" class="empty-state">历史数据加载中...</div>
      <div v-else-if="historyData && !hasPoints" class="empty-state">当前时间范围内暂无历史数据。</div>

      <div v-else-if="historyData" class="history-layout">
        <section class="summary-grid">
          <article
            v-for="card in summaryCards"
            :key="card.key"
            :class="['summary-card', { active: activeMetric === card.key }]"
            @click="setActiveMetric(card.key)"
          >
            <span>{{ card.label }}</span>
            <strong>{{ card.value }}</strong>
            <small>{{ card.alarmCount }} 次异常</small>
          </article>
        </section>

        <section v-if="latestSnapshot" class="snapshot-card">
          <div>
            <span>当前历史截止点</span>
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
          <SingleMetricTrendChart
            :metric="activeMetricConfig"
            :points="aggregatedMetricPoints"
            :granularity-minutes="displayGranularityMinutes"
            @density-change="handleDensityChange"
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
  </AppShell>
</template>

<script>
import AppShell from './common/AppShell.vue'
import SingleMetricTrendChart from './common/SingleMetricTrendChart.vue'
import { fetchHistory } from '../api/medicalColdChain'
import { useDeviceStore } from '../store/deviceStore'
import { formatDateTime, formatMonthDayTime } from '../utils/dateTime'

export default {
  name: 'SixthPage',
  components: {
    AppShell,
    SingleMetricTrendChart
  },
  data() {
    return {
      deviceStore: useDeviceStore(),
      selectedDeviceId: null,
      hours: 24,
      historyData: null,
      loading: false,
      loadedAt: '',
      activeMetric: 'temperature',
      granularityOptions: [1, 2, 5, 10, 15, 30, 60],
      manualGranularityMinutes: null
    }
  },
  computed: {
    points() {
      return this.historyData && Array.isArray(this.historyData.points) ? this.historyData.points : []
    },
    hasPoints() {
      return this.points.length > 0
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
    metricConfigs() {
      const threshold = this.historyData && this.historyData.threshold ? this.historyData.threshold : {}
      const { tempMin, tempMax, humidityMin, humidityMax, lightMax } = threshold

      return {
        temperature: {
          key: 'temperature',
          field: 'temperature',
          label: '温度',
          unit: '°C',
          color: '#2f8cff',
          thresholdMin: tempMin,
          thresholdMax: tempMax
        },
        humidity: {
          key: 'humidity',
          field: 'humidity',
          label: '湿度',
          unit: '%',
          color: '#21d07a',
          thresholdMin: humidityMin,
          thresholdMax: humidityMax
        },
        light: {
          key: 'light',
          field: 'light',
          label: '光照',
          unit: 'Lux',
          color: '#f7c948',
          thresholdMin: null,
          thresholdMax: lightMax
        }
      }
    },
    activeMetricConfig() {
      return this.metricConfigs[this.activeMetric] || this.metricConfigs.temperature
    },
    defaultGranularityMinutes() {
      const granularityMap = {
        6: 5,
        12: 10,
        24: 15
      }
      return granularityMap[this.hours] || 15
    },
    requestedGranularityMinutes() {
      return this.manualGranularityMinutes || this.defaultGranularityMinutes
    },
    displayGranularityMinutes() {
      const backendStepMinutes = this.historyData ? this.normalizeFiniteNumber(this.historyData.stepMinutes) : null
      return backendStepMinutes || this.requestedGranularityMinutes
    },
    aggregatedMetricPoints() {
      const metric = this.activeMetricConfig
      if (!metric || !metric.field) return []
      return this.points
        .map(point => {
          const value = this.normalizeFiniteNumber(point[metric.field])
          return {
            ...point,
            label: formatMonthDayTime(point.recordedAt),
            value
          }
        })
        .filter(point => Number.isFinite(point.value))
    },
    dataCurrentAt() {
      return this.latestSnapshot ? this.latestSnapshot.recordedAt : ''
    },
    alarmMoments() {
      if (!this.historyData || !this.historyData.threshold) return []
      const { tempMin, tempMax, humidityMin, humidityMax, lightMax } = this.historyData.threshold
      const normalizedThresholds = {
        tempMin: this.normalizeFiniteNumber(tempMin),
        tempMax: this.normalizeFiniteNumber(tempMax),
        humidityMin: this.normalizeFiniteNumber(humidityMin),
        humidityMax: this.normalizeFiniteNumber(humidityMax),
        lightMax: this.normalizeFiniteNumber(lightMax)
      }

      return this.points.flatMap(point => {
        const alarms = []
        const temperature = this.normalizeFiniteNumber(point.temperature)
        const humidity = this.normalizeFiniteNumber(point.humidity)
        const light = this.normalizeFiniteNumber(point.light)

        if (
          Number.isFinite(temperature) &&
          ((Number.isFinite(normalizedThresholds.tempMin) && temperature < normalizedThresholds.tempMin) ||
            (Number.isFinite(normalizedThresholds.tempMax) && temperature > normalizedThresholds.tempMax))
        ) {
          alarms.push({
            recordedAt: point.recordedAt,
            type: '温度异常',
            value: `${point.temperature}°C`,
            detail: `阈值 ${tempMin} ~ ${tempMax}°C`
          })
        }

        if (
          Number.isFinite(humidity) &&
          ((Number.isFinite(normalizedThresholds.humidityMin) && humidity < normalizedThresholds.humidityMin) ||
            (Number.isFinite(normalizedThresholds.humidityMax) && humidity > normalizedThresholds.humidityMax))
        ) {
          alarms.push({
            recordedAt: point.recordedAt,
            type: '湿度异常',
            value: `${point.humidity}%`,
            detail: `阈值 ${humidityMin} ~ ${humidityMax}%`
          })
        }

        if (Number.isFinite(light) && Number.isFinite(normalizedThresholds.lightMax) && light > normalizedThresholds.lightMax) {
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
      if (!this.hasPoints) return []

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
  },
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
      this.manualGranularityMinutes = null
      if (!this.selectedDeviceId) return
      this.loadHistory()
    }
  },
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
  },
  methods: {
    formatDateTime,
    formatMonthDayTime,
    setActiveMetric(metric) {
      if (['temperature', 'humidity', 'light'].includes(metric)) {
        this.activeMetric = metric
      }
    },
    async handleDensityChange(direction) {
      const currentIndex = this.granularityOptions.indexOf(this.displayGranularityMinutes)
      if (currentIndex === -1) return

      const offset = direction === 'denser' ? -1 : 1
      const nextIndex = Math.max(0, Math.min(this.granularityOptions.length - 1, currentIndex + offset))
      const nextGranularityMinutes = this.granularityOptions[nextIndex]
      if (nextGranularityMinutes === this.requestedGranularityMinutes) return

      this.manualGranularityMinutes = nextGranularityMinutes
      if (this.selectedDeviceId) {
        await this.loadHistory()
      }
    },
    normalizeFiniteNumber(value) {
      if (value === null || value === undefined || value === '') return null
      const normalizedValue = Number(value)
      return Number.isFinite(normalizedValue) ? normalizedValue : null
    },
    async loadHistory() {
      if (!this.selectedDeviceId) return
      this.loading = true
      try {
        this.historyData = await fetchHistory(this.selectedDeviceId, this.hours, this.requestedGranularityMinutes)
        this.loadedAt = formatDateTime()
        if (!['temperature', 'humidity', 'light'].includes(this.activeMetric)) {
          this.activeMetric = 'temperature'
        }
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
  position: relative;
  overflow: hidden;
  padding: 24px;
  border: 1px solid rgba(34, 211, 238, 0.16);
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.96), rgba(15, 23, 42, 0.78));
  box-shadow: 0 18px 45px rgba(2, 6, 23, 0.34), inset 0 1px 0 rgba(148, 163, 184, 0.1);
}

.panel::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(circle at top right, rgba(34, 211, 238, 0.16), transparent 32%),
    radial-gradient(circle at 8% 88%, rgba(14, 165, 233, 0.12), transparent 30%),
    linear-gradient(90deg, rgba(34, 211, 238, 0.06) 1px, transparent 1px),
    linear-gradient(180deg, rgba(34, 211, 238, 0.05) 1px, transparent 1px);
  background-size: auto, auto, 44px 44px, 44px 44px;
}

.toolbar,
.empty-state,
.history-layout {
  position: relative;
  z-index: 1;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 14px;
  margin-bottom: 18px;
}

.toolbar-group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.toolbar-meta {
  justify-content: space-between;
  flex: 1;
  min-width: 280px;
  color: rgba(191, 219, 254, 0.72);
}

.status-copy {
  display: grid;
  gap: 4px;
  padding: 8px 12px;
  border: 1px solid rgba(34, 211, 238, 0.16);
  border-radius: 14px;
  background: rgba(2, 6, 23, 0.3);
}

.status-copy strong {
  font-size: 14px;
  color: #e0f2fe;
}

.status-copy small {
  color: rgba(191, 219, 254, 0.62);
}

select,
button {
  height: 42px;
  border-radius: 12px;
  font: inherit;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease, transform 0.2s ease;
}

select {
  min-width: 180px;
  padding: 0 14px;
  border: 1px solid rgba(34, 211, 238, 0.24);
  color: #dbeafe;
  background: rgba(15, 23, 42, 0.84);
  outline: none;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.38);
  cursor: pointer;
}

select:focus {
  border-color: rgba(34, 211, 238, 0.82);
  box-shadow: 0 0 0 3px rgba(34, 211, 238, 0.14), 0 0 24px rgba(34, 211, 238, 0.2);
}

button {
  padding: 0 16px;
  border: 1px solid rgba(34, 211, 238, 0.42);
  color: #ecfeff;
  background: rgba(8, 145, 178, 0.2);
  box-shadow: 0 0 16px rgba(34, 211, 238, 0.14);
  cursor: pointer;
}

button:not(:disabled):hover {
  transform: translateY(-1px);
  border-color: rgba(125, 211, 252, 0.82);
  background: rgba(14, 165, 233, 0.3);
  box-shadow: 0 0 24px rgba(34, 211, 238, 0.28);
}

button:disabled {
  opacity: 0.48;
  cursor: not-allowed;
  box-shadow: none;
}

.history-layout {
  display: grid;
  gap: 16px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.summary-card,
.snapshot-card,
.alarm-panel {
  border: 1px solid rgba(34, 211, 238, 0.16);
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(8, 47, 73, 0.44), rgba(2, 6, 23, 0.5));
  box-shadow: inset 0 1px 0 rgba(148, 163, 184, 0.08), 0 14px 30px rgba(2, 6, 23, 0.22);
}

.summary-card {
  position: relative;
  overflow: hidden;
  padding: 18px;
  display: grid;
  gap: 10px;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.summary-card::after {
  content: '';
  position: absolute;
  right: -24px;
  top: -28px;
  width: 82px;
  height: 82px;
  border-radius: 999px;
  background: rgba(34, 211, 238, 0.12);
  filter: blur(2px);
}

.summary-card:hover {
  transform: translateY(-2px);
  border-color: rgba(34, 211, 238, 0.36);
}

.summary-card.active {
  border-color: rgba(34, 211, 238, 0.88);
  background: linear-gradient(180deg, rgba(14, 116, 144, 0.52), rgba(2, 6, 23, 0.55));
  box-shadow: 0 0 0 1px rgba(34, 211, 238, 0.16), 0 0 30px rgba(34, 211, 238, 0.24), inset 0 1px 0 rgba(207, 250, 254, 0.12);
}

.summary-card span,
.summary-card small,
.snapshot-card span,
.alarm-panel span,
.alarm-panel small {
  position: relative;
  z-index: 1;
  color: rgba(191, 219, 254, 0.68);
}

.summary-card span,
.snapshot-card span {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.summary-card strong,
.snapshot-card strong {
  position: relative;
  z-index: 1;
  color: #67e8f9;
  font-size: 24px;
  line-height: 1.12;
  text-shadow: 0 0 18px rgba(34, 211, 238, 0.26);
}

.snapshot-card {
  padding: 20px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.snapshot-card > div {
  padding: 14px;
  border: 1px solid rgba(34, 211, 238, 0.14);
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.46);
  display: grid;
  gap: 8px;
}

.trend-list {
  display: grid;
  gap: 12px;
}

.trend-list ::v-deep .single-metric-chart {
  min-height: 460px;
  border-color: rgba(34, 211, 238, 0.16);
  background: linear-gradient(180deg, rgba(8, 47, 73, 0.44), rgba(2, 6, 23, 0.54));
  box-shadow: inset 0 1px 0 rgba(148, 163, 184, 0.08), 0 14px 30px rgba(2, 6, 23, 0.24);
}

.trend-list ::v-deep .chart-head h4 {
  color: #e0f2fe;
}

.trend-list ::v-deep .chart-head p,
.trend-list ::v-deep .granularity-pill {
  color: rgba(191, 219, 254, 0.68);
}

.trend-list ::v-deep .granularity-pill {
  border: 1px solid rgba(34, 211, 238, 0.14);
  background: rgba(15, 23, 42, 0.5);
}

.trend-list ::v-deep .chart-shell {
  border: 1px solid rgba(34, 211, 238, 0.12);
  border-radius: 16px;
  background: rgba(2, 6, 23, 0.28);
}

.trend-list ::v-deep .grid-line {
  stroke: rgba(34, 211, 238, 0.14);
}

.trend-list ::v-deep .axis-text {
  fill: rgba(191, 219, 254, 0.62);
}

.alarm-panel {
  padding: 20px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.panel-header h3 {
  margin: 0;
  color: #e0f2fe;
  font-size: 20px;
}

.panel-header span {
  padding: 4px 10px;
  border: 1px solid rgba(248, 113, 113, 0.28);
  border-radius: 999px;
  color: #fecaca;
  background: rgba(127, 29, 29, 0.22);
}

.alarm-list {
  display: grid;
  gap: 10px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.alarm-list li {
  padding: 14px 16px;
  border: 1px solid rgba(248, 113, 113, 0.26);
  border-radius: 14px;
  background: linear-gradient(90deg, rgba(127, 29, 29, 0.48), rgba(251, 146, 60, 0.1));
  box-shadow: inset 3px 0 0 rgba(248, 113, 113, 0.88), 0 10px 24px rgba(127, 29, 29, 0.16);
  display: grid;
  gap: 4px;
}

.alarm-list strong {
  color: #fee2e2;
}

.alarm-list span {
  color: #fecaca;
}

.alarm-list small {
  color: rgba(254, 202, 202, 0.72);
}

.alarm-empty,
.empty-state {
  color: rgba(191, 219, 254, 0.68);
}

.empty-state {
  padding: 34px 0;
  text-align: center;
}

@media (max-width: 980px) {
  .summary-grid,
  .snapshot-card {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .panel {
    padding: 18px;
  }

  .toolbar,
  .toolbar-group,
  .toolbar-meta {
    flex-direction: column;
    align-items: stretch;
  }

  .summary-grid,
  .snapshot-card {
    grid-template-columns: 1fr;
  }

  .status-copy {
    text-align: left;
  }

  select,
  button {
    width: 100%;
  }

  select {
    min-width: 0;
  }
}
</style>
