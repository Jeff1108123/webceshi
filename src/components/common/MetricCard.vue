<template>
  <div class="metric-card" :class="tone">
    <p class="label">{{ label }}</p>
    <p class="value">{{ value }}</p>
    <p class="hint">{{ hint }}</p>
  </div>
</template>

<script>
export default {
  name: 'MetricCard',
  props: {
    label: {
      type: String,
      default: ''
    },
    value: {
      type: [String, Number],
      default: '-'
    },
    hint: {
      type: String,
      default: ''
    },
    tone: {
      type: String,
      default: 'primary'
    }
  }
}
</script>

<style scoped>
.metric-card {
  padding: 22px;
  border-radius: 22px;
  color: #fff;
  min-height: 140px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  box-shadow: 0 24px 42px rgba(15, 23, 42, 0.12);
}

.metric-card.primary {
  background: linear-gradient(135deg, #0f7bff, #38bdf8);
}

.metric-card.success {
  background: linear-gradient(135deg, #179c7d, #30c98b);
}

.metric-card.warning {
  background: linear-gradient(135deg, #7c3d02, #a85a05);
}

.metric-card.danger {
  background: linear-gradient(135deg, #7f1d1d, #b91c1c);
}

.metric-card.warning .label,
.metric-card.warning .hint,
.metric-card.danger .label,
.metric-card.danger .hint {
  color: #fff7ed;
  opacity: 1;
}

.metric-card.warning .value,
.metric-card.danger .value {
  color: #ffffff;
  text-shadow: 0 1px 2px rgba(15, 23, 42, 0.42);
}

.label {
  font-size: 14px;
  opacity: 0.82;
}

.value {
  font-size: 34px;
  font-weight: 700;
  margin: 14px 0;
}

.hint {
  font-size: 13px;
  line-height: 1.5;
  opacity: 0.86;
}
</style>
