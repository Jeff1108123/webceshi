<template>
  <article class="single-metric-chart" @wheel="handleWheel">
    <header class="chart-head">
      <div>
        <p class="eyebrow">历史单项趋势</p>
        <h4>{{ metricLabel }}</h4>
      </div>
      <div class="granularity-pill" aria-label="当前时间密度">
        {{ granularityLabel }}
      </div>
    </header>

    <div v-if="validPoints.length" class="chart-shell">
      <svg
        class="trend-svg"
        :viewBox="`0 0 ${width} ${height}`"
        preserveAspectRatio="none"
        role="img"
        :aria-label="chartAriaLabel"
      >
        <title>{{ chartTitle }}</title>
        <desc>{{ chartDescription }}</desc>
        <defs>
          <linearGradient :id="areaGradientId" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" :stop-color="metricColor" stop-opacity="0.34" />
            <stop offset="72%" :stop-color="metricColor" stop-opacity="0.1" />
            <stop offset="100%" :stop-color="metricColor" stop-opacity="0" />
          </linearGradient>
          <filter :id="glowFilterId" x="-25%" y="-25%" width="150%" height="150%">
            <feGaussianBlur stdDeviation="3" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        <rect class="plot-backdrop" :x="padding.left" :y="padding.top" :width="chartWidth" :height="chartHeight" rx="14" />

        <rect
          v-if="safeBand"
          class="safe-band"
          :x="padding.left"
          :y="safeBand.y"
          :width="chartWidth"
          :height="safeBand.height"
        />

        <g class="grid-group" aria-hidden="true">
          <line
            v-for="tick in yTicks"
            :key="`horizontal-${tick.key}`"
            class="grid-line"
            :x1="padding.left"
            :y1="tick.y"
            :x2="width - padding.right"
            :y2="tick.y"
          />
          <line
            v-for="tick in xTicks"
            :key="`vertical-${tick.key}`"
            class="grid-line grid-line-vertical"
            :x1="tick.x"
            :y1="padding.top"
            :x2="tick.x"
            :y2="height - padding.bottom"
          />
        </g>

        <g class="axis-group" aria-hidden="true">
          <line class="axis-line" :x1="padding.left" :y1="padding.top" :x2="padding.left" :y2="height - padding.bottom" />
          <line class="axis-line" :x1="padding.left" :y1="height - padding.bottom" :x2="width - padding.right" :y2="height - padding.bottom" />
          <g v-for="tick in yTicks" :key="`y-${tick.key}`">
            <text class="axis-text axis-text-y" :x="padding.left - 12" :y="tick.y + 4" text-anchor="end">
              {{ tick.label }}
            </text>
          </g>
          <text
            v-for="tick in xTicks"
            :key="`x-${tick.key}`"
            class="axis-text axis-text-x"
            :x="tick.x"
            :y="height - 26"
            :text-anchor="tick.anchor"
          >
            {{ tick.label }}
          </text>
        </g>

        <g class="threshold-group">
          <line
            v-for="line in thresholdLines"
            :key="line.key"
            class="threshold-line"
            :class="`threshold-line-${line.type}`"
            :x1="padding.left"
            :y1="line.y"
            :x2="width - padding.right"
            :y2="line.y"
          />
          <text
            v-for="line in thresholdLines"
            :key="`${line.key}-label`"
            class="threshold-label"
            :x="width - padding.right - 6"
            :y="line.labelY"
            text-anchor="end"
          >
            {{ line.label }}
          </text>
        </g>

        <path v-if="areaPath" class="trend-area" :d="areaPath" :fill="`url(#${areaGradientId})`" />
        <path
          class="trend-line"
          :d="smoothPath"
          :style="{ stroke: metricColor, filter: `url(#${glowFilterId})` }"
        />

        <g class="marker-group">
          <g v-for="marker in markers" :key="marker.key" class="marker">
            <circle class="marker-halo" :cx="marker.x" :cy="marker.y" r="8" :style="{ stroke: marker.color }" />
            <circle class="marker-dot" :cx="marker.x" :cy="marker.y" r="4.5" :style="{ fill: marker.color }" />
            <text class="marker-label" :x="marker.labelX" :y="marker.labelY" :text-anchor="marker.anchor">
              {{ marker.label }} {{ formatValue(marker.value) }}
            </text>
          </g>
        </g>
      </svg>
    </div>

    <div v-else class="empty-state">暂无{{ metricLabel }}历史数据</div>
  </article>
</template>

<script>
export default {
  name: 'SingleMetricTrendChart',
  props: {
    metric: {
      type: Object,
      default: () => ({})
    },
    points: {
      type: Array,
      default: () => []
    },
    granularityMinutes: {
      type: Number,
      default: 60
    }
  },
  data() {
    return {
      width: 820,
      height: 360,
      padding: {
        top: 34,
        right: 34,
        bottom: 62,
        left: 72
      }
    }
  },
  computed: {
    metricKey() {
      return this.metric.key || this.metric.field || 'metric'
    },
    metricLabel() {
      return this.metric.label || '指标'
    },
    metricUnit() {
      return this.metric.unit || ''
    },
    metricColor() {
      return this.metric.color || '#22d3ee'
    },
    metricField() {
      return this.metric.field || this.metric.key || 'value'
    },
    thresholdMin() {
      return this.normalizeThreshold(this.metric.thresholdMin)
    },
    thresholdMax() {
      return this.normalizeThreshold(this.metric.thresholdMax)
    },
    isLightMetric() {
      const text = `${this.metricKey} ${this.metricField} ${this.metricLabel} ${this.metricUnit}`.toLowerCase()
      return text.includes('light') || text.includes('lux') || text.includes('illumination') || text.includes('光')
    },
    chartWidth() {
      return this.width - this.padding.left - this.padding.right
    },
    chartHeight() {
      return this.height - this.padding.top - this.padding.bottom
    },
    validPoints() {
      return this.points
        .map((point, index) => {
          const rawValue = point && Object.prototype.hasOwnProperty.call(point, this.metricField)
            ? point[this.metricField]
            : (point && Object.prototype.hasOwnProperty.call(point, 'value') ? point.value : point)
          const value = Number(rawValue)
          return {
            sourceIndex: index,
            value,
            label: this.resolvePointLabel(point, index)
          }
        })
        .filter(point => Number.isFinite(point.value))
    },
    minPoint() {
      if (!this.validPoints.length) return null
      return this.validPoints.reduce((min, point) => (point.value < min.value ? point : min), this.validPoints[0])
    },
    maxPoint() {
      if (!this.validPoints.length) return null
      return this.validPoints.reduce((max, point) => (point.value > max.value ? point : max), this.validPoints[0])
    },
    latestPoint() {
      return this.validPoints[this.validPoints.length - 1] || null
    },
    scale() {
      const dataValues = this.validPoints.map(point => point.value)
      const thresholdValues = [this.thresholdMin, this.thresholdMax].filter(Number.isFinite)
      const candidates = dataValues.concat(thresholdValues)

      if (!candidates.length) {
        return { min: 0, max: 1 }
      }

      let rawMin = Math.min(...candidates)
      let rawMax = Math.max(...candidates)

      if (rawMin === rawMax) {
        const offset = Math.max(Math.abs(rawMin) * 0.08, 1)
        rawMin -= offset
        rawMax += offset
      }

      const padding = Math.max((rawMax - rawMin) * 0.12, 1)
      return {
        min: rawMin - padding,
        max: rawMax + padding
      }
    },
    coordinates() {
      const denominator = Math.max(this.validPoints.length - 1, 1)
      return this.validPoints.map((point, index) => ({
        ...point,
        x: this.padding.left + (this.chartWidth * index) / denominator,
        y: this.valueToY(point.value)
      }))
    },
    smoothPath() {
      return this.toSmoothPath(this.coordinates)
    },
    areaPath() {
      if (!this.coordinates.length) return ''
      const baseline = this.height - this.padding.bottom
      const linePath = this.toSmoothPath(this.coordinates)
      const first = this.coordinates[0]
      const last = this.coordinates[this.coordinates.length - 1]
      return `${linePath} L ${last.x} ${baseline} L ${first.x} ${baseline} Z`
    },
    yTicks() {
      const tickCount = 5
      return Array.from({ length: tickCount }, (_, index) => {
        const ratio = index / (tickCount - 1)
        const value = this.scale.max - (this.scale.max - this.scale.min) * ratio
        return {
          key: `y-${index}`,
          value,
          y: this.valueToY(value),
          label: this.formatValue(value)
        }
      })
    },
    xTicks() {
      if (!this.coordinates.length) return []
      const lastIndex = this.coordinates.length - 1
      const desiredTickCount = Math.min(6, this.coordinates.length)
      const step = Math.max(1, Math.ceil(lastIndex / Math.max(desiredTickCount - 1, 1)))
      const indexes = [0]

      for (let index = step; index < lastIndex; index += step) {
        indexes.push(index)
      }

      if (!indexes.includes(lastIndex)) {
        indexes.push(lastIndex)
      }

      return indexes.map((pointIndex, index) => {
        const point = this.coordinates[pointIndex]
        return {
          key: `x-${index}-${pointIndex}`,
          x: point.x,
          label: point.label,
          anchor: pointIndex === 0 ? 'start' : (pointIndex === lastIndex ? 'end' : 'middle')
        }
      })
    },
    safeBand() {
      if (this.isLightMetric || !Number.isFinite(this.thresholdMin) || !Number.isFinite(this.thresholdMax)) {
        return null
      }
      const top = this.valueToY(this.thresholdMax)
      const bottom = this.valueToY(this.thresholdMin)
      return {
        y: Math.min(top, bottom),
        height: Math.abs(bottom - top)
      }
    },
    thresholdLines() {
      const lines = []

      if (!this.isLightMetric && Number.isFinite(this.thresholdMin)) {
        lines.push({ key: 'threshold-min', type: 'min', value: this.thresholdMin, label: `下限 ${this.formatValue(this.thresholdMin)}` })
      }

      if (Number.isFinite(this.thresholdMax)) {
        lines.push({ key: 'threshold-max', type: 'max', value: this.thresholdMax, label: `上限 ${this.formatValue(this.thresholdMax)}` })
      }

      return lines.map(line => {
        const y = this.valueToY(line.value)
        return {
          ...line,
          y,
          labelY: Math.max(this.padding.top + 14, Math.min(this.height - this.padding.bottom - 8, y - 7))
        }
      })
    },
    markers() {
      const markerMap = []
      const addMarker = (key, point, label, color) => {
        if (!point) return
        const coordinate = this.coordinates.find(item => item.sourceIndex === point.sourceIndex)
        if (!coordinate) return
        const duplicate = markerMap.find(item => Math.abs(item.x - coordinate.x) < 0.1 && Math.abs(item.y - coordinate.y) < 0.1)
        if (duplicate) {
          duplicate.label = `${duplicate.label}/${label}`
          return
        }
        const nearRight = coordinate.x > this.width - this.padding.right - 88
        const nearTop = coordinate.y < this.padding.top + 26
        markerMap.push({
          key,
          x: coordinate.x,
          y: coordinate.y,
          value: coordinate.value,
          label,
          color,
          labelX: nearRight ? coordinate.x - 12 : coordinate.x + 12,
          labelY: nearTop ? coordinate.y + 24 : coordinate.y - 12,
          anchor: nearRight ? 'end' : 'start'
        })
      }

      addMarker('min', this.minPoint, '最低', '#67e8f9')
      addMarker('max', this.maxPoint, '最高', '#ffb86b')
      addMarker('latest', this.latestPoint, '最新', this.metricColor)
      return markerMap
    },
    granularityLabel() {
      const minutes = Number(this.granularityMinutes)
      if (!Number.isFinite(minutes) || minutes <= 0) return '自适应粒度'
      if (minutes < 60) return `${minutes}分钟/点`
      if (minutes % 1440 === 0) return `${minutes / 1440}天/点`
      if (minutes % 60 === 0) return `${minutes / 60}小时/点`
      return `${minutes}分钟/点`
    },
    chartTitle() {
      return `${this.metricLabel}历史趋势图`
    },
    chartDescription() {
      if (!this.validPoints.length) return `${this.chartTitle}暂无数据。`
      const firstPoint = this.validPoints[0]
      const lastPoint = this.validPoints[this.validPoints.length - 1]
      const thresholdText = this.thresholdDescription()
      return `${this.chartTitle}，当前粒度${this.granularityLabel}，时间范围从${firstPoint.label || '首个记录'}到${lastPoint.label || '最新记录'}，最新值${this.formatValue(this.latestPoint.value)}，最低值${this.formatValue(this.minPoint.value)}，最高值${this.formatValue(this.maxPoint.value)}。${thresholdText}`
    },
    chartAriaLabel() {
      return this.chartDescription
    },
    areaGradientId() {
      return `single-metric-area-${this.metricKey}-${this._uid}`.replace(/[^a-zA-Z0-9_-]/g, '-')
    },
    glowFilterId() {
      return `single-metric-glow-${this.metricKey}-${this._uid}`.replace(/[^a-zA-Z0-9_-]/g, '-')
    }
  },
  methods: {
    handleWheel(event) {
      if (!event.ctrlKey && !event.metaKey) return
      event.preventDefault()
      this.$emit('density-change', event.deltaY < 0 ? 'denser' : 'sparser')
    },
    normalizeThreshold(rawValue) {
      if (rawValue === null || rawValue === undefined || rawValue === '') return null
      const value = Number(rawValue)
      return Number.isFinite(value) ? value : null
    },
    valueToY(value) {
      const range = Math.max(this.scale.max - this.scale.min, 1)
      const ratio = (value - this.scale.min) / range
      return this.padding.top + this.chartHeight - ratio * this.chartHeight
    },
    toSmoothPath(points) {
      if (!points.length) return ''
      if (points.length === 1) {
        const point = points[0]
        return `M ${point.x} ${point.y}`
      }

      return points.reduce((path, point, index, allPoints) => {
        if (index === 0) return `M ${point.x} ${point.y}`
        const previous = allPoints[index - 1]
        const controlOffset = (point.x - previous.x) * 0.42
        return `${path} C ${previous.x + controlOffset} ${previous.y}, ${point.x - controlOffset} ${point.y}, ${point.x} ${point.y}`
      }, '')
    },
    formatValue(value) {
      if (!Number.isFinite(value)) return '--'
      const absValue = Math.abs(value)
      const decimals = absValue >= 100 ? 0 : (absValue >= 10 ? 1 : 2)
      return `${Number(value).toFixed(decimals)}${this.metricUnit}`
    },
    resolvePointLabel(point, index) {
      if (point && typeof point === 'object') {
        return point.label || point.timeLabel || point.timestamp || point.time || point.createdAt || point.date || `#${index + 1}`
      }
      return `#${index + 1}`
    },
    thresholdDescription() {
      if (this.isLightMetric && Number.isFinite(this.thresholdMax)) {
        return `参考阈值不高于${this.formatValue(this.thresholdMax)}。`
      }
      if (Number.isFinite(this.thresholdMin) && Number.isFinite(this.thresholdMax)) {
        return `参考安全区间为${this.formatValue(this.thresholdMin)}至${this.formatValue(this.thresholdMax)}。`
      }
      if (Number.isFinite(this.thresholdMax)) {
        return `参考阈值不高于${this.formatValue(this.thresholdMax)}。`
      }
      if (Number.isFinite(this.thresholdMin)) {
        return `参考阈值不低于${this.formatValue(this.thresholdMin)}。`
      }
      return '暂无阈值参考。'
    }
  }
}
</script>

<style scoped>
.single-metric-chart {
  display: grid;
  gap: 18px;
  padding: 22px;
  color: var(--text-main);
  background:
    radial-gradient(circle at 18% 0%, rgba(34, 211, 238, 0.12), transparent 34%),
    linear-gradient(145deg, rgba(14, 32, 59, 0.94), rgba(7, 18, 36, 0.9)),
    var(--surface);
  border: 1px solid var(--line);
  border-radius: 22px;
  box-shadow: var(--card-shadow), inset 0 1px 0 rgba(255, 255, 255, 0.04);
  backdrop-filter: blur(16px);
}

.chart-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.eyebrow {
  margin: 0 0 6px;
  color: var(--cyan);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.chart-head h4 {
  margin: 0;
  color: var(--text-strong);
  font-size: 22px;
  letter-spacing: 0.01em;
}

.granularity-pill {
  flex: 0 0 auto;
  padding: 9px 14px;
  color: #dffaff;
  font-size: 13px;
  font-weight: 700;
  background: rgba(8, 22, 43, 0.78);
  border: 1px solid rgba(113, 206, 255, 0.2);
  border-radius: 999px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05), 0 0 18px rgba(34, 211, 238, 0.08);
}

.chart-shell {
  min-height: 360px;
  overflow: hidden;
  background: rgba(4, 14, 29, 0.35);
  border: 1px solid rgba(113, 206, 255, 0.12);
  border-radius: 18px;
}

.trend-svg {
  display: block;
  width: 100%;
  height: 360px;
}

.plot-backdrop {
  fill: rgba(4, 14, 29, 0.42);
  stroke: rgba(113, 206, 255, 0.08);
}

.safe-band {
  fill: rgba(33, 208, 122, 0.12);
  stroke: rgba(33, 208, 122, 0.18);
  stroke-width: 1;
}

.grid-line {
  stroke: rgba(113, 206, 255, 0.14);
  stroke-width: 1;
}

.grid-line-vertical {
  stroke: rgba(113, 206, 255, 0.09);
}

.axis-line {
  stroke: rgba(118, 221, 255, 0.26);
  stroke-width: 1.2;
}

.axis-text {
  fill: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
}

.axis-text-y {
  letter-spacing: 0.01em;
}

.axis-text-x {
  fill: var(--text-subtle);
}

.threshold-line {
  stroke-width: 1.6;
  stroke-dasharray: 7 6;
}

.threshold-line-min {
  stroke: rgba(103, 232, 249, 0.74);
}

.threshold-line-max {
  stroke: rgba(255, 93, 108, 0.78);
}

.threshold-label {
  fill: rgba(230, 242, 255, 0.72);
  font-size: 12px;
  font-weight: 700;
  paint-order: stroke;
  stroke: rgba(4, 14, 29, 0.9);
  stroke-width: 4px;
  stroke-linejoin: round;
}

.trend-area {
  pointer-events: none;
}

.trend-line {
  fill: none;
  stroke-width: 3.4;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.marker-halo {
  fill: rgba(4, 14, 29, 0.72);
  stroke-width: 1.4;
  opacity: 0.72;
}

.marker-dot {
  stroke: rgba(255, 255, 255, 0.86);
  stroke-width: 1.2;
}

.marker-label {
  fill: var(--text-main);
  font-size: 12px;
  font-weight: 700;
  paint-order: stroke;
  stroke: rgba(4, 14, 29, 0.92);
  stroke-width: 4px;
  stroke-linejoin: round;
}

.empty-state {
  min-height: 280px;
  display: grid;
  place-items: center;
  color: var(--text-muted);
  background: rgba(8, 22, 43, 0.58);
  border: 1px dashed var(--line-strong);
  border-radius: 18px;
}

@media (max-width: 780px) {
  .single-metric-chart {
    padding: 18px;
  }

  .chart-head {
    flex-direction: column;
  }

  .trend-svg,
  .chart-shell {
    min-height: 320px;
    height: 320px;
  }
}
</style>
