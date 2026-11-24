import { Navigate, Outlet } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'
import { Sidebar } from './Sidebar'
import { Header } from './Header'

export function AppLayout() {
  const { isAuthenticated } = useAuthStore()

  if (!isAuthenticated) return <Navigate to="/login" replace />

  return (
    <div className="flex h-screen overflow-hidden bg-background">
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-y-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

export function AdminRoute() {
  const { role } = useAuthStore()
  if (role !== 'ADMIN') return <Navigate to="/dashboard" replace />
  return <Outlet />
}

/** 최고 관리자(admin) 전용 화면 */
export function SuperAdminRoute() {
  const { superAdmin } = useAuthStore()
  if (!superAdmin) return <Navigate to="/dashboard" replace />
  return <Outlet />
}
