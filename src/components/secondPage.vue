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
  gap: 16px;
  margin-bottom: 18px;
}

.summary div {
  position: relative;
  overflow: hidden;
  min-height: 112px;
  padding: 18px;
  display: grid;
  align-content: space-between;
  gap: 14px;
  border: 1px solid rgba(34, 211, 238, 0.18);
  border-radius: 22px;
  background:
    linear-gradient(145deg, rgba(15, 23, 42, 0.94), rgba(15, 23, 42, 0.68)),
    radial-gradient(circle at top right, rgba(34, 211, 238, 0.2), transparent 36%);
  box-shadow: 0 18px 42px rgba(2, 8, 23, 0.28), inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.summary div::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: linear-gradient(180deg, #22d3ee, rgba(34, 211, 238, 0.18));
}

.summary div:nth-child(4) {
  border-color: rgba(248, 113, 113, 0.26);
  background:
    linear-gradient(145deg, rgba(15, 23, 42, 0.94), rgba(15, 23, 42, 0.68)),
    radial-gradient(circle at top right, rgba(248, 113, 113, 0.2), transparent 36%);
}

.summary div:nth-child(4)::before {
  background: linear-gradient(180deg, #f87171, rgba(248, 113, 113, 0.18));
}

.summary span {
  color: rgba(203, 213, 225, 0.78);
  font-size: 13px;
  letter-spacing: 0.08em;
}

.summary strong {
  color: #f8fafc;
  font-size: 34px;
  line-height: 1;
  text-shadow: 0 0 18px rgba(34, 211, 238, 0.3);
}

.summary div:nth-child(4) strong {
  text-shadow: 0 0 18px rgba(248, 113, 113, 0.35);
}

.panel {
  padding: 22px;
  border-color: rgba(34, 211, 238, 0.14);
  background:
    linear-gradient(180deg, rgba(15, 23, 42, 0.86), rgba(15, 23, 42, 0.72)),
    radial-gradient(circle at top left, rgba(14, 165, 233, 0.1), transparent 34%);
  box-shadow: 0 22px 56px rgba(2, 8, 23, 0.28);
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: end;
  margin-bottom: 18px;
}

label {
  display: grid;
  gap: 7px;
  color: rgba(203, 213, 225, 0.82);
  font-size: 13px;
}

input {
  width: 120px;
  height: 40px;
  padding: 0 12px;
  color: #e2e8f0;
  border: 1px solid rgba(34, 211, 238, 0.24);
  border-radius: 12px;
  outline: none;
  background: rgba(2, 6, 23, 0.48);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

input:focus {
  border-color: rgba(34, 211, 238, 0.58);
  box-shadow: 0 0 0 3px rgba(34, 211, 238, 0.12);
}

button {
  height: 40px;
  padding: 0 14px;
  border: 1px solid transparent;
  border-radius: 12px;
  cursor: pointer;
  font-weight: 700;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

button:not(:disabled):hover {
  transform: translateY(-1px);
}

.primary-btn {
  color: #03131f;
  border-color: rgba(34, 211, 238, 0.42);
  background: linear-gradient(135deg, #67e8f9, #0ea5e9);
  box-shadow: 0 10px 24px rgba(14, 165, 233, 0.26);
}

.danger-btn,
.danger-link {
  color: #fff;
  border-color: rgba(248, 113, 113, 0.38);
  background: linear-gradient(135deg, #fb7185, #dc2626);
  box-shadow: 0 10px 24px rgba(220, 38, 38, 0.2);
}

.ghost-btn,
.actions button:not(.danger-link) {
  color: #a5f3fc;
  border-color: rgba(34, 211, 238, 0.24);
  background: rgba(8, 47, 73, 0.48);
}

.device-list {
  display: grid;
  gap: 14px;
}

.device-card {
  position: relative;
  overflow: hidden;
  padding: 18px;
  border: 1px solid rgba(34, 211, 238, 0.16);
  border-radius: 22px;
  background:
    linear-gradient(145deg, rgba(15, 23, 42, 0.96), rgba(15, 23, 42, 0.78)),
    radial-gradient(circle at 10% 0, rgba(34, 211, 238, 0.13), transparent 32%);
  box-shadow: 0 20px 48px rgba(2, 8, 23, 0.3), inset 0 1px 0 rgba(255, 255, 255, 0.06);
}

.device-card::after {
  content: '';
  position: absolute;
  top: 0;
  right: 18px;
  width: 120px;
  height: 2px;
  background: linear-gradient(90deg, transparent, rgba(34, 211, 238, 0.78), transparent);
}

.device-card header {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.device-card h3 {
  margin-bottom: 5px;
  color: #f8fafc;
  font-size: 18px;
  letter-spacing: 0.02em;
}

.device-card p {
  margin: 0;
  line-height: 1.6;
  color: rgba(203, 213, 225, 0.78);
}

.status {
  align-self: start;
  flex: 0 0 auto;
  padding: 6px 11px;
  border: 1px solid rgba(34, 197, 94, 0.28);
  border-radius: 999px;
  color: #86efac;
  background: rgba(22, 101, 52, 0.22);
  box-shadow: 0 0 18px rgba(34, 197, 94, 0.14);
  font-size: 12px;
  font-weight: 800;
}

.status.alarm {
  color: #fecdd3;
  border-color: rgba(248, 113, 113, 0.4);
  background: rgba(127, 29, 29, 0.34);
  box-shadow: 0 0 18px rgba(248, 113, 113, 0.18);
}

.metrics {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: 14px 0;
}

.metrics span {
  min-height: 58px;
  padding: 11px 12px;
  display: flex;
  align-items: center;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 14px;
  color: #e0f2fe;
  background: rgba(2, 6, 23, 0.36);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
  font-size: 13px;
}

.meta {
  position: relative;
  z-index: 1;
  padding: 10px 12px;
  border: 1px solid rgba(34, 211, 238, 0.12);
  border-radius: 14px;
  color: rgba(203, 213, 225, 0.72);
  background: rgba(8, 47, 73, 0.22);
}

.actions {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.actions button {
  min-width: 72px;
  height: 34px;
  padding: 0 12px;
  border-radius: 10px;
  font-size: 13px;
}

.empty-state {
  padding: 34px 0;
  text-align: center;
  color: rgba(203, 213, 225, 0.72);
}

button:disabled {
  opacity: 0.58;
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

  .panel,
  .device-card {
    padding: 16px;
  }

  .device-card header {
    flex-direction: column;
  }

  .actions button {
    flex: 1 1 86px;
  }
}
</style>
