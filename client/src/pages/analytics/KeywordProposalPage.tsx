import { useEffect, useMemo, useState } from 'react'
import { AlertCircle, Check, Lightbulb, Sparkles, Undo2, Wand2 } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import { Progress } from '@/components/ui/progress'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { AdScopeBar } from '@/components/ads/AdScopeBar'
import { apiMessage, keywordProposalApi, keywordToolApi } from '@/services/api'
import { useAdScopeStore } from '@/store/adScopeStore'
import type { AdGroupSummary, AutopilotRun, KeywordProposal, ProposalItem, ProposalOption } from '@/types'
import { cn } from '@/lib/utils'

const won = (v: number) => `${Math.round(v).toLocaleString()}원`
const num = (v: number) => Math.round(v).toLocaleString()
const words = (v: string) => v.split(',').map((w) => w.trim()).filter(Boolean)

const COMPETITION_TONE: Record<string, string> = {
  높음: 'text-rose-600',
  중간: 'text-amber-600',
  낮음: 'text-emerald-600',
}

const TAG_LABEL: Record<ProposalItem['tag'], { label: string; cls: string }> = {
  PICK: { label: '추천', cls: 'bg-indigo-600 text-white hover:bg-indigo-600' },
  OVER_CAP: { label: '비싸서 빠짐', cls: 'bg-rose-100 text-rose-700 hover:bg-rose-100' },
  LOW_VOLUME: { label: '검색 적음', cls: 'bg-slate-100 text-slate-600 hover:bg-slate-100' },
  LOW_EFFICIENCY: { label: '효율 낮음', cls: 'bg-amber-100 text-amber-700 hover:bg-amber-100' },
}

/** 한 키워드에서 고를 수 있는 입찰가 단계. 최대 금액 위 단계는 "비싸서 빠진" 키워드를 일부러 넣을 때 쓴다. */
const choices = (item: ProposalItem): (ProposalOption & { aboveCap?: boolean })[] => {
  const list: (ProposalOption & { aboveCap?: boolean })[] = [...item.options]
  if (item.minBidAboveCap > 0) {
    list.push({ bid: item.minBidAboveCap, clicks: item.clicksAboveCap, cost: item.costAboveCap, aboveCap: true })
  }
  return list
}

const defaultBid = (item: ProposalItem) => {
  if (item.recommendedBid > 0) return item.recommendedBid
  const list = choices(item)
  return list.length > 0 ? list[list.length - 1].bid : 0
}

export function KeywordProposalPage() {
  const { accountId, campaignId } = useAdScopeStore()
  const [adgroups, setAdgroups] = useState<AdGroupSummary[]>([])
  const [adgroupId, setAdgroupId] = useState('')
  const [hints, setHints] = useState('')
  const [focus, setFocus] = useState('')
  const [types, setTypes] = useState('렌트, 리스, 장기, 할부')
  const [audience, setAudience] = useState('')
  const [exclude, setExclude] = useState('중고, 매매, 후기, 단기, 대출')
  const [profit, setProfit] = useState('')
  const [maxCpc, setMaxCpc] = useState('')
  const [budget, setBudget] = useState('')
  const [cvr, setCvr] = useState('2')

  const [proposal, setProposal] = useState<KeywordProposal | null>(null)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [bids, setBids] = useState<Record<string, number>>({})
  const [removeOthers, setRemoveOthers] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [result, setResult] = useState<AutopilotRun | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!accountId) return
    keywordToolApi
      .adgroups()
      .then((list) => {
        setAdgroups(list)
        setAdgroupId((prev) => (list.some((g) => g.nccAdgroupId === prev) ? prev : list[0]?.nccAdgroupId ?? ''))
      })
      .catch(() => setError('광고그룹을 불러오지 못했습니다. 먼저 검색광고 동기화를 실행하세요.'))
  }, [accountId, campaignId])

  const group = adgroups.find((g) => g.nccAdgroupId === adgroupId)

  const resetToRecommendation = (p: KeywordProposal) => {
    const nextBids: Record<string, number> = {}
    p.items.forEach((i) => (nextBids[i.keyword] = defaultBid(i)))
    setBids(nextBids)
    setSelected(new Set(p.items.filter((i) => i.recommendedBid > 0).map((i) => i.keyword)))
  }

  const analyze = async () => {
    if (!accountId) return
    if (words(hints).length === 0) {
      setError('무엇을 파는지 보여 줄 키워드를 하나 이상 넣어 주세요.')
      return
    }
    setBusy(true)
    setError(null)
    setResult(null)
    setConfirming(false)
    try {
      const p = await keywordProposalApi.analyze({
        nccCampaignId: group?.nccCampaignId ?? null,
        nccAdgroupId: adgroupId || null,
        hints: words(hints),
        focusTerms: words(focus),
        typeTerms: words(types),
        audienceTerms: words(audience),
        excludeWords: words(exclude),
        profitPerConversion: Number(profit) || 0,
        maxCpc: Number(maxCpc) || 0,
        monthlyBudget: Number(budget) || 0,
        cvrPct: Number(cvr) || 0,
        maxKeywords: 25,
      })
      setProposal(p)
      resetToRecommendation(p)
    } catch (e) {
      setError(apiMessage(e, '분석에 실패했습니다. 잠시 뒤 다시 시도해 주세요.'))
    } finally {
      setBusy(false)
    }
  }

  // 고른 것 기준으로 바로 다시 계산한 요약
  const live = useMemo(() => {
    if (!proposal) return null
    const s = proposal.summary
    const cvrRate = s.cvrPct / 100
    let clicks = 0
    let cost = 0
    let conversions = 0
    let aboveCap = 0
    proposal.items.forEach((i) => {
      if (!selected.has(i.keyword)) return
      const choice = choices(i).find((o) => o.bid === bids[i.keyword])
      if (!choice) return
      clicks += choice.clicks
      cost += choice.cost
      conversions += choice.clicks * cvrRate * i.weight
      if (choice.aboveCap) aboveCap++
    })
    return {
      count: selected.size,
      clicks,
      cost,
      cpc: clicks > 0 ? cost / clicks : 0,
      conversions,
      profit: s.profitPerConversion > 0 ? conversions * s.profitPerConversion - cost : 0,
      budgetPct: s.monthlyBudget > 0 ? Math.min(100, (cost / s.monthlyBudget) * 100) : 0,
      overBudget: cost > s.monthlyBudget,
      aboveCap,
    }
  }, [proposal, selected, bids])

  const toggle = (keyword: string) =>
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(keyword)) next.delete(keyword)
      else next.add(keyword)
      return next
    })

  const apply = async () => {
    if (!proposal || !adgroupId) return
    setBusy(true)
    setError(null)
    try {
      const run = await keywordProposalApi.apply({
        nccAdgroupId: adgroupId,
        items: proposal.items
          .filter((i) => selected.has(i.keyword) && bids[i.keyword] > 0)
          .map((i) => ({ keyword: i.keyword, bid: bids[i.keyword] })),
        removeUnselected: removeOthers,
      })
      setResult(run)
      setConfirming(false)
    } catch (e) {
      setError(apiMessage(e, '반영에 실패했습니다.'))
    } finally {
      setBusy(false)
    }
  }

  const byTag = (tags: ProposalItem['tag'][]) => (proposal?.items ?? []).filter((i) => tags.includes(i.tag))

  return (
    <div className="space-y-6">
      <AdScopeBar />

      {/* ① 무엇을 팔까요 */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="flex items-center gap-2 text-base">
            <span className="flex h-6 w-6 items-center justify-center rounded-full bg-indigo-600 text-xs text-white">1</span>
            무엇을, 누구에게 팔까요?
          </CardTitle>
          <CardDescription className="text-xs">
            네이버 키워드도구의 검색량·클릭률과 입찰가별 예상 클릭·비용을 받아, 예산 안에서 광고비 1원당 문의가 가장 많이 나올 키워드를 추천합니다.
            분석만으로는 광고가 바뀌지 않습니다.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 md:grid-cols-2">
          <Field label="키워드를 넣을 광고그룹">
            <Select value={adgroupId} onValueChange={setAdgroupId}>
              <SelectTrigger className="h-9"><SelectValue placeholder="광고그룹 선택" /></SelectTrigger>
              <SelectContent>
                {adgroups.map((g) => (
                  <SelectItem key={g.nccAdgroupId} value={g.nccAdgroupId}>{g.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
          <Field label="파는 상품을 보여 줄 키워드 (쉼표로 구분)" hint="예: 레이장기렌트, 레이리스, 레이EV장기렌트">
            <Input value={hints} onChange={(e) => setHints(e.target.value)} placeholder="레이장기렌트, 레이리스" className="h-9" />
          </Field>
          <Field label="꼭 하나는 들어가야 할 상품 말" hint="이 말이 없는 키워드는 후보에서 뺍니다. 예: 레이 / 포터, 봉고">
            <Input value={focus} onChange={(e) => setFocus(e.target.value)} placeholder="레이" className="h-9" />
          </Field>
          <Field label="거래 방식 말 (하나는 들어가야 함)" hint="비우면 따지지 않습니다">
            <Input value={types} onChange={(e) => setTypes(e.target.value)} className="h-9" />
          </Field>
          <Field label="대상 고객 말 (들어가면 전환 가능성 1.5배)" hint="예: 저신용, 신용불량, 회생">
            <Input value={audience} onChange={(e) => setAudience(e.target.value)} placeholder="저신용, 신용불량, 회생" className="h-9" />
          </Field>
          <Field label="들어가면 뺄 말" hint="정보만 찾거나 다른 상품을 찾는 검색">
            <Input value={exclude} onChange={(e) => setExclude(e.target.value)} className="h-9" />
          </Field>
          <div className="grid grid-cols-2 gap-3 md:col-span-2 md:grid-cols-4">
            <Field label="계약 1건당 남는 돈(원)" hint="비우면 캠페인 저장값">
              <Input inputMode="numeric" value={profit} onChange={(e) => setProfit(e.target.value)} placeholder="500000" className="h-9" />
            </Field>
            <Field label="클릭 1회 최대(원)" hint="비우면 남는 돈 × 전환율">
              <Input inputMode="numeric" value={maxCpc} onChange={(e) => setMaxCpc(e.target.value)} placeholder="자동" className="h-9" />
            </Field>
            <Field label="한 달 광고비(원)" hint="비우면 하루 예산 × 30">
              <Input inputMode="numeric" value={budget} onChange={(e) => setBudget(e.target.value)} placeholder="자동" className="h-9" />
            </Field>
            <Field label="예상 전환율(%)" hint="모르면 2">
              <Input inputMode="decimal" value={cvr} onChange={(e) => setCvr(e.target.value)} className="h-9" />
            </Field>
          </div>
          <div className="md:col-span-2">
            <Button onClick={() => void analyze()} disabled={busy || !accountId} className="gap-2">
              <Sparkles className="h-4 w-4" /> {busy && !proposal ? '분석 중... (30초 정도)' : '키워드 분석하기'}
            </Button>
          </div>
        </CardContent>
      </Card>

      {error && (
        <div className="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          <AlertCircle className="h-4 w-4 shrink-0" /> {error}
        </div>
      )}

      {proposal && live && (
        <>
          {/* ② 분석 해설 */}
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="flex items-center gap-2 text-base">
                <span className="flex h-6 w-6 items-center justify-center rounded-full bg-indigo-600 text-xs text-white">2</span>
                분석 결과 한눈에 보기
              </CardTitle>
              <CardDescription className="text-xs">
                후보 {proposal.summary.candidates}개 · 클릭 1회 최대 {won(proposal.summary.maxCpc)} · 한 달 예산 {won(proposal.summary.monthlyBudget)} ·
                전환율 {proposal.summary.cvrPct}% 기준
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-2">
              {proposal.insights.map((text, idx) => (
                <div key={idx} className="flex gap-2 rounded-md bg-amber-50/60 px-3 py-2 text-sm leading-relaxed">
                  <Lightbulb className="mt-0.5 h-4 w-4 shrink-0 text-amber-500" />
                  <span>{text}</span>
                </div>
              ))}
            </CardContent>
          </Card>

          {/* ③ 고르기: 고를 때마다 바뀌는 요약 */}
          <div className="sticky top-0 z-10 rounded-lg border bg-background/95 p-4 shadow-sm backdrop-blur">
            <div className="mb-2 flex flex-wrap items-center gap-x-6 gap-y-2 text-sm">
              <span className="flex items-center gap-2 font-semibold">
                <span className="flex h-6 w-6 items-center justify-center rounded-full bg-indigo-600 text-xs text-white">3</span>
                내가 고른 안
              </span>
              <Stat label="키워드" value={`${live.count}개`} />
              <Stat label="월 예상 클릭" value={`${num(live.clicks)}회`} />
              <Stat label="월 광고비" value={won(live.cost)} warn={live.overBudget} />
              <Stat label="클릭당" value={won(live.cpc)} />
              <Stat label="예상 문의·계약" value={`${live.conversions.toFixed(1)}건`} />
              {proposal.summary.profitPerConversion > 0 && (
                <Stat label="광고비 빼고 남는 돈" value={won(live.profit)} warn={live.profit < 0} />
              )}
            </div>
            <div className="flex items-center gap-3">
              <Progress value={live.budgetPct} className="h-2 flex-1" />
              <span className={cn('text-xs', live.overBudget ? 'font-semibold text-rose-600' : 'text-muted-foreground')}>
                예산 {Math.round((live.cost / Math.max(1, proposal.summary.monthlyBudget)) * 100)}% 사용
                {live.overBudget && ' — 예산을 넘습니다'}
              </span>
              <Button variant="ghost" size="sm" className="gap-1 text-xs" onClick={() => resetToRecommendation(proposal)}>
                <Undo2 className="h-3.5 w-3.5" /> 추천안으로
              </Button>
              <Button variant="ghost" size="sm" className="text-xs" onClick={() => setSelected(new Set())}>
                모두 해제
              </Button>
            </div>
            <p className="mt-2 text-xs text-muted-foreground">
              추천안: {proposal.summary.picked}개 · 월 {num(proposal.summary.clicks)}클릭 · {won(proposal.summary.cost)} · 문의 {proposal.summary.conversions}건.
              체크를 넣고 빼거나 입찰가를 바꾸면 위 숫자가 바로 바뀝니다.
            </p>
          </div>

          <Tabs defaultValue="pick">
            <TabsList>
              <TabsTrigger value="pick">추천 ({byTag(['PICK']).length})</TabsTrigger>
              <TabsTrigger value="over">비싸서 빠짐 ({byTag(['OVER_CAP']).length})</TabsTrigger>
              <TabsTrigger value="rest">그 외 ({byTag(['LOW_EFFICIENCY', 'LOW_VOLUME']).length})</TabsTrigger>
            </TabsList>
            {(
              [
                ['pick', ['PICK'], '예산 안에서 광고비 대비 효율이 좋아 추천하는 키워드입니다.'],
                ['over', ['OVER_CAP'], '검색 의도는 좋지만 클릭 1회 최대 금액으로는 노출이 안 되는 키워드입니다. 넣으려면 최대 금액 위 단계를 고르세요.'],
                ['rest', ['LOW_EFFICIENCY', 'LOW_VOLUME'], '검색이 적거나 다른 키워드보다 효율이 낮아 뺀 키워드입니다. 필요하면 직접 고를 수 있습니다.'],
              ] as [string, ProposalItem['tag'][], string][]
            ).map(([value, tags, desc]) => (
              <TabsContent key={value} value={value} className="mt-3">
                <Card>
                  <CardHeader className="pb-2">
                    <CardDescription className="text-xs">{desc}</CardDescription>
                  </CardHeader>
                  <CardContent className="p-0">
                    <KeywordTable
                      items={byTag(tags)}
                      selected={selected}
                      bids={bids}
                      onToggle={toggle}
                      onBid={(k, b) => {
                        setBids((prev) => ({ ...prev, [k]: b }))
                        setSelected((prev) => new Set(prev).add(k))
                      }}
                      maxCpc={proposal.summary.maxCpc}
                    />
                  </CardContent>
                </Card>
              </TabsContent>
            ))}
          </Tabs>

          {/* ④ 적용 */}
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="flex items-center gap-2 text-base">
                <span className="flex h-6 w-6 items-center justify-center rounded-full bg-indigo-600 text-xs text-white">4</span>
                고른 키워드 적용
              </CardTitle>
              <CardDescription className="text-xs">
                {group ? `광고그룹 '${group.name}'에` : '광고그룹에'} 고른 키워드를 넣고 입찰가를 맞춥니다. 적용 후 7일 동안은 실시간 입찰이 이 입찰가를 그대로 두고
                실제 클릭·전환을 모읍니다. 새 키워드는 네이버 검수 뒤 노출됩니다.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="flex items-center gap-2">
                <Checkbox id="remove" checked={removeOthers} onCheckedChange={(v) => setRemoveOthers(Boolean(v))} />
                <Label htmlFor="remove" className="text-sm">이 그룹에 있지만 고르지 않은 키워드는 지우기</Label>
              </div>
              {!confirming ? (
                <Button disabled={busy || live.count === 0 || !adgroupId} onClick={() => setConfirming(true)} className="gap-2">
                  <Wand2 className="h-4 w-4" /> 선택한 {live.count}개 적용하기
                </Button>
              ) : (
                <div className="space-y-3 rounded-md border border-indigo-200 bg-indigo-50/50 p-4 text-sm">
                  <p>
                    <b>{live.count}개</b> 키워드를 월 예상 <b>{won(live.cost)}</b>(클릭 {num(live.clicks)}회)로 넣습니다.
                    {live.aboveCap > 0 && ` 이 중 ${live.aboveCap}개는 클릭 1회 최대 금액보다 비싼 단계입니다.`}
                    {removeOthers && ' 고르지 않은 기존 키워드는 지웁니다.'}
                  </p>
                  <div className="flex gap-2">
                    <Button size="sm" disabled={busy} onClick={() => void apply()}>
                      {busy ? '반영 중...' : '네, 적용합니다'}
                    </Button>
                    <Button size="sm" variant="outline" onClick={() => setConfirming(false)}>다시 고르기</Button>
                  </div>
                </div>
              )}
              {result && (
                <div className="space-y-1 rounded-md border border-emerald-500/30 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
                  <p className="flex items-center gap-2 font-medium"><Check className="h-4 w-4" /> {result.summary}</p>
                  {result.notes.map((n, i) => <p key={i} className="text-xs">{n}</p>)}
                </div>
              )}
            </CardContent>
          </Card>
        </>
      )}
    </div>
  )
}

function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <Label className="text-xs text-muted-foreground">{label}</Label>
      {children}
      {hint && <p className="text-[11px] text-muted-foreground">{hint}</p>}
    </div>
  )
}

function Stat({ label, value, warn }: { label: string; value: string; warn?: boolean }) {
  return (
    <span className="flex flex-col leading-tight">
      <span className="text-[11px] text-muted-foreground">{label}</span>
      <span className={cn('font-semibold', warn && 'text-rose-600')}>{value}</span>
    </span>
  )
}

function KeywordTable({
  items, selected, bids, onToggle, onBid, maxCpc,
}: {
  items: ProposalItem[]
  selected: Set<string>
  bids: Record<string, number>
  onToggle: (keyword: string) => void
  onBid: (keyword: string, bid: number) => void
  maxCpc: number
}) {
  if (items.length === 0) {
    return <p className="px-6 pb-6 text-sm text-muted-foreground">해당하는 키워드가 없습니다.</p>
  }
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="bg-muted/40 text-xs text-muted-foreground">
            <th className="px-4 py-2.5 text-left font-semibold">고르기</th>
            <th className="px-3 py-2.5 text-left font-semibold">키워드</th>
            <th className="px-3 py-2.5 text-right font-semibold">월 검색</th>
            <th className="px-3 py-2.5 text-right font-semibold">클릭률</th>
            <th className="px-3 py-2.5 text-center font-semibold">경쟁</th>
            <th className="px-3 py-2.5 text-left font-semibold">입찰가 (월 예상 클릭 · 비용)</th>
            <th className="px-3 py-2.5 text-right font-semibold">클릭당</th>
            <th className="px-4 py-2.5 text-left font-semibold">분석</th>
          </tr>
        </thead>
        <tbody className="divide-y">
          {items.map((item) => {
            const list = choices(item)
            const bid = bids[item.keyword] ?? 0
            const choice = list.find((o) => o.bid === bid)
            const on = selected.has(item.keyword)
            return (
              <tr key={item.keyword} className={cn('align-top transition-colors', on ? 'bg-indigo-50/40' : 'hover:bg-muted/20')}>
                <td className="px-4 py-3">
                  <Checkbox checked={on} disabled={list.length === 0} onCheckedChange={() => onToggle(item.keyword)} />
                </td>
                <td className="px-3 py-3">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <span className="font-medium">{item.keyword}</span>
                    <Badge className={cn('text-[10px]', TAG_LABEL[item.tag].cls)}>{TAG_LABEL[item.tag].label}</Badge>
                    {item.weight > 1 && (
                      <Badge variant="outline" className="text-[10px]">전환 가중 ×{item.weight.toFixed(1)}</Badge>
                    )}
                    {item.registered && (
                      <Badge variant="outline" className="text-[10px]">
                        등록됨{item.currentBid ? ` · 지금 ${won(item.currentBid)}` : ''}
                      </Badge>
                    )}
                  </div>
                </td>
                <td className="px-3 py-3 text-right">{num(item.monthlySearches)}</td>
                <td className="px-3 py-3 text-right">
                  <span className={cn(item.ctrPct >= 5 && 'font-semibold text-emerald-600')}>{item.ctrPct.toFixed(1)}%</span>
                </td>
                <td className={cn('px-3 py-3 text-center', COMPETITION_TONE[item.competition] ?? '')}>{item.competition}</td>
                <td className="px-3 py-3">
                  {list.length === 0 ? (
                    <span className="text-xs text-muted-foreground">고를 단계 없음</span>
                  ) : (
                    <Select value={String(bid)} onValueChange={(v) => onBid(item.keyword, Number(v))}>
                      <SelectTrigger className="h-8 w-60 text-xs"><SelectValue /></SelectTrigger>
                      <SelectContent>
                        {list.map((o) => (
                          <SelectItem key={o.bid} value={String(o.bid)} className="text-xs">
                            {won(o.bid)} · 월 {num(o.clicks)}클릭 · {won(o.cost)}
                            {o.bid === item.recommendedBid && ' (추천)'}
                            {o.aboveCap && ` (최대 ${won(maxCpc)} 초과)`}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                </td>
                <td className="px-3 py-3 text-right">{choice && choice.clicks > 0 ? won(choice.cost / choice.clicks) : '-'}</td>
                <td className="max-w-xs px-4 py-3 text-xs leading-relaxed text-muted-foreground">{item.reason}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
