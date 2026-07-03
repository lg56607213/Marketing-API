import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { FileText, Wand2, BarChart3, Clock, Plus, TrendingUp, Zap, Activity } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { contentApi } from '@/services/api'
import { useAuthStore } from '@/store/authStore'
import type { ContentResponse } from '@/types'
import { CONTENT_TYPE_LABELS, STATUS_LABELS } from '@/types'
import { formatDateTime } from '@/lib/utils'

const statusVariant = (s: string) => {
  if (s === 'PUBLISHED') return 'success'
  if (s === 'APPROVED') return 'default'
  if (s === 'REJECTED') return 'destructive'
  return 'secondary'
}

export function DashboardPage() {
  const { displayId } = useAuthStore()
  const navigate = useNavigate()
  const [contents, setContents] = useState<ContentResponse[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    contentApi.list().then(setContents).catch(() => {}).finally(() => setLoading(false))
  }, [])

  const totalContents = contents.length
  const published = contents.filter(c => c.status === 'PUBLISHED').length
  const drafts = contents.filter(c => c.status === 'DRAFT').length
  const recent = [...contents].sort((a, b) => b.id - a.id).slice(0, 5)

  const stats = [
    { label: '전체 콘텐츠', value: totalContents, icon: FileText, color: 'text-indigo-500', bg: 'bg-indigo-50' },
    { label: '게시됨', value: published, icon: TrendingUp, color: 'text-emerald-500', bg: 'bg-emerald-50' },
    { label: '초안', value: drafts, icon: Clock, color: 'text-amber-500', bg: 'bg-amber-50' },
    { label: 'AI 생성 횟수', value: totalContents, icon: Zap, color: 'text-violet-500', bg: 'bg-violet-50' },
  ]

  return (
    <div className="space-y-6">
      {/* 환영 메시지 */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold">안녕하세요, {displayId}님 👋</h2>
          <p className="text-muted-foreground text-sm mt-1">오늘의 마케팅 콘텐츠 현황을 확인하세요.</p>
        </div>
        <Button onClick={() => navigate('/content/new')} className="gap-2">
          <Plus className="h-4 w-4" />
          콘텐츠 생성
        </Button>
      </div>

      {/* 통계 카드 */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {stats.map(({ label, value, icon: Icon, color, bg }) => (
          <Card key={label}>
            <CardContent className="pt-6">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm text-muted-foreground">{label}</p>
                  <p className="text-3xl font-bold mt-1">{loading ? '—' : value}</p>
                </div>
                <div className={`${bg} p-2.5 rounded-lg`}>
                  <Icon className={`h-5 w-5 ${color}`} />
                </div>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="grid lg:grid-cols-3 gap-6">
        {/* 최근 콘텐츠 */}
        <Card className="lg:col-span-2">
          <CardHeader className="flex flex-row items-center justify-between pb-3">
            <div>
              <CardTitle className="text-base">최근 콘텐츠</CardTitle>
              <CardDescription>최근 AI 생성 콘텐츠 목록</CardDescription>
            </div>
            <Button variant="ghost" size="sm" onClick={() => navigate('/history')}>전체 보기</Button>
          </CardHeader>
          <CardContent>
            {loading ? (
              <div className="space-y-3">
                {[...Array(3)].map((_, i) => (
                  <div key={i} className="h-12 bg-muted rounded-lg animate-pulse" />
                ))}
              </div>
            ) : recent.length === 0 ? (
              <div className="text-center py-8 text-muted-foreground">
                <FileText className="h-8 w-8 mx-auto mb-2 opacity-40" />
                <p className="text-sm">아직 생성된 콘텐츠가 없습니다.</p>
                <Button variant="outline" size="sm" className="mt-3" onClick={() => navigate('/content/new')}>
                  첫 콘텐츠 생성하기
                </Button>
              </div>
            ) : (
              <div className="divide-y">
                {recent.map((c) => (
                  <div key={c.id} className="flex items-center gap-3 py-3">
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium truncate">{c.topic}</p>
                      <p className="text-xs text-muted-foreground">{c.brandName} · {CONTENT_TYPE_LABELS[c.contentType]} · {formatDateTime(c.createdAt)}</p>
                    </div>
                    <Badge variant={statusVariant(c.status) as 'default'}>{STATUS_LABELS[c.status]}</Badge>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        {/* 빠른 실행 */}
        <div className="space-y-4">
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base">빠른 실행</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              {[
                { label: '콘텐츠 생성', icon: Wand2, to: '/content/new', primary: true },
                { label: '애널리틱스 보기', icon: BarChart3, to: '/analytics' },
                { label: 'AI 히스토리', icon: Activity, to: '/history' },
              ].map(({ label, icon: Icon, to, primary }) => (
                <Button
                  key={to}
                  variant={primary ? 'default' : 'outline'}
                  className="w-full justify-start gap-2"
                  onClick={() => navigate(to)}
                >
                  <Icon className="h-4 w-4" />
                  {label}
                </Button>
              ))}
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base">AI 사용 현황</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-3">
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">이번 달</span>
                  <span className="font-semibold">{totalContents}회 / 무제한</span>
                </div>
                <div className="h-2 bg-muted rounded-full overflow-hidden">
                  <div className="h-full bg-indigo-500 rounded-full" style={{ width: `${Math.min(totalContents * 10, 100)}%` }} />
                </div>
                <p className="text-xs text-muted-foreground">Stub AI 모드 — 무제한 사용 가능</p>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}
