<template>
  <AppShell title="阈值设置">
    <section class="page-section panel">
      <div class="toolbar">
        <select v-if="deviceStore.devices.length" v-model="selectedDeviceId">
          <option v-for="item in deviceStore.devices" :key="item.id" :value="item.id">
            {{ item.deviceName }} ({{ item.deviceCode }})
          </option>
        </select>
        <button :disabled="saving || !currentDevice" @click="handleSave">
          {{ saving ? '保存中...' : '保存阈值' }}
        </button>
      </div>

      <div v-if="!currentDevice" class="empty-state">暂无可配置设备。</div>

      <div v-else class="form-grid">
        <label>温度下限<input v-model.number="form.tempMin" type="number" step="0.1" /></label>
        <label>温度上限<input v-model.number="form.tempMax" type="number" step="0.1" /></label>
        <label>湿度下限<input v-model.number="form.humidityMin" type="number" step="0.1" /></label>
        <label>湿度上限<input v-model.number="form.humidityMax" type="number" step="0.1" /></label>
        <label>光照上限<input v-model.number="form.lightMax" type="number" step="0.1" /></label>
        <label>失效时长<input v-model.number="form.durationLimitHours" type="number" min="1" step="1" /></label>
      </div>
    </section>
  </AppShell>
</template>

<script>
import AppShell from './common/AppShell.vue'
import { getThreshold, saveThreshold } from '../api/medicalColdChain'
import { useDeviceStore } from '../store/deviceStore'

export default {
  name: 'ThirdPage',
  components: {
    AppShell
  },
  data() {
    return {
      deviceStore: useDeviceStore(),
      selectedDeviceId: null,
      saving: false,
      form: {
        tempMin: 2,
        tempMax: 8,
        humidityMin: 35,
        humidityMax: 75,
        lightMax: 10,
        durationLimitHours: 8
      }
    }
  },
  computed: {
    currentDevice() {
      return this.deviceStore.devices.find(item => item.id === this.selectedDeviceId) || null
    }
  },
  watch: {
    async selectedDeviceId(value) {
      if (!value) return
      const selectedDevice = this.deviceStore.setSelectedDevice(value)
      if (!selectedDevice || selectedDevice.id !== value) {
        this.selectedDeviceId = selectedDevice ? selectedDevice.id : null
        return
      }
      await this.loadThresholdData(value)
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
  methods: {
    async loadThresholdData(deviceId) {
      try {
        const threshold = await getThreshold(deviceId)
        this.form = {
          tempMin: threshold.tempMin,
          tempMax: threshold.tempMax,
          humidityMin: threshold.humidityMin,
          humidityMax: threshold.humidityMax,
          lightMax: threshold.lightMax,
          durationLimitHours: threshold.durationLimitHours
        }
      } catch (error) {
        this.$message.error(error.message)
      }
    },
    async handleSave() {
      if (!this.currentDevice) return
      if (Number(this.form.tempMin) >= Number(this.form.tempMax)) {
        this.$message.error('温度下限必须小于上限')
        return
      }
      if (Number(this.form.humidityMin) >= Number(this.form.humidityMax)) {
        this.$message.error('湿度下限必须小于上限')
        return
      }

      this.saving = true
      try {
        await saveThreshold(this.currentDevice.id, this.form)
        await this.deviceStore.refreshDevices()
        this.selectedDeviceId = this.deviceStore.selectedDeviceId
        this.$message.success('保存成功')
      } catch (error) {
        this.$message.error(error.message)
      } finally {
        this.saving = false
      }
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
  margin-bottom: 18px;
}

select,
button,
input {
  height: 42px;
  border-radius: 10px;
}

select,
input {
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: #fff;
}

select {
  min-width: 260px;
  padding: 0 12px;
}

button {
  border: none;
  padding: 0 16px;
  color: #fff;
  background: var(--primary);
  cursor: pointer;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

label {
  display: grid;
  gap: 8px;
  font-weight: 600;
}

input {
  padding: 0 12px;
}

.empty-state {
  padding: 30px 0;
  text-align: center;
  color: var(--text-muted);
}

button:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

@media (max-width: 760px) {
  .toolbar,
  .form-grid {
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .form-grid {
    display: grid;
  }

  select {
    min-width: 0;
    width: 100%;
  }
}
</style>
