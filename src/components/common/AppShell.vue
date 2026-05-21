<template>
  <div class="shell">
    <header class="topbar">
      <div>
        <p class="eyebrow">Medical Cold Chain</p>
        <h1>{{ title }}</h1>
      </div>

      <div v-if="authStore.isLoggedIn" class="account">
        <span>{{ authStore.isSuperAdmin ? '超级管理员' : (authStore.userName || '用户') }}</span>
        <strong>{{ authStore.userPhone }}</strong>
        <button type="button" @click="handleLogout">退出</button>
      </div>
    </header>

    <nav v-if="authStore.isLoggedIn" class="nav">
      <router-link
        v-for="item in visibleNavigationItems"
        :key="item.path"
        :to="item.path"
        class="nav-item"
        :class="{ active: $route.path === item.path }"
      >
        {{ item.label }}
      </router-link>
    </nav>

    <main class="content">
      <slot />
    </main>
  </div>
</template>

<script>
import { navigationItems } from '../../config/navigation'
import { useAuthStore } from '../../store/authStore'
import { useDeviceStore } from '../../store/deviceStore'

export default {
  name: 'AppShell',
  props: {
    title: {
      type: String,
      default: '医疗冷链运输箱监控系统'
    }
  },
  data() {
    return {
      authStore: useAuthStore(),
      deviceStore: useDeviceStore(),
      navigationItems
    }
  },
  computed: {
    visibleNavigationItems() {
      if (this.authStore.isSuperAdmin) {
        return this.navigationItems.filter(item => item.requiresSuperAdmin)
      }
      return this.navigationItems.filter(item => !item.requiresSuperAdmin)
    }
  },
  methods: {
    handleLogout() {
      this.deviceStore.clear()
      this.authStore.logout()
      this.$router.replace('/')
      this.$message.success('已退出登录')
    }
  }
}
</script>

<style scoped>
.shell {
  width: min(var(--page-max-width), calc(100vw - 28px));
  margin: 18px auto 36px;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 20px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.88);
}

.eyebrow {
  margin-bottom: 6px;
  color: var(--primary);
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

h1 {
  font-size: 26px;
  line-height: 1.25;
}

.account {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--text-muted);
}

.account strong {
  color: var(--text-main);
}

.account button {
  border: none;
  border-radius: 999px;
  padding: 8px 14px;
  background: rgba(220, 38, 38, 0.1);
  color: #b91c1c;
  cursor: pointer;
}

.nav {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 14px 0;
}

.nav-item {
  padding: 9px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.78);
  color: var(--text-muted);
  border: 1px solid rgba(148, 163, 184, 0.22);
}

.nav-item.active,
.nav-item:hover {
  color: var(--primary-dark);
  border-color: rgba(15, 123, 255, 0.35);
  background: rgba(15, 123, 255, 0.08);
}

.content {
  margin-top: 14px;
}

@media (max-width: 700px) {
  .topbar,
  .account {
    align-items: flex-start;
    flex-direction: column;
  }

  .account {
    gap: 6px;
  }
}
</style>
