<template>
  <AppShell title="实时监测">
    <section class="page-section panel">
      <div class="toolbar">
        <select v-if="deviceStore.devices.length" v-model="selectedDeviceId" aria-label="选择设备">
          <option v-for="item in deviceStore.devices" :key="item.id" :value="item.id">
            {{ item.deviceName }} ({{ item.deviceCode }})
          </option>
        </select>
        <button :disabled="!selectedDeviceId" @click="loadMonitorData">刷新</button>
        <span class="telemetry-time">{{ telemetryRecordedAt ? `上报时间：${telemetryRecordedAt}` : '暂无上报时间' }}</span>
      </div>

      <div v-if="!deviceStore.devices.length" class="empty-state">暂无设备，请先申请。</div>

      <div v-else-if="monitorData && monitorData.threshold" class="monitor-grid">
        <div :class="['metric', { alarm: tempAlarm }]" :aria-label="`温度状态：${tempAlarm ? '告警' : '正常'}`">
          <span>温度</span>
          <strong>{{ metricValue('temperature') }}°C</strong>
          <small>{{ monitorData.threshold.tempMin }} ~ {{ monitorData.threshold.tempMax }}°C</small>
          <small>状态：{{ tempAlarm ? '告警' : '正常' }}</small>
        </div>
        <div :class="['metric', { alarm: humidityAlarm }]" :aria-label="`湿度状态：${humidityAlarm ? '告警' : '正常'}`">
          <span>湿度</span>
          <strong>{{ metricValue('humidity') }}%</strong>
          <small>{{ monitorData.threshold.humidityMin }} ~ {{ monitorData.threshold.humidityMax }}%</small>
          <small>状态：{{ humidityAlarm ? '告警' : '正常' }}</small>
        </div>
        <div :class="['metric', { alarm: lightAlarm }]" :aria-label="`光照状态：${lightAlarm ? '告警' : '正常'}`">
          <span>光照</span>
          <strong>{{ metricValue('light') }}Lux</strong>
          <small>≤ {{ monitorData.threshold.lightMax }}Lux</small>
          <small>状态：{{ lightAlarm ? '告警' : '正常' }}</small>
        </div>
        <div class="metric">
          <span>位置</span>
          <strong>{{ monitorData.location ? monitorData.location.city : '--' }}</strong>
          <small>{{ monitorData.location ? monitorData.location.address : '暂无定位' }}</small>
        </div>
      </div>

      <div v-else-if="monitorData" class="empty-state">暂无阈值数据。</div>
    </section>
  </AppShell>
</template>

<script>
import AppShell from './common/AppShell.vue'
import { fetchMonitor } from '../api/medicalColdChain'
import { useDeviceStore } from '../store/deviceStore'
import { formatDateTime } from '../utils/dateTime'

export default {
  name: 'FifthPage',
  components: {
    AppShell
  },
  data() {
    return {
      deviceStore: useDeviceStore(),
      selectedDeviceId: null,
      monitorData: null,
      refreshTimer: null
    }
  },
  computed: {
    tempAlarm() {
      if (!this.monitorData || !this.monitorData.telemetry || !this.monitorData.threshold) return false
      const { temperature } = this.monitorData.telemetry
      const { tempMin, tempMax } = this.monitorData.threshold
      return temperature < tempMin || temperature > tempMax
    },
    humidityAlarm() {
      if (!this.monitorData || !this.monitorData.telemetry || !this.monitorData.threshold) return false
      const { humidity } = this.monitorData.telemetry
      const { humidityMin, humidityMax } = this.monitorData.threshold
      return humidity < humidityMin || humidity > humidityMax
    },
    lightAlarm() {
      if (!this.monitorData || !this.monitorData.telemetry || !this.monitorData.threshold) return false
      return this.monitorData.telemetry.light > this.monitorData.threshold.lightMax
    },
    telemetryRecordedAt() {
      const telemetry = this.monitorData && this.monitorData.telemetry
      return telemetry && telemetry.recordedAt ? formatDateTime(telemetry.recordedAt) : ''
    }
  },
  watch: {
    async selectedDeviceId(value) {
      if (!value) {
        this.monitorData = null
        return
      }
      const selectedDevice = this.deviceStore.setSelectedDevice(value)
      if (!selectedDevice || selectedDevice.id !== value) {
        this.selectedDeviceId = selectedDevice ? selectedDevice.id : null
        return
      }
      await this.loadMonitorData()
    }
  },
  async created() {
    try {
      await this.deviceStore.refreshDevices()
      const selectedDevice = this.deviceStore.syncSelectedDevice()
      this.selectedDeviceId = selectedDevice ? selectedDevice.id : null
    } catch (error) {
      this.$message.error(error.message)
    }
  },
  mounted() {
    this.refreshTimer = setInterval(this.loadMonitorData, 5000)
  },
  beforeDestroy() {
    if (this.refreshTimer) clearInterval(this.refreshTimer)
  },
  methods: {
    metricValue(key) {
      if (!this.monitorData || !this.monitorData.telemetry) return '--'
      return this.monitorData.telemetry[key]
    },
    async loadMonitorData() {
      if (!this.selectedDeviceId) return
      try {
        this.monitorData = await fetchMonitor(this.selectedDeviceId)
      } catch (error) {
        this.$message.error(error.message)
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
    radial-gradient(circle at top left, rgba(34, 211, 238, 0.15), transparent 34%),
    linear-gradient(90deg, rgba(34, 211, 238, 0.06) 1px, transparent 1px),
    linear-gradient(180deg, rgba(34, 211, 238, 0.05) 1px, transparent 1px);
  background-size: auto, 44px 44px, 44px 44px;
}

.toolbar,
.monitor-grid,
.empty-state {
  position: relative;
  z-index: 1;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}

select,
button {
  height: 42px;
  border-radius: 12px;
  font: inherit;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease, transform 0.2s ease;
}

select {
  min-width: 300px;
  padding: 0 14px;
  border: 1px solid rgba(34, 211, 238, 0.24);
  color: #dbeafe;
  background: rgba(15, 23, 42, 0.82);
  outline: none;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.38);
  cursor: pointer;
}

select:focus {
  border-color: rgba(34, 211, 238, 0.82);
  box-shadow: 0 0 0 3px rgba(34, 211, 238, 0.14), 0 0 24px rgba(34, 211, 238, 0.2);
}

.telemetry-time {
  padding: 10px 13px;
  border: 1px solid rgba(34, 211, 238, 0.18);
  border-radius: 999px;
  color: rgba(191, 219, 254, 0.78);
  background: rgba(2, 6, 23, 0.32);
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

.monitor-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.metric {
  position: relative;
  min-height: 156px;
  overflow: hidden;
  padding: 20px;
  border: 1px solid rgba(34, 211, 238, 0.18);
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(8, 47, 73, 0.46), rgba(2, 6, 23, 0.5));
  box-shadow: inset 0 1px 0 rgba(148, 163, 184, 0.08), 0 14px 30px rgba(2, 6, 23, 0.24);
  display: grid;
  align-content: space-between;
  gap: 12px;
}

.metric::after {
  content: '';
  position: absolute;
  right: -26px;
  top: -26px;
  width: 86px;
  height: 86px;
  border-radius: 999px;
  background: rgba(34, 211, 238, 0.14);
  filter: blur(2px);
}

.metric span,
.metric small {
  position: relative;
  z-index: 1;
  color: rgba(191, 219, 254, 0.7);
}

.metric span {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.metric strong {
  position: relative;
  z-index: 1;
  color: #67e8f9;
  font-size: 36px;
  line-height: 1.08;
  text-shadow: 0 0 18px rgba(34, 211, 238, 0.34);
  word-break: break-word;
}

.metric small {
  line-height: 1.5;
}

.metric.alarm {
  border-color: rgba(248, 113, 113, 0.52);
  background: linear-gradient(180deg, rgba(127, 29, 29, 0.46), rgba(67, 20, 7, 0.42));
  box-shadow: inset 0 1px 0 rgba(254, 202, 202, 0.1), 0 0 28px rgba(248, 113, 113, 0.16);
}

.metric.alarm::after {
  background: rgba(248, 113, 113, 0.22);
}

.metric.alarm strong {
  color: #fb923c;
  text-shadow: 0 0 18px rgba(251, 146, 60, 0.42);
}

.metric.alarm span,
.metric.alarm small {
  color: rgba(254, 226, 226, 0.78);
}

.empty-state {
  padding: 34px 0;
  text-align: center;
  color: rgba(191, 219, 254, 0.68);
}

@media (max-width: 1080px) {
  .monitor-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 620px) {
  .panel {
    padding: 18px;
  }

  .toolbar,
  .monitor-grid {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: stretch;
  }

  select,
  button {
    min-width: 0;
    width: 100%;
  }

  .metric strong {
    font-size: 31px;
  }
}
</style>
