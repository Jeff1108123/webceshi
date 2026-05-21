<template>
  <article class="trend-card">
    <div class="trend-head">
      <div class="trend-copy">
        <h4>{{ title }}</h4>
        <p>{{ thresholdText }}</p>
      </div>
      <div class="trend-stats">
        <div class="stat-pill">
          <label>最新</label>
          <strong>{{ formatMetric(latestValue) }}</strong>
        </div>
        <div class="stat-pill">
          <label>最低</label>
          <strong>{{ formatMetric(minValue) }}</strong>
        </div>
        <div class="stat-pill">
          <label>最高</label>
          <strong>{{ formatMetric(maxValue) }}</strong>
        </div>
      </div>
    </div>

    <div v-if="points.length" class="chart-shell">
      <svg class="trend-svg" viewBox="0 0 720 260" preserveAspectRatio="none">
        <g>
          <line
            v-for="gridLine in gridLines"
            :key="gridLine.key"
            class="grid-line"
            :x1="padding.left"
            :y1="gridLine.y"
            :x2="width - padding.right"
            :y2="gridLine.y"
          />
        </g>

        <g>
          <line
            v-for="line in thresholdLines"
            :key="line.key"
            class="threshold-line"
            :x1="padding.left"
            :y1="line.y"
            :x2="width - padding.right"
            :y2="line.y"
            :style="{ stroke: line.color }"
          />
        </g>

        <path
          class="trend-line"
          :d="smoothPath"
          :style="{ stroke: color }"
        />

        <circle
          v-if="lastPoint"
          :cx="lastPoint.x"
          :cy="lastPoint.y"
          r="4.5"
          :style="{ fill: color }"
        />

        <g>
          <text
            v-for="tick in xTicks"
            :key="tick.key"
            class="axis-text"
            :x="tick.x"
            :y="height - 16"
            :text-anchor="tick.anchor"
          >
            {{ tick.label }}
          </text>
        </g>
      </svg>
    </div>

    <div v-else class="empty-state">暂无历史数据</div>
  </article>
</template>

<script>
export default {
  name: 'TrendChartCard',
  props: {
    title: {
      type: String,
      default: ''
    },
    unit: {
      type: String,
      default: ''
    },
    labels: {
      type: Array,
      default: () => []
    },
    values: {
      type: Array,
      default: () => []
    },
    color: {
      type: String,
      default: '#0f7bff'
    },
    thresholdMin: {
      type: Number,
      default: null
    },
    thresholdMax: {
      type: Number,
      default: null
    }
  },
  data() {
    return {
      width: 720,
      height: 260,
      padding: {
        top: 20,
        right: 28,
        bottom: 48,
        left: 28
      }
    }
  },
  computed: {
    points() {
      return this.values
        .map((value, index) => ({ value: Number(value), label: this.labels[index] || '' }))
        .filter(item => Number.isFinite(item.value))
    },
    minValue() {
      if (!this.points.length) return null
      return Math.min(...this.points.map(item => item.value))
    },
    maxValue() {
      if (!this.points.length) return null
      return Math.max(...this.points.map(item => item.value))
    },
    latestValue() {
      return this.points.length ? this.points[this.points.length - 1].value : null
    },
    scaleMin() {
      const candidates = [this.minValue, this.thresholdMin, this.thresholdMax].filter(Number.isFinite)
      if (!candidates.length) return 0
      const rawMin = Math.min(...candidates)
      const rawMax = Math.max(...candidates)
      const padding = Math.max((rawMax - rawMin) * 0.12, 1)
      return rawMin - padding
    },
    scaleMax() {
      const candidates = [this.maxValue, this.thresholdMin, this.thresholdMax].filter(Number.isFinite)
      if (!candidates.length) return 1
      const rawMin = Math.min(...candidates)
      const rawMax = Math.max(...candidates)
      const padding = Math.max((rawMax - rawMin) * 0.12, 1)
      return rawMax + padding
    },
    pointCoordinates() {
      if (!this.points.length) return []
      const chartWidth = this.width - this.padding.left - this.padding.right
      const chartHeight = this.height - this.padding.top - this.padding.bottom
      const denominator = Math.max(this.points.length - 1, 1)
      const valueRange = Math.max(this.scaleMax - this.scaleMin, 1)

      return this.points.map((item, index) => {
        const x = this.padding.left + (chartWidth * index) / denominator
        const y = this.padding.top + chartHeight - ((item.value - this.scaleMin) / valueRange) * chartHeight
        return {
          x,
          y,
          value: item.value,
          label: item.label
        }
      })
    },
    smoothPath() {
      if (!this.pointCoordinates.length) return ''
      if (this.pointCoordinates.length === 1) {
        const point = this.pointCoordinates[0]
        return `M ${point.x} ${point.y}`
      }

      return this.pointCoordinates.reduce((path, point, index, points) => {
        if (index === 0) return `M ${point.x} ${point.y}`
        const previous = points[index - 1]
        const controlOffset = (point.x - previous.x) * 0.42
        return `${path} C ${previous.x + controlOffset} ${previous.y}, ${point.x - controlOffset} ${point.y}, ${point.x} ${point.y}`
      }, '')
    },
    lastPoint() {
      return this.pointCoordinates[this.pointCoordinates.length - 1] || null
    },
    gridLines() {
      const chartHeight = this.height - this.padding.top - this.padding.bottom
      return Array.from({ length: 4 }, (_, index) => ({
        key: `grid-${index}`,
        y: this.padding.top + (chartHeight * index) / 3
      }))
    },
    thresholdLines() {
      const lines = []
      if (Number.isFinite(this.thresholdMin)) {
        lines.push({
          key: 'min',
          value: this.thresholdMin,
          color: 'rgba(15, 123, 255, 0.38)'
        })
      }
      if (Number.isFinite(this.thresholdMax)) {
        lines.push({
          key: 'max',
          value: this.thresholdMax,
          color: 'rgba(214, 69, 69, 0.42)'
        })
      }

      const chartHeight = this.height - this.padding.top - this.padding.bottom
      const valueRange = Math.max(this.scaleMax - this.scaleMin, 1)

      return lines.map(line => ({
        ...line,
        y: this.padding.top + chartHeight - ((line.value - this.scaleMin) / valueRange) * chartHeight
      }))
    },
    xTicks() {
      if (!this.pointCoordinates.length) return []
      const lastIndex = this.pointCoordinates.length - 1
      const desiredTickCount = 4
      const step = Math.max(1, Math.ceil(lastIndex / Math.max(desiredTickCount - 1, 1)))
      const indexes = [0]

      for (let index = step; index < lastIndex; index += step) {
        indexes.push(index)
      }

      if (!indexes.includes(lastIndex)) {
        indexes.push(lastIndex)
      }

      return indexes.map((pointIndex, index) => {
        const point = this.pointCoordinates[pointIndex]
        return {
          key: `tick-${index}-${pointIndex}`,
          x: point.x,
          label: point.label,
          anchor: pointIndex === 0 ? 'start' : (pointIndex === lastIndex ? 'end' : 'middle')
        }
      })
    },
    thresholdText() {
      if (Number.isFinite(this.thresholdMin) && Number.isFinite(this.thresholdMax)) {
        return `参考阈值 ${this.thresholdMin} ~ ${this.thresholdMax} ${this.unit}`
      }
      if (Number.isFinite(this.thresholdMax)) {
        return `参考阈值 ≤ ${this.thresholdMax} ${this.unit}`
      }
      return '暂无阈值参考'
    }
  },
  methods: {
    formatMetric(value) {
      if (!Number.isFinite(value)) return '--'
      return `${value.toFixed(1)}${this.unit}`
    }
  }
}
</script>

<style scoped>
.trend-card {
  padding: 20px;
  border-radius: 22px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(255, 255, 255, 0.92);
  display: grid;
  gap: 16px;
}

.trend-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.trend-copy {
  display: grid;
  gap: 8px;
}

.trend-head h4 {
  font-size: 20px;
  margin: 0;
}

.trend-head p,
.trend-stats,
.stat-pill label {
  color: var(--text-muted);
}

.trend-head p {
  margin: 0;
}

.trend-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(84px, 1fr));
  gap: 8px;
  min-width: 250px;
}

.stat-pill {
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(248, 250, 252, 0.9);
  display: grid;
  gap: 4px;
  text-align: right;
}

.stat-pill label {
  font-size: 12px;
}

.stat-pill strong {
  font-size: 14px;
  color: #0f172a;
}

.chart-shell {
  width: 100%;
  min-height: 260px;
}

.trend-svg {
  width: 100%;
  height: 260px;
  display: block;
}

.grid-line {
  stroke: rgba(148, 163, 184, 0.18);
  stroke-width: 1;
}

.threshold-line {
  stroke-width: 1.5;
  stroke-dasharray: 6 6;
}

.trend-line {
  fill: none;
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.axis-text {
  fill: #64748b;
  font-size: 12px;
}

.empty-state {
  color: var(--text-muted);
  text-align: center;
  padding: 48px 0;
}

@media (max-width: 900px) {
  .trend-head {
    flex-direction: column;
  }

  .trend-stats {
    width: 100%;
    min-width: 0;
  }

  .stat-pill {
    text-align: left;
  }
}

@media (max-width: 560px) {
  .trend-stats {
    grid-template-columns: 1fr;
  }
}
</style>
