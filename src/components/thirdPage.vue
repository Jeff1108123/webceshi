<template>
  <AppShell title="阈值设置">
    <section class="page-section panel">
      <div class="toolbar">
        <select v-if="deviceStore.devices.length" v-model="selectedDeviceId" aria-label="选择设备">
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
        tempMin: 20,
        tempMax: 30,
        humidityMin: 40,
        humidityMax: 70,
        lightMax: 13
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
          lightMax: threshold.lightMax
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
  position: relative;
  overflow: hidden;
  padding: 24px;
  border: 1px solid rgba(34, 211, 238, 0.16);
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.94), rgba(15, 23, 42, 0.78));
  box-shadow: 0 18px 45px rgba(2, 6, 23, 0.34), inset 0 1px 0 rgba(148, 163, 184, 0.1);
}

.panel::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(circle at top left, rgba(34, 211, 238, 0.16), transparent 34%),
    linear-gradient(90deg, rgba(34, 211, 238, 0.08) 1px, transparent 1px),
    linear-gradient(180deg, rgba(34, 211, 238, 0.05) 1px, transparent 1px);
  background-size: auto, 42px 42px, 42px 42px;
}

.toolbar,
.form-grid,
.empty-state {
  position: relative;
  z-index: 1;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 22px;
}

select,
button,
input {
  height: 44px;
  border-radius: 12px;
  font: inherit;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease, transform 0.2s ease;
}

select,
input {
  border: 1px solid rgba(34, 211, 238, 0.24);
  color: #dbeafe;
  background: rgba(15, 23, 42, 0.82);
  outline: none;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.38);
}

select:focus,
input:focus {
  border-color: rgba(34, 211, 238, 0.82);
  box-shadow: 0 0 0 3px rgba(34, 211, 238, 0.14), 0 0 24px rgba(34, 211, 238, 0.2);
}

select {
  min-width: 300px;
  padding: 0 14px;
  cursor: pointer;
}

button {
  border: 1px solid rgba(34, 211, 238, 0.46);
  padding: 0 18px;
  color: #ecfeff;
  background: linear-gradient(135deg, rgba(8, 145, 178, 0.95), rgba(14, 165, 233, 0.72));
  box-shadow: 0 0 18px rgba(34, 211, 238, 0.18);
  cursor: pointer;
}

button:not(:disabled):hover {
  transform: translateY(-1px);
  border-color: rgba(125, 211, 252, 0.88);
  box-shadow: 0 0 26px rgba(34, 211, 238, 0.32);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

label {
  display: grid;
  gap: 9px;
  padding: 16px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 16px;
  color: #bae6fd;
  font-weight: 700;
  letter-spacing: 0.02em;
  background: rgba(2, 6, 23, 0.34);
}

input {
  width: 100%;
  box-sizing: border-box;
  padding: 0 13px;
}

.empty-state {
  padding: 34px 0;
  text-align: center;
  color: rgba(191, 219, 254, 0.68);
}

button:disabled {
  opacity: 0.48;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

@media (max-width: 980px) {
  .form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .panel {
    padding: 18px;
  }

  .toolbar,
  .form-grid {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: stretch;
  }

  .form-grid {
    display: grid;
  }

  select,
  button {
    width: 100%;
    min-width: 0;
  }
}
</style>
