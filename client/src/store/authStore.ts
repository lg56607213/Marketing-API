import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { AuthResponse } from '@/types'
import { toDisplayId } from '@/types'

interface AuthState {
  token: string | null
  email: string | null
  role: 'USER' | 'ADMIN' | null
  displayId: string | null
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
      isAuthenticated: false,
      login: (auth) => {
        localStorage.setItem('token', auth.token)
        set({
          token: auth.token,
          email: auth.email,
          role: auth.role,
          displayId: toDisplayId(auth.email),
          isAuthenticated: true,
        })
      },
      logout: () => {
        localStorage.removeItem('token')
        set({ token: null, email: null, role: null, displayId: null, isAuthenticated: false })
      },
    }),
    { name: 'auth' }
  )
)
