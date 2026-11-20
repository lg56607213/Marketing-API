import { useEffect, useRef, useState } from 'react'
import {
  AlertCircle, AlertTriangle, CheckCircle2, ClipboardCheck, Loader2, MessageSquare, Pencil, RefreshCw, Send, Sparkles, Target,
} from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Checkbox } from '@/components/ui/checkbox'
import { AdScopeBar } from '@/components/ads/AdScopeBar'
import { apiMessage, goalApi } from '@/services/api'
import { useAdScopeStore } from '@/store/adScopeStore'
import type {
  BusinessModel, GoalCheckinResult, GoalEconomicsResult, GoalKind, GoalOption, GoalView, ValueSource,
} from '@/types'
import { cn } from '@/lib/utils'

const won = (v?: number | null) => (v == null ? '-' : `${Math.round(v).toLocaleString('ko-KR')}원`)
const num = (v?: number | null) => (v == null ? '-' : Math.round(v).toLocaleString('ko-KR'))
const pct = (v?: number | null) => (v == null ? '-' : `${Math.round(v * 10) / 10}%`)
const toNumber = (v: string) => {
  const n = Number(v.replace(/[^0-9.]/g, ''))
  return Number.isFinite(n) && v.trim() !== '' ? n : undefined
}
const isoDate = (d: Date) => d.toISOString().slice(0, 10)

const REVENUE_MODEL_LABEL: Record<string, string> = {
  DIRECT: '직접 판매·대여',
  COMMISSION: '중개 수수료',
  SUBSCRIPTION: '정기 결제',
}

const GOAL_LABEL: Record<GoalKind, string> = {
  REVENUE: '매출',
  PROFIT: '이익',
  DEALS: '계약',
  LEADS: '문의',
}

const goalText = (m: BusinessModel) => {
  if (!m.goalType || m.goalAmount == null) return null
  const money = m.goalType === 'REVENUE' || m.goalType === 'PROFIT'
  return `한 달 ${GOAL_LABEL[m.goalType]} ${money ? won(m.goalAmount) : `${num(m.goalAmount)}건`}`
}

const sourceLabel = (s?: ValueSource) => (s === 'ASSUMED' ? '가정' : s === 'MEASURED' ? '실측' : '입력')

function GradeBadge({ grade }: { grade: string }) {
  const variant = grade === '가정' ? 'warning' : grade === '실측' ? 'success' : grade === '계산' ? 'secondary' : 'outline'
  return <Badge variant={variant} className="text-[10px] font-normal">{grade}</Badge>
}

/** 질문 문구를 보고 한 번에 누를 수 있는 답을 고른다. 마케팅을 모르는 분이 타이핑하지 않아도 되게. */
function quickReplies(question: string | null): string[] {
  if (!question) return []
  const replies: string[] = []
  if (question.includes('1) 직접')) replies.push('1', '2', '3')
  if (question.includes("'네'")) replies.push('네')
  if (question.includes("'전부'")) replies.push('전부')
  if (question.includes("'추천대로'")) replies.push('추천대로')
  if (question.includes("'없음'")) replies.push('없음')
  if (question.includes("'모름'")) replies.push('모름')
  return replies
}

/**
 * 목표 상담. 사업 구조를 대화로 알려 주면 서버가 목표 CPA·필요한 문의 수·운영안을 계산한다.
 * 광고 계정(회사)마다 따로 상담한다.
 */
export function GoalPage() {
  const { accountId } = useAdScopeStore()
  const [view, setView] = useState<GoalView | null>(null)
  const [loaded, setLoaded] = useState(false)
  const [busy, setBusy] = useState(false)
  const [pending, setPending] = useState<string | null>(null)
  const [text, setText] = useState('')
  const [error, setError] = useState<string | null>(null)
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    setView(null)
    setLoaded(false)
    setError(null)
    if (!accountId) return
    let cancelled = false
    goalApi
      .current()
      .then((v) => {
        if (!cancelled) setView(v)
      })
      .catch((err) => {
        if (!cancelled) setError(apiMessage(err, '상담 내용을 불러오지 못했습니다.'))
      })
      .finally(() => {
        if (!cancelled) setLoaded(true)
      })
    return () => {
      cancelled = true
    }
  }, [accountId])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [view?.messages.length, pending])

  const act = async (work: () => Promise<GoalView>, fallback: string) => {
    setBusy(true)
    setError(null)
    try {
      setView(await work())
    } catch (err) {
      setError(apiMessage(err, fallback))
    } finally {
      setBusy(false)
      setPending(null)
    }
  }

  const send = (value: string) => {
    const message = value.trim()
    if (!message || busy) return
    setText('')
    setPending(message)
    void act(() => goalApi.send(message), '말을 보내지 못했습니다. 잠시 뒤 다시 보내 주세요.')
  }

  const restart = () => {
    if (view && !window.confirm('새로 상담을 시작할까요? 지금까지 알려 주신 사업 구조는 이어받고, 목표부터 다시 묻습니다.')) return
    void act(() => goalApi.start(), '상담을 시작하지 못했습니다.')
  }

  return (
    <div className="space-y-6">
      <AdScopeBar showCampaign={false} />

      <p className="text-sm text-muted-foreground">
        사업 구조와 원하는 목표를 알려 주시면, 문의 1건에 광고비를 얼마까지 써도 되는지(목표 CPA)와 한 달 예산을 계산해
        드립니다. 회사(광고 계정)마다 따로 상담합니다.
      </p>

      {error && (
        <div className="flex items-start gap-2 rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
          <span className="whitespace-pre-line">{error}</span>
        </div>
      )}

      {accountId && loaded && !view && (
        <Card>
          <CardContent className="flex flex-col items-center gap-4 py-12 text-center">
            <Target className="h-10 w-10 text-indigo-500" />
            <div className="space-y-1">
              <p className="font-semibold">아직 이 계정의 광고 목표를 정하지 않았어요</p>
              <p className="text-sm text-muted-foreground">
                질문 10여 개에 답하시면 됩니다. 모르는 건 &lsquo;모름&rsquo;이라고 하셔도 괜찮아요.
              </p>
            </div>
            <Button onClick={restart} disabled={busy} className="gap-2">
              {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <MessageSquare className="h-4 w-4" />} 상담 시작하기
            </Button>
          </CardContent>
        </Card>
      )}

      {view && (
        <>
          <div className="grid gap-4 lg:grid-cols-5">
            <Card className="flex h-[620px] flex-col lg:col-span-3">
              <CardHeader className="flex-row items-center justify-between space-y-0 pb-3">
                <CardTitle className="flex items-center gap-2 text-base">
                  <MessageSquare className="h-4 w-4" /> 상담
                </CardTitle>
                <div className="flex items-center gap-2">
                  <Badge variant="outline" className="font-normal">
                    {view.aiMode === 'ai' ? 'AI 상담' : '기본 질문'}
                  </Badge>
                  <Button size="sm" variant="outline" onClick={restart} disabled={busy} className="gap-1.5">
                    <RefreshCw className="h-3.5 w-3.5" /> 새로 상담
                  </Button>
                </div>
              </CardHeader>
              <CardContent className="flex-1 space-y-3 overflow-y-auto">
                {view.messages.map((m) => (
                  <Bubble key={m.id} mine={m.role === 'USER'} text={m.content} />
                ))}
                {pending && <Bubble mine text={pending} />}
                {busy && (
                  <div className="flex items-center gap-2 text-xs text-muted-foreground">
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    {pending ? '읽고 계산하는 중… 마지막 답이면 네이버 예측을 받아오느라 10초쯤 걸립니다.' : '처리 중…'}
                  </div>
                )}
                <div ref={bottomRef} />
              </CardContent>
              <div className="space-y-2 border-t p-3">
                {quickReplies(view.nextQuestion).length > 0 && (
                  <div className="flex flex-wrap gap-1.5">
                    {quickReplies(view.nextQuestion).map((r) => (
                      <Button key={r} size="sm" variant="secondary" disabled={busy} onClick={() => send(r)}>
                        {r}
                      </Button>
                    ))}
                  </div>
                )}
                <div className="flex gap-2">
                  <Textarea
                    rows={2}
                    value={text}
                    disabled={busy}
                    onChange={(e) => setText(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
                        e.preventDefault()
                        send(text)
                      }
                    }}
                    placeholder={view.nextQuestion ? "답을 적어 주세요. 모르면 '모름'" : '궁금한 점이나 고칠 내용을 적어 주세요'}
                    className="resize-none"
                  />
                  <Button onClick={() => send(text)} disabled={busy || !text.trim()} className="self-end" aria-label="보내기">
                    <Send className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            </Card>

            <div className="space-y-4 lg:col-span-2">
              <ModelCard view={view} busy={busy} onSave={(m) => act(() => goalApi.updateModel(m), '고친 내용을 저장하지 못했습니다.')} />
              {view.economics && <EconomicsCard economics={view.economics} />}
            </div>
          </div>

          {view.analysis && (
            <OptionsSection
              view={view}
              busy={busy}
              onConfirm={(key, autopilot) => act(() => goalApi.confirm(key, autopilot), '확정하지 못했습니다.')}
              onReanalyze={() => act(() => goalApi.analyze(), '다시 검토하지 못했습니다.')}
            />
          )}

          {view.status === 'CONFIRMED' && (
            <CheckinSection
              view={view}
              onUpdated={setView}
              onApply={() => act(() => goalApi.applyTarget(), '새 목표 CPA를 적용하지 못했습니다.')}
            />
          )}
        </>
      )}
    </div>
  )
}

function Bubble({ mine, text }: { mine: boolean; text: string }) {
  return (
    <div className={cn('flex', mine ? 'justify-end' : 'justify-start')}>
      <div
        className={cn(
          'max-w-[85%] whitespace-pre-line rounded-2xl px-4 py-2.5 text-sm leading-relaxed',
          mine ? 'rounded-br-sm bg-indigo-600 text-white' : 'rounded-bl-sm bg-muted text-foreground',
        )}
      >
        {text}
      </div>
    </div>
  )
}

/** 지금까지 파악한 사업 구조. 숫자는 바로 고칠 수 있다. */
function ModelCard({ view, busy, onSave }: { view: GoalView; busy: boolean; onSave: (m: BusinessModel) => void }) {
  const m = view.model
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState<BusinessModel>(m)
  const [keywords, setKeywords] = useState('')

  useEffect(() => {
    setDraft(m)
    setKeywords(m.seedKeywords.join(', '))
  }, [m])

  const save = () => {
    onSave({
      ...draft,
      seedKeywords: keywords.split(',').map((k) => k.trim()).filter(Boolean).slice(0, 5),
    })
    setEditing(false)
  }

  const row = (label: string, value: React.ReactNode, source?: ValueSource) => (
    <div className="flex items-start justify-between gap-3 py-1.5 text-sm">
      <span className="shrink-0 text-muted-foreground">{label}</span>
      <span className="flex items-center gap-1.5 text-right">
        {value ?? <span className="text-muted-foreground/60">아직</span>}
        {value != null && source && <GradeBadge grade={sourceLabel(source)} />}
      </span>
    </div>
  )

  const numberField = (label: string, value: number | undefined, onChange: (v: number | undefined) => void, unit: string) => (
    <div className="space-y-1">
      <Label className="text-xs">{label}</Label>
      <div className="flex items-center gap-1.5">
        <Input
          inputMode="decimal"
          value={value ?? ''}
          onChange={(e) => onChange(toNumber(e.target.value))}
          className="h-8"
        />
        <span className="shrink-0 text-xs text-muted-foreground">{unit}</span>
      </div>
    </div>
  )

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-base">파악한 사업 구조</CardTitle>
        {!editing ? (
          <Button size="sm" variant="ghost" className="gap-1.5" onClick={() => setEditing(true)} disabled={busy}>
            <Pencil className="h-3.5 w-3.5" /> 고치기
          </Button>
        ) : (
          <div className="flex gap-1.5">
            <Button size="sm" variant="ghost" onClick={() => setEditing(false)}>취소</Button>
            <Button size="sm" onClick={save} disabled={busy}>저장하고 다시 계산</Button>
          </div>
        )}
      </CardHeader>
      <CardContent>
        {!editing ? (
          <div className="divide-y">
            {row('업종', m.industry)}
            {row('수익 방식', m.revenueModel ? REVENUE_MODEL_LABEL[m.revenueModel] : null)}
            {m.offerings.map((o) => (
              <div key={o.name}>
                {row(
                  o.name,
                  o.revenuePerDeal != null ? (
                    <span>
                      {m.revenueModel === 'SUBSCRIPTION' ? '월 ' : '건당 '}{won(o.revenuePerDeal)}
                      {o.profitPerDeal != null && <span className="text-muted-foreground"> · 남는 돈 {won(o.profitPerDeal)}</span>}
                    </span>
                  ) : null,
                  o.profitPerDeal != null ? o.profitSource : undefined,
                )}
              </div>
            ))}
            {row('문의 → 계약', m.leadToDealPct != null ? pct(m.leadToDealPct) : null, m.leadToDealSource)}
            {row(
              m.revenueModel === 'SUBSCRIPTION' ? '평균 유지 기간' : '고객 1명당 계약',
              m.dealsPerCustomer != null ? `${m.dealsPerCustomer}${m.revenueModel === 'SUBSCRIPTION' ? '개월' : '회'}` : null,
              m.dealsPerCustomerSource,
            )}
            {row('광고 목표', goalText(m))}
            {row('이익 중 광고비', m.adSharePct != null ? pct(m.adSharePct) : null, m.adShareSource)}
            {row('한 달 광고비 상한', m.budgetLimit == null ? null : m.budgetLimit === 0 ? '없음' : won(m.budgetLimit))}
            <div className="py-1.5 text-sm">
              <span className="text-muted-foreground">핵심 키워드</span>
              <div className="mt-1 flex flex-wrap gap-1">
                {m.seedKeywords.length === 0 ? (
                  <span className="text-muted-foreground/60">아직</span>
                ) : (
                  m.seedKeywords.map((k) => <Badge key={k} variant="secondary" className="font-normal">{k}</Badge>)
                )}
              </div>
            </div>
          </div>
        ) : (
          <div className="space-y-3">
            {draft.offerings.map((o, i) => (
              <div key={o.name} className="grid grid-cols-2 gap-2 rounded-md border p-2">
                <p className="col-span-2 text-xs font-medium">{o.name}</p>
                {numberField(m.revenueModel === 'SUBSCRIPTION' ? '월 결제액' : '계약 1건 매출', o.revenuePerDeal, (v) =>
                  setDraft({ ...draft, offerings: draft.offerings.map((x, j) => (j === i ? { ...x, revenuePerDeal: v } : x)) }), '원')}
                {numberField('그중 남는 돈', o.profitPerDeal, (v) =>
                  setDraft({ ...draft, offerings: draft.offerings.map((x, j) => (j === i ? { ...x, profitPerDeal: v } : x)) }), '원')}
              </div>
            ))}
            <div className="grid grid-cols-2 gap-2">
              {numberField('문의 → 계약', draft.leadToDealPct, (v) => setDraft({ ...draft, leadToDealPct: v }), '%')}
              {numberField(m.revenueModel === 'SUBSCRIPTION' ? '유지 기간' : '고객당 계약', draft.dealsPerCustomer,
                (v) => setDraft({ ...draft, dealsPerCustomer: v }), m.revenueModel === 'SUBSCRIPTION' ? '개월' : '회')}
              {numberField('이익 중 광고비', draft.adSharePct, (v) => setDraft({ ...draft, adSharePct: v }), '%')}
              {numberField('월 광고비 상한(0=없음)', draft.budgetLimit, (v) => setDraft({ ...draft, budgetLimit: v }), '원')}
              {draft.goalType &&
                numberField(`목표 ${GOAL_LABEL[draft.goalType]}`, draft.goalAmount, (v) => setDraft({ ...draft, goalAmount: v }),
                  draft.goalType === 'REVENUE' || draft.goalType === 'PROFIT' ? '원' : '건')}
            </div>
            <div className="space-y-1">
              <Label className="text-xs">핵심 키워드 (쉼표로 구분, 최대 5개)</Label>
              <Input value={keywords} onChange={(e) => setKeywords(e.target.value)} className="h-8" />
            </div>
            <p className="text-xs text-muted-foreground">범위를 벗어난 값(예: 100%를 넘는 비율)은 저장되지 않습니다.</p>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

/** 목표 CPA 가 어떻게 나왔는지 한 줄씩. */
function EconomicsCard({ economics }: { economics: GoalEconomicsResult }) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-base">계산</CardTitle>
        <CardDescription>목표 CPA {won(economics.targetCpa)} · 필요한 문의 월 {num(economics.leadsNeeded)}건</CardDescription>
      </CardHeader>
      <CardContent className="divide-y">
        {economics.basis.map((line) => (
          <div key={line.label} className={cn('py-2', line.label === '목표 CPA' && 'rounded-md bg-indigo-50 px-2 dark:bg-indigo-950/40')}>
            <div className="flex items-center justify-between gap-2 text-sm">
              <span className="text-muted-foreground">{line.label}</span>
              <span className="flex items-center gap-1.5 font-semibold">
                {line.value} <GradeBadge grade={line.grade} />
              </span>
            </div>
            <p className="mt-0.5 text-xs text-muted-foreground">{line.how}</p>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}

function OptionsSection({
  view, busy, onConfirm, onReanalyze,
}: {
  view: GoalView
  busy: boolean
  onConfirm: (key: string, autopilot: boolean) => void
  onReanalyze: () => void
}) {
  const analysis = view.analysis!
  const [autopilot, setAutopilot] = useState(false)

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 text-base font-semibold">
          <Sparkles className="h-4 w-4 text-indigo-500" /> 운영안
        </h2>
        <Button size="sm" variant="outline" onClick={onReanalyze} disabled={busy} className="gap-1.5">
          <RefreshCw className="h-3.5 w-3.5" /> 최근 실적으로 다시 검토
        </Button>
      </div>

      {analysis.notes.length > 0 && (
        <div className="space-y-1 rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200">
          {analysis.notes.map((n) => (
            <p key={n} className="flex items-start gap-2">
              <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" /> {n}
            </p>
          ))}
        </div>
      )}

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {analysis.options.map((o) => (
          <OptionCard
            key={o.key}
            option={o}
            targetCpa={analysis.economics.targetCpa}
            recommended={analysis.recommended === o.key}
            confirmed={view.status === 'CONFIRMED' && view.confirmedOption === o.key}
            busy={busy}
            onConfirm={() => onConfirm(o.key, autopilot)}
          />
        ))}
      </div>

      {analysis.recommended && (
        <p className="text-sm text-muted-foreground">
          <span className="font-medium text-foreground">추천 이유:</span> {analysis.recommendReason}
        </p>
      )}

      <label className="flex items-start gap-2 text-sm">
        <Checkbox checked={autopilot} onCheckedChange={(v) => setAutopilot(v === true)} className="mt-0.5" />
        <span>
          확정하면서 자동 운영도 켜기
          <span className="block text-xs text-muted-foreground">
            매일 아침 정해진 한도 안에서 입찰가·일예산을 조정하고, 전환 없이 돈만 쓰는 키워드는 끕니다. 끄면 추천만 보고 직접 승인합니다.
          </span>
        </span>
      </label>
    </div>
  )
}

function OptionCard({
  option, targetCpa, recommended, confirmed, busy, onConfirm,
}: {
  option: GoalOption
  targetCpa: number
  recommended: boolean
  confirmed: boolean
  busy: boolean
  onConfirm: () => void
}) {
  const stat = (label: string, value: React.ReactNode, hint?: string, tone?: string) => (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className={cn('text-sm font-semibold', tone)}>{value}</p>
      {hint && <p className="text-[11px] text-muted-foreground">{hint}</p>}
    </div>
  )
  const keywords = option.plan?.keywords.filter((k) => k.selected) ?? []

  return (
    <Card className={cn('flex flex-col', recommended && 'ring-2 ring-indigo-500')}>
      <CardHeader className="pb-2">
        <div className="flex flex-wrap items-center gap-1.5">
          <CardTitle className="text-base">{option.label}</CardTitle>
          {recommended && <Badge>추천</Badge>}
          {confirmed && <Badge variant="success">확정됨</Badge>}
          {option.plan && (option.meetsGoal ? <Badge variant="success">목표 충족</Badge> : <Badge variant="warning">목표의 {pct(option.goalRatePct)}</Badge>)}
        </div>
        <CardDescription>{option.description}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-1 flex-col gap-4">
        {option.error ? (
          <p className="flex items-start gap-2 text-sm text-destructive">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" /> {option.error}
          </p>
        ) : (
          <>
            <div>
              <p className="text-2xl font-bold">월 {won(option.monthlyBudget)}</p>
              <p className="text-xs text-muted-foreground">하루 {won(option.dailyBudget)} 안팎 · 실제 예상 지출 {won(option.expectedCost)}</p>
            </div>
            <div className="grid grid-cols-2 gap-3">
              {stat('예상 문의', `${num(option.expectedLeads)}건`, `${num(option.leadsLow)} ~ ${num(option.leadsHigh)}건`)}
              {stat('예상 계약', `${num(option.expectedDeals)}건`)}
              {stat('예상 매출', won(option.expectedRevenue))}
              {stat('광고비 뺀 이익', won(option.expectedProfit), '첫 계약만 센 값',
                option.expectedProfit < 0 ? 'text-rose-600' : 'text-emerald-600')}
              {stat('문의 1건 비용', won(option.expectedCpa), `목표 ${won(targetCpa)}`,
                option.expectedCpa > targetCpa ? 'text-rose-600' : undefined)}
            </div>
            {option.warnings.length > 0 && (
              <ul className="space-y-1 text-xs text-amber-700 dark:text-amber-300">
                {option.warnings.map((w) => <li key={w}>• {w}</li>)}
              </ul>
            )}
            {keywords.length > 0 && (
              <details className="text-xs">
                <summary className="cursor-pointer text-muted-foreground">키워드 {keywords.length}개와 시작 입찰가 보기</summary>
                <table className="mt-2 w-full">
                  <thead className="text-muted-foreground">
                    <tr>
                      <th className="py-1 text-left font-normal">키워드</th>
                      <th className="py-1 text-right font-normal">입찰가</th>
                      <th className="py-1 text-right font-normal">예상 클릭</th>
                      <th className="py-1 text-right font-normal">예상 비용</th>
                    </tr>
                  </thead>
                  <tbody>
                    {keywords.map((k) => (
                      <tr key={k.keyword} className="border-t">
                        <td className="py-1">{k.keyword}</td>
                        <td className="py-1 text-right">{won(k.suggestedBid)}</td>
                        <td className="py-1 text-right">{num(k.expectedClicks)}</td>
                        <td className="py-1 text-right">{won(k.expectedCost)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </details>
            )}
            <Button className="mt-auto gap-2" variant={recommended ? 'default' : 'outline'} disabled={busy || confirmed} onClick={onConfirm}>
              <CheckCircle2 className="h-4 w-4" /> {confirmed ? '확정한 안' : '이 안으로 확정'}
            </Button>
          </>
        )}
      </CardContent>
    </Card>
  )
}

/** 확정 뒤 실제 계약 결과를 받아 계약 비율과 목표 CPA 를 다시 맞춘다. */
function CheckinSection({
  view, onUpdated, onApply,
}: {
  view: GoalView
  onUpdated: (v: GoalView) => void
  onApply: () => void
}) {
  const yesterday = new Date(Date.now() - 86_400_000)
  const weekAgo = new Date(Date.now() - 7 * 86_400_000)
  const [since, setSince] = useState(isoDate(weekAgo))
  const [until, setUntil] = useState(isoDate(yesterday))
  const [leads, setLeads] = useState('')
  const [deals, setDeals] = useState('')
  const [revenue, setRevenue] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<GoalCheckinResult | null>(null)

  const submit = async () => {
    const dealCount = toNumber(deals)
    if (dealCount == null) {
      setError('계약된 건수를 넣어 주세요. 없으면 0입니다.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const r = await goalApi.checkin({ since, until, leads: toNumber(leads), deals: dealCount, revenue: toNumber(revenue) })
      setResult(r)
      onUpdated(r.view)
      setLeads('')
      setDeals('')
      setRevenue('')
    } catch (err) {
      setError(apiMessage(err, '결과를 반영하지 못했습니다.'))
    } finally {
      setBusy(false)
    }
  }

  const canApply = result && result.suggestedTargetCpa !== result.appliedTargetCpa

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2 text-base">
          <ClipboardCheck className="h-4 w-4" /> 실제 결과 알려 주기
        </CardTitle>
        <CardDescription>
          네이버는 문의까지만 압니다. 그 문의 중 몇 건이 실제 계약이 됐는지 알려 주시면, 계약 비율을 실제 값으로 고치고 목표 CPA를 다시
          계산합니다. 일주일에 한 번이면 충분합니다.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
          <div className="space-y-1">
            <Label className="text-xs">시작일</Label>
            <Input type="date" value={since} onChange={(e) => setSince(e.target.value)} />
          </div>
          <div className="space-y-1">
            <Label className="text-xs">종료일</Label>
            <Input type="date" value={until} onChange={(e) => setUntil(e.target.value)} />
          </div>
          <div className="space-y-1">
            <Label className="text-xs">광고로 온 문의 수</Label>
            <Input inputMode="numeric" placeholder="비우면 네이버 전환 수" value={leads} onChange={(e) => setLeads(e.target.value)} />
          </div>
          <div className="space-y-1">
            <Label className="text-xs">그중 계약된 수</Label>
            <Input inputMode="numeric" placeholder="예: 3" value={deals} onChange={(e) => setDeals(e.target.value)} />
          </div>
          <div className="space-y-1">
            <Label className="text-xs">계약 매출 (선택)</Label>
            <Input inputMode="numeric" placeholder="원" value={revenue} onChange={(e) => setRevenue(e.target.value)} />
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button onClick={() => void submit()} disabled={busy} className="gap-2">
            {busy && <Loader2 className="h-4 w-4 animate-spin" />} 결과 반영
          </Button>
          {canApply && (
            <Button variant="outline" onClick={onApply} className="gap-2">
              <Target className="h-4 w-4" /> 새 목표 CPA {won(result!.suggestedTargetCpa)} 적용
            </Button>
          )}
        </div>
        {error && <p className="text-sm text-destructive">{error}</p>}
        {result && <p className="whitespace-pre-line rounded-md bg-muted px-4 py-3 text-sm">{result.message}</p>}

        {view.checkins.length > 0 && (
          <table className="w-full text-sm">
            <thead className="text-xs text-muted-foreground">
              <tr className="border-b">
                <th className="py-2 text-left font-normal">기간</th>
                <th className="py-2 text-right font-normal">문의</th>
                <th className="py-2 text-right font-normal">계약</th>
                <th className="py-2 text-right font-normal">계약 비율</th>
                <th className="py-2 text-right font-normal">매출</th>
              </tr>
            </thead>
            <tbody>
              {view.checkins.map((c) => (
                <tr key={c.id} className="border-b last:border-0">
                  <td className="py-2">{c.since} ~ {c.until}</td>
                  <td className="py-2 text-right">
                    {num(c.leads)}건{c.leadsSource === 'NAVER' && <span className="ml-1 text-xs text-muted-foreground">(네이버)</span>}
                  </td>
                  <td className="py-2 text-right">{num(c.deals)}건</td>
                  <td className="py-2 text-right">{pct(c.dealRatePct)}</td>
                  <td className="py-2 text-right">{c.revenue != null ? won(c.revenue) : '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </CardContent>
    </Card>
  )
}
