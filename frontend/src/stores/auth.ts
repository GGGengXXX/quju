import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi, type UserVO } from '../api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('quju_token') || '')
  const user = ref<UserVO | null>(null)

  async function login(email: string, password: string) {
    const data = await authApi.login({ email, password })
    token.value = data.token
    localStorage.setItem('quju_token', data.token)
    user.value = data.user
  }

  async function loadMe() {
    user.value = await authApi.getMe()
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('quju_token')
  }

  return { token, user, login, loadMe, logout }
})
