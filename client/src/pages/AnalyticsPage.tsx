import { BarChart3, TrendingUp, Search, MousePointerClick, Eye, FileText, Zap, Clock } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'

const widgets = [
  {
    title: '방문자 추이',
    icon: TrendingUp,
    color: 'text-indigo-500',
    bg: 'bg-indigo-50',
    desc: '일별/주별/월별 방문자 추이',
    status: '준비 중',
  },
  {
    title: '키워드 순위',
    icon: Search,
    color: 'text-emerald-500',
    bg: 'bg-emerald-50',
    desc: '검색 엔진별 SEO 키워드 순위',
    status: '준비 중',
  },
  {
    title: 'SEO 점수',
    icon: BarChart3,
    color: 'text-violet-500',
    bg: 'bg-violet-50',
    desc: '콘텐츠 SEO 건강 상태 및 최적화 점수',
    status: '준비 중',
  },
  {
    title: '클릭률(CTR)',
    icon: MousePointerClick,
    color: 'text-amber-500',
    bg: 'bg-amber-50',
    desc: '채널별 게시된 콘텐츠 CTR',
    status: '준비 중',
  },
  {
    title: '검색 볼륨',
    icon: Eye,
    color: 'text-sky-500',
    bg: 'bg-sky-50',
    desc: '추적 키워드의 월간 검색 볼륨',
    status: '준비 중',
  },
  {
    title: '콘텐츠 성과',
    icon: FileText,
    color: 'text-rose-500',
    bg: 'bg-rose-50',
    desc: '콘텐츠별 참여도, 공유, 전환율',
    status: '준비 중',
  },
  {
    title: 'AI 사용 통계',
    icon: Zap,
    color: 'text-orange-500',
    bg: 'bg-orange-50',
    desc: 'AI 생성 횟수, 토큰, 비용 분석',
    status: '준비 중',
  },
  {
    title: '게시까지 소요 시간',
    icon: Clock,
    color: 'text-teal-500',
    bg: 'bg-teal-50',
    desc: '초안에서 게시까지 평균 소요 시간',
    status: '준비 중',
  },
]

export function AnalyticsPage() {
  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <div>
          <p className="text-sm text-muted-foreground">애널리틱스 연동 기능이 개발 중입니다. 아래 위젯은 예정된 기능을 미리 보여줍니다.</p>
        </div>
        <Badge variant="secondary" className="shrink-0 ml-auto">베타 미리보기</Badge>
      </div>

      {/* 차트 플레이스홀더 */}
      <Card className="border-dashed">
        <CardContent className="flex flex-col items-center justify-center py-16 text-center">
          <BarChart3 className="h-14 w-14 text-muted-foreground/30 mb-4" />
          <p className="font-medium text-muted-foreground">애널리틱스 대시보드</p>
          <p className="text-sm text-muted-foreground/70 mt-1 max-w-sm">
            Google Analytics, Search Console 등 애널리틱스 제공업체를 연결하면 실시간 인사이트를 확인할 수 있습니다.
          </p>
        </CardContent>
      </Card>

      <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {widgets.map(({ title, icon: Icon, color, bg, desc, status }) => (
          <Card key={title} className="relative overflow-hidden">
            <div className="absolute top-0 right-0 w-24 h-24 -mr-6 -mt-6 rounded-full opacity-10 bg-gradient-to-br from-current to-transparent" />
            <CardHeader className="pb-2">
              <div className={`w-10 h-10 rounded-lg ${bg} flex items-center justify-center mb-2`}>
                <Icon className={`h-5 w-5 ${color}`} />
              </div>
              <CardTitle className="text-sm">{title}</CardTitle>
              <CardDescription className="text-xs">{desc}</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="h-8 bg-muted/50 rounded animate-pulse" />
              <Badge variant="outline" className="mt-2 text-[10px]">{status}</Badge>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
