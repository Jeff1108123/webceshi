<template>
  <div class="gauge-card">
    <div class="gauge-ring" :style="ringStyle">
      <div class="gauge-inner">
        <span class="gauge-title">{{ title }}</span>
        <strong class="gauge-value">{{ displayValue }}</strong>
        <span class="gauge-unit">{{ unit }}</span>
        <span class="gauge-meta">{{ metaText }}</span>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'GaugeRing',
  props: {
    title: {
      type: String,
      default: ''
    },
    value: {
      type: Number,
      default: 0
    },
    max: {
      type: Number,
      default: 100
    },
    unit: {
      type: String,
      default: ''
    },
    alarm: {
      type: Boolean,
      default: false
    },
    accent: {
      type: String,
      default: '#0f7bff'
    }
  },
  computed: {
    safeValue() {
      return Number.isFinite(this.value) ? this.value : 0
    },
    displayValue() {
      return Number.isFinite(this.value) ? this.value : '--'
    },
    progressRatio() {
      if (!this.max) return 0
      return Math.max(0, Math.min(1, this.safeValue / this.max))
    },
    ringColor() {
      return this.alarm ? '#ef4444' : this.accent
    },
    ringStyle() {
      const degree = Math.round(this.progressRatio * 360)
      return {
        background: `conic-gradient(${this.ringColor} ${degree}deg, rgba(21, 55, 93, 0.72) ${degree}deg 360deg)`,
        boxShadow: this.alarm ? '0 18px 32px rgba(255, 93, 108, 0.18)' : '0 18px 32px rgba(34, 211, 238, 0.14)'
      }
    },
    metaText() {
      return `上限参考 ${this.max}${this.unit}`
    }
  }
}
</script>

<style scoped>
.gauge-card {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 320px;
  border-radius: 24px;
  background:
    linear-gradient(145deg, rgba(14, 32, 59, 0.94), rgba(7, 18, 36, 0.88)),
    var(--surface);
  border: 1px solid var(--line);
  box-shadow: var(--card-shadow), inset 0 1px 0 rgba(255, 255, 255, 0.04);
  padding: 20px;
  backdrop-filter: blur(16px);
}

.gauge-ring {
  width: min(240px, 100%);
  aspect-ratio: 1;
  border-radius: 50%;
  padding: 18px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.gauge-inner {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: linear-gradient(180deg, rgba(8, 22, 43, 0.98), rgba(4, 14, 29, 0.98));
  border: 1px solid rgba(113, 206, 255, 0.16);
  display: grid;
  place-items: center;
  align-content: center;
  text-align: center;
  gap: 6px;
  padding: 18px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05), inset 0 0 28px rgba(34, 211, 238, 0.06);
}

.gauge-title,
.gauge-unit,
.gauge-meta {
  color: var(--text-muted);
}

.gauge-title {
  font-size: 15px;
  font-weight: 600;
}

.gauge-value {
  color: var(--text-strong);
  font-size: 42px;
  line-height: 1;
  text-shadow: 0 0 22px rgba(34, 211, 238, 0.18);
}

.gauge-unit {
  font-size: 15px;
}

.gauge-meta {
  font-size: 12px;
}
</style>
