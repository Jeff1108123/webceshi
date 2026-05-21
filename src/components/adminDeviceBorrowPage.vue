<template>
  <AppShell title="设备借用总览">
    <section class="page-section panel">
      <div class="toolbar">
        <input
          v-model.trim="filters.keyword"
          placeholder="搜索设备 / 借用人 / 手机号"
          @keyup.enter="loadRecords"
        />
        <select v-model="filters.status" @change="loadRecords">
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
    status: String(raw.status || (raw.returnTime ? 'RETURNED' : 'BORROWED')).toUpperCase()
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
  border-radius: 10px;
}

input,
select {
  padding: 0 12px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: #fff;
}

button {
  border: none;
  color: var(--primary-dark);
  background: rgba(15, 123, 255, 0.08);
  cursor: pointer;
}

.table-wrap {
  overflow: auto;
}

table {
  width: 100%;
  min-width: 1100px;
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

.status {
  display: inline-flex;
  padding: 6px 10px;
  border-radius: 999px;
  color: #b45309;
  background: rgba(245, 158, 11, 0.12);
}

.status.returned {
  color: var(--success);
  background: rgba(31, 157, 102, 0.12);
}

.danger-action {
  color: #fff;
  background: var(--danger);
}

.muted {
  color: var(--text-muted);
}

.empty-state {
  padding: 30px 0;
  text-align: center;
  color: var(--text-muted);
}

@media (max-width: 760px) {
  .toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
