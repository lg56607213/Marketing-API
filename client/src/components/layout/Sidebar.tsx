import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard, FolderOpen, Wand2, BarChart3, History,
  ImageIcon, Settings, Building2, Users, LogOut, Zap,
  TrendingUp, Radio, Globe, MousePointerClick, Gavel, FileBarChart } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { cn } from '@/lib/utils'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'

const topNavItems = [
  { to: '/dashboard', icon: LayoutDashboard, label: '대시보드' },
  { to: '/projects', icon: FolderOpen, label: '프로젝트' },
  { to: '/content/new', icon: Wand2, label: '콘텐츠 생성' },
]

const analyticsItems = [
  { to: '/analytics', icon: BarChart3, label: '개요', exact: true },
  { to: '/analytics/market', icon: TrendingUp, label: '마켓 인텔리전스', exact: false },
  { to: '/analytics/channels', icon: Radio, label: '채널 애널리틱스', exact: false },
  { to: '/analytics/ads', icon: MousePointerClick, label: '검색광고 성과', exact: false },
  { to: '/analytics/bids', icon: Gavel, label: '입찰가 조정', exact: false },
  { to: '/analytics/ad-report', icon: FileBarChart, label: 'AI 광고 리포트', exact: false },
  { to: '/analytics/competitors', icon: Globe, label: '경쟁사 분석', exact: false },
]

const bottomNavItems = [
  { to: '/history', icon: History, label: 'AI 히스토리' },
  { to: '/images', icon: ImageIcon, label: '이미지 라이브러리' },
  { to: '/settings', icon: Settings, label: '설정' },
]

const adminItems = [
  { to: '/admin/companies', icon: Building2, label: '기업 관리' },
  { to: '/admin/users', icon: Users, label: '사용자 관리' },
]

const NAV_CLS = (isActive: boolean) =>
  cn(
    'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
    isActive
      ? 'bg-indigo-600 text-white'
      : 'text-slate-400 hover:bg-slate-800 hover:text-white'
  )

export function Sidebar() {
  const { displayId, role, logout } = useAuthStore()
  const navigate = useNavigate()
  const location = useLocation()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  const initials = (displayId ?? 'U').slice(0, 2).toUpperCase()
  const isAnalyticsActive = location.pathname.startsWith('/analytics')

  return (
    <aside className="flex h-screen w-60 flex-col bg-slate-900 text-slate-100">
      {/* 로고 */}
      <div className="flex items-center gap-2.5 px-5 py-5">
        <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-indigo-500">
          <Zap className="h-4 w-4 text-white" />
        </div>
        <span className="text-sm font-bold tracking-wide text-white">AI 마케팅 에이전트</span>
      </div>

      <Separator className="bg-slate-800" />

      {/* 내비게이션 */}
      <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-0.5">
        {topNavItems.map(({ to, icon: Icon, label }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) => NAV_CLS(isActive)}
          >
            <Icon className="h-4 w-4 shrink-0" />
            {label}
          </NavLink>
        ))}

        {/* 마케팅 인텔리전스 그룹 */}
        <div className="pt-1.5">
          <div className={cn(
            'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium',
            isAnalyticsActive ? 'text-slate-200' : 'text-slate-500'
          )}>
            <BarChart3 className="h-4 w-4 shrink-0" />
            마케팅 인텔리전스
          </div>
          <div className="ml-3.5 mt-0.5 space-y-0.5 border-l border-slate-700/70 pl-3">
            {analyticsItems.map(({ to, icon: Icon, label, exact }) => (
              <NavLink
                key={to}
                to={to}
                end={exact}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-2.5 rounded-md px-2.5 py-1.5 text-xs font-medium transition-colors',
                    isActive
                      ? 'bg-indigo-600 text-white'
                      : 'text-slate-400 hover:bg-slate-800 hover:text-white'
                  )
                }
              >
                <Icon className="h-3.5 w-3.5 shrink-0" />
                {label}
              </NavLink>
            ))}
          </div>
        </div>

        {bottomNavItems.map(({ to, icon: Icon, label }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) => NAV_CLS(isActive)}
          >
            <Icon className="h-4 w-4 shrink-0" />
            {label}
          </NavLink>
        ))}

        {role === 'ADMIN' && (
          <>
            <div className="px-3 pt-5 pb-1">
              <p className="text-[10px] font-semibold uppercase tracking-widest text-slate-500">
                관리자
              </p>
            </div>
            {adminItems.map(({ to, icon: Icon, label }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) => NAV_CLS(isActive)}
              >
                <Icon className="h-4 w-4 shrink-0" />
                {label}
              </NavLink>
            ))}
          </>
        )}
      </nav>

      <Separator className="bg-slate-800" />

      {/* 사용자 정보 */}
      <div className="flex items-center justify-between px-4 py-4">
        <div className="flex items-center gap-2.5 min-w-0">
          <Avatar className="h-7 w-7 shrink-0">
            <AvatarFallback className="text-[10px] bg-indigo-500">{initials}</AvatarFallback>
          </Avatar>
          <div className="min-w-0">
            <p className="truncate text-sm font-medium text-slate-200">{displayId}</p>
            <p className="text-[10px] text-slate-500">{role === 'ADMIN' ? '관리자' : '사용자'}</p>
          </div>
        </div>
        <Button
          variant="ghost"
          size="icon"
          className="h-7 w-7 shrink-0 text-slate-400 hover:text-white hover:bg-slate-800"
          onClick={handleLogout}
          title="로그아웃"
        >
          <LogOut className="h-4 w-4" />
        </Button>
      </div>
    </aside>
  )
}
