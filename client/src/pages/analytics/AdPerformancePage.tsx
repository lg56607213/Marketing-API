import { useCallback, useEffect, useState } from 'react'
import { RefreshCw, TrendingUp, MousePointerClick, Eye, Wallet, Target, AlertCircle } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { adsApi } from '@/services/api'
import type { AdSummary, KeywordPerformance } from '@/types'
import { cn } from '@/lib/utils'

const daysAgo = (n: number) => {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return d.toISOString().slice(0, 10)
}

const won = (v: number) => `${Math.round(v).toLocaleString()}원`
const num = (v: number) => v.toLocaleString()

export function AdPerformancePage() {
  const [since, setSince] = useState(daysAgo(30))
  const [until, setUntil] = useState(daysAgo(1))
  const [summary, setSummary] = useState<AdSummary | null>(null)
  const [keywords, setKeywords] = useState<KeywordPerformance[]>([])
  const [loading, setLoading] = useState(false)
  const [syncing, setSyncing] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [s, k] = await Promise.all([adsApi.summary(since, until), adsApi.keywords(since, until)])
      setSummary(s)
      setKeywords(k)
    } catch {
      setError('성과 데이터를 불러오지 못했습니다. 백엔드가 실행 중인지 확인하세요.')
    } finally {
      setLoading(false)
    }
  }, [since, until])

  useEffect(() => {
    void load()
  }, [load])

  const handleSync = async () => {
    setSyncing(true)
    setError(null)
    try {
      await adsApi.sync(30)
      await load()
    } catch {
      setError('동기화에 실패했습니다. 네이버 검색광고 자격증명 설정을 확인하세요.')
    } finally {
      setSyncing(false)
    }
  }

  const empty = !loading && keywords.length === 0

  const stats = summary
    ? [
        { label: '노출수', value: num(summary.impCnt), icon: Eye, color: 'text-sky-500', bg: 'bg-sky-50' },
        { label: '클릭수', value: num(summary.clkCnt), icon: MousePointerClick, color: 'text-indigo-500', bg: 'bg-indigo-50' },
        { label: '클릭률(CTR)', value: `${summary.ctr}%`, icon: TrendingUp, color: 'text-emerald-500', bg: 'bg-emerald-50' },
        { label: '광고비', value: won(summary.salesAmt), icon: Wallet, color: 'text-amber-500', bg: 'bg-amber-50' },
        { label: '평균 클릭비용(CPC)', value: won(summary.cpc), icon: Target, color: 'text-violet-500', bg: 'bg-violet-50' },
        { label: '전환당 비용(CPA)', value: summary.ccnt > 0 ? won(summary.cpa) : '-', icon: Target, color: 'text-rose-500', bg: 'bg-rose-50' },
      ]
    : []

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-wrap items-end gap-3">
          <div className="space-y-1">
            <Label htmlFor="since" className="text-xs text-muted-foreground">시작일</Label>
            <Input id="since" type="date" value={since} onChange={(e) => setSince(e.target.value)} className="h-9 w-40" />
          </div>
          <div className="space-y-1">
            <Label htmlFor="until" className="text-xs text-muted-foreground">종료일</Label>
            <Input id="until" type="date" value={until} onChange={(e) => setUntil(e.target.value)} className="h-9 w-40" />
          </div>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => void load()} disabled={loading} className="gap-2">
            <RefreshCw className={cn('h-4 w-4', loading && 'animate-spin')} /> 새로고침
          </Button>
          <Button size="sm" onClick={() => void handleSync()} disabled={syncing} className="gap-2">
            <RefreshCw className={cn('h-4 w-4', syncing && 'animate-spin')} />
            {syncing ? '동기화 중...' : '네이버에서 가져오기'}
          </Button>
        </div>
      </div>

      {error && (
        <div className="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          <AlertCircle className="h-4 w-4 shrink-0" />
          {error}
        </div>
      )}

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
        {stats.map(({ label, value, icon: Icon, color, bg }) => (
          <Card key={label}>
            <CardContent className="flex items-center gap-3 p-4">
              <div className={cn('rounded-md p-2', bg)}>
                <Icon className={cn('h-4 w-4', color)} />
              </div>
              <div className="min-w-0">
                <p className="text-[11px] text-muted-foreground truncate">{label}</p>
                <p className="text-lg font-semibold truncate">{value}</p>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">키워드별 성과</CardTitle>
          <CardDescription className="text-xs">
            광고비 기준 내림차순. 기간 합계로 CTR·CPC·전환율을 다시 계산한 값입니다.
          </CardDescription>
        </CardHeader>
        <CardContent className="p-0">
          {empty ? (
            <div className="py-14 text-center text-muted-foreground">
              <Eye className="mx-auto mb-3 h-10 w-10 opacity-30" />
              <p className="text-sm">적재된 성과 데이터가 없습니다.</p>
              <p className="mt-1 text-xs">「네이버에서 가져오기」를 눌러 동기화하세요.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
                    <th className="px-6 py-2.5 text-left font-semibold">키워드</th>
                    <th className="px-3 py-2.5 text-left font-semibold">광고그룹</th>
                    <th className="px-3 py-2.5 text-right font-semibold">입찰가</th>
                    <th className="px-3 py-2.5 text-right font-semibold">노출수</th>
                    <th className="px-3 py-2.5 text-right font-semibold">클릭</th>
                    <th className="px-3 py-2.5 text-right font-semibold">CTR</th>
                    <th className="px-3 py-2.5 text-right font-semibold">CPC</th>
                    <th className="px-3 py-2.5 text-right font-semibold">광고비</th>
                    <th className="px-3 py-2.5 text-right font-semibold">전환</th>
                    <th className="px-6 py-2.5 text-right font-semibold">평균순위</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {keywords.map((k) => (
                    <tr key={k.nccKeywordId} className="transition-colors hover:bg-muted/20">
                      <td className="px-6 py-3">
                        <span className="font-medium">{k.keyword}</span>
                        {k.status && k.status !== 'ELIGIBLE' && (
                          <Badge variant="outline" className="ml-2 text-[10px]">{k.status}</Badge>
                        )}
                      </td>
                      <td className="px-3 py-3 text-xs text-muted-foreground">{k.adgroupName}</td>
                      <td className="px-3 py-3 text-right">{k.bidAmt != null ? won(k.bidAmt) : '-'}</td>
                      <td className="px-3 py-3 text-right">{num(k.impCnt)}</td>
                      <td className="px-3 py-3 text-right">{num(k.clkCnt)}</td>
                      <td className={cn('px-3 py-3 text-right font-medium', k.ctr >= 5 ? 'text-emerald-600' : 'text-amber-600')}>
                        {k.ctr}%
                      </td>
                      <td className="px-3 py-3 text-right">{won(k.cpc)}</td>
                      <td className="px-3 py-3 text-right font-medium">{won(k.salesAmt)}</td>
                      <td className="px-3 py-3 text-right">{num(k.ccnt)}</td>
                      <td className="px-6 py-3 text-right">{k.avgRnk}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
