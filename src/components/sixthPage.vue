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
            <option :value="48">近 48 小时</option>
            <option :value="72">近 72 小时</option>
          </select>
        </div>
        <div class="toolbar-group toolbar-meta">
          <div class="status-copy">
            <strong>{{ dataCurrentAt ? `数据更新到：${formatDateTime(dataCurrentAt)}` : '等待加载历史数据' }}</strong>
            <small>{{ loadedAt ? `页面刷新时间：${loadedAt}` : '点击刷新可拉取当前时间范围内的最新历史数据' }}</small>
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
  </AppShell>
</template>

<script>
import AppShell from './common/AppShell.vue'
import TrendChartCard from './common/TrendChartCard.vue'
import { fetchHistory } from '../api/medicalColdChain'
import { useDeviceStore } from '../store/deviceStore'
import { formatDateTime, formatMonthDayTime } from '../utils/dateTime'

export default {
  name: 'SixthPage',
  components: {
    AppShell,
    TrendChartCard
  },
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
  },
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
    dataCurrentAt() {
      return this.latestSnapshot ? this.latestSnapshot.recordedAt : ''
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
    async loadHistory() {
      if (!this.selectedDeviceId) return
      this.loading = true
      try {
        this.historyData = await fetchHistory(this.selectedDeviceId, this.hours)
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
  padding: 20px;
}

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
  justify-content: space-between;
  flex: 1;
  min-width: 280px;
  color: var(--text-muted);
}

.status-copy {
  display: grid;
  gap: 4px;
}

.status-copy strong {
  font-size: 14px;
  color: var(--text-main, #0f172a);
}

.status-copy small {
  color: var(--text-muted);
}

select,
button {
  height: 40px;
  border-radius: 10px;
}

select {
  min-width: 180px;
  padding: 0 12px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: #fff;
}

button {
  padding: 0 14px;
  border: none;
  color: var(--primary-dark);
  background: rgba(15, 123, 255, 0.08);
  cursor: pointer;
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
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.9);
}

.summary-card {
  padding: 18px;
  display: grid;
  gap: 10px;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.summary-card:hover {
  transform: translateY(-1px);
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
  padding: 20px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.snapshot-card > div,
.summary-card {
  background: rgba(248, 250, 252, 0.65);
  border-radius: 14px;
}

.snapshot-card > div {
  padding: 14px;
  display: grid;
  gap: 8px;
}

.trend-list {
  display: grid;
  gap: 12px;
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
  border-radius: 14px;
  background: rgba(214, 69, 69, 0.06);
  border: 1px solid rgba(214, 69, 69, 0.08);
  display: grid;
  gap: 4px;
}

.alarm-empty,
.empty-state {
  color: var(--text-muted);
}

.empty-state {
  padding: 30px 0;
  text-align: center;
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
