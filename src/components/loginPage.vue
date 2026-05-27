<template>
  <AppShell title="医疗冷链运输箱监控系统">
    <section class="login-stage">
      <div class="access-brief">
        <span class="eyebrow">Cold Chain Command Access</span>
        <h1>医疗冷链运输箱监控系统</h1>
        <p>安全接入指挥中心，实时掌控运输箱状态、温湿度与告警态势。</p>
        <ul>
          <li>身份校验链路在线</li>
          <li>设备监控通道待授权</li>
          <li>超级管理员手机号 18800000000</li>
        </ul>
      </div>

      <section class="login-card">
        <div class="intro">
          <h2>登录系统</h2>
          <p>输入手机号获取验证码。超级管理员使用手机号 18800000000 登录。</p>
        </div>

        <form @submit.prevent="handleLogin">
          <label for="login-phone">手机号</label>
          <input
            id="login-phone"
            v-model.trim="phone"
            type="tel"
            inputmode="numeric"
            autocomplete="tel"
            maxlength="11"
            placeholder="请输入手机号"
          />

          <label for="login-code">验证码</label>
          <div class="code-row">
            <input
              id="login-code"
              v-model.trim="code"
              type="text"
              inputmode="numeric"
              autocomplete="one-time-code"
              maxlength="6"
              placeholder="请输入验证码"
            />
            <button type="button" :disabled="sendingCode || countdown > 0" @click="handleSendCode">
              {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
            </button>
          </div>

          <button type="submit" class="primary-btn" :disabled="submitting">
            {{ submitting ? '登录中...' : '登录' }}
          </button>
        </form>
      </section>
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
      window.alert(`验证码：${response.code}\n请手动输入验证码完成登录。`)
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
.login-stage {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 460px);
  gap: 28px;
  align-items: center;
  width: min(1080px, 100%);
  margin: 46px auto;
  padding: 8px;
}

.login-stage::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 30px;
  border: 1px solid rgba(34, 211, 238, 0.16);
  background:
    linear-gradient(135deg, rgba(34, 211, 238, 0.08), transparent 36%),
    linear-gradient(315deg, rgba(37, 99, 235, 0.12), transparent 42%);
  pointer-events: none;
}

.access-brief,
.login-card {
  position: relative;
  border: 1px solid rgba(125, 211, 252, 0.18);
  background: linear-gradient(145deg, rgba(15, 23, 42, 0.9), rgba(2, 8, 23, 0.78));
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.32), inset 0 1px 0 rgba(255, 255, 255, 0.06);
  backdrop-filter: blur(18px);
}

.access-brief {
  min-height: 430px;
  padding: 46px;
  border-radius: 28px;
  overflow: hidden;
}

.access-brief::after {
  content: '';
  position: absolute;
  right: -80px;
  bottom: -100px;
  width: 260px;
  height: 260px;
  border: 1px solid rgba(34, 211, 238, 0.16);
  border-radius: 50%;
  box-shadow: 0 0 70px rgba(34, 211, 238, 0.16);
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 12px;
  border: 1px solid rgba(34, 211, 238, 0.22);
  border-radius: 999px;
  color: #67e8f9;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  background: rgba(8, 145, 178, 0.1);
}

.eyebrow::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #22d3ee;
  box-shadow: 0 0 16px #22d3ee;
}

.access-brief h1 {
  max-width: 580px;
  margin: 28px 0 16px;
  color: #f8fafc;
  font-size: clamp(34px, 5vw, 58px);
  line-height: 1.08;
  letter-spacing: -0.04em;
}

.access-brief p {
  max-width: 560px;
  color: #94a3b8;
  font-size: 16px;
  line-height: 1.8;
}

.access-brief ul {
  display: grid;
  gap: 12px;
  margin: 32px 0 0;
  padding: 0;
  list-style: none;
}

.access-brief li {
  position: relative;
  padding-left: 22px;
  color: #cbd5e1;
  line-height: 1.6;
}

.access-brief li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10px;
  width: 9px;
  height: 2px;
  background: #22d3ee;
  box-shadow: 0 0 10px rgba(34, 211, 238, 0.86);
}

.login-card {
  width: 100%;
  padding: 30px;
  border-radius: 24px;
}

.login-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 28px;
  right: 28px;
  height: 2px;
  background: linear-gradient(90deg, transparent, #22d3ee, transparent);
  box-shadow: 0 0 18px rgba(34, 211, 238, 0.7);
}

.intro {
  margin-bottom: 24px;
}

.intro h2 {
  margin-bottom: 8px;
  color: #f8fafc;
  font-size: 28px;
  letter-spacing: -0.03em;
}

.intro p {
  color: #94a3b8;
  line-height: 1.7;
}

label {
  display: block;
  margin: 16px 0 8px;
  color: #cbd5e1;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

input {
  width: 100%;
  height: 48px;
  padding: 0 14px;
  border: 1px solid rgba(125, 211, 252, 0.2);
  border-radius: 12px;
  color: #e2e8f0;
  background: rgba(2, 6, 23, 0.68);
  outline: none;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}

input::placeholder {
  color: #64748b;
}

input:focus {
  border-color: rgba(34, 211, 238, 0.72);
  background: rgba(2, 6, 23, 0.86);
  box-shadow: 0 0 0 3px rgba(34, 211, 238, 0.12), 0 0 22px rgba(34, 211, 238, 0.14);
}

.code-row {
  display: grid;
  grid-template-columns: 1fr 126px;
  gap: 10px;
}

button {
  min-height: 46px;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, opacity 0.2s ease;
}

button:focus-visible {
  outline: 3px solid #f8fafc;
  outline-offset: 3px;
  box-shadow: 0 0 0 6px rgba(34, 211, 238, 0.42), 0 0 24px rgba(248, 250, 252, 0.28);
}

button:not(:disabled):hover {
  transform: translateY(-1px);
}

.code-row button {
  border: 1px solid rgba(34, 211, 238, 0.24);
  color: #67e8f9;
  font-weight: 700;
  background: rgba(8, 145, 178, 0.12);
}

.code-row button:not(:disabled):hover {
  box-shadow: 0 0 18px rgba(34, 211, 238, 0.16);
}

.primary-btn {
  width: 100%;
  height: 50px;
  margin-top: 20px;
  color: #00111c;
  font-weight: 800;
  letter-spacing: 0.08em;
  background: linear-gradient(135deg, #67e8f9, #22d3ee 44%, #38bdf8);
  box-shadow: 0 0 26px rgba(34, 211, 238, 0.26), 0 14px 28px rgba(14, 116, 144, 0.28);
}

.primary-btn:not(:disabled):hover {
  box-shadow: 0 0 34px rgba(34, 211, 238, 0.38), 0 18px 34px rgba(14, 116, 144, 0.34);
}

button:disabled {
  opacity: 0.58;
  cursor: not-allowed;
}

@media (max-width: 860px) {
  .login-stage {
    grid-template-columns: 1fr;
    margin: 24px auto;
  }

  .access-brief {
    min-height: auto;
    padding: 30px;
  }
}

@media (max-width: 560px) {
  .login-stage {
    padding: 0;
  }

  .access-brief,
  .login-card {
    padding: 22px;
    border-radius: 20px;
  }

  .code-row {
    grid-template-columns: 1fr;
  }
}
</style>
