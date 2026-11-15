import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { AuthResponse } from '@/types'
import { toDisplayId } from '@/types'
import { useAdScopeStore } from '@/store/adScopeStore'

interface AuthState {
  token: string | null
  email: string | null
  role: 'USER' | 'ADMIN' | null
  displayId: string | null
  companyName: string | null
  isAuthenticated: boolean
  login: (auth: AuthResponse) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      email: null,
      role: null,
      displayId: null,
      companyName: null,
      isAuthenticated: false,
      login: (auth) => {
        localStorage.setItem('token', auth.token)
        useAdScopeStore.getState().reset()
        set({
          token: auth.token,
          email: auth.email,
          role: auth.role,
          displayId: toDisplayId(auth.email),
          companyName: auth.companyName,
          isAuthenticated: true,
        })
      },
      logout: () => {
        localStorage.removeItem('token')
        useAdScopeStore.getState().reset()
        set({ token: null, email: null, role: null, displayId: null, companyName: null, isAuthenticated: false })
      },
    }),
    { name: 'auth' }
  )
)
