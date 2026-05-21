import { defineStore } from 'pinia'
import { clearSession, getToken, getUser, isSuperAdminUser, saveSession } from '../utils/session'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken(),
    user: getUser()
  }),
  getters: {
    isLoggedIn: state => Boolean(state.token),
    userPhone: state => (state.user ? state.user.phone : ''),
    userName: state => (state.user ? state.user.name : ''),
    isSuperAdmin: state => isSuperAdminUser(state.user)
  },
  actions: {
    setSession(payload) {
      this.token = payload.token
      this.user = payload.user
      saveSession(payload.token, payload.user)
    },
    logout() {
      this.token = ''
      this.user = null
      clearSession()
    }
  }
})
