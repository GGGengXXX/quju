import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/activities' },
  { path: '/login', component: () => import('../views/Login.vue') },
  { path: '/register', component: () => import('../views/Register.vue') },
  { path: '/activate', component: () => import('../views/Activate.vue') },
  { path: '/profile', component: () => import('../views/Profile.vue'), meta: { auth: true } },
  { path: '/teams', component: () => import('../views/team/TeamHub.vue'), meta: { auth: true } },
  { path: '/activities', component: () => import('../views/activity/ActivityDiscover.vue'), meta: { auth: true } },
  { path: '/social', component: () => import('../views/social/SocialHub.vue'), meta: { auth: true } },
  { path: '/social/chat/:id', component: () => import('../views/social/ChatView.vue'), meta: { auth: true } },
  { path: '/social/user/:id', component: () => import('../views/social/UserProfile.vue'), meta: { auth: true } },
  { path: '/notifications', component: () => import('../views/social/NotificationsView.vue'), meta: { auth: true } },

  // 管理员后台
  { path: '/admin/login', component: () => import('../views/admin/AdminLogin.vue') },
  {
    path: '/admin',
    component: () => import('../views/admin/AdminLayout.vue'),
    meta: { adminAuth: true },
    redirect: '/admin/dashboard',
    children: [
      { path: 'dashboard', component: () => import('../views/admin/AdminDashboard.vue') },
      { path: 'users', component: () => import('../views/admin/AdminUsers.vue') },
      { path: 'merchants', component: () => import('../views/admin/AdminMerchants.vue') },
      { path: 'activities', component: () => import('../views/admin/AdminActivities.vue') },
      { path: 'teams', component: () => import('../views/admin/AdminTeams.vue') },
      { path: 'reports', component: () => import('../views/admin/AdminReports.vue') },
    ],
  },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  if (to.meta.auth && !localStorage.getItem('quju_token')) return '/login'
  if (to.meta.adminAuth && !localStorage.getItem('quju_admin_token')) return '/admin/login'
})

export default router
