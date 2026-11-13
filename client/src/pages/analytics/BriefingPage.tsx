import { useCallback, useEffect, useState } from 'react'
import { AlertCircle, ClipboardList, Loader2, Minus, TrendingDown, TrendingUp, HelpCircle } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { plannerApi } from '@/services/api'
import type { Briefing, BriefingPeriod, BriefingVerdict } from '@/types'
import { cn } from '@/lib/utils'

const fmt = (v: number, unit: string) =>
  unit === '%' ? `${v.toLocaleString(undefined, { maximumFractionDigits: 2 })}%` : `${Math.round(v).toLocaleString()}${unit}`

const VERDICT: Record<BriefingVerdict, { label: string; short: string; icon: typeof TrendingUp; className: string }> = {
  INCREASE: { short: '증액', label: '광고비를 더 써도 됩니다', icon: TrendingUp, className: 'border-emerald-500/40 bg-emerald-50 text-emerald-900' },
  HOLD: { short: '유지', label: '지금 수준을 유지하세요', icon: Minus, className: 'border-slate-300 bg-slate-50 text-slate-900' },
  DECREASE: { short: '감액', label: '광고비를 줄이거나 새는 곳부터 막으세요', icon: TrendingDown, className: 'border-red-500/40 bg-red-50 text-red-900' },
  NEED_DATA: { short: '보류', label: '아직 판단하기 이릅니다', icon: HelpCircle, className: 'border-amber-500/40 bg-amber-50 text-amber-900' },
}

export function BriefingPage() {
  const [briefings, setBriefings] = useState<Briefing[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [generating, setGenerating] = useState<BriefingPeriod | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      const list = await plannerApi.briefings()
      setBriefings(list)
      setSelectedId((current) => current ?? list[0]?.id ?? null)
    } catch {
      setError('브리핑 목록을 불러오지 못했습니다.')
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const generate = async (period: BriefingPeriod) => {
    setGenerating(period)
    setError(null)
    try {
      const created = await plannerApi.generateBriefing(period)
      await load()
      setSelectedId(created.id)
    } catch (err) {
      const message = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      setError(message ?? '브리핑을 만들지 못했습니다.')
    } finally {
      setGenerating(null)
    }
  }

  const briefing = briefings.find((b) => b.id === selectedId) ?? null
  const verdict = briefing ? VERDICT[briefing.verdict] : null

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">
          광고 운영 결과를 일간·주간으로 보고합니다. 자동 수집이 켜져 있으면 매일 아침 만들어집니다.
        </p>
        <div className="flex gap-2">
          {([['DAILY', '어제 브리핑 만들기'], ['WEEKLY', '주간 브리핑 만들기']] as [BriefingPeriod, string][]).map(
            ([period, label]) => (
              <Button key={period} size="sm" variant={period === 'WEEKLY' ? 'default' : 'outline'}
                disabled={generating !== null} onClick={() => void generate(period)} className="gap-2">
                {generating === period && <Loader2 className="h-4 w-4 animate-spin" />}
                {label}
              </Button>
            )
          )}
        </div>
      </div>

      {error && (
        <div className="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          <AlertCircle className="h-4 w-4 shrink-0" /> {error}
        </div>
      )}

      {briefings.length === 0 ? (
        <Card>
          <CardContent className="py-14 text-center text-muted-foreground">
            <ClipboardList className="mx-auto mb-3 h-10 w-10 opacity-30" />
            <p className="text-sm">아직 브리핑이 없습니다. 광고 데이터를 가져온 뒤 위 버튼으로 만들어 보세요.</p>
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-6 lg:grid-cols-[16rem_1fr]">
          <Card className="h-fit">
            <CardContent className="p-2">
              {briefings.map((b) => (
                <button key={b.id} type="button" onClick={() => setSelectedId(b.id)}
                  className={cn('w-full rounded-md px-3 py-2 text-left text-sm transition-colors hover:bg-muted/50',
                    b.id === selectedId && 'bg-muted')}>
                  <span className="flex items-center justify-between gap-2">
                    <span className="font-medium">{b.period === 'DAILY' ? '일간' : '주간'}</span>
                    <Badge variant="outline" className="text-[10px]">{VERDICT[b.verdict].short}</Badge>
                  </span>
                  <span className="text-xs text-muted-foreground">
                    {b.period === 'DAILY' ? b.until : `${b.since} ~ ${b.until}`}
                  </span>
                </button>
              ))}
            </CardContent>
          </Card>

          {briefing && verdict && (
            <div className="space-y-6">
              <div className={cn('rounded-lg border px-5 py-4', verdict.className)}>
                <p className="flex items-center gap-2 text-base font-semibold">
                  <verdict.icon className="h-5 w-5" /> {verdict.label}
                </p>
                <p className="mt-2 text-sm leading-relaxed">{briefing.verdictReason}</p>
                {briefing.suggestedDailyBudget != null && (
                  <p className="mt-2 text-sm font-medium">
                    일예산: {fmt(briefing.currentDailyBudget, '원')} → {fmt(briefing.suggestedDailyBudget, '원')}
                  </p>
                )}
              </div>

              <Card>
                <CardHeader className="pb-3">
                  <CardTitle className="text-base">보고</CardTitle>
                  <CardDescription className="text-xs">
                    {briefing.period === 'DAILY' ? briefing.until : `${briefing.since} ~ ${briefing.until}`} · 작성: {briefing.generatedBy === 'rule-based' ? '규칙 기반' : briefing.generatedBy}
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-3 text-sm leading-relaxed">
                  {briefing.narrative.map((p) => <p key={p}>{p}</p>)}
                </CardContent>
              </Card>

              <Card>
                <CardHeader className="pb-3">
                  <CardTitle className="text-base">지표</CardTitle>
                  <CardDescription className="text-xs">
                    비교 기준: {briefing.period === 'DAILY' ? '직전 7일의 하루 평균' : '직전 7일'}
                  </CardDescription>
                </CardHeader>
                <CardContent className="p-0">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="bg-muted/40 text-xs text-muted-foreground">
                        <th className="px-6 py-2.5 text-left font-semibold">지표</th>
                        <th className="px-3 py-2.5 text-right font-semibold">이번</th>
                        <th className="px-3 py-2.5 text-right font-semibold">비교 기준</th>
                        <th className="px-6 py-2.5 text-right font-semibold">증감</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y">
                      {briefing.metrics.map((m) => (
                        <tr key={m.label}>
                          <td className="px-6 py-2.5 font-medium">{m.label}</td>
                          <td className="px-3 py-2.5 text-right">{fmt(m.current, m.unit)}</td>
                          <td className="px-3 py-2.5 text-right text-muted-foreground">{fmt(m.previous, m.unit)}</td>
                          <td className="px-6 py-2.5 text-right">
                            {m.changePct == null ? '-' : `${m.changePct > 0 ? '+' : ''}${m.changePct.toFixed(1)}%`}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </CardContent>
              </Card>

              {briefing.pacing && (
                <Card>
                  <CardHeader className="pb-3">
                    <CardTitle className="text-base">이번 달 목표 대비</CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-3 text-sm">
                    <dl className="grid grid-cols-2 gap-x-6 gap-y-2 sm:grid-cols-4">
                      {[
                        ['계획 예산', fmt(briefing.pacing.monthlyBudget, '원')],
                        ['월말 예상 지출', fmt(briefing.pacing.projectedSpend, '원')],
                        ['목표 전환', fmt(briefing.pacing.targetConversions, '건')],
                        ['월말 예상 전환', fmt(briefing.pacing.projectedConversions, '건')],
                      ].map(([label, value]) => (
                        <div key={label}>
                          <dt className="text-[11px] text-muted-foreground">{label}</dt>
                          <dd className="font-semibold">{value}</dd>
                        </div>
                      ))}
                    </dl>
                    <p className="text-muted-foreground">{briefing.pacing.comment}</p>
                  </CardContent>
                </Card>
              )}

              <Card>
                <CardHeader className="pb-3">
                  <CardTitle className="text-base">권장 조치</CardTitle>
                  <CardDescription className="text-xs">최근 14일 기준입니다. 실행은 입찰가 조정·키워드 화면에서 합니다.</CardDescription>
                </CardHeader>
                <CardContent>
                  {briefing.actions.length === 0 ? (
                    <p className="text-sm text-muted-foreground">지금 손댈 항목이 없습니다.</p>
                  ) : (
                    <ul className="space-y-3 text-sm">
                      {briefing.actions.map((a) => (
                        <li key={`${a.type}-${a.target}`} className="flex flex-wrap items-baseline gap-x-2">
                          <Badge variant="outline" className="text-[10px]">{a.type}</Badge>
                          <span className="font-medium">{a.target}</span>
                          <span className="text-muted-foreground">{a.detail}</span>
                        </li>
                      ))}
                    </ul>
                  )}
                </CardContent>
              </Card>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
