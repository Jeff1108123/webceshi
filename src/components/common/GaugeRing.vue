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
        background: `conic-gradient(${this.ringColor} ${degree}deg, rgba(226, 232, 240, 0.95) ${degree}deg 360deg)`,
        boxShadow: this.alarm ? '0 18px 32px rgba(239, 68, 68, 0.14)' : '0 18px 32px rgba(15, 123, 255, 0.12)'
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
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.18);
  padding: 20px;
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
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.98));
  display: grid;
  place-items: center;
  align-content: center;
  text-align: center;
  gap: 6px;
  padding: 18px;
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
  font-size: 42px;
  line-height: 1;
}

.gauge-unit {
  font-size: 15px;
}

.gauge-meta {
  font-size: 12px;
}
</style>
