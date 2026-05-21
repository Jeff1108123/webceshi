<template>
  <div class="shell">
    <header class="topbar">
      <div class="brand-block">
        <p class="eyebrow">Medical Cold Chain</p>
        <div class="title-row">
          <span class="system-dot" aria-hidden="true"></span>
          <h1>{{ title }}</h1>
        </div>
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
  position: sticky;
  top: 14px;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 20px;
  border: 1px solid rgba(34, 211, 238, 0.26);
  border-radius: 24px;
  background:
    linear-gradient(135deg, rgba(8, 18, 34, 0.94), rgba(15, 23, 42, 0.82)),
    rgba(15, 23, 42, 0.86);
  box-shadow:
    0 22px 48px rgba(0, 0, 0, 0.34),
    0 0 34px rgba(34, 211, 238, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(18px);
}

.brand-block {
  min-width: 0;
}

.eyebrow {
  margin-bottom: 8px;
  color: #67e8f9;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.24em;
  text-transform: uppercase;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.system-dot {
  position: relative;
  width: 11px;
  height: 11px;
  flex: 0 0 auto;
  border-radius: 999px;
  background: #22d3ee;
  box-shadow: 0 0 0 6px rgba(34, 211, 238, 0.12), 0 0 18px rgba(34, 211, 238, 0.78);
}

.system-dot::after {
  content: '';
  position: absolute;
  inset: -5px;
  border: 1px solid rgba(34, 211, 238, 0.24);
  border-radius: inherit;
}

h1 {
  color: #f8fafc;
  font-size: 26px;
  line-height: 1.25;
  text-shadow: 0 0 24px rgba(34, 211, 238, 0.18);
}

.account {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
  padding: 6px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.74);
  color: #94a3b8;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.06);
}

.account span,
.account strong {
  padding-left: 8px;
  font-size: 13px;
  white-space: nowrap;
}

.account strong {
  color: #e2e8f0;
}

.account button {
  border: 1px solid rgba(248, 113, 113, 0.36);
  border-radius: 999px;
  padding: 7px 14px;
  background: linear-gradient(135deg, rgba(220, 38, 38, 0.95), rgba(127, 29, 29, 0.92));
  color: #fee2e2;
  cursor: pointer;
  box-shadow: 0 10px 24px rgba(127, 29, 29, 0.28);
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.account button:hover {
  border-color: rgba(254, 202, 202, 0.72);
  box-shadow: 0 12px 28px rgba(220, 38, 38, 0.34);
  transform: translateY(-1px);
}

.nav {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  max-height: 132px;
  margin: 16px 0;
  padding: 2px 2px 6px;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: rgba(34, 211, 238, 0.45) rgba(15, 23, 42, 0.36);
}

.nav::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

.nav::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(34, 211, 238, 0.45);
}

.nav::-webkit-scrollbar-track {
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.36);
}

.nav-item {
  padding: 9px 15px;
  border: 1px solid rgba(34, 211, 238, 0.16);
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.66);
  color: #a7b7cc;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
  transition: transform 0.18s ease, color 0.18s ease, border-color 0.18s ease, background 0.18s ease;
}

.nav-item.active,
.nav-item:hover {
  color: #e0faff;
  border-color: rgba(34, 211, 238, 0.58);
  background: linear-gradient(135deg, rgba(8, 145, 178, 0.32), rgba(37, 99, 235, 0.22));
  box-shadow: 0 0 22px rgba(34, 211, 238, 0.14), inset 0 1px 0 rgba(255, 255, 255, 0.08);
  transform: translateY(-1px);
}

.content {
  margin-top: 14px;
}

@media (max-width: 700px) {
  .topbar {
    position: static;
    align-items: flex-start;
    flex-direction: column;
    padding: 16px;
  }

  .account {
    align-items: flex-start;
    flex-direction: column;
    width: 100%;
    gap: 6px;
    border-radius: 18px;
  }

  .account span,
  .account strong {
    padding-left: 4px;
  }

  .account button {
    width: 100%;
  }

  .nav {
    max-height: 190px;
  }
}
</style>
