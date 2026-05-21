import Vue from 'vue'
import Router from 'vue-router'
import { getDefaultHomePath, getToken, getUser, isSuperAdminUser } from '../utils/session'

Vue.use(Router)

const LoginPage = () => import(/* webpackChunkName: "login" */ '../components/loginPage.vue')
const SecondPage = () => import(/* webpackChunkName: "device" */ '../components/secondPage.vue')
const ThirdPage = () => import(/* webpackChunkName: "threshold" */ '../components/thirdPage.vue')
const FourthPage = () => import(/* webpackChunkName: "realdata" */ '../components/fourthPage.vue')
const FifthPage = () => import(/* webpackChunkName: "monitor" */ '../components/fifthPage.vue')
const SixthPage = () => import(/* webpackChunkName: "history" */ '../components/sixthPage.vue')
const SeventhPage = () => import(/* webpackChunkName: "location" */ '../components/seventhPage.vue')
const AdminDeviceBorrowPage = () => import(/* webpackChunkName: "admin-borrow" */ '../components/adminDeviceBorrowPage.vue')
const AdminBorrowLimitPage = () => import(/* webpackChunkName: "admin-borrow-limits" */ '../components/adminBorrowLimitPage.vue')

const router = new Router({
  mode: 'history',
  scrollBehavior() {
    return { x: 0, y: 0 }
  },
  routes: [
    { path: '/', name: 'Login', component: LoginPage, meta: { title: '登录' } },
    { path: '/device', name: 'Device', component: SecondPage, meta: { requiresAuth: true, title: '设备管理' } },
    { path: '/threshold', name: 'Threshold', component: ThirdPage, meta: { requiresAuth: true, title: '阈值设置' } },
    { path: '/realdata', name: 'RealData', component: FourthPage, meta: { requiresAuth: true, title: '实时数据' } },
    { path: '/monitor', name: 'Monitor', component: FifthPage, meta: { requiresAuth: true, title: '实时监测' } },
    { path: '/history', name: 'History', component: SixthPage, meta: { requiresAuth: true, title: '历史数据' } },
    { path: '/location', name: 'Location', component: SeventhPage, meta: { requiresAuth: true, title: '实时位置' } },
    { path: '/admin/device-borrows', name: 'AdminDeviceBorrows', component: AdminDeviceBorrowPage, meta: { requiresAuth: true, requiresSuperAdmin: true, title: '设备借用总览' } },
    { path: '/admin/borrow-limits', name: 'AdminBorrowLimits', component: AdminBorrowLimitPage, meta: { requiresAuth: true, requiresSuperAdmin: true, title: '借用限制管理' } }
  ]
})

const originalPush = Router.prototype.push
Router.prototype.push = function push(location) {
  return originalPush.call(this, location).catch(error => {
    if (error.name !== 'NavigationDuplicated') throw error
  })
}

router.beforeEach((to, from, next) => {
  document.title = `医疗冷链运输箱监控系统 - ${to.meta.title || '首页'}`

  const token = getToken()
  const currentUser = getUser()
  const isSuperAdmin = isSuperAdminUser(currentUser)
  const defaultHomePath = getDefaultHomePath(currentUser)

  if (to.meta.requiresAuth && !token) {
    next({ path: '/' })
    return
  }

  if (to.meta.requiresSuperAdmin && !isSuperAdmin) {
    next({ path: defaultHomePath })
    return
  }

  if (isSuperAdmin && token && to.path !== '/' && !to.meta.requiresSuperAdmin) {
    next({ path: defaultHomePath })
    return
  }

  if (to.path === '/' && token) {
    next({ path: defaultHomePath })
    return
  }

  next()
})

export default router
