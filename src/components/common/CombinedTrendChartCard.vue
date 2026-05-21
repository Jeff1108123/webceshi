<template>
  <article class="combined-trend-card">
    <div class="combined-head">
      <div>
        <h4>综合趋势</h4>
        <p>温度、湿度、光照三项指标独立缩放后合并展示</p>
      </div>
      <div class="legend">
        <span v-for="series in chartSeries" :key="series.key">
          <i :style="{ background: series.color }"></i>{{ series.label }} {{ formatMetric(series.latest, series.unit) }}
        </span>
      </div>
    </div>

    <div v-if="hasPoints" class="chart-shell">
      <svg
        class="trend-svg"
        viewBox="0 0 720 300"
        preserveAspectRatio="none"
        role="img"
        :aria-label="chartAriaLabel"
      >
        <title>{{ chartTitle }}</title>
        <desc>{{ chartDescription }}</desc>
        <line
          v-for="gridLine in gridLines"
          :key="gridLine.key"
          class="grid-line"
          :x1="padding.left"
          :y1="gridLine.y"
          :x2="width - padding.right"
          :y2="gridLine.y"
        />
        <path
          v-for="series in drawableSeries"
          :key="series.key"
          class="trend-line"
          :d="series.path"
          :style="{ stroke: series.color }"
        />
        <circle
          v-for="series in drawableSeries"
          :key="`${series.key}-last`"
          :cx="series.lastPoint.x"
          :cy="series.lastPoint.y"
          r="4"
          :style="{ fill: series.color }"
        />
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
      </svg>
    </div>
    <div v-else class="empty-state">暂无历史数据</div>

    <div class="metric-stats">
      <div v-for="series in chartSeries" :key="`${series.key}-stats`" class="stat-row">
        <strong :style="{ color: series.color }">{{ series.label }}</strong>
        <span>最新 {{ formatMetric(series.latest, series.unit) }}</span>
        <span>最低 {{ formatMetric(series.min, series.unit) }}</span>
        <span>最高 {{ formatMetric(series.max, series.unit) }}</span>
        <small>{{ thresholdText(series) }}</small>
      </div>
    </div>
  </article>
</template>

<script>
export default {
  name: 'CombinedTrendChartCard',
  props: {
    labels: {
      type: Array,
      default: () => []
    },
    temperatureValues: {
      type: Array,
      default: () => []
    },
    humidityValues: {
      type: Array,
      default: () => []
    },
    lightValues: {
      type: Array,
      default: () => []
    },
    threshold: {
      type: Object,
      default: () => ({})
    }
  },
  data() {
    return {
      width: 720,
      height: 300,
      padding: {
        top: 22,
        right: 28,
        bottom: 48,
        left: 28
      }
    }
  },
  computed: {
    metricConfigs() {
      return [
        {
          key: 'temperature',
          label: '温度',
          unit: '°C',
          color: '#2f8cff',
          values: this.temperatureValues,
          thresholdMin: this.threshold.tempMin,
          thresholdMax: this.threshold.tempMax
        },
        {
          key: 'humidity',
          label: '湿度',
          unit: '%',
          color: '#21d07a',
          values: this.humidityValues,
          thresholdMin: this.threshold.humidityMin,
          thresholdMax: this.threshold.humidityMax
        },
        {
          key: 'light',
          label: '光照',
          unit: 'Lux',
          color: '#f7c948',
          values: this.lightValues,
          thresholdMin: null,
          thresholdMax: this.threshold.lightMax
        }
      ]
    },
    chartSeries() {
      return this.metricConfigs.map(config => {
        const points = this.buildPoints(config.values)
        const values = points.map(point => point.value)
        const min = values.length ? Math.min(...values) : null
        const max = values.length ? Math.max(...values) : null
        const latest = values.length ? values[values.length - 1] : null
        const scale = this.resolveScale(min, max, config.thresholdMin, config.thresholdMax)
        const coordinates = this.toCoordinates(points, scale)

        return {
          ...config,
          points,
          min,
          max,
          latest,
          coordinates,
          path: this.toSmoothPath(coordinates),
          lastPoint: coordinates[coordinates.length - 1] || null
        }
      })
    },
    drawableSeries() {
      return this.chartSeries.filter(series => series.coordinates.length)
    },
    hasPoints() {
      return this.drawableSeries.length > 0
    },
    gridLines() {
      const chartHeight = this.height - this.padding.top - this.padding.bottom
      return Array.from({ length: 4 }, (_, index) => ({
        key: `grid-${index}`,
        y: this.padding.top + (chartHeight * index) / 3
      }))
    },
    xTicks() {
      const labelCount = this.labels.length
      if (!labelCount) return []
      const lastIndex = labelCount - 1
      const desiredTickCount = 4
      const step = Math.max(1, Math.ceil(lastIndex / Math.max(desiredTickCount - 1, 1)))
      const indexes = [0]

      for (let index = step; index < lastIndex; index += step) {
        indexes.push(index)
      }

      if (!indexes.includes(lastIndex)) {
        indexes.push(lastIndex)
      }

      const chartWidth = this.width - this.padding.left - this.padding.right
      const denominator = Math.max(labelCount - 1, 1)

      return indexes.map((labelIndex, index) => ({
        key: `tick-${index}-${labelIndex}`,
        x: this.padding.left + (chartWidth * labelIndex) / denominator,
        label: this.labels[labelIndex] || '',
        anchor: labelIndex === 0 ? 'start' : (labelIndex === lastIndex ? 'end' : 'middle')
      }))
    },
    chartTitle() {
      return '综合趋势图'
    },
    chartDescription() {
      const firstLabel = this.labels[0] || '首个记录'
      const lastLabel = this.labels[this.labels.length - 1] || '最新记录'
      const seriesSummary = this.chartSeries
        .map(series => `${series.label}最新${this.formatMetric(series.latest, series.unit)}，最低${this.formatMetric(series.min, series.unit)}，最高${this.formatMetric(series.max, series.unit)}`)
        .join('；')
      return `${this.chartTitle}，展示温度、湿度、光照三项指标，时间范围从${firstLabel}到${lastLabel}。${seriesSummary}`
    },
    chartAriaLabel() {
      return this.chartDescription
    }
  },
  methods: {
    buildPoints(values) {
      return values
        .map((value, index) => ({ value: Number(value), index }))
        .filter(item => Number.isFinite(item.value))
    },
    resolveScale(min, max, thresholdMin, thresholdMax) {
      const candidates = [min, max, thresholdMin, thresholdMax].filter(Number.isFinite)
      if (!candidates.length) {
        return { min: 0, max: 1 }
      }
      const rawMin = Math.min(...candidates)
      const rawMax = Math.max(...candidates)
      const padding = Math.max((rawMax - rawMin) * 0.12, 1)
      return {
        min: rawMin - padding,
        max: rawMax + padding
      }
    },
    toCoordinates(points, scale) {
      const chartWidth = this.width - this.padding.left - this.padding.right
      const chartHeight = this.height - this.padding.top - this.padding.bottom
      const denominator = Math.max(this.labels.length - 1, points.length - 1, 1)
      const valueRange = Math.max(scale.max - scale.min, 1)

      return points.map(point => ({
        x: this.padding.left + (chartWidth * point.index) / denominator,
        y: this.padding.top + chartHeight - ((point.value - scale.min) / valueRange) * chartHeight,
        value: point.value,
        label: this.labels[point.index] || ''
      }))
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
    formatMetric(value, unit) {
      if (!Number.isFinite(value)) return '--'
      return `${value.toFixed(1)}${unit}`
    },
    thresholdText(series) {
      if (Number.isFinite(series.thresholdMin) && Number.isFinite(series.thresholdMax)) {
        return `阈值 ${series.thresholdMin} ~ ${series.thresholdMax}${series.unit}`
      }
      if (Number.isFinite(series.thresholdMax)) {
        return `阈值 ≤ ${series.thresholdMax}${series.unit}`
      }
      return '暂无阈值参考'
    }
  }
}
</script>

<style scoped>
.combined-trend-card {
  padding: 20px;
  border: 1px solid var(--line);
  border-radius: 22px;
  background:
    linear-gradient(145deg, rgba(14, 32, 59, 0.94), rgba(7, 18, 36, 0.88)),
    var(--surface);
  box-shadow: var(--card-shadow), inset 0 1px 0 rgba(255, 255, 255, 0.04);
  display: grid;
  gap: 16px;
  backdrop-filter: blur(16px);
}

.combined-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.combined-head h4 {
  margin: 0 0 8px;
  color: var(--text-strong);
  font-size: 20px;
}

.combined-head p {
  margin: 0;
  color: var(--text-muted);
}

.legend {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  color: var(--text-main);
}

.legend span {
  padding: 8px 10px;
  border: 1px solid rgba(113, 206, 255, 0.16);
  border-radius: 999px;
  background: rgba(8, 22, 43, 0.72);
  display: inline-flex;
  gap: 6px;
  align-items: center;
  white-space: nowrap;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.legend i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  box-shadow: 0 0 12px currentColor;
}

.chart-shell {
  width: 100%;
  min-height: 300px;
}

.trend-svg {
  width: 100%;
  height: 300px;
  display: block;
}

.grid-line {
  stroke: rgba(113, 206, 255, 0.14);
  stroke-width: 1;
}

.trend-line {
  fill: none;
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
  filter: drop-shadow(0 0 6px rgba(34, 211, 238, 0.18));
}

.axis-text {
  fill: #8aa6bf;
  font-size: 12px;
}

.metric-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.stat-row {
  padding: 12px;
  border: 1px solid rgba(113, 206, 255, 0.16);
  border-radius: 14px;
  background: rgba(8, 22, 43, 0.72);
  display: grid;
  gap: 6px;
  color: var(--text-muted);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.stat-row strong {
  font-size: 16px;
}

.stat-row small {
  color: var(--text-muted);
}

.empty-state {
  padding: 48px 0;
  color: var(--text-muted);
  text-align: center;
}

@media (max-width: 900px) {
  .combined-head {
    flex-direction: column;
  }

  .legend {
    justify-content: flex-start;
  }

  .metric-stats {
    grid-template-columns: 1fr;
  }
}
</style>
