import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/activities' },
  { path: '/login', component: () => import('../views/Login.vue') },
  { path: '/register', component: () => import('../views/Register.vue') },
  { path: '/activate', component: () => import('../views/Activate.vue') },
  { path: '/profile', component: () => import('../views/Profile.vue'), meta: { auth: true } },
  { path: '/teams', component: () => import('../views/team/TeamHub.vue'), meta: { auth: true } },
  { path: '/activities', component: () => import('../views/activity/ActivityDiscover.vue') },
  { path: '/activities/checkin', component: () => import('../views/activity/ActivityMobileCheckin.vue'), meta: { minimalLayout: true } },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  if (to.meta.auth && !localStorage.getItem('quju_token')) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
})

export default router
