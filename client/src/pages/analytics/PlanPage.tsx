import { useEffect, useState } from 'react'
import { AlertCircle, AlertTriangle, Loader2, Target } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { plannerApi } from '@/services/api'
import type { GoalType, PlanProposal } from '@/types'
import { cn } from '@/lib/utils'

const won = (v: number) => `${Math.round(v).toLocaleString()}원`
const num = (v: number) => Math.round(v).toLocaleString()
const toNumber = (v: string) => Number(v.replace(/[^0-9.]/g, ''))

export function PlanPage() {
  const [plan, setPlan] = useState<PlanProposal | null>(null)
  const [goalType, setGoalType] = useState<GoalType>('CONVERSIONS')
  const [seeds, setSeeds] = useState('')
  const [target, setTarget] = useState('')
  const [budget, setBudget] = useState('')
  const [saleValue, setSaleValue] = useState('')
  const [cvr, setCvr] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    plannerApi
      .currentPlan()
      .then((current) => {
        if (current) {
          setPlan(current)
          setSeeds(current.seedKeywords.join(', '))
          setGoalType(current.goalType)
        }
      })
      .catch(() => undefined)
  }, [])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    const seedKeywords = seeds.split(/[,\n]/).map((s) => s.trim()).filter(Boolean)
    if (seedKeywords.length === 0 || seedKeywords.length > 5) {
      setError('핵심 키워드를 1~5개 입력하세요. 쉼표로 구분합니다.')
      return
    }
    if (goalType === 'CONVERSIONS' && !(toNumber(target) > 0)) {
      setError('월 목표 건수를 입력하세요.')
      return
    }
    if (goalType === 'BUDGET' && !(toNumber(budget) >= 10000)) {
      setError('월 예산을 10,000원 이상으로 입력하세요.')
      return
    }
    setLoading(true)
    try {
      setPlan(
        await plannerApi.propose({
          seedKeywords,
          goalType,
          targetConversions: goalType === 'CONVERSIONS' ? toNumber(target) : null,
          monthlyBudget: goalType === 'BUDGET' ? toNumber(budget) : null,
          saleValue: saleValue ? toNumber(saleValue) : null,
          assumedCvrPct: cvr ? toNumber(cvr) : null,
        })
      )
    } catch (err) {
      const message = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      setError(message ?? '계획을 만들지 못했습니다. 네이버 연동 상태를 확인하세요.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-6">
      <p className="text-sm text-muted-foreground">
        목표를 알려주면 어떤 키워드에 얼마를 써서 어떤 결과를 기대할 수 있는지 제안합니다. 제안만 하고 광고 계정은 바꾸지 않습니다.
      </p>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">목표 입력</CardTitle>
          <CardDescription className="text-xs">계획을 다시 받으면 이전 계획을 대신합니다.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={submit} className="space-y-4">
            <div className="flex gap-2">
              {([
                ['CONVERSIONS', '목표 건수로 예산 받기'],
                ['BUDGET', '정해진 예산으로 기대 성과 보기'],
              ] as [GoalType, string][]).map(([key, label]) => (
                <Button
                  key={key}
                  type="button"
                  size="sm"
                  variant={goalType === key ? 'default' : 'outline'}
                  onClick={() => setGoalType(key)}
                >
                  {label}
                </Button>
              ))}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="plan-seeds">핵심 키워드 (1~5개, 쉼표로 구분)</Label>
              <Input
                id="plan-seeds"
                placeholder="예: 화물차 리스, 특장차"
                value={seeds}
                onChange={(e) => setSeeds(e.target.value)}
              />
            </div>

            <div className="grid gap-4 sm:grid-cols-3">
              {goalType === 'CONVERSIONS' ? (
                <div className="space-y-1.5">
                  <Label htmlFor="plan-target">월 목표 건수 (문의·견적·구매)</Label>
                  <Input id="plan-target" inputMode="numeric" placeholder="예: 30" value={target}
                    onChange={(e) => setTarget(e.target.value)} />
                </div>
              ) : (
                <div className="space-y-1.5">
                  <Label htmlFor="plan-budget">월 예산 (원)</Label>
                  <Input id="plan-budget" inputMode="numeric" placeholder="예: 500000" value={budget}
                    onChange={(e) => setBudget(e.target.value)} />
                </div>
              )}
              <div className="space-y-1.5">
                <Label htmlFor="plan-sale">1건당 평균 매출 (원, 선택)</Label>
                <Input id="plan-sale" inputMode="numeric" placeholder="비우면 매출 계산 생략" value={saleValue}
                  onChange={(e) => setSaleValue(e.target.value)} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="plan-cvr">전환율 가정 (%, 선택)</Label>
                <Input id="plan-cvr" inputMode="decimal" placeholder="비우면 2%" value={cvr}
                  onChange={(e) => setCvr(e.target.value)} />
              </div>
            </div>

            {error && (
              <div className="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
                <AlertCircle className="h-4 w-4 shrink-0" /> {error}
              </div>
            )}

            <Button type="submit" disabled={loading} className="gap-2">
              {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Target className="h-4 w-4" />}
              {plan ? '계획 다시 받기' : '계획 받기'}
            </Button>
          </form>
        </CardContent>
      </Card>

      {plan && (
        <>
          {plan.warnings.length > 0 && (
            <div className="space-y-2 rounded-md border border-amber-500/30 bg-amber-50 px-4 py-3 text-sm text-amber-900">
              {plan.warnings.map((w) => (
                <p key={w} className="flex gap-2">
                  <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" /> {w}
                </p>
              ))}
            </div>
          )}

          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {[
              { label: '권장 월 예산', value: won(plan.monthlyBudget) },
              { label: '일예산', value: won(plan.dailyBudget) },
              { label: '기대 전환 (기본)', value: `${num(plan.expectedConversions)}건` },
              { label: '전환당 비용 목표', value: plan.targetCpa > 0 ? won(plan.targetCpa) : '-' },
            ].map((s) => (
              <Card key={s.label}>
                <CardContent className="p-4">
                  <p className="text-[11px] text-muted-foreground">{s.label}</p>
                  <p className="text-lg font-semibold">{s.value}</p>
                </CardContent>
              </Card>
            ))}
          </div>

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base">기대 효과</CardTitle>
              <CardDescription className="text-xs">
                예상 클릭 {num(plan.expectedClicks)}회 기준. 전환율이 달라지면 결과가 이렇게 바뀝니다.
              </CardDescription>
            </CardHeader>
            <CardContent className="p-0">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-muted/40 text-xs text-muted-foreground">
                      <th className="px-6 py-2.5 text-left font-semibold">시나리오</th>
                      <th className="px-3 py-2.5 text-right font-semibold">전환율</th>
                      <th className="px-3 py-2.5 text-right font-semibold">전환</th>
                      <th className="px-3 py-2.5 text-right font-semibold">전환당 비용</th>
                      <th className="px-3 py-2.5 text-right font-semibold">매출</th>
                      <th className="px-6 py-2.5 text-right font-semibold">광고수익률</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {plan.scenarios.map((s) => (
                      <tr key={s.name} className={cn(s.name === '기본' && 'bg-muted/20 font-medium')}>
                        <td className="px-6 py-2.5">{s.name}</td>
                        <td className="px-3 py-2.5 text-right">{s.cvrPct}%</td>
                        <td className="px-3 py-2.5 text-right">{num(s.conversions)}건</td>
                        <td className="px-3 py-2.5 text-right">{s.cpa > 0 ? won(s.cpa) : '-'}</td>
                        <td className="px-3 py-2.5 text-right">{s.revenue != null ? won(s.revenue) : '-'}</td>
                        <td className="px-6 py-2.5 text-right">{s.roasPct != null ? `${num(s.roasPct)}%` : '-'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base">키워드 계획</CardTitle>
              <CardDescription className="text-xs">
                「포함」 키워드를 추천 입찰가로 등록하면 됩니다. 나머지는 예산·목표 안에 들지 않아 뺀 후보입니다.
              </CardDescription>
            </CardHeader>
            <CardContent className="p-0">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-muted/40 text-xs text-muted-foreground">
                      <th className="px-6 py-2.5 text-left font-semibold">키워드</th>
                      <th className="px-3 py-2.5 text-left font-semibold">계획</th>
                      <th className="px-3 py-2.5 text-right font-semibold">월 검색수</th>
                      <th className="px-3 py-2.5 text-left font-semibold">경쟁</th>
                      <th className="px-3 py-2.5 text-right font-semibold">추천 입찰가</th>
                      <th className="px-3 py-2.5 text-right font-semibold">예상 클릭</th>
                      <th className="px-6 py-2.5 text-right font-semibold">예상 비용</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {plan.keywords.map((k) => (
                      <tr key={k.keyword} className={cn(!k.selected && 'text-muted-foreground')}>
                        <td className="px-6 py-2.5 font-medium">{k.keyword}</td>
                        <td className="px-3 py-2.5">
                          <Badge variant={k.selected ? 'default' : 'outline'} className="text-[10px]">
                            {k.selected ? '포함' : '제외'}
                          </Badge>
                        </td>
                        <td className="px-3 py-2.5 text-right">{num(k.monthlySearches)}</td>
                        <td className="px-3 py-2.5">{k.competition}</td>
                        <td className="px-3 py-2.5 text-right">{won(k.suggestedBid)}</td>
                        <td className="px-3 py-2.5 text-right">{k.selected ? num(k.expectedClicks) : '-'}</td>
                        <td className="px-6 py-2.5 text-right">{k.selected ? won(k.expectedCost) : '-'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </CardContent>
          </Card>

          <div className="grid gap-6 lg:grid-cols-2">
            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="text-base">운영 방법</CardTitle>
                <CardDescription className="text-xs">순서대로 진행합니다.</CardDescription>
              </CardHeader>
              <CardContent>
                <ol className="list-decimal space-y-2 pl-5 text-sm">
                  {plan.steps.map((s) => <li key={s}>{s}</li>)}
                </ol>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="text-base">계산에 쓴 가정</CardTitle>
                <CardDescription className="text-xs">이 가정이 틀리면 결과도 달라집니다.</CardDescription>
              </CardHeader>
              <CardContent>
                <ul className="list-disc space-y-2 pl-5 text-sm">
                  {plan.assumptions.map((a) => <li key={a}>{a}</li>)}
                </ul>
              </CardContent>
            </Card>
          </div>
        </>
      )}
    </div>
  )
}
