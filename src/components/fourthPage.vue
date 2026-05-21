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
            <tr v-for="item in records" :key="item.deviceId" :class="{ alarm: item.telemetry && item.telemetry.alarm }">
              <td>
                <strong>{{ item.deviceName }}</strong>
                <span>{{ item.deviceCode }}</span>
              </td>
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
  padding: 20px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  color: var(--text-muted);
}

button {
  height: 36px;
  padding: 0 12px;
  border: none;
  border-radius: 10px;
  color: var(--primary-dark);
  background: rgba(15, 123, 255, 0.08);
  cursor: pointer;
}

.table-wrap {
  overflow: auto;
}

table {
  width: 100%;
  min-width: 980px;
  border-collapse: collapse;
}

th,
td {
  padding: 12px;
  text-align: left;
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
}

th {
  color: var(--text-muted);
  font-size: 13px;
  background: rgba(248, 250, 252, 0.9);
}

td strong,
td span {
  display: block;
}

td span {
  margin-top: 4px;
  color: var(--text-muted);
}

tr.alarm {
  background: rgba(214, 69, 69, 0.06);
}

.actions {
  display: flex;
  gap: 6px;
}

.empty-state {
  padding: 30px 0;
  text-align: center;
  color: var(--text-muted);
}
</style>
