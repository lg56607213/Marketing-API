import { useLocation } from 'react-router-dom'
import { Bell } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useAuthStore } from '@/store/authStore'

const pageTitles: Record<string, string> = {
  '/dashboard': '대시보드',
  '/projects': '프로젝트',
  '/content/new': '콘텐츠 생성',
  '/analytics/market': '마켓 인텔리전스',
  '/analytics/channels': '채널 애널리틱스',
  '/analytics/competitors': '경쟁사 분석',
  '/analytics': '마케팅 인텔리전스 — 개요',
  '/history': 'AI 히스토리',
  '/images': '이미지 라이브러리',
  '/settings': '설정',
  '/admin/companies': '기업 관리',
  '/admin/users': '사용자 관리',
}

export function Header() {
  const location = useLocation()
  const { displayId } = useAuthStore()

  const title =
    Object.entries(pageTitles).find(([path]) => location.pathname.startsWith(path))?.[1] ??
    'AI 마케팅 에이전트'

  return (
    <header className="flex h-14 items-center justify-between border-b bg-background px-6">
      <h1 className="text-base font-semibold text-foreground">{title}</h1>
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="icon" className="h-8 w-8 relative">
          <Bell className="h-4 w-4" />
          <span className="absolute top-1.5 right-1.5 h-1.5 w-1.5 rounded-full bg-indigo-500" />
        </Button>
        <div className="text-sm text-muted-foreground hidden sm:block">
          {displayId}
        </div>
      </div>
    </header>
  )
}
