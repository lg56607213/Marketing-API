import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { adsApi, onboardingApi, plannerApi } from '@/services/api'
import type { AdSummary, Briefing } from '@/types'

const won = (v: number) => `${Math.round(v).toLocaleString()}원`
const num = (v: number) => Math.round(v).toLocaleString()

type State =
  | { kind: 'loading' }
  | { kind: 'empty'; hasProfile: boolean }
  | { kind: 'ready'; summary: AdSummary; briefing: Briefing | null }

/** 최근 30일 광고 실적 요약. 실제로 수집된 데이터만 보여 주고, 없으면 무엇을 해야 하는지 안내한다. */
export function AdOverview() {
  const navigate = useNavigate()
  const [state, setState] = useState<State>({ kind: 'loading' })

  useEffect(() => {
    let cancelled = false
    const load = async () => {
      const [summary, briefings, profile] = await Promise.all([
        adsApi.summary().catch(() => null),
        plannerApi.briefings().catch(() => [] as Briefing[]),
        onboardingApi.current().catch(() => null),
      ])
      if (cancelled) return
      if (!summary || summary.impCnt === 0) {
        setState({ kind: 'empty', hasProfile: profile != null })
      } else {
        setState({ kind: 'ready', summary, briefing: briefings[0] ?? null })
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [])

  if (state.kind === 'loading') {
    return (
      <Card>
        <CardContent className="flex items-center gap-2 py-8 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" /> 광고 실적을 불러오는 중
        </CardContent>
      </Card>
    )
  }

  if (state.kind === 'empty') {
    return (
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">아직 광고 실적이 없습니다</CardTitle>
          <CardDescription className="text-xs">아래 순서로 시작하면 이 자리에 실제 광고비·클릭·전환이 나타납니다.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3 text-sm">
          <ol className="list-decimal space-y-1.5 pl-5">
            <li>{state.hasProfile ? '업체 정보 등록 완료' : '업체 정보와 광고 목표를 등록하고 예산 제안을 받습니다.'}</li>
            <li>설정에서 네이버 검색광고 계정을 연결합니다.</li>
            <li>검색광고 성과 화면에서 동기화를 눌러 실적을 가져옵니다.</li>
          </ol>
          <div className="flex flex-wrap gap-2">
            {!state.hasProfile && <Button size="sm" onClick={() => navigate('/onboarding')}>업체 정보 등록</Button>}
            <Button size="sm" variant="outline" onClick={() => navigate('/settings')}>네이버 계정 연결</Button>
            <Button size="sm" variant="outline" onClick={() => navigate('/analytics/ads')}>실적 가져오기</Button>
          </div>
        </CardContent>
      </Card>
    )
  }

  const { summary, briefing } = state
  const days = summary.daily.slice(-30)
  const maxCost = Math.max(1, ...days.map((d) => d.salesAmt))
  const cards = [
    { label: '광고비', value: won(summary.salesAmt) },
    { label: '클릭', value: `${num(summary.clkCnt)}회`, sub: `클릭률 ${summary.ctr.toFixed(2)}%` },
    { label: '전환', value: `${num(summary.ccnt)}건`, sub: `전환율 ${summary.cvr.toFixed(2)}%` },
    { label: '전환 1건당 비용', value: summary.ccnt > 0 ? won(summary.cpa) : '-', sub: `클릭당 ${won(summary.cpc)}` },
  ]

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {cards.map((c) => (
          <Card key={c.label}>
            <CardContent className="p-4">
              <p className="text-[11px] text-muted-foreground">{c.label}</p>
              <p className="text-xl font-semibold">{c.value}</p>
              {c.sub && <p className="text-[11px] text-muted-foreground">{c.sub}</p>}
            </CardContent>
          </Card>
        ))}
      </div>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">일별 광고비와 전환</CardTitle>
          <CardDescription className="text-xs">
            {summary.since} ~ {summary.until} · 막대는 광고비, 점은 전환이 있었던 날
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex h-28 items-end gap-[3px]">
            {days.map((d) => (
              <div key={d.statDate} className="flex flex-1 flex-col items-center justify-end gap-1"
                title={`${d.statDate} · 광고비 ${won(d.salesAmt)} · 클릭 ${num(d.clkCnt)}회 · 전환 ${num(d.ccnt)}건`}>
                <span className={d.ccnt > 0 ? 'h-1.5 w-1.5 rounded-full bg-emerald-500' : 'h-1.5 w-1.5'} />
                <span className="w-full rounded-t bg-indigo-400" style={{ height: `${Math.max(2, (d.salesAmt / maxCost) * 88)}px` }} />
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

      {briefing && (
        <Card>
          <CardContent className="flex flex-wrap items-center justify-between gap-3 p-4">
            <div className="min-w-0">
              <p className="text-[11px] text-muted-foreground">최근 브리핑 · {briefing.until}</p>
              <p className="text-sm font-medium">{briefing.headline}</p>
            </div>
            <Button size="sm" variant="outline" onClick={() => navigate('/analytics/briefings')}>브리핑 보기</Button>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
