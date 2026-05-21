<template>
  <AppShell title="医疗冷链运输箱监控系统">
    <section class="login-card">
      <div class="intro">
        <h2>登录系统</h2>
        <p>输入手机号获取验证码。超级管理员使用手机号 18800000000 登录。</p>
      </div>

      <label>手机号</label>
      <input v-model.trim="phone" maxlength="11" placeholder="请输入手机号" />

      <label>验证码</label>
      <div class="code-row">
        <input v-model.trim="code" maxlength="6" placeholder="请输入验证码" />
        <button :disabled="sendingCode || countdown > 0" @click="handleSendCode">
          {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
        </button>
      </div>

      <p v-if="demoCode" class="demo-code">验证码：{{ demoCode }}</p>

      <button class="primary-btn" :disabled="submitting" @click="handleLogin">
        {{ submitting ? '登录中...' : '登录' }}
      </button>
    </section>
  </AppShell>
</template>

<script>
import AppShell from './common/AppShell.vue'
import { login, sendCode } from '../api/medicalColdChain'
import { useAuthStore } from '../store/authStore'
import { useDeviceStore } from '../store/deviceStore'
import { getDefaultHomePath } from '../utils/session'

export default {
  name: 'LoginPage',
  components: {
    AppShell
  },
  data() {
    return {
      authStore: useAuthStore(),
      deviceStore: useDeviceStore(),
      phone: '',
      code: '',
      demoCode: '',
      countdown: 0,
      countdownTimer: null,
      sendingCode: false,
      submitting: false
    }
  },
  beforeDestroy() {
    if (this.countdownTimer) clearInterval(this.countdownTimer)
  },
  methods: {
    validatePhone() {
      return /^1[3-9]\d{9}$/.test(this.phone)
    },
    async requestVerificationCode(checkOnly) {
      const response = await sendCode(this.phone, { checkOnly })
      this.demoCode = response.code
      this.code = response.code
      this.startCountdown()
    },
    async handleSendCode() {
      if (!this.validatePhone()) {
        this.$message.error('请输入正确的手机号')
        return
      }

      this.sendingCode = true
      try {
        await this.requestVerificationCode(true)
        this.$message.success('验证码已获取')
      } catch (error) {
        if (error.message === 'NEW_USER') {
          const confirmed = window.confirm('该手机号尚未注册，是否继续注册？')
          if (!confirmed) return
          await this.requestVerificationCode(false)
          this.$message.success('注册成功，验证码已获取')
          return
        }
        this.$message.error(error.message)
      } finally {
        this.sendingCode = false
      }
    },
    startCountdown() {
      this.countdown = 60
      if (this.countdownTimer) clearInterval(this.countdownTimer)
      this.countdownTimer = setInterval(() => {
        this.countdown -= 1
        if (this.countdown <= 0) {
          clearInterval(this.countdownTimer)
          this.countdownTimer = null
        }
      }, 1000)
    },
    async handleLogin() {
      if (!this.validatePhone()) {
        this.$message.error('请输入正确的手机号')
        return
      }
      if (!/^\d{6}$/.test(this.code)) {
        this.$message.error('请输入 6 位验证码')
        return
      }

      this.submitting = true
      try {
        const result = await login(this.phone, this.code)
        this.authStore.setSession(result)
        await this.deviceStore.refreshAll()
        this.$message.success('登录成功')
        this.$router.push(getDefaultHomePath(result.user))
      } catch (error) {
        this.$message.error(error.message)
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style scoped>
.login-card {
  width: min(460px, 100%);
  margin: 42px auto;
  padding: 28px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.2);
}

.intro {
  margin-bottom: 22px;
}

.intro h2 {
  margin-bottom: 8px;
  font-size: 26px;
}

.intro p {
  color: var(--text-muted);
  line-height: 1.7;
}

label {
  display: block;
  margin: 14px 0 8px;
  font-weight: 600;
}

input {
  width: 100%;
  height: 46px;
  padding: 0 14px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 12px;
  background: #fff;
}

.code-row {
  display: grid;
  grid-template-columns: 1fr 120px;
  gap: 10px;
}

button {
  border: none;
  border-radius: 12px;
  cursor: pointer;
}

.code-row button {
  background: rgba(15, 123, 255, 0.08);
  color: var(--primary-dark);
}

.primary-btn {
  width: 100%;
  height: 48px;
  margin-top: 18px;
  color: #fff;
  font-weight: 700;
  background: linear-gradient(135deg, #0f7bff, #1d4ed8);
}

button:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.demo-code {
  margin-top: 12px;
  color: var(--success);
}

@media (max-width: 560px) {
  .login-card {
    margin: 20px auto;
    padding: 20px;
  }

  .code-row {
    grid-template-columns: 1fr;
  }
}
</style>
