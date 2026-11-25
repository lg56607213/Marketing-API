import { useCallback, useEffect, useState } from 'react'
import { AlertCircle, AlertTriangle, Bot, Check, Eye, Loader2, Play, Zap } from 'lucide-react'
import { ReportMailCard } from '@/components/ads/ReportMailCard'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { autopilotApi } from '@/services/api'
import type { AutopilotAction, AutopilotRun, AutopilotSettings } from '@/types'
import { cn } from '@/lib/utils'
import { AdScopeBar } from '@/components/ads/AdScopeBar'
import { useAdScopeStore } from '@/store/adScopeStore'

const won = (v: number) => `${Math.round(v).toLocaleString()}원`
const toNumber = (v: string) => Number(v.replace(/[^0-9.]/g, '')) || 0
const errorMessage = (err: unknown, fallback: string) =>
  (err as { response?: { data?: { message?: string } } }).response?.data?.message ?? fallback

const SCOPES: { key: 'manageBids' | 'pauseWasteful' | 'manageBudget'; label: string; detail: string }[] = [
  {
    key: 'manageBids',
    label: '입찰가 조정',
    detail: '키워드마다 클릭 한 번에 쓸 수 있는 돈을 계산해, 실제 클릭 단가가 그보다 비싸면 내리고 싸면 올립니다.',
  },
  {
    key: 'pauseWasteful',
    label: '돈만 쓰는 키워드 중지',
    detail: '전환 없이 전환 1건에 쓸 수 있는 돈의 3배 이상을 쓴 키워드를 끕니다. 한 번에 5개까지입니다.',
  },
  {
    key: 'manageBudget',
    label: '일예산 조정',
    detail: '이번 달 한도를 넘기지 않도록 낮추고, 효율이 좋으면서 일예산을 거의 다 쓸 때만 20% 올립니다.',
  },
]

type DelegationKey = 'realtimeBids' | 'addKeywords' | 'addNegatives' | 'cleanupKeywords'

const DELEGATION: { key: DelegationKey; label: string; detail: string }[] = [
  {
    key: 'realtimeBids',
    label: '실시간 입찰 (1시간마다)',
    detail:
      '낮 8시~밤 11시 매시간 네이버의 "목표 순위 추정 입찰가"를 받아 입찰가를 따라갑니다. 경쟁사가 입찰가를 올려 순위가 밀려도 사람이 볼 필요가 없습니다. 클릭 1회 최대 금액은 넘지 않고, 오늘 예산이 너무 빨리 나가면 속도를 줄입니다. 전환 데이터가 없어도 동작합니다.',
  },
  {
    key: 'addKeywords',
    label: '키워드 자동 추가',
    detail: '실제로 들어온 검색어와 목표 상담에서 고른 키워드 중 살 사람이 검색할 말만 AI가 골라 넣습니다. 하루 5개까지입니다.',
  },
  {
    key: 'addNegatives',
    label: '제외 키워드 자동 등록',
    detail: '채용·무료 정보·다른 업종처럼 사지 않을 사람의 검색어를 AI가 골라 막습니다. 하루 10개까지입니다.',
  },
  {
    key: 'cleanupKeywords',
    label: '실적 없는 키워드 정리',
    detail: '자동 운영이 끄거나 추가한 키워드 중 30일 동안 클릭이 한 번도 없는 것만 지웁니다. 직접 만드신 키워드는 지우지 않습니다.',
  },
]

const daysUntil = (date: string | null) => {
  if (!date) return 0
  const diff = Math.ceil((new Date(`${date}T23:59:59`).getTime() - Date.now()) / 86_400_000)
  return diff > 0 ? diff : 0
}

function ActionList({ actions }: { actions: AutopilotAction[] }) {
  return (
    <ul className="divide-y text-sm">
      {actions.map((a) => (
        <li key={`${a.type}-${a.target}`} className="py-3">
          <span className="flex flex-wrap items-baseline gap-x-2">
            <Badge variant="outline" className="text-[10px]">{a.type}</Badge>
            <span className="font-medium">{a.target}</span>
            <span>{a.before} → {a.after}</span>
            <span className={cn('text-xs', a.status === '실패' ? 'text-destructive' : 'text-muted-foreground')}>
              ({a.status})
            </span>
          </span>
          <span className="mt-1 block text-xs leading-relaxed text-muted-foreground">{a.reason}</span>
        </li>
      ))}
    </ul>
  )
}

export function AutopilotPage() {
  const { accountId } = useAdScopeStore()
  const [settings, setSettings] = useState<AutopilotSettings | null>(null)
  const [targetCpa, setTargetCpa] = useState('')
  const [targetRoas, setTargetRoas] = useState('')
  const [budgetCap, setBudgetCap] = useState('')
  const [scopes, setScopes] = useState({ manageBids: true, pauseWasteful: true, manageBudget: true })
  const [runs, setRuns] = useState<AutopilotRun[]>([])
  const [result, setResult] = useState<AutopilotRun | null>(null)
  const [delegation, setDelegation] = useState<Record<DelegationKey, boolean>>({
    realtimeBids: true, addKeywords: true, addNegatives: true, cleanupKeywords: true,
  })
  const [targetRank, setTargetRank] = useState(3)
  const [maxCpc, setMaxCpc] = useState('')
  const [observeDays, setObserveDays] = useState(0)
  const [busy, setBusy] = useState<'save' | 'toggle' | 'preview' | 'run' | 'delegation' | 'realtime' | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const apply = (s: AutopilotSettings) => {
    setSettings(s)
    setTargetCpa(s.targetCpa > 0 ? String(s.targetCpa) : '')
    setTargetRoas(s.targetRoasPct > 0 ? String(s.targetRoasPct) : '')
    setBudgetCap(s.monthlyBudgetCap > 0 ? String(s.monthlyBudgetCap) : '')
    setScopes({ manageBids: s.manageBids, pauseWasteful: s.pauseWasteful, manageBudget: s.manageBudget })
    setDelegation({
      realtimeBids: s.realtimeBids, addKeywords: s.addKeywords, addNegatives: s.addNegatives, cleanupKeywords: s.cleanupKeywords,
    })
    setTargetRank(s.targetRank || 3)
    setMaxCpc(s.maxCpc > 0 ? String(s.maxCpc) : '')
    setObserveDays(daysUntil(s.observeUntil))
  }

  const saveDelegation = async () => {
    setError(null)
    setNotice(null)
    setBusy('delegation')
    try {
      apply(
        await autopilotApi.updateDelegation({
          ...delegation,
          targetRank,
          maxCpc: toNumber(maxCpc),
          observeDays,
        })
      )
      setNotice('일임 범위를 저장했습니다.')
    } catch (err) {
      setError(errorMessage(err, '저장하지 못했습니다.'))
    } finally {
      setBusy(null)
    }
  }

  const runRealtime = async () => {
    setError(null)
    setNotice(null)
    setBusy('realtime')
    try {
      setResult(await autopilotApi.realtime(true))
    } catch (err) {
      setError(errorMessage(err, '실시간 입찰을 계산하지 못했습니다.'))
    } finally {
      setBusy(null)
    }
  }

  const load = useCallback(async () => {
    setResult(null)
    setRuns([])
    setError(null)
    if (!accountId) return
    try {
      const [s, r] = await Promise.all([autopilotApi.settings(), autopilotApi.runs()])
      apply(s)
      setRuns(r)
    } catch {
      setError('자동 운영 설정을 불러오지 못했습니다.')
    }
  }, [accountId])

  useEffect(() => {
    void load()
  }, [load])

  const save = async (enabled: boolean, kind: 'save' | 'toggle') => {
    setError(null)
    setNotice(null)
    setBusy(kind)
    try {
      apply(
        await autopilotApi.update({
          enabled,
          targetCpa: toNumber(targetCpa),
          targetRoasPct: toNumber(targetRoas),
          monthlyBudgetCap: toNumber(budgetCap),
          ...scopes,
        })
      )
      setNotice(kind === 'toggle' ? (enabled ? '자동 운영을 켰습니다.' : '자동 운영을 껐습니다.') : '저장했습니다.')
    } catch (err) {
      setError(errorMessage(err, '저장하지 못했습니다.'))
    } finally {
      setBusy(null)
    }
  }

  const toggle = () => {
    if (!settings) return
    if (
      !settings.enabled &&
      !window.confirm(
        '자동 운영을 켜면 매일 실적을 받은 뒤 광고 계정의 입찰가·키워드 상태·일예산이 사람 확인 없이 바뀝니다. 켤까요?'
      )
    ) {
      return
    }
    void save(!settings.enabled, 'toggle')
  }

  const run = async (preview: boolean) => {
    if (!preview && !window.confirm('지금 광고 계정에 바로 반영합니다. 실행할까요?')) return
    setError(null)
    setNotice(null)
    setBusy(preview ? 'preview' : 'run')
    try {
      setResult(await autopilotApi.run(preview))
      setRuns(await autopilotApi.runs())
    } catch (err) {
      setError(errorMessage(err, '실행하지 못했습니다.'))
    } finally {
      setBusy(null)
    }
  }

  if (!settings) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        {error ? <><AlertCircle className="h-4 w-4" /> {error}</> : <><Loader2 className="h-4 w-4 animate-spin" /> 불러오는 중</>}
      </div>
    )
  }

  const history = runs.filter((r) => !r.preview)

  return (
    <div className="space-y-6">
      <AdScopeBar showCampaign={false} />
      <p className="text-sm text-muted-foreground">
        켜 두면 사람 대신 입찰가를 1시간마다 맞추고, 매일 아침 키워드 추가·제외·정리와 일예산 조정을 한 뒤 결과를 메일로 보고합니다.
        목표는 목표 상담 결과를 그대로 쓰므로, 상담만 마치고 켜 두시면 됩니다.
      </p>

      <div
        className={cn(
          'flex flex-wrap items-center justify-between gap-4 rounded-lg border px-5 py-4',
          settings.enabled ? 'border-emerald-500/40 bg-emerald-50 text-emerald-900' : 'border-slate-300 bg-slate-50 text-slate-900'
        )}
      >
        <div>
          <p className="flex items-center gap-2 text-base font-semibold">
            <Bot className="h-5 w-5" /> {settings.enabled ? '자동 운영 중' : '자동 운영 꺼짐'}
          </p>
          <p className="mt-1 text-sm">
            {settings.enabled
              ? '매일 아침 수집이 끝나면 아래 범위와 한도 안에서 광고 계정을 조정합니다.'
              : '지금은 아무것도 바꾸지 않습니다. 미리보기로 무엇을 하게 될지 먼저 확인할 수 있습니다.'}
          </p>
        </div>
        <Button variant={settings.enabled ? 'outline' : 'default'} disabled={busy !== null} onClick={toggle} className="gap-2">
          {busy === 'toggle' && <Loader2 className="h-4 w-4 animate-spin" />}
          {settings.enabled ? '끄기' : '자동 운영 켜기'}
        </Button>
      </div>

      {settings.recordOnly && (
        <div className="flex gap-2 rounded-md border border-amber-500/30 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          {settings.dryRun
            ? '지금은 시험 모드입니다. 자동 운영이 바꾸려는 내용을 기록만 하고 네이버에는 반영하지 않습니다. 실행 기록을 보시고 괜찮으면 관리자에게 실제 반영을 요청하세요.'
            : `${settings.observeUntil}까지 지켜보기 기간입니다. 판단과 기록만 하고 네이버에는 반영하지 않습니다.`}
        </div>
      )}
      {error && (
        <div className="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          <AlertCircle className="h-4 w-4 shrink-0" /> {error}
        </div>
      )}
      {notice && (
        <div className="flex items-center gap-2 rounded-md border border-emerald-500/30 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
          <Check className="h-4 w-4 shrink-0" /> {notice}
        </div>
      )}

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">목표와 한도</CardTitle>
          <CardDescription className="text-xs">
            비워 두면 운영 계획이나 목표 상담 결과를 따릅니다. 지금 적용되는 기준: 전환당 비용{' '}
            {settings.effectiveTargetCpa > 0 ? `${won(settings.effectiveTargetCpa)} (${settings.targetSource})` : '없음'} · 월 한도{' '}
            {settings.effectiveMonthlyBudgetCap > 0 ? won(settings.effectiveMonthlyBudgetCap) : '없음'}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="grid gap-4 sm:grid-cols-3">
            <div className="space-y-1.5">
              <Label htmlFor="ap-cpa">목표 전환당 비용 (원)</Label>
              <Input id="ap-cpa" inputMode="numeric" placeholder="비우면 운영 계획의 목표" value={targetCpa}
                onChange={(e) => setTargetCpa(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ap-roas">목표 광고수익률 (%)</Label>
              <Input id="ap-roas" inputMode="numeric" placeholder="구매 금액이 잡히는 쇼핑몰만" value={targetRoas}
                onChange={(e) => setTargetRoas(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ap-cap">한 달 광고비 한도 (원)</Label>
              <Input id="ap-cap" inputMode="numeric" placeholder="비우면 운영 계획의 월 예산" value={budgetCap}
                onChange={(e) => setBudgetCap(e.target.value)} />
            </div>
          </div>

          <div className="space-y-3">
            <p className="text-sm font-medium">맡기는 범위</p>
            {SCOPES.map((scope) => (
              <label key={scope.key} className="flex cursor-pointer gap-3 text-sm">
                <input
                  type="checkbox"
                  className="mt-1 h-4 w-4 shrink-0"
                  checked={scopes[scope.key]}
                  onChange={(e) => setScopes((prev) => ({ ...prev, [scope.key]: e.target.checked }))}
                />
                <span>
                  <span className="font-medium">{scope.label}</span>
                  <span className="block text-xs text-muted-foreground">{scope.detail}</span>
                </span>
              </label>
            ))}
          </div>

          <div className="rounded-md border bg-muted/20 px-4 py-3 text-xs leading-relaxed text-muted-foreground">
            바꿀 수 없는 한도: 입찰가는 한 번에 ±{settings.maxChangeRatePct}%까지, {won(settings.minBid)}~{won(settings.maxBid)} 사이에서,
            하루 {settings.maxDailyApplies}건까지만 바뀝니다. 일예산은 월 한도를 넘는 쪽으로는 올리지 않습니다. 광고 문구는 자동으로 건드리지
            않습니다.
          </div>

          <Button variant="outline" disabled={busy !== null} onClick={() => void save(settings.enabled, 'save')} className="gap-2">
            {busy === 'save' && <Loader2 className="h-4 w-4 animate-spin" />}
            설정 저장
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">완전 일임 범위</CardTitle>
          <CardDescription className="text-xs">
            초보자도 맡겨 두기만 하면 되도록 기본으로 모두 켜 두었습니다. 지금 클릭 1회 최대 금액:{' '}
            {settings.effectiveMaxCpc > 0
              ? `${won(settings.effectiveMaxCpc)}${settings.maxCpc > 0 ? ' (직접 정함)' : ' (목표 상담의 손익분기 × 전환율로 자동 계산)'}`
              : '정할 근거가 없음 — 목표 상담을 마치거나 아래에 직접 넣어 주세요'}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="space-y-3">
            {DELEGATION.map((item) => (
              <label key={item.key} className="flex cursor-pointer gap-3 text-sm">
                <input
                  type="checkbox"
                  className="mt-1 h-4 w-4 shrink-0"
                  checked={delegation[item.key]}
                  onChange={(e) => setDelegation((prev) => ({ ...prev, [item.key]: e.target.checked }))}
                />
                <span>
                  <span className="font-medium">{item.label}</span>
                  <span className="block text-xs leading-relaxed text-muted-foreground">{item.detail}</span>
                </span>
              </label>
            ))}
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            <div className="space-y-1.5">
              <Label htmlFor="ap-rank">노릴 순위</Label>
              <select
                id="ap-rank"
                className="h-9 w-full rounded-md border bg-background px-3 text-sm"
                value={targetRank}
                onChange={(e) => setTargetRank(Number(e.target.value))}
              >
                {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((r) => (
                  <option key={r} value={r}>{r}위{r === 3 ? ' (추천)' : ''}</option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ap-maxcpc">클릭 1회 최대 금액 (원)</Label>
              <Input id="ap-maxcpc" inputMode="numeric" placeholder="비우면 자동 계산" value={maxCpc}
                onChange={(e) => setMaxCpc(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ap-observe">지켜보기 기간</Label>
              <select
                id="ap-observe"
                className="h-9 w-full rounded-md border bg-background px-3 text-sm"
                value={observeDays}
                onChange={(e) => setObserveDays(Number(e.target.value))}
              >
                {!([0, 7, 14].includes(observeDays)) && <option value={observeDays}>남은 {observeDays}일</option>}
                <option value={0}>없음 (바로 반영)</option>
                <option value={7}>7일 동안 기록만</option>
                <option value={14}>14일 동안 기록만</option>
              </select>
            </div>
          </div>

          <Button variant="outline" disabled={busy !== null} onClick={() => void saveDelegation()} className="gap-2">
            {busy === 'delegation' && <Loader2 className="h-4 w-4 animate-spin" />}
            일임 범위 저장
          </Button>
        </CardContent>
      </Card>

      <ReportMailCard />

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">지금 실행하면 할 일</CardTitle>
          <CardDescription className="text-xs">
            미리보기는 계산만 하고 아무것도 바꾸지 않습니다. 저장한 설정을 기준으로 계산합니다.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex flex-wrap gap-2">
            <Button size="sm" disabled={busy !== null} onClick={() => void run(true)} className="gap-2">
              {busy === 'preview' ? <Loader2 className="h-4 w-4 animate-spin" /> : <Eye className="h-4 w-4" />}
              미리보기
            </Button>
            <Button size="sm" variant="outline" disabled={busy !== null} onClick={() => void runRealtime()} className="gap-2">
              {busy === 'realtime' ? <Loader2 className="h-4 w-4 animate-spin" /> : <Zap className="h-4 w-4" />}
              실시간 입찰 미리보기
            </Button>
            {settings.enabled && (
              <Button size="sm" variant="outline" disabled={busy !== null} onClick={() => void run(false)} className="gap-2">
                {busy === 'run' ? <Loader2 className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}
                지금 실행
              </Button>
            )}
          </div>
          {result && (
            <div>
              <p className="text-sm font-medium">{result.summary}</p>
              {result.notes.map((n) => (
                <p key={n} className="mt-2 flex gap-2 text-sm text-amber-900">
                  <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" /> {n}
                </p>
              ))}
              {result.actions.length > 0 && <ActionList actions={result.actions} />}
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">실행 기록</CardTitle>
          <CardDescription className="text-xs">자동 운영이 실제로 실행된 기록입니다. 미리보기는 빼고 보여 줍니다.</CardDescription>
        </CardHeader>
        <CardContent>
          {history.length === 0 ? (
            <p className="text-sm text-muted-foreground">아직 실행된 적이 없습니다.</p>
          ) : (
            <ul className="space-y-4">
              {history.map((r) => (
                <li key={r.id} className="text-sm">
                  <details>
                    <summary className="cursor-pointer">
                      <span className="text-muted-foreground">{r.ranAt.replace('T', ' ').slice(0, 16)}</span>{' '}
                      <span className="font-medium">{r.summary}</span>
                    </summary>
                    {r.notes.map((n) => (
                      <p key={n} className="mt-2 text-xs text-amber-900">{n}</p>
                    ))}
                    {r.actions.length > 0 && <ActionList actions={r.actions} />}
                  </details>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
