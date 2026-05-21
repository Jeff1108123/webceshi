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
        color: '#fff',
        backgroundColor: '#0f7bff',
        borderColor: '#0f7bff',
        borderRadius: '10px',
        padding: '5px 8px',
        fontSize: '12px'
      })
      this.marker.setLabel(this.label)
    }
  }
}
</script>

<style scoped>
.panel {
  padding: 20px;
}

.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

select,
button {
  height: 40px;
  border-radius: 10px;
}

select {
  min-width: 240px;
  padding: 0 12px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: #fff;
}

button {
  padding: 0 14px;
  border: none;
  color: var(--primary-dark);
  background: rgba(15, 123, 255, 0.08);
  cursor: pointer;
}

.map-container {
  width: 100%;
  height: 520px;
  overflow: hidden;
  border-radius: 18px;
}

.location-info {
  margin-top: 12px;
  padding: 14px;
  border-radius: 14px;
  background: rgba(248, 250, 252, 0.95);
  display: grid;
  gap: 6px;
}

.location-info span,
.location-info small {
  color: var(--text-muted);
}

.empty-state {
  padding: 30px 0;
  text-align: center;
  color: var(--text-muted);
}

@media (max-width: 560px) {
  .toolbar {
    flex-direction: column;
  }

  select {
    min-width: 0;
    width: 100%;
  }

  .map-container {
    height: 420px;
  }
}
</style>
