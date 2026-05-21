import { defineStore } from 'pinia'
import { fetchMyDevices, fetchOverview } from '../api/medicalColdChain'

export const useDeviceStore = defineStore('device', {
  state: () => ({
    overview: {
      availableCount: 0,
      inUseCount: 0,
      myDeviceCount: 0,
      alarmCount: 0
    },
    devices: [],
    selectedDeviceId: null
  }),
  getters: {
    selectedDevice(state) {
      return state.devices.find(item => item.id === state.selectedDeviceId) || state.devices[0] || null
    }
  },
  actions: {
    async refreshOverview() {
      this.overview = await fetchOverview()
    },
    async refreshDevices() {
      this.devices = await fetchMyDevices()
      this.syncSelectedDevice()
    },
    async refreshAll() {
      await Promise.all([this.refreshOverview(), this.refreshDevices()])
    },
    setSelectedDevice(id) {
      this.selectedDeviceId = id
      this.syncSelectedDevice()
      return this.selectedDevice
    },
    syncSelectedDevice() {
      if (!this.selectedDeviceId || !this.devices.find(item => item.id === this.selectedDeviceId)) {
        this.selectedDeviceId = this.devices.length ? this.devices[0].id : null
      }
      return this.selectedDevice
    },
    clear() {
      this.devices = []
      this.selectedDeviceId = null
      this.overview = {
        availableCount: 0,
        inUseCount: 0,
        myDeviceCount: 0,
        alarmCount: 0
      }
    }
  }
})
