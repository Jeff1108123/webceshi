<template>
  <AppShell title="设备管理">
    <section class="summary">
      <div><span>可借设备</span><strong>{{ deviceStore.overview.availableCount }}</strong></div>
      <div><span>系统在用</span><strong>{{ deviceStore.overview.inUseCount }}</strong></div>
      <div><span>我的设备</span><strong>{{ deviceStore.overview.myDeviceCount }}</strong></div>
      <div><span>告警</span><strong>{{ deviceStore.overview.alarmCount }}</strong></div>
    </section>

    <section class="page-section panel">
      <div class="toolbar">
        <label>
          申请数量
          <input v-model.number="applyCount" type="number" min="1" max="10" />
        </label>
        <button class="primary-btn" :disabled="loading" @click="handleApply">申请设备</button>
        <button class="danger-btn" :disabled="loading || !deviceStore.devices.length" @click="handleReturnAll">
          归还全部
        </button>
        <button class="ghost-btn" :disabled="loading" @click="refreshPage">刷新</button>
      </div>

      <div v-if="!deviceStore.devices.length" class="empty-state">暂无借用中的设备，请先申请创建设备。</div>

      <div v-else class="device-list">
        <article v-for="item in deviceStore.devices" :key="item.id" class="device-card">
          <header>
            <div>
              <h3>{{ item.deviceName }}</h3>
              <p>{{ item.deviceCode }} / {{ item.medicineName }}</p>
            </div>
            <span :class="['status', { alarm: item.alarm }]">{{ item.alarm ? '告警' : '正常' }}</span>
          </header>

          <div class="metrics">
            <span>温度 {{ formatValue(item.latestTelemetry, 'temperature', '°C') }}</span>
            <span>湿度 {{ formatValue(item.latestTelemetry, 'humidity', '%') }}</span>
            <span>光照 {{ formatValue(item.latestTelemetry, 'light', 'Lux') }}</span>
            <span>电量 {{ item.batteryLevel }}%</span>
          </div>

          <p class="meta">{{ item.routeName }} / {{ item.latestLocation ? item.latestLocation.address : '暂无定位' }}</p>

          <div class="actions">
            <button @click="openPage('/threshold', item.id)">阈值</button>
            <button @click="openPage('/monitor', item.id)">监测</button>
            <button class="danger-link" :disabled="loading" @click="handleReturnSingle(item.id)">归还</button>
          </div>
        </article>
      </div>
    </section>
  </AppShell>
</template>

<script>
import AppShell from './common/AppShell.vue'
import { applyDevices, returnDevices } from '../api/medicalColdChain'
import { useDeviceStore } from '../store/deviceStore'

export default {
  name: 'SecondPage',
  components: {
    AppShell
  },
  data() {
    return {
      deviceStore: useDeviceStore(),
      loading: false,
      applyCount: 1,
      refreshTimer: null
    }
  },
  async created() {
    await this.refreshPage()
    this.refreshTimer = setInterval(this.refreshPage, 30000)
  },
  beforeDestroy() {
    if (this.refreshTimer) clearInterval(this.refreshTimer)
  },
  methods: {
    formatValue(record, key, unit) {
      if (!record) return '--'
      return `${record[key]}${unit}`
    },
    async refreshPage() {
      try {
        await this.deviceStore.refreshAll()
      } catch (error) {
        this.$message.error(error.message)
      }
    },
    async handleApply() {
      const count = Number(this.applyCount)
      if (!Number.isInteger(count) || count < 1 || count > 10) {
        this.$message.error('申请数量必须是 1 到 10')
        return
      }

      this.loading = true
      try {
        await applyDevices(count)
        await this.refreshPage()
        this.$message.success('申请成功')
      } catch (error) {
        this.$message.error(error.message)
      } finally {
        this.loading = false
      }
    },
    async handleReturnAll() {
      await this.handleReturn([])
    },
    async handleReturnSingle(deviceId) {
      await this.handleReturn([deviceId])
    },
    async handleReturn(deviceIds) {
      this.loading = true
      try {
        const devices = await returnDevices(deviceIds)
        if (Array.isArray(devices)) {
          this.deviceStore.devices = devices
          this.deviceStore.syncSelectedDevice()
        }
        await this.deviceStore.refreshOverview()
        this.$message.success('归还成功')
      } catch (error) {
        this.$message.error(error.message)
      } finally {
        this.loading = false
      }
    },
    openPage(path, deviceId) {
      this.deviceStore.setSelectedDevice(deviceId)
      this.$router.push(path)
    }
  }
}
</script>

<style scoped>
.summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.summary div,
.device-card {
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.9);
}

.summary div {
  padding: 16px;
  display: grid;
  gap: 8px;
}

.summary span,
.meta {
  color: var(--text-muted);
}

.summary strong {
  font-size: 28px;
}

.panel {
  padding: 20px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: end;
  margin-bottom: 16px;
}

label {
  display: grid;
  gap: 6px;
  color: var(--text-muted);
}

input {
  width: 120px;
  height: 40px;
  padding: 0 12px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 10px;
}

button {
  height: 40px;
  padding: 0 14px;
  border: none;
  border-radius: 10px;
  cursor: pointer;
}

.primary-btn {
  color: #fff;
  background: var(--primary);
}

.danger-btn,
.danger-link {
  color: #fff;
  background: var(--danger);
}

.ghost-btn,
.actions button:not(.danger-link) {
  color: var(--primary-dark);
  background: rgba(15, 123, 255, 0.08);
}

.device-list {
  display: grid;
  gap: 12px;
}

.device-card {
  padding: 16px;
}

.device-card header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.device-card h3 {
  margin-bottom: 4px;
  font-size: 18px;
}

.device-card p {
  line-height: 1.6;
}

.status {
  align-self: start;
  padding: 6px 10px;
  border-radius: 999px;
  color: var(--success);
  background: rgba(31, 157, 102, 0.1);
}

.status.alarm {
  color: var(--danger);
  background: rgba(214, 69, 69, 0.12);
}

.metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin: 12px 0;
}

.metrics span {
  padding: 10px;
  border-radius: 12px;
  background: rgba(248, 250, 252, 0.95);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
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

@media (max-width: 900px) {
  .summary,
  .metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .summary,
  .metrics {
    grid-template-columns: 1fr;
  }

  .device-card header {
    flex-direction: column;
  }
}
</style>
