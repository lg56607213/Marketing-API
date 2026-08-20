import { useCallback, useEffect, useState } from 'react'
import { Globe, AlertCircle, CheckCircle2, XCircle, Plug, RefreshCw } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { seoApi } from '@/services/api'
import type { SeoRow, SeoHealth } from '@/types'
import { cn } from '@/lib/utils'

const num = (v: number) => v.toLocaleString()

export function SeoPage() {
  const [rows, setRows] = useState<SeoRow[]>([])
  const [health, setHealth] = useState<SeoHealth | null>(null)
  const [dimension, setDimension] = useState<'query' | 'page'>('query')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async (dim: 'query' | 'page') => {
    setLoading(true)
    setError(null)
    try {
      const [h, r] = await Promise.all([seoApi.health(), seoApi.searchAnalytics(dim)])
      setHealth(h)
      setRows(r)
    } catch {
      setError('자연검색 데이터를 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load(dimension)
  }, [dimension, load])

  const total = rows.reduce(
    (acc, r) => ({ imp: acc.imp + r.impressions, clk: acc.clicks + r.clicks, clicks: acc.clicks + r.clicks }),
    { imp: 0, clk: 0, clicks: 0 }
  )
  const avgPosition = rows.length > 0
    ? Math.round((rows.reduce((a, r) => a + r.position * r.impressions, 0) / Math.max(total.imp, 1)) * 100) / 100
    : 0

  return (
    <div className="space-y-6">
      {health && (
        <div
          className={cn(
            'flex items-center gap-2 rounded-md border px-4 py-3 text-sm',
            health.provider === 'stub'
              ? 'border-amber-500/30 bg-amber-50 text-amber-800'
              : health.reachable
                ? 'border-emerald-500/30 bg-emerald-50 text-emerald-700'
                : 'border-destructive/30 bg-destructive/5 text-destructive'
          )}
        >
          {health.provider === 'stub' ? (
            <Plug className="h-4 w-4 shrink-0" />
          ) : health.reachable ? (
            <CheckCircle2 className="h-4 w-4 shrink-0" />
          ) : (
            <XCircle className="h-4 w-4 shrink-0" />
          )}
          {health.message}
        </div>
      )}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">
          광고가 아닌 자연검색 유입입니다. 최근 28일 기준이며 광고비 개념이 없습니다.
        </p>
        <div className="flex items-center gap-2">
          <Tabs value={dimension} onValueChange={(v) => setDimension(v as 'query' | 'page')}>
            <TabsList>
              <TabsTrigger value="query">검색어</TabsTrigger>
              <TabsTrigger value="page">페이지</TabsTrigger>
            </TabsList>
          </Tabs>
          <Button variant="outline" size="sm" disabled={loading} onClick={() => void load(dimension)} className="gap-2">
            <RefreshCw className={cn('h-4 w-4', loading && 'animate-spin')} /> 새로고침
          </Button>
        </div>
      </div>

      {error && (
        <div className="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          <AlertCircle className="h-4 w-4 shrink-0" /> {error}
        </div>
      )}

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {[
          { label: dimension === 'query' ? '검색어 수' : '페이지 수', value: num(rows.length) },
          { label: '노출수', value: num(total.imp) },
          { label: '클릭수', value: num(total.clicks) },
          { label: '평균 순위', value: avgPosition > 0 ? `${avgPosition}위` : '-' },
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
          <CardTitle className="text-base">
            {dimension === 'query' ? '자연검색 유입 검색어' : '페이지별 자연검색 성과'}
          </CardTitle>
          <CardDescription className="text-xs">노출수 내림차순. 순위는 낮을수록 좋습니다.</CardDescription>
        </CardHeader>
        <CardContent className="p-0">
          {!loading && rows.length === 0 ? (
            <div className="py-14 text-center text-muted-foreground">
              <Globe className="mx-auto mb-3 h-10 w-10 opacity-30" />
              <p className="text-sm">표시할 데이터가 없습니다.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
                    <th className="px-6 py-2.5 text-left font-semibold">
                      {dimension === 'query' ? '검색어' : '페이지'}
                    </th>
                    <th className="px-3 py-2.5 text-right font-semibold">노출</th>
                    <th className="px-3 py-2.5 text-right font-semibold">클릭</th>
                    <th className="px-3 py-2.5 text-right font-semibold">CTR</th>
                    <th className="px-6 py-2.5 text-right font-semibold">평균순위</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {[...rows]
                    .sort((a, b) => b.impressions - a.impressions)
                    .map((r) => (
                      <tr key={r.key} className="transition-colors hover:bg-muted/20">
                        <td className="max-w-[380px] truncate px-6 py-2.5 font-medium">{r.key}</td>
                        <td className="px-3 py-2.5 text-right">{num(r.impressions)}</td>
                        <td className="px-3 py-2.5 text-right">{num(r.clicks)}</td>
                        <td className="px-3 py-2.5 text-right">{r.ctr}%</td>
                        <td
                          className={cn(
                            'px-6 py-2.5 text-right font-medium',
                            r.position <= 3 ? 'text-emerald-600' : r.position <= 10 ? 'text-amber-600' : 'text-muted-foreground'
                          )}
                        >
                          {r.position}
                        </td>
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
