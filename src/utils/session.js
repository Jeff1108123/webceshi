const TOKEN_KEY = 'medical-cold-chain-token'
const USER_KEY = 'medical-cold-chain-user'
const TOKEN_MAX_LENGTH = 128
const SUPER_ADMIN_PHONES = parseSuperAdminPhones()

function parseJson(value, fallback) {
  if (!value) return fallback
  try {
    return JSON.parse(value)
  } catch (error) {
    return fallback
  }
}

function parseSuperAdminPhones() {
  const raw = process.env.VUE_APP_SUPER_ADMIN_PHONES || ''
  return raw
    .split(',')
    .map(item => item.trim())
    .filter(Boolean)
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function getUser() {
  return parseJson(localStorage.getItem(USER_KEY), null)
}

export function saveSession(token, user) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function sanitizeSession() {
  const token = localStorage.getItem(TOKEN_KEY)
  if (!token) return

  const invalidToken = typeof token !== 'string'
    || token.length > TOKEN_MAX_LENGTH
    || token.includes('[object Object]')
    || token.includes('{')

  if (invalidToken) {
    clearSession()
  }
}

export function isSuperAdminUser(user) {
  if (!user) return false
  const role = String(user.role || '').toUpperCase()
  return user.isSuperAdmin === true
    || role === 'SUPER_ADMIN'
    || role === 'ADMIN'
    || SUPER_ADMIN_PHONES.includes(user.phone)
}

export function getDefaultHomePath(user) {
  return isSuperAdminUser(user) ? '/admin/device-borrows' : '/realdata'
}
