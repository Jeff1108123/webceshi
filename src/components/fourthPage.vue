<template>
  <AppShell title="实时数据">
    <section class="page-section panel">
      <div class="toolbar">
        <span>{{ lastUpdated ? `更新时间：${lastUpdated}` : '暂无数据' }}</span>
        <button :disabled="loading" @click="loadLatestData">刷新</button>
      </div>

      <div v-if="!records.length" class="empty-state">暂无设备数据。</div>

      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>设备</th>
              <th>上报时间</th>
              <th>温度</th>
              <th>湿度</th>
              <th>光照</th>
              <th>电量</th>
              <th>信号</th>
              <th>位置</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in records"
              :key="item.deviceId"
              :class="{ alarm: item.telemetry && item.telemetry.alarm }"
              :aria-label="`${item.deviceName}状态：${item.telemetry && item.telemetry.alarm ? '告警' : '正常'}`"
            >
              <td>
                <strong>{{ item.deviceName }}</strong>
                <span>{{ item.deviceCode }}</span>
                <span class="alarm-status">状态：{{ item.telemetry && item.telemetry.alarm ? '告警' : '正常' }}</span>
              </td>
              <td>{{ formatTelemetryTime(item.telemetry) }}</td>
              <td>{{ renderMetric(item.telemetry, 'temperature', '°C') }}</td>
              <td>{{ renderMetric(item.telemetry, 'humidity', '%') }}</td>
              <td>{{ renderMetric(item.telemetry, 'light', 'Lux') }}</td>
              <td>{{ item.telemetry ? `${item.telemetry.batteryLevel}%` : '--' }}</td>
              <td>{{ item.telemetry && item.telemetry.signalStatus ? '正常' : '异常' }}</td>
              <td>{{ item.location ? item.location.address : '--' }}</td>
              <td class="actions">
                <button @click="navigateTo('/monitor', item.deviceId)">监测</button>
                <button @click="navigateTo('/history', item.deviceId)">历史</button>
                <button @click="navigateTo('/location', item.deviceId)">位置</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </AppShell>
</template>

<script>
import AppShell from './common/AppShell.vue'
import { fetchLatest } from '../api/medicalColdChain'
import { useDeviceStore } from '../store/deviceStore'
import { formatDateTime } from '../utils/dateTime'

export default {
  name: 'FourthPage',
  components: {
    AppShell
  },
  data() {
    return {
      deviceStore: useDeviceStore(),
      loading: false,
      records: [],
      lastUpdated: '',
      refreshTimer: null
    }
  },
  async created() {
    await this.loadLatestData()
    this.refreshTimer = setInterval(this.loadLatestData, 15000)
  },
  beforeDestroy() {
    if (this.refreshTimer) clearInterval(this.refreshTimer)
  },
  methods: {
    renderMetric(record, key, unit) {
      if (!record) return '--'
      return `${record[key]}${unit}`
    },
    formatTelemetryTime(record) {
      return record && record.recordedAt ? formatDateTime(record.recordedAt) : '--'
    },
    async loadLatestData() {
      this.loading = true
      try {
        this.records = await fetchLatest()
        this.lastUpdated = formatDateTime()
        await this.deviceStore.refreshDevices()
      } catch (error) {
        this.$message.error(error.message)
      } finally {
        this.loading = false
      }
    },
    navigateTo(path, deviceId) {
      this.deviceStore.setSelectedDevice(deviceId)
      this.$router.push(path)
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
    radial-gradient(circle at top right, rgba(34, 211, 238, 0.15), transparent 32%),
    linear-gradient(90deg, rgba(34, 211, 238, 0.06) 1px, transparent 1px),
    linear-gradient(180deg, rgba(34, 211, 238, 0.05) 1px, transparent 1px);
  background-size: auto, 44px 44px, 44px 44px;
}

.toolbar,
.table-wrap,
.empty-state {
  position: relative;
  z-index: 1;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  color: rgba(191, 219, 254, 0.78);
}

.toolbar span {
  padding: 9px 12px;
  border: 1px solid rgba(34, 211, 238, 0.18);
  border-radius: 999px;
  background: rgba(2, 6, 23, 0.3);
}

button {
  height: 36px;
  padding: 0 13px;
  border: 1px solid rgba(34, 211, 238, 0.36);
  border-radius: 10px;
  color: #e0f2fe;
  background: rgba(8, 145, 178, 0.18);
  box-shadow: 0 0 16px rgba(34, 211, 238, 0.12);
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease, transform 0.2s ease;
}

button:not(:disabled):hover {
  transform: translateY(-1px);
  border-color: rgba(125, 211, 252, 0.8);
  background: rgba(14, 165, 233, 0.28);
  box-shadow: 0 0 22px rgba(34, 211, 238, 0.24);
}

button:disabled {
  opacity: 0.48;
  cursor: not-allowed;
  box-shadow: none;
}

.table-wrap {
  overflow-x: auto;
  overflow-y: hidden;
  border: 1px solid rgba(34, 211, 238, 0.16);
  border-radius: 16px;
  background: rgba(2, 6, 23, 0.34);
  box-shadow: inset 0 1px 0 rgba(148, 163, 184, 0.08);
}

.table-wrap::-webkit-scrollbar {
  height: 10px;
}

.table-wrap::-webkit-scrollbar-track {
  background: rgba(15, 23, 42, 0.82);
}

.table-wrap::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(34, 211, 238, 0.42);
}

table {
  width: 100%;
  min-width: 980px;
  border-collapse: collapse;
  color: #dbeafe;
}

th,
td {
  padding: 13px 14px;
  text-align: left;
  border-bottom: 1px solid rgba(148, 163, 184, 0.12);
  vertical-align: middle;
}

th {
  position: sticky;
  top: 0;
  color: #67e8f9;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  background: rgba(8, 47, 73, 0.92);
}

tbody tr {
  transition: background 0.2s ease;
}

tbody tr:hover {
  background: rgba(14, 165, 233, 0.08);
}

td strong,
td span {
  display: block;
}

td strong {
  color: #f8fafc;
}

td span {
  margin-top: 4px;
  color: rgba(191, 219, 254, 0.62);
}

.alarm-status {
  display: inline-block;
  width: fit-content;
  padding: 2px 7px;
  border: 1px solid rgba(34, 211, 238, 0.22);
  border-radius: 999px;
  color: #e0f2fe;
  background: rgba(15, 23, 42, 0.48);
  font-size: 12px;
  line-height: 1.4;
}

tr.alarm {
  background: linear-gradient(90deg, rgba(127, 29, 29, 0.42), rgba(251, 146, 60, 0.1));
}

tr.alarm td {
  border-bottom-color: rgba(248, 113, 113, 0.24);
}

tr.alarm td:first-child {
  box-shadow: inset 3px 0 0 rgba(248, 113, 113, 0.9);
}

.actions {
  display: flex;
  flex-wrap: nowrap;
  gap: 6px;
  min-width: 168px;
}

.actions button {
  height: 32px;
  padding: 0 10px;
  color: #cffafe;
  background: rgba(14, 116, 144, 0.22);
}

.empty-state {
  padding: 34px 0;
  text-align: center;
  color: rgba(191, 219, 254, 0.68);
}

@media (max-width: 760px) {
  .panel {
    padding: 18px;
  }

  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar span,
  .toolbar button {
    width: 100%;
    box-sizing: border-box;
  }
}
</style>
