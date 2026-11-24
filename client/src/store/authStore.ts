import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { AuthResponse } from '@/types'
import { toDisplayId } from '@/types'
import { useAdScopeStore } from '@/store/adScopeStore'

interface AuthState {
  token: string | null
  email: string | null
  role: 'USER' | 'ADMIN' | null
  /** 모든 업체의 가입 신청을 승인하는 최고 관리자 */
  superAdmin: boolean
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
      superAdmin: false,
      displayId: null,
      companyName: null,
      isAuthenticated: false,
      login: (auth) => {
        // 승인 대기 계정은 토큰이 없다. 호출하는 쪽에서 걸러야 하지만, 혹시 들어와도 로그인시키지 않는다.
        if (!auth.token) return
        localStorage.setItem('token', auth.token)
        useAdScopeStore.getState().reset()
        set({
          token: auth.token,
          email: auth.email,
          role: auth.role,
          superAdmin: auth.superAdmin === true,
          displayId: toDisplayId(auth.email),
          companyName: auth.companyName,
          isAuthenticated: true,
        })
      },
      logout: () => {
        localStorage.removeItem('token')
        useAdScopeStore.getState().reset()
        set({
          token: null, email: null, role: null, superAdmin: false,
          displayId: null, companyName: null, isAuthenticated: false,
        })
      },
    }),
    { name: 'auth' }
  )
)
