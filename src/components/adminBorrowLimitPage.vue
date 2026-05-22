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
        <input v-model.number="defaultLimitForm" aria-label="全局默认借用上限" type="number" min="1" />
      </label>
      <button class="primary-btn" :disabled="loading" @click="saveDefaultLimit">保存全局上限</button>
    </section>

    <section class="page-section panel">
      <div class="toolbar">
        <input v-model.trim="keyword" aria-label="搜索姓名、手机号或机构" placeholder="搜索姓名 / 手机号 / 机构" />
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
                  :aria-label="`${item.name || item.phone || '用户'}的借用上限`"
                  @input="updateLimitForm(item.userId, $event.target.value)"
                />
              </td>
              <td>{{ item.effectiveBorrowLimit }}</td>
              <td class="actions">
                <button :disabled="loading" @click="saveUserLimit(item)">保存</button>
                <button class="ghost-btn" :disabled="loading || item.borrowLimitOverride === null" @click="clearUserLimit(item)">
                  清除覆盖
                </button>
                <button
                  class="danger-btn"
                  :disabled="loading || isAdminRole(item.role)"
                  :title="isAdminRole(item.role) ? '不能删除超级管理员' : '删除用户'"
                  @click="deleteUser(item)"
                >
                  删除用户
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
import {
  deleteAdminUser,
  fetchBorrowLimits,
  updateDefaultBorrowLimit,
  updateUserBorrowLimit
} from '../api/medicalColdChain'

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
    isAdminRole(role) {
      return String(role || '').toUpperCase() === 'ADMIN'
    },
    roleLabel(role) {
      return this.isAdminRole(role) ? '超级管理员' : '普通用户'
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
    },
    async deleteUser(item) {
      if (this.isAdminRole(item.role)) {
        this.$message.error('不能删除超级管理员')
        return
      }
      if (Number(item.currentBorrowCount || 0) > 0) {
        this.$message.error('该用户仍有在用设备，请先强制归还后再删除')
        return
      }
      const displayName = item.name || item.phone || '该用户'
      if (!window.confirm(`确定删除 ${displayName} 吗？此操作不可撤销。`)) {
        return
      }
      this.loading = true
      try {
        const payload = await deleteAdminUser(item.userId)
        this.syncForms(payload || {})
        this.$message.success('用户已删除')
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
  color: #67e8f9;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

h2 {
  margin-bottom: 8px;
  color: var(--text-strong);
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
  border-radius: 12px;
}

input {
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

input:focus {
  background: rgba(8, 22, 43, 0.9);
  border-color: rgba(34, 211, 238, 0.72);
  box-shadow: 0 0 0 3px rgba(34, 211, 238, 0.12), 0 0 22px rgba(34, 211, 238, 0.12);
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

.primary-btn {
  color: #f8fbff;
  border-color: rgba(34, 211, 238, 0.5);
  background: linear-gradient(135deg, var(--primary), var(--cyan));
  box-shadow: 0 12px 26px rgba(47, 140, 255, 0.24), 0 0 18px rgba(34, 211, 238, 0.18);
}

.ghost-btn {
  color: var(--text-main);
  border-color: var(--line);
  background: rgba(10, 27, 50, 0.62);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.danger-btn {
  color: #ffe7e7;
  border-color: rgba(248, 113, 113, 0.46);
  background: linear-gradient(135deg, rgba(185, 28, 28, 0.34), rgba(127, 29, 29, 0.26));
  box-shadow: 0 10px 24px rgba(248, 113, 113, 0.1), inset 0 1px 0 rgba(255, 255, 255, 0.05);
}

.danger-btn:hover:not(:disabled) {
  border-color: rgba(248, 113, 113, 0.72);
  box-shadow: 0 12px 28px rgba(248, 113, 113, 0.16);
}

.table-wrap {
  overflow: auto;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: rgba(5, 15, 31, 0.42);
}

table {
  width: 100%;
  min-width: 1100px;
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
  opacity: 0.62;
  cursor: not-allowed;
}

@media (max-width: 760px) {
  .default-panel,
  .toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
