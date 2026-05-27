import request from './request'

export function sendCode(phone, options = {}) {
  return request.post('/auth/send-code', {
    phone,
    ...options
  })
}

export function login(phone, code) {
  return request.post('/auth/login', { phone, code })
}

export function fetchOverview() {
  return request.get('/devices/overview')
}

export function fetchMyDevices() {
  return request.get('/devices/mine')
}

export function fetchAdminUsers() {
  return request.get('/devices/users')
}

export function applyDevices(count) {
  return request.post('/devices/apply', { count })
}

export function returnDevices(deviceIds) {
  return request.post('/devices/return', { deviceIds })
}

export function forceReturnDevices(payload) {
  return request.post('/devices/force-return', payload)
}

export function getThreshold(deviceId) {
  return request.get(`/devices/${deviceId}/threshold`)
}

export function saveThreshold(deviceId, payload) {
  return request.put(`/devices/${deviceId}/threshold`, payload)
}

export function fetchLatest() {
  return request.get('/devices/latest')
}

export function fetchMonitor(deviceId) {
  return request.get(`/devices/${deviceId}/monitor`)
}

export function fetchHistory(deviceId, hours, stepMinutes) {
  return request.get(`/devices/${deviceId}/history`, {
    params: { hours, stepMinutes }
  })
}

export function fetchLocation(deviceId) {
  return request.get(`/devices/${deviceId}/location`)
}

export function fetchAllDeviceBorrows(params = {}) {
  return request.get('/devices/borrow-records', { params })
}

export function fetchBorrowLimits() {
  return request.get('/devices/borrow-limits')
}

export function updateDefaultBorrowLimit(limit) {
  return request.put('/devices/borrow-limits/default', { limit })
}

export function updateUserBorrowLimit(userId, limit) {
  return request.put(`/devices/users/${userId}/borrow-limit`, { limit })
}

export function deleteAdminUser(userId) {
  return request.delete(`/devices/users/${userId}`)
}
