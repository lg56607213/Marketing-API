import { useCallback, useEffect, useState } from 'react'
import {
  ArrowDownRight,
  ArrowUpRight,
  AlertCircle,
  Check,
  Sparkles,
  ShieldCheck,
  History,
  X,
} from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { bidApi } from '@/services/api'
import type { BidRecommendation } from '@/types'
import { cn } from '@/lib/utils'

const won = (v: number | null) => (v == null ? '-' : `${Math.round(v).toLocaleString()}원`)

const STATUS_LABEL: Record<string, string> = {
  PENDING: '대기',
  APPLIED: '반영됨',
  REJECTED: '거절',
  FAILED: '실패',
  SUPERSEDED: '대체됨',
}

export function BidManagementPage() {
  const [pending, setPending] = useState<BidRecommendation[]>([])
  const [history, setHistory] = useState<BidRecommendation[]>([])
  const [selected, setSelected] = useState<number[]>([])
  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [p, h] = await Promise.all([bidApi.pending(), bidApi.history()])
      setPending(p)
      setHistory(h)
      setSelected([])
    } catch {
      setError('추천 목록을 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const handleRecommend = async () => {
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const result = await bidApi.recommend()
      setNotice(
        result.length === 0
          ? '조정이 필요한 키워드가 없습니다. 현재 입찰가가 적정 범위입니다.'
          : `${result.length}건의 조정안을 만들었습니다. 검토 후 승인하세요.`
      )
      await load()
    } catch {
      setError('추천 생성에 실패했습니다. 먼저 검색광고 데이터를 동기화했는지 확인하세요.')
    } finally {
      setBusy(false)
    }
  }

  const runDecision = async (action: () => Promise<unknown>, message: string) => {
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      await action()
      setNotice(message)
      await load()
    } catch {
      setError('처리에 실패했습니다. 잠시 후 다시 시도하세요.')
    } finally {
      setBusy(false)
    }
  }

  const toggle = (id: number) =>
    setSelected((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]))

  const toggleAll = () =>
    setSelected((prev) => (prev.length === pending.length ? [] : pending.map((r) => r.id)))

  const renderRow = (r: BidRecommendation, actionable: boolean) => {
    const up = r.direction === 'UP'
    return (
      <div key={r.id} className="px-6 py-4 transition-colors hover:bg-muted/20">
        <div className="flex flex-wrap items-start gap-3">
          {actionable && (
            <Checkbox
              checked={selected.includes(r.id)}
              onCheckedChange={() => toggle(r.id)}
              className="mt-1"
            />
          )}
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-medium">{r.keyword}</span>
              <span className="text-sm text-muted-foreground">{won(r.currentBid)}</span>
              <span className={cn('flex items-center gap-0.5 text-sm font-semibold', up ? 'text-emerald-600' : 'text-rose-600')}>
                {up ? <ArrowUpRight className="h-3.5 w-3.5" /> : <ArrowDownRight className="h-3.5 w-3.5" />}
                {won(r.recommendedBid)}
              </span>
              <Badge variant="outline" className={cn('text-[10px]', up ? 'text-emerald-600' : 'text-rose-600')}>
                {r.changeRate > 0 ? '+' : ''}{r.changeRate}%
              </Badge>
              {!actionable && (
                <Badge variant="outline" className="text-[10px]">{STATUS_LABEL[r.status] ?? r.status}</Badge>
              )}
            </div>
            <p className="mt-1 text-xs leading-relaxed text-muted-foreground">{r.reason}</p>
            {r.resultMessage && (
              <p className="mt-1 text-xs text-muted-foreground">→ {r.resultMessage}</p>
            )}
          </div>
          {actionable && (
            <div className="flex gap-1.5">
              <Button
                size="sm"
                disabled={busy}
                className="gap-1"
                onClick={() => void runDecision(() => bidApi.approve(r.id), `"${r.keyword}" 입찰가를 반영했습니다.`)}
              >
                <Check className="h-3.5 w-3.5" /> 승인
              </Button>
              <Button
                size="sm"
                variant="outline"
                disabled={busy}
                className="gap-1"
                onClick={() => void runDecision(() => bidApi.reject(r.id), `"${r.keyword}" 조정안을 거절했습니다.`)}
              >
                <X className="h-3.5 w-3.5" /> 거절
              </Button>
            </div>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <ShieldCheck className="h-4 w-4 text-emerald-600" />
          승인하신 건만 네이버에 반영됩니다. 자동 조정은 하지 않습니다.
        </div>
        <div className="flex gap-2">
          {selected.length > 0 && (
            <Button
              size="sm"
              disabled={busy}
              className="gap-2"
              onClick={() => void runDecision(() => bidApi.approveAll(selected), `${selected.length}건을 반영했습니다.`)}
            >
              <Check className="h-4 w-4" /> 선택 {selected.length}건 승인
            </Button>
          )}
          <Button size="sm" variant="outline" disabled={busy} onClick={() => void handleRecommend()} className="gap-2">
            <Sparkles className="h-4 w-4" /> {busy ? '분석 중...' : '조정안 분석'}
          </Button>
        </div>
      </div>

      {error && (
        <div className="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          <AlertCircle className="h-4 w-4 shrink-0" /> {error}
        </div>
      )}
      {notice && (
        <div className="flex items-center gap-2 rounded-md border border-emerald-500/30 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
          <Check className="h-4 w-4 shrink-0" /> {notice}
        </div>
      )}

      <Tabs defaultValue="pending">
        <TabsList>
          <TabsTrigger value="pending">승인 대기 {pending.length > 0 && `(${pending.length})`}</TabsTrigger>
          <TabsTrigger value="history">처리 이력</TabsTrigger>
        </TabsList>

        <TabsContent value="pending" className="mt-4">
          <Card>
            <CardHeader className="pb-3">
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle className="text-base">입찰가 조정안</CardTitle>
                  <CardDescription className="text-xs">
                    변동폭 상한과 입찰가 상·하한이 이미 적용된 값입니다.
                  </CardDescription>
                </div>
                {pending.length > 0 && (
                  <Button variant="ghost" size="sm" onClick={toggleAll} className="text-xs">
                    {selected.length === pending.length ? '전체 해제' : '전체 선택'}
                  </Button>
                )}
              </div>
            </CardHeader>
            <CardContent className="p-0">
              {!loading && pending.length === 0 ? (
                <div className="py-14 text-center text-muted-foreground">
                  <Sparkles className="mx-auto mb-3 h-10 w-10 opacity-30" />
                  <p className="text-sm">승인 대기 중인 조정안이 없습니다.</p>
                  <p className="mt-1 text-xs">「조정안 분석」을 눌러 최근 성과를 검토하세요.</p>
                </div>
              ) : (
                <div className="divide-y">{pending.map((r) => renderRow(r, true))}</div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="history" className="mt-4">
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base">처리 이력</CardTitle>
              <CardDescription className="text-xs">누가 언제 무엇을 반영했는지 남는 감사 기록입니다.</CardDescription>
            </CardHeader>
            <CardContent className="p-0">
              {history.length === 0 ? (
                <div className="py-14 text-center text-muted-foreground">
                  <History className="mx-auto mb-3 h-10 w-10 opacity-30" />
                  <p className="text-sm">아직 처리된 조정안이 없습니다.</p>
                </div>
              ) : (
                <div className="divide-y">{history.map((r) => renderRow(r, false))}</div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
