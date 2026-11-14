import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  AlertCircle, AlertTriangle, Building2, Check, CheckCircle2, Loader2, Minus, TrendingDown, TrendingUp, XCircle,
} from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { onboardingApi } from '@/services/api'
import type { OnboardingAlternative, OnboardingProposal } from '@/types'
import { cn } from '@/lib/utils'

const won = (v: number) => `${Math.round(v).toLocaleString()}원`
const num = (v: number) => Math.round(v).toLocaleString()
const toNumber = (v: string) => Number(v.replace(/[^0-9.]/g, '')) || 0

const OPTION_ICON = { INCREASE: TrendingUp, REQUESTED: Minus, DECREASE: TrendingDown } as const
const OPTION_TITLE = {
  INCREASE: '1안 · 광고비를 늘릴 때',
  REQUESTED: '2안 · 요청하신 대로 할 때',
  DECREASE: '3안 · 광고비를 줄일 때',
} as const

function SiteRow({ ok, label }: { ok: boolean; label: string }) {
  return (
    <span className={cn('flex items-center gap-1.5', ok ? 'text-emerald-800' : 'text-muted-foreground')}>
      {ok ? <CheckCircle2 className="h-4 w-4" /> : <XCircle className="h-4 w-4" />} {label}
    </span>
  )
}

function AlternativeCard({
  option, selected, busy, onSelect,
}: {
  option: OnboardingAlternative
  selected: boolean
  busy: boolean
  onSelect: () => void
}) {
  const Icon = OPTION_ICON[option.key]
  return (
    <Card className={cn('flex flex-col', option.recommended && 'border-primary shadow-md', selected && 'ring-2 ring-emerald-500')}>
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between gap-2">
          <CardTitle className="flex items-center gap-2 text-base">
            <Icon className="h-4 w-4" /> {OPTION_TITLE[option.key]}
          </CardTitle>
          {option.recommended && <Badge className="shrink-0 text-[10px]">추천</Badge>}
        </div>
        <CardDescription className="text-xs">
          월 {won(option.monthlyBudget)} · 하루 {won(option.dailyBudget)}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-1 flex-col gap-4">
        <div>
          <p className="text-[11px] text-muted-foreground">기대 전환 (문의·구매)</p>
          <p className="text-2xl font-semibold">
            약 {num(option.expectedConversions)}건
            <span className="ml-2 text-xs font-normal text-muted-foreground">
              {num(option.conversionsLow)}~{num(option.conversionsHigh)}건 · 목표의 {num(option.goalRatePct)}%
            </span>
          </p>
        </div>
        <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
          {[
            ['실제 예상 지출', won(option.expectedCost)],
            ['예상 클릭', `${num(option.expectedClicks)}회`],
            ['전환 1건당 비용', option.costPerConversion > 0 ? won(option.costPerConversion) : '-'],
            ['추가 전환 1건의 값', option.extraCostPerExtraConversion != null ? won(option.extraCostPerExtraConversion) : '-'],
            ['기대 매출', option.revenue != null ? won(option.revenue) : '-'],
            ['광고수익률', option.roasPct != null ? `${num(option.roasPct)}%` : '-'],
          ].map(([label, value]) => (
            <div key={label}>
              <dt className="text-[11px] text-muted-foreground">{label}</dt>
              <dd className="font-medium">{value}</dd>
            </div>
          ))}
        </dl>
        <p className="text-sm leading-relaxed text-muted-foreground">{option.effect}</p>
        <details className="text-sm">
          <summary className="cursor-pointer text-muted-foreground">키워드 {option.keywordCount}개와 입찰가 보기</summary>
          <table className="mt-2 w-full text-xs">
            <thead>
              <tr className="text-muted-foreground">
                <th className="py-1 text-left font-medium">키워드</th>
                <th className="py-1 text-right font-medium">입찰가</th>
                <th className="py-1 text-right font-medium">클릭</th>
                <th className="py-1 text-right font-medium">비용</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {option.plan.keywords.filter((k) => k.selected).map((k) => (
                <tr key={k.keyword}>
                  <td className="py-1">{k.keyword}</td>
                  <td className="py-1 text-right">{won(k.suggestedBid)}</td>
                  <td className="py-1 text-right">{num(k.expectedClicks)}</td>
                  <td className="py-1 text-right">{won(k.expectedCost)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </details>
        <Button
          className="mt-auto gap-2"
          variant={selected ? 'outline' : option.recommended ? 'default' : 'outline'}
          disabled={busy || selected}
          onClick={onSelect}
        >
          {selected ? <><Check className="h-4 w-4" /> 이 안으로 진행 중</> : '이 안으로 진행'}
        </Button>
      </CardContent>
    </Card>
  )
}

export function OnboardingPage() {
  const navigate = useNavigate()
  const [proposal, setProposal] = useState<OnboardingProposal | null>(null)
  const [editing, setEditing] = useState(true)
  const [form, setForm] = useState({
    companyName: '', homepageUrl: '', businessDescription: '', keywords: '',
    monthlyBudget: '', targetConversions: '', saleValue: '', visitors: '', inquiries: '',
  })
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState<'submit' | 'select' | null>(null)
  const [error, setError] = useState<string | null>(null)

  const fill = (p: OnboardingProposal) => {
    setProposal(p)
    setForm({
      companyName: p.profile.companyName,
      homepageUrl: p.profile.homepageUrl,
      businessDescription: p.profile.businessDescription,
      keywords: p.profile.keywords.join(', '),
      monthlyBudget: String(p.profile.monthlyBudget),
      targetConversions: String(p.profile.targetConversions),
      saleValue: p.profile.saleValue != null ? String(p.profile.saleValue) : '',
      visitors: p.profile.siteMonthlyVisitors != null ? String(p.profile.siteMonthlyVisitors) : '',
      inquiries: p.profile.siteMonthlyConversions != null ? String(p.profile.siteMonthlyConversions) : '',
    })
  }

  useEffect(() => {
    onboardingApi
      .current()
      .then((current) => {
        if (current) {
          fill(current)
          setEditing(false)
        }
      })
      .catch(() => undefined)
      .finally(() => setLoading(false))
  }, [])

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    setForm((prev) => ({ ...prev, [key]: e.target.value }))

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    const keywords = form.keywords.split(/[,\n]/).map((k) => k.trim()).filter(Boolean)
    if (!form.companyName.trim() || !form.homepageUrl.trim() || !form.businessDescription.trim()) {
      setError('업체명, 홈페이지, 사업 내용을 모두 적어 주세요.')
      return
    }
    if (keywords.length === 0 || keywords.length > 5) {
      setError('핵심 키워드를 1~5개 적어 주세요. 쉼표로 구분합니다.')
      return
    }
    if (toNumber(form.monthlyBudget) < 10000) {
      setError('한 달 광고 가능 예산을 10,000원 이상으로 적어 주세요.')
      return
    }
    if (toNumber(form.targetConversions) < 1) {
      setError('한 달 목표 건수를 1 이상으로 적어 주세요.')
      return
    }
    setBusy('submit')
    try {
      fill(
        await onboardingApi.submit({
          companyName: form.companyName.trim(),
          homepageUrl: form.homepageUrl.trim(),
          businessDescription: form.businessDescription.trim(),
          keywords,
          monthlyBudget: toNumber(form.monthlyBudget),
          targetConversions: toNumber(form.targetConversions),
          saleValue: form.saleValue ? toNumber(form.saleValue) : null,
          siteMonthlyVisitors: form.visitors ? toNumber(form.visitors) : null,
          siteMonthlyConversions: form.inquiries ? toNumber(form.inquiries) : null,
        })
      )
      setEditing(false)
    } catch (err) {
      const message = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      setError(message ?? '제안을 만들지 못했습니다. 잠시 뒤 다시 시도하세요.')
    } finally {
      setBusy(null)
    }
  }

  const select = async (key: string) => {
    setError(null)
    setBusy('select')
    try {
      fill(await onboardingApi.select(key))
    } catch (err) {
      const message = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      setError(message ?? '선택을 저장하지 못했습니다.')
    } finally {
      setBusy(null)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" /> 불러오는 중
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <p className="text-sm text-muted-foreground">
        업체와 광고 목표를 알려 주시면 홈페이지를 확인하고, 광고비를 늘릴 때 · 요청대로 할 때 · 줄일 때의 효과를 비교해 제안합니다.
        제안만 하고 광고 계정은 바꾸지 않습니다.
      </p>

      {error && (
        <div className="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          <AlertCircle className="h-4 w-4 shrink-0" /> {error}
        </div>
      )}

      {editing ? (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="flex items-center gap-2 text-base">
              <Building2 className="h-4 w-4" /> 업체 정보와 광고 목표
            </CardTitle>
            <CardDescription className="text-xs">아는 만큼만 적어도 됩니다. 선택 항목을 채울수록 제안이 정확해집니다.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={submit} className="space-y-5">
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="ob-name">업체명</Label>
                  <Input id="ob-name" placeholder="예: 플릿케어" value={form.companyName} onChange={set('companyName')} />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="ob-home">업체 홈페이지</Label>
                  <Input id="ob-home" placeholder="예: www.example.com" value={form.homepageUrl} onChange={set('homepageUrl')} />
                </div>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="ob-desc">사업 내용</Label>
                <Textarea id="ob-desc" rows={4}
                  placeholder="무엇을 누구에게 파는지 적어 주세요. 예: 법인 대상 화물차 장기 렌트와 정비 관리. 주 고객은 운송업체."
                  value={form.businessDescription} onChange={set('businessDescription')} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="ob-kw">핵심 키워드 (1~5개, 쉼표로 구분)</Label>
                <Input id="ob-kw" placeholder="고객이 검색할 만한 말. 예: 화물차 렌트, 법인 차량 관리" value={form.keywords} onChange={set('keywords')} />
              </div>
              <div className="grid gap-4 sm:grid-cols-3">
                <div className="space-y-1.5">
                  <Label htmlFor="ob-budget">한 달 광고 가능 예산 (원)</Label>
                  <Input id="ob-budget" inputMode="numeric" placeholder="예: 500000" value={form.monthlyBudget} onChange={set('monthlyBudget')} />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="ob-target">한 달 목표 건수 (문의·견적·구매)</Label>
                  <Input id="ob-target" inputMode="numeric" placeholder="예: 20" value={form.targetConversions} onChange={set('targetConversions')} />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="ob-sale">1건당 평균 매출 (원, 선택)</Label>
                  <Input id="ob-sale" inputMode="numeric" placeholder="넣으면 매출·수익률까지 계산" value={form.saleValue} onChange={set('saleValue')} />
                </div>
              </div>
              <div className="rounded-md border bg-muted/20 p-4">
                <p className="text-sm font-medium">홈페이지 실적 (선택)</p>
                <p className="mt-1 text-xs text-muted-foreground">
                  넣으면 가정값 대신 내 홈페이지의 실제 전환율로 계산합니다. 모르면 비워 두세요.
                </p>
                <div className="mt-3 grid gap-4 sm:grid-cols-2">
                  <div className="space-y-1.5">
                    <Label htmlFor="ob-visitors">최근 한 달 방문자수</Label>
                    <Input id="ob-visitors" inputMode="numeric" placeholder="예: 2000" value={form.visitors} onChange={set('visitors')} />
                  </div>
                  <div className="space-y-1.5">
                    <Label htmlFor="ob-inq">같은 기간 문의·구매 건수</Label>
                    <Input id="ob-inq" inputMode="numeric" placeholder="예: 40" value={form.inquiries} onChange={set('inquiries')} />
                  </div>
                </div>
              </div>
              <div className="flex gap-2">
                <Button type="submit" disabled={busy !== null} className="gap-2">
                  {busy === 'submit' && <Loader2 className="h-4 w-4 animate-spin" />}
                  {proposal ? '다시 제안 받기' : '제안 받기'}
                </Button>
                {proposal && (
                  <Button type="button" variant="ghost" disabled={busy !== null} onClick={() => setEditing(false)}>
                    취소
                  </Button>
                )}
              </div>
            </form>
          </CardContent>
        </Card>
      ) : (
        proposal && (
          <>
            <Card>
              <CardContent className="flex flex-wrap items-start justify-between gap-4 p-5">
                <div className="min-w-0">
                  <p className="text-base font-semibold">{proposal.profile.companyName}</p>
                  <p className="truncate text-sm text-muted-foreground">{proposal.profile.homepageUrl}</p>
                  <p className="mt-2 text-sm">
                    한 달 예산 <span className="font-medium">{won(proposal.profile.monthlyBudget)}</span> · 목표{' '}
                    <span className="font-medium">{num(proposal.profile.targetConversions)}건</span> · 키워드{' '}
                    {proposal.profile.keywords.join(', ')}
                  </p>
                </div>
                <Button variant="outline" size="sm" onClick={() => setEditing(true)}>정보 수정</Button>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="text-base">홈페이지 확인 결과</CardTitle>
                <CardDescription className="text-xs">
                  {proposal.siteCheck.reachable
                    ? proposal.siteCheck.title ?? '제목 없음'
                    : '홈페이지를 열어 보지 못했습니다.'}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-3 text-sm">
                {proposal.siteCheck.reachable && (
                  <div className="flex flex-wrap gap-x-6 gap-y-2">
                    <SiteRow ok label="접속됨" />
                    <SiteRow ok={proposal.siteCheck.naverTracking} label="네이버 전환 추적" />
                    <SiteRow ok={proposal.siteCheck.googleAnalytics} label="구글 애널리틱스" />
                    <SiteRow ok={proposal.siteCheck.hasForm} label="문의 양식·전화 버튼" />
                  </div>
                )}
                {proposal.siteCheck.notes.map((n) => (
                  <p key={n} className="text-muted-foreground">{n}</p>
                ))}
              </CardContent>
            </Card>

            <div className="rounded-lg border border-primary/40 bg-primary/5 px-5 py-4">
              <p className="text-sm font-semibold">제안</p>
              <p className="mt-1 text-sm leading-relaxed">{proposal.recommendation}</p>
            </div>

            {proposal.warnings.length > 0 && (
              <div className="space-y-2 rounded-md border border-amber-500/30 bg-amber-50 px-4 py-3 text-sm text-amber-900">
                {proposal.warnings.map((w) => (
                  <p key={w} className="flex gap-2">
                    <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" /> {w}
                  </p>
                ))}
              </div>
            )}

            <div className="grid gap-4 lg:grid-cols-3">
              {proposal.alternatives.map((option) => (
                <AlternativeCard
                  key={option.key}
                  option={option}
                  selected={proposal.selectedOption === option.key}
                  busy={busy !== null}
                  onSelect={() => void select(option.key)}
                />
              ))}
            </div>

            {proposal.selectedOption && (
              <Card>
                <CardHeader className="pb-3">
                  <CardTitle className="text-base">다음 단계</CardTitle>
                  <CardDescription className="text-xs">고른 안이 운영 계획으로 저장됐습니다.</CardDescription>
                </CardHeader>
                <CardContent className="space-y-3 text-sm">
                  <ol className="list-decimal space-y-1.5 pl-5">
                    <li>설정에서 네이버 검색광고 계정을 연결합니다.</li>
                    <li>운영 계획 화면의 키워드와 입찰가, 일예산을 네이버 광고에 등록합니다.</li>
                    <li>자동 운영을 켜면 매일 입찰가와 예산을 조정하고 브리핑으로 보고합니다.</li>
                  </ol>
                  <div className="flex flex-wrap gap-2">
                    <Button size="sm" onClick={() => navigate('/settings')}>네이버 계정 연결</Button>
                    <Button size="sm" variant="outline" onClick={() => navigate('/analytics/plan')}>운영 계획 보기</Button>
                  </div>
                </CardContent>
              </Card>
            )}
          </>
        )
      )}
    </div>
  )
}
