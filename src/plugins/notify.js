const TYPES = ['success', 'error', 'warning', 'info']

let container = null

function ensureContainer() {
  if (container) return container
  container = document.createElement('div')
  container.className = 'app-message-container'
  document.body.appendChild(container)
  return container
}

function show(type, text) {
  const host = ensureContainer()
  const item = document.createElement('div')
  item.className = `app-message app-message-${type}`
  item.textContent = text
  host.appendChild(item)

  requestAnimationFrame(() => {
    item.classList.add('is-visible')
  })

  setTimeout(() => {
    item.classList.remove('is-visible')
    setTimeout(() => {
      if (item.parentNode) {
        item.parentNode.removeChild(item)
      }
    }, 220)
  }, 2200)
}

const messageApi = TYPES.reduce((result, type) => {
  result[type] = text => show(type, text)
  return result
}, {})

export default {
  install(Vue) {
    Vue.prototype.$message = messageApi
  }
}
