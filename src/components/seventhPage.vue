<template>
  <AppShell title="实时位置">
    <section class="page-section panel">
      <div class="toolbar">
        <select v-if="deviceStore.devices.length" v-model="selectedDeviceId">
          <option v-for="item in deviceStore.devices" :key="item.id" :value="item.id">
            {{ item.deviceName }}
          </option>
        </select>
        <button :disabled="!selectedDeviceId" @click="loadLocation">刷新定位</button>
      </div>

      <div v-if="!deviceStore.devices.length" class="empty-state">暂无设备，请先申请。</div>

      <div v-else>
        <div id="device-map" class="map-container"></div>
        <div v-if="locationData" class="location-info">
          <span>{{ locationData.city }}</span>
          <strong>{{ locationData.address }}</strong>
          <small>经度 {{ locationData.longitude }} / 纬度 {{ locationData.latitude }}</small>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<script>
import AppShell from './common/AppShell.vue'
import { fetchLocation } from '../api/medicalColdChain'
import { useDeviceStore } from '../store/deviceStore'

export default {
  name: 'SeventhPage',
  components: {
    AppShell
  },
  data() {
    return {
      deviceStore: useDeviceStore(),
      selectedDeviceId: null,
      locationData: null,
      map: null,
      marker: null,
      label: null,
      refreshTimer: null,
      mapWaitTimer: null
    }
  },
  watch: {
    selectedDeviceId(value) {
      if (!value) {
        this.locationData = null
        return
      }
      const selectedDevice = this.deviceStore.setSelectedDevice(value)
      if (!selectedDevice || selectedDevice.id !== value) {
        this.selectedDeviceId = selectedDevice ? selectedDevice.id : null
        return
      }
      this.loadLocation()
    }
  },
  async created() {
    try {
      await this.deviceStore.refreshDevices()
      const selectedDevice = this.deviceStore.syncSelectedDevice()
      this.selectedDeviceId = selectedDevice ? selectedDevice.id : null
    } catch (error) {
      this.$message.error(error.message)
    }
  },
  mounted() {
    this.waitForMap()
    this.refreshTimer = setInterval(this.loadLocation, 10000)
  },
  beforeDestroy() {
    if (this.refreshTimer) clearInterval(this.refreshTimer)
    if (this.mapWaitTimer) clearInterval(this.mapWaitTimer)
  },
  methods: {
    waitForMap() {
      if (window.BMap) {
        this.initMap()
        return
      }

      this.mapWaitTimer = setInterval(() => {
        if (window.BMap) {
          clearInterval(this.mapWaitTimer)
          this.mapWaitTimer = null
          this.initMap()
        }
      }, 100)
    },
    initMap() {
      if (this.map || !window.BMap) return
      this.map = new window.BMap.Map('device-map')
      const center = new window.BMap.Point(121.4737, 31.2304)
      this.map.centerAndZoom(center, 12)
      this.map.enableScrollWheelZoom(true)
      this.renderMarker()
    },
    async loadLocation() {
      if (!this.selectedDeviceId) return
      try {
        this.locationData = await fetchLocation(this.selectedDeviceId)
        this.renderMarker()
      } catch (error) {
        this.$message.error(error.message)
      }
    },
    renderMarker() {
      if (!this.map || !this.locationData || !window.BMap) return

      const point = new window.BMap.Point(this.locationData.longitude, this.locationData.latitude)
      this.map.centerAndZoom(point, 15)

      if (this.marker) this.map.removeOverlay(this.marker)
      if (this.label) this.map.removeOverlay(this.label)

      this.marker = new window.BMap.Marker(point)
      this.map.addOverlay(this.marker)

      this.label = new window.BMap.Label(this.locationData.address, {
        offset: new window.BMap.Size(14, -20)
      })
      this.label.setStyle({
        color: '#e0f2fe',
        backgroundColor: 'rgba(15, 23, 42, 0.92)',
        borderColor: '#22d3ee',
        borderRadius: '10px',
        padding: '5px 8px',
        fontSize: '12px',
        boxShadow: '0 0 16px rgba(34, 211, 238, 0.32)'
      })
      this.marker.setLabel(this.label)
    }
  }
}
</script>

<style scoped>
.panel {
  position: relative;
  overflow: hidden;
  padding: 24px;
  border: 1px solid rgba(34, 211, 238, 0.16);
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.96), rgba(15, 23, 42, 0.78));
  box-shadow: 0 18px 45px rgba(2, 6, 23, 0.34), inset 0 1px 0 rgba(148, 163, 184, 0.1);
}

.panel::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(circle at top left, rgba(34, 211, 238, 0.16), transparent 34%),
    radial-gradient(circle at 86% 86%, rgba(14, 165, 233, 0.12), transparent 30%),
    linear-gradient(90deg, rgba(34, 211, 238, 0.06) 1px, transparent 1px),
    linear-gradient(180deg, rgba(34, 211, 238, 0.05) 1px, transparent 1px);
  background-size: auto, auto, 44px 44px, 44px 44px;
}

.toolbar,
.empty-state,
.map-container,
.location-info {
  position: relative;
  z-index: 1;
}

.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

select,
button {
  height: 42px;
  border-radius: 12px;
  font: inherit;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease, transform 0.2s ease;
}

select {
  min-width: 240px;
  padding: 0 14px;
  border: 1px solid rgba(34, 211, 238, 0.24);
  color: #dbeafe;
  background: rgba(15, 23, 42, 0.84);
  outline: none;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.38);
  cursor: pointer;
}

select:focus {
  border-color: rgba(34, 211, 238, 0.82);
  box-shadow: 0 0 0 3px rgba(34, 211, 238, 0.14), 0 0 24px rgba(34, 211, 238, 0.2);
}

button {
  padding: 0 16px;
  border: 1px solid rgba(34, 211, 238, 0.42);
  color: #ecfeff;
  background: rgba(8, 145, 178, 0.2);
  box-shadow: 0 0 16px rgba(34, 211, 238, 0.14);
  cursor: pointer;
}

button:not(:disabled):hover {
  transform: translateY(-1px);
  border-color: rgba(125, 211, 252, 0.82);
  background: rgba(14, 165, 233, 0.3);
  box-shadow: 0 0 24px rgba(34, 211, 238, 0.28);
}

button:disabled {
  opacity: 0.48;
  cursor: not-allowed;
  box-shadow: none;
}

.map-container {
  width: 100%;
  height: 520px;
  overflow: hidden;
  border: 1px solid rgba(34, 211, 238, 0.32);
  border-radius: 20px;
  background: rgba(2, 6, 23, 0.55);
  box-shadow: 0 0 0 1px rgba(34, 211, 238, 0.08), 0 0 34px rgba(34, 211, 238, 0.2), inset 0 0 30px rgba(2, 6, 23, 0.42);
}

.map-container::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  border-radius: 20px;
  box-shadow: inset 0 0 0 1px rgba(207, 250, 254, 0.08), inset 0 0 40px rgba(2, 6, 23, 0.24);
}

.location-info {
  margin: -76px 18px 0;
  padding: 14px 16px;
  border: 1px solid rgba(34, 211, 238, 0.28);
  border-radius: 16px;
  background: rgba(2, 6, 23, 0.82);
  box-shadow: 0 18px 36px rgba(2, 6, 23, 0.34), 0 0 24px rgba(34, 211, 238, 0.16);
  backdrop-filter: blur(10px);
  display: grid;
  grid-template-columns: minmax(80px, 0.3fr) minmax(220px, 1fr) auto;
  gap: 8px 14px;
  align-items: center;
}

.location-info span {
  color: #67e8f9;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.location-info strong {
  color: #e0f2fe;
  line-height: 1.35;
}

.location-info small {
  color: rgba(191, 219, 254, 0.68);
  white-space: nowrap;
}

.empty-state {
  padding: 34px 0;
  text-align: center;
  color: rgba(191, 219, 254, 0.68);
}

@media (max-width: 760px) {
  .location-info {
    grid-template-columns: 1fr;
    margin: 12px 0 0;
  }

  .location-info small {
    white-space: normal;
  }
}

@media (max-width: 560px) {
  .panel {
    padding: 18px;
  }

  .toolbar {
    flex-direction: column;
  }

  select,
  button {
    width: 100%;
  }

  select {
    min-width: 0;
  }

  .map-container {
    height: 420px;
  }
}
</style>
