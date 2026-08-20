import { useCallback, useEffect, useState } from 'react'
import { RefreshCw, AlertCircle, Check, ListFilter } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { searchQueryApi } from '@/services/api'
import type { SearchQueryPerformance } from '@/types'
import { cn } from '@/lib/utils'

const won = (v: number) => `${Math.round(v).toLocaleString()}원`
const num = (v: number) => v.toLocaleString()

type Filter = 'all' | 'expanded' | 'registered'

export function SearchQueryPage() {
  const [rows, setRows] = useState<SearchQueryPerformance[]>([])
  const [filter, setFilter] = useState<Filter>('all')
  const [days, setDays] = useState('7')
  const [loading, setLoading] = useState(false)
  const [syncing, setSyncing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setRows(await searchQueryApi.list())
    } catch {
      setRows([])
      setError('적재된 검색어가 없습니다. 「네이버에서 가져오기」를 먼저 실행하세요.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const sync = async () => {
    setSyncing(true)
    setError(null)
    setNotice(null)
    try {
      const result = await searchQueryApi.sync(Number(days))
      setNotice(`${result.days}일치 ${num(result.rows)}행을 가져왔습니다.`)
      await load()
    } catch {
      setError('검색어 리포트를 가져오지 못했습니다. 네이버 연동 상태를 확인하세요.')
    } finally {
      setSyncing(false)
    }
  }

  const visible = rows.filter((r) =>
    filter === 'all' ? true : filter === 'registered' ? r.registered : !r.registered
  )

  const total = visible.reduce(
    (acc, r) => ({
      imp: acc.imp + r.impCnt,
      clk: acc.clk + r.clkCnt,
      cost: acc.cost + r.salesAmt,
    }),
    { imp: 0, clk: 0, cost: 0 }
  )

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">
          광고가 실제로 노출된 검색어입니다. 등록 키워드뿐 아니라 확장검색으로 들어온 것까지 보입니다.
        </p>
        <div className="flex gap-2">
          <Select value={days} onValueChange={setDays}>
            <SelectTrigger className="h-9 w-28"><SelectValue /></SelectTrigger>
            <SelectContent>
              {['3', '7', '14'].map((d) => (
                <SelectItem key={d} value={d}>{d}일치</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button size="sm" disabled={syncing} onClick={() => void sync()} className="gap-2">
            <RefreshCw className={cn('h-4 w-4', syncing && 'animate-spin')} />
            {syncing ? '가져오는 중...' : '네이버에서 가져오기'}
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

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {[
          { label: '검색어 수', value: num(visible.length) },
          { label: '노출수', value: num(total.imp) },
          { label: '클릭수', value: num(total.clk) },
          { label: '클릭당 평균', value: total.clk > 0 ? won(total.cost / total.clk) : '-' },
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
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <CardTitle className="text-base">검색어별 성과</CardTitle>
              <CardDescription className="text-xs">광고비 내림차순</CardDescription>
            </div>
            <div className="flex gap-1">
              {([
                ['all', '전체'],
                ['expanded', '확장검색만'],
                ['registered', '등록 키워드만'],
              ] as [Filter, string][]).map(([key, label]) => (
                <Button
                  key={key}
                  variant={filter === key ? 'default' : 'outline'}
                  size="sm"
                  className="h-7 text-xs"
                  onClick={() => setFilter(key)}
                >
                  {label}
                </Button>
              ))}
            </div>
          </div>
        </CardHeader>
        <CardContent className="p-0">
          {!loading && visible.length === 0 ? (
            <div className="py-14 text-center text-muted-foreground">
              <ListFilter className="mx-auto mb-3 h-10 w-10 opacity-30" />
              <p className="text-sm">표시할 검색어가 없습니다.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
                    <th className="px-6 py-2.5 text-left font-semibold">검색어</th>
                    <th className="px-3 py-2.5 text-left font-semibold">유형</th>
                    <th className="px-3 py-2.5 text-right font-semibold">노출</th>
                    <th className="px-3 py-2.5 text-right font-semibold">클릭</th>
                    <th className="px-3 py-2.5 text-right font-semibold">CTR</th>
                    <th className="px-3 py-2.5 text-right font-semibold">클릭당</th>
                    <th className="px-6 py-2.5 text-right font-semibold">광고비</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {visible.slice(0, 300).map((r) => (
                    <tr key={r.searchQuery} className="transition-colors hover:bg-muted/20">
                      <td className="px-6 py-2.5 font-medium">{r.searchQuery}</td>
                      <td className="px-3 py-2.5">
                        <Badge
                          variant="outline"
                          className={cn('text-[10px]', r.registered ? '' : 'text-emerald-600')}
                        >
                          {r.registered ? '등록' : '확장'}
                        </Badge>
                      </td>
                      <td className="px-3 py-2.5 text-right">{num(r.impCnt)}</td>
                      <td className="px-3 py-2.5 text-right">{num(r.clkCnt)}</td>
                      <td className="px-3 py-2.5 text-right">{r.ctr}%</td>
                      <td className="px-3 py-2.5 text-right">{r.clkCnt > 0 ? won(r.cpc) : '-'}</td>
                      <td className="px-6 py-2.5 text-right font-medium">{won(r.salesAmt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {visible.length > 300 && (
                <p className="px-6 py-3 text-xs text-muted-foreground">
                  상위 300개만 표시했습니다. 전체 {num(visible.length)}개.
                </p>
              )}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
