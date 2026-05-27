<template>
  <AppShell title="设备借用总览">
    <section class="page-section panel">
      <div class="toolbar">
        <input
          v-model.trim="filters.keyword"
          aria-label="搜索设备借用记录"
          placeholder="搜索设备 / 借用人 / 手机号"
          @keyup.enter="loadRecords"
        />
        <select v-model="filters.status" aria-label="筛选借用状态" @change="loadRecords">
          <option value="">全部</option>
          <option value="BORROWED">借用中</option>
          <option value="RETURNED">已归还</option>
        </select>
        <button :disabled="loading" @click="loadRecords">刷新</button>
      </div>

      <div v-if="loading" class="empty-state">加载中...</div>
      <div v-else-if="!records.length" class="empty-state">暂无借用记录。</div>

      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>设备</th>
              <th>药品</th>
              <th>线路</th>
              <th>借用人</th>
              <th>手机号</th>
              <th>借用时间</th>
              <th>归还时间</th>
              <th>当前阈值</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in records" :key="item.recordId">
              <td>
                <strong>{{ item.deviceName }}</strong>
                <span>{{ item.deviceCode }}</span>
              </td>
              <td>{{ item.medicineName || '--' }}</td>
              <td>{{ item.routeName || '--' }}</td>
              <td>{{ item.borrowerName || '--' }}</td>
              <td>{{ item.borrowerPhone || '--' }}</td>
              <td>{{ formatDateTime(item.borrowTime) }}</td>
              <td>{{ formatDateTime(item.returnTime) }}</td>
              <td>
                <div v-if="item.threshold" class="threshold-cell">
                  <span>温度 {{ formatRange(item.threshold.tempMin, item.threshold.tempMax, '°C') }}</span>
                  <span>湿度 {{ formatRange(item.threshold.humidityMin, item.threshold.humidityMax, '%') }}</span>
                  <span>光照 ≤{{ formatValue(item.threshold.lightMax, 'Lux') }}</span>
                </div>
                <span v-else class="muted">--</span>
              </td>
              <td>
                <span :class="['status', { returned: item.status === 'RETURNED' }]">
                  {{ statusLabel(item.status) }}
                </span>
              </td>
              <td>
                <button
                  v-if="item.status === 'BORROWED'"
                  class="danger-action"
                  :disabled="loading || !item.deviceId"
                  @click="handleForceReturn(item)"
                >
                  强制归还
                </button>
                <span v-else class="muted">--</span>
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
import { fetchAllDeviceBorrows, forceReturnDevices } from '../api/medicalColdChain'
import { formatDateTime as formatDateTimeValue } from '../utils/dateTime'

function normalizeThreshold(raw = {}) {
  const threshold = raw.threshold || raw
  const hasThreshold = ['tempMin', 'tempMax', 'humidityMin', 'humidityMax', 'lightMax']
    .some(key => threshold[key] !== undefined && threshold[key] !== null)

  if (!hasThreshold) {
    return null
  }

  return {
    tempMin: threshold.tempMin,
    tempMax: threshold.tempMax,
    humidityMin: threshold.humidityMin,
    humidityMax: threshold.humidityMax,
    lightMax: threshold.lightMax
  }
}

function normalizeRecord(raw = {}, index) {
  return {
    recordId: raw.recordId || raw.id || `${raw.deviceId || 'device'}-${index}`,
    deviceId: raw.deviceId || null,
    deviceName: raw.deviceName || '--',
    deviceCode: raw.deviceCode || '--',
    medicineName: raw.medicineName || '',
    routeName: raw.routeName || '',
    borrowerName: raw.borrowerName || raw.userName || '',
    borrowerPhone: raw.borrowerPhone || raw.userPhone || '',
    borrowTime: raw.borrowTime || raw.borrowedAt || '',
    returnTime: raw.returnTime || raw.returnedAt || '',
    threshold: normalizeThreshold(raw),
    status: String(raw.status || ((raw.returnTime || raw.returnedAt) ? 'RETURNED' : 'BORROWED')).toUpperCase()
  }
}

export default {
  name: 'AdminDeviceBorrowPage',
  components: {
    AppShell
  },
  data() {
    return {
      loading: false,
      records: [],
      filters: {
        keyword: '',
        status: ''
      }
    }
  },
  created() {
    this.loadRecords()
  },
  methods: {
    statusLabel(status) {
      return status === 'RETURNED' ? '已归还' : '借用中'
    },
    formatDateTime(value) {
      return formatDateTimeValue(value) || '--'
    },
    formatValue(value, unit = '') {
      if (value === null || value === undefined || value === '') {
        return '--'
      }
      return `${value}${unit}`
    },
    formatRange(min, max, unit = '') {
      if (min === null || min === undefined || min === '' || max === null || max === undefined || max === '') {
        return '--'
      }
      return `${min}~${max}${unit}`
    },
    async loadRecords() {
      this.loading = true
      try {
        const payload = await fetchAllDeviceBorrows({
          keyword: this.filters.keyword || undefined,
          status: this.filters.status || undefined
        })
        const rawRecords = Array.isArray(payload) ? payload : (payload.records || payload.list || [])
        this.records = rawRecords.map((item, index) => normalizeRecord(item, index))
      } catch (error) {
        this.$message.error(error.message)
      } finally {
        this.loading = false
      }
    },
    async handleForceReturn(item) {
      if (!item.deviceId) {
        this.$message.error('缺少设备信息，无法强制归还')
        return
      }
      this.loading = true
      try {
        await forceReturnDevices({ deviceIds: [item.deviceId] })
        await this.loadRecords()
        this.$message.success('强制归还成功')
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
  display: grid;
  grid-template-columns: 1fr 150px 80px;
  gap: 10px;
  margin-bottom: 14px;
}

input,
select,
button {
  height: 40px;
  border-radius: 12px;
}

input,
select {
  padding: 0 12px;
  color: var(--text-main);
  border: 1px solid var(--line);
  background: rgba(4, 14, 29, 0.72);
  outline: 2px solid transparent;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}

input::placeholder {
  color: var(--text-subtle);
}

input:focus,
select:focus {
  background: rgba(8, 22, 43, 0.9);
  border-color: rgba(34, 211, 238, 0.72);
  box-shadow: 0 0 0 3px rgba(34, 211, 238, 0.12), 0 0 22px rgba(34, 211, 238, 0.12);
}

select option {
  color: var(--text-main);
  background: #071224;
}

button {
  border: 1px solid rgba(34, 211, 238, 0.32);
  color: #e0faff;
  font-weight: 700;
  background: linear-gradient(135deg, rgba(8, 145, 178, 0.34), rgba(37, 99, 235, 0.24));
  box-shadow: 0 10px 24px rgba(34, 211, 238, 0.1), inset 0 1px 0 rgba(255, 255, 255, 0.06);
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

button:hover:not(:disabled) {
  border-color: rgba(34, 211, 238, 0.62);
  box-shadow: 0 12px 28px rgba(34, 211, 238, 0.16);
  transform: translateY(-1px);
}

.table-wrap {
  overflow: auto;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: rgba(5, 15, 31, 0.42);
}

table {
  width: 100%;
  min-width: 1320px;
  border-collapse: collapse;
  color: var(--text-main);
}

th,
td {
  padding: 12px;
  text-align: left;
  border-bottom: 1px solid rgba(113, 206, 255, 0.14);
}

tbody tr:hover {
  background: rgba(21, 55, 93, 0.34);
}

th {
  color: #9ee8ff;
  font-size: 13px;
  background: rgba(8, 22, 43, 0.86);
}

td strong,
td span {
  display: block;
}

td strong {
  color: var(--text-strong);
}

td span {
  margin-top: 4px;
  color: var(--text-muted);
}

.threshold-cell {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 10px;
  min-width: 210px;
}

.threshold-cell span {
  margin-top: 0;
  color: var(--text-muted);
  white-space: nowrap;
}

.status {
  display: inline-flex;
  padding: 6px 10px;
  border: 1px solid rgba(247, 201, 72, 0.36);
  border-radius: 999px;
  color: #fde68a;
  background: rgba(247, 201, 72, 0.12);
  box-shadow: 0 0 16px rgba(247, 201, 72, 0.08);
}

.status.returned {
  color: #b6f7d2;
  border-color: rgba(33, 208, 122, 0.42);
  background: rgba(33, 208, 122, 0.12);
  box-shadow: 0 0 16px rgba(33, 208, 122, 0.1);
}

.danger-action {
  color: #fff7f8;
  border-color: rgba(255, 93, 108, 0.42);
  background: linear-gradient(135deg, #d9364d, var(--danger));
  box-shadow: 0 12px 26px rgba(255, 93, 108, 0.22);
}

.muted {
  color: var(--text-muted);
}

.empty-state {
  padding: 30px 0;
  text-align: center;
  color: var(--text-muted);
}

button:disabled {
  opacity: 0.62;
  cursor: not-allowed;
}

@media (max-width: 760px) {
  .toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
