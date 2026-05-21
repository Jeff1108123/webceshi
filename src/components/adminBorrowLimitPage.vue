<template>
  <AppShell title="借用限制管理">
    <section class="page-section panel default-panel">
      <div>
        <p class="eyebrow">Global Limit</p>
        <h2>全局默认借用上限</h2>
        <p>未单独配置的用户默认最多可同时借用 {{ defaultLimit || 3 }} 台设备。</p>
      </div>
      <label>
        默认上限
        <input v-model.number="defaultLimitForm" type="number" min="1" />
      </label>
      <button class="primary-btn" :disabled="loading" @click="saveDefaultLimit">保存全局上限</button>
    </section>

    <section class="page-section panel">
      <div class="toolbar">
        <input v-model.trim="keyword" placeholder="搜索姓名 / 手机号 / 机构" />
        <button :disabled="loading" @click="loadBorrowLimits">刷新</button>
      </div>

      <div v-if="loading" class="empty-state">加载中...</div>
      <div v-else-if="!filteredUsers.length" class="empty-state">暂无用户。</div>

      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>用户</th>
              <th>手机号</th>
              <th>机构</th>
              <th>角色</th>
              <th>当前借用</th>
              <th>覆盖上限</th>
              <th>有效上限</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredUsers" :key="item.userId">
              <td>{{ item.name || '--' }}</td>
              <td>{{ item.phone || '--' }}</td>
              <td>{{ item.organization || '--' }}</td>
              <td>{{ roleLabel(item.role) }}</td>
              <td>{{ item.currentBorrowCount }}</td>
              <td>
                <input
                  :value="limitForms[item.userId]"
                  class="limit-input"
                  type="number"
                  min="1"
                  :placeholder="String(defaultLimit || 3)"
                  @input="updateLimitForm(item.userId, $event.target.value)"
                />
              </td>
              <td>{{ item.effectiveBorrowLimit }}</td>
              <td class="actions">
                <button :disabled="loading" @click="saveUserLimit(item)">保存</button>
                <button class="ghost-btn" :disabled="loading || item.borrowLimitOverride === null" @click="clearUserLimit(item)">
                  清除覆盖
                </button>
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
import { fetchBorrowLimits, updateDefaultBorrowLimit, updateUserBorrowLimit } from '../api/medicalColdChain'

function toPositiveInteger(value) {
  const numberValue = Number(value)
  if (!Number.isInteger(numberValue) || numberValue < 1) return null
  return numberValue
}

export default {
  name: 'AdminBorrowLimitPage',
  components: {
    AppShell
  },
  data() {
    return {
      loading: false,
      defaultLimit: 3,
      defaultLimitForm: 3,
      users: [],
      limitForms: {},
      keyword: ''
    }
  },
  computed: {
    filteredUsers() {
      const normalizedKeyword = this.keyword.trim().toLowerCase()
      if (!normalizedKeyword) return this.users
      return this.users.filter(item => [item.name, item.phone, item.organization]
        .some(value => String(value || '').toLowerCase().includes(normalizedKeyword)))
    }
  },
  created() {
    this.loadBorrowLimits()
  },
  methods: {
    roleLabel(role) {
      return String(role || '').toUpperCase() === 'ADMIN' ? '超级管理员' : '普通用户'
    },
    updateLimitForm(userId, value) {
      this.$set(this.limitForms, userId, value ? Number(value) : null)
    },
    syncForms(payload) {
      this.defaultLimit = payload.defaultLimit || 3
      this.defaultLimitForm = this.defaultLimit
      this.users = Array.isArray(payload.users) ? payload.users : []
      const forms = {}
      this.users.forEach(item => {
        forms[item.userId] = item.borrowLimitOverride
      })
      this.limitForms = forms
    },
    async loadBorrowLimits() {
      this.loading = true
      try {
        const payload = await fetchBorrowLimits()
        this.syncForms(payload || {})
      } catch (error) {
        this.$message.error(error.message)
      } finally {
        this.loading = false
      }
    },
    async saveDefaultLimit() {
      const limit = toPositiveInteger(this.defaultLimitForm)
      if (!limit) {
        this.$message.error('全局借用上限必须是正整数')
        return
      }
      this.loading = true
      try {
        const payload = await updateDefaultBorrowLimit(limit)
        this.syncForms(payload || {})
        this.$message.success('全局借用上限已保存')
      } catch (error) {
        this.$message.error(error.message)
      } finally {
        this.loading = false
      }
    },
    async saveUserLimit(item) {
      const limit = toPositiveInteger(this.limitForms[item.userId])
      if (!limit) {
        this.$message.error('用户借用上限必须是正整数')
        return
      }
      this.loading = true
      try {
        const payload = await updateUserBorrowLimit(item.userId, limit)
        this.syncForms(payload || {})
        this.$message.success('用户借用上限已保存')
      } catch (error) {
        this.$message.error(error.message)
      } finally {
        this.loading = false
      }
    },
    async clearUserLimit(item) {
      this.loading = true
      try {
        const payload = await updateUserBorrowLimit(item.userId, null)
        this.syncForms(payload || {})
        this.$message.success('已清除用户覆盖上限')
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

.default-panel {
  display: grid;
  grid-template-columns: 1fr 160px 140px;
  gap: 14px;
  align-items: end;
  margin-bottom: 14px;
}

.eyebrow {
  margin-bottom: 6px;
  color: var(--primary);
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

h2 {
  margin-bottom: 8px;
  font-size: 20px;
}

p,
label {
  color: var(--text-muted);
}

label {
  display: grid;
  gap: 6px;
}

.toolbar {
  display: grid;
  grid-template-columns: 1fr 80px;
  gap: 10px;
  margin-bottom: 14px;
}

input,
button {
  height: 40px;
  border-radius: 10px;
}

input {
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

.primary-btn {
  color: #fff;
  background: var(--primary);
}

.ghost-btn {
  color: var(--text-muted);
  background: rgba(148, 163, 184, 0.12);
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

.limit-input {
  width: 110px;
}

.actions {
  display: flex;
  gap: 8px;
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

@media (max-width: 760px) {
  .default-panel,
  .toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
