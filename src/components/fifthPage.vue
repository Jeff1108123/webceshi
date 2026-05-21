<template>
  <AppShell title="实时监测">
    <section class="page-section panel">
      <div class="toolbar">
        <select v-if="deviceStore.devices.length" v-model="selectedDeviceId">
          <option v-for="item in deviceStore.devices" :key="item.id" :value="item.id">
            {{ item.deviceName }} ({{ item.deviceCode }})
          </option>
        </select>
        <button :disabled="!selectedDeviceId" @click="loadMonitorData">刷新</button>
      </div>

      <div v-if="!deviceStore.devices.length" class="empty-state">暂无设备，请先申请。</div>

      <div v-else-if="monitorData" class="monitor-grid">
        <div :class="['metric', { alarm: tempAlarm }]">
          <span>温度</span>
          <strong>{{ metricValue('temperature') }}°C</strong>
          <small>{{ monitorData.threshold.tempMin }} ~ {{ monitorData.threshold.tempMax }}°C</small>
        </div>
        <div :class="['metric', { alarm: humidityAlarm }]">
          <span>湿度</span>
          <strong>{{ metricValue('humidity') }}%</strong>
          <small>{{ monitorData.threshold.humidityMin }} ~ {{ monitorData.threshold.humidityMax }}%</small>
        </div>
        <div :class="['metric', { alarm: lightAlarm }]">
          <span>光照</span>
          <strong>{{ metricValue('light') }}Lux</strong>
          <small>≤ {{ monitorData.threshold.lightMax }}Lux</small>
        </div>
        <div class="metric">
          <span>位置</span>
          <strong>{{ monitorData.location ? monitorData.location.city : '--' }}</strong>
          <small>{{ monitorData.location ? monitorData.location.address : '暂无定位' }}</small>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<script>
import AppShell from './common/AppShell.vue'
import { fetchMonitor } from '../api/medicalColdChain'
import { useDeviceStore } from '../store/deviceStore'

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
  padding: 20px;
}

.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

select,
button {
  height: 40px;
  border-radius: 10px;
}

select {
  min-width: 280px;
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

.monitor-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric {
  min-height: 140px;
  padding: 18px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.9);
  display: grid;
  gap: 10px;
}

.metric span,
.metric small {
  color: var(--text-muted);
}

.metric strong {
  font-size: 28px;
  line-height: 1.2;
}

.metric.alarm {
  border-color: rgba(214, 69, 69, 0.3);
  background: rgba(214, 69, 69, 0.06);
}

.empty-state {
  padding: 30px 0;
  text-align: center;
  color: var(--text-muted);
}

@media (max-width: 980px) {
  .monitor-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .toolbar,
  .monitor-grid {
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  select {
    min-width: 0;
    width: 100%;
  }
}
</style>
