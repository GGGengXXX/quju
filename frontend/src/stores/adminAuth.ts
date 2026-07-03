import { defineStore } from 'pinia'
import { ref } from 'vue'
import { adminApi } from '../api/admin'

export const useAdminAuthStore = defineStore('adminAuth', () => {
  const token = ref<string>(localStorage.getItem('quju_admin_token') || '')
  const username = ref<string>(localStorage.getItem('quju_admin_username') || '')

  async function login(user: string, password: string) {
    const data = await adminApi.login({ username: user, password })
    token.value = data.token
    username.value = data.username
    localStorage.setItem('quju_admin_token', data.token)
    localStorage.setItem('quju_admin_username', data.username)
  }

  function logout() {
    token.value = ''
    username.value = ''
    localStorage.removeItem('quju_admin_token')
    localStorage.removeItem('quju_admin_username')
  }

  return { token, username, login, logout }
})
