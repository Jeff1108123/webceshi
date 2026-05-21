import Vue from 'vue'
import App from './App.vue'
import router from './router'
import { createPinia, PiniaVuePlugin } from 'pinia'
import NotifyPlugin from './plugins/notify'
import './styles/theme.css'
import { sanitizeSession } from './utils/session'

Vue.use(PiniaVuePlugin)
Vue.use(NotifyPlugin)

const pinia = createPinia()

Vue.config.productionTip = false

sanitizeSession()

new Vue({
  router,
  pinia,
  render: h => h(App)
}).$mount('#app')
