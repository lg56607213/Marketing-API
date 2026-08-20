import { useCallback, useEffect, useState } from 'react'
import { FileText, Sparkles, AlertCircle, CheckCircle2, XCircle, Plug } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { adReportApi } from '@/services/api'
import type { AdReport, AdHealth } from '@/types'
import { cn } from '@/lib/utils'

const RANGES = [
  { value: '7', label: '최근 7일' },
  { value: '14', label: '최근 14일' },
  { value: '30', label: '최근 30일' },
]

export function AdReportPage() {
  const [reports, setReports] = useState<AdReport[]>([])
  const [current, setCurrent] = useState<AdReport | null>(null)
  const [health, setHealth] = useState<AdHealth | null>(null)
  const [days, setDays] = useState('7')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setError(null)
    try {
      const [list, h] = await Promise.all([adReportApi.list(), adReportApi.health()])
      setReports(list)
      setHealth(h)
      if (list.length > 0) setCurrent(list[0])
    } catch {
      setError('리포트 목록을 불러오지 못했습니다.')
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const handleGenerate = async () => {
    setBusy(true)
    setError(null)
    try {
      const report = await adReportApi.generate(Number(days))
      setCurrent(report)
      await load()
    } catch {
      setError('리포트 생성에 실패했습니다. 먼저 검색광고 데이터를 동기화했는지 확인하세요.')
    } finally {
      setBusy(false)
    }
  }

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
          데이터 표는 DB에서 직접 계산하고, 해석만 AI가 작성합니다.
        </p>
        <div className="flex gap-2">
          <Select value={days} onValueChange={setDays}>
            <SelectTrigger className="h-9 w-32">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {RANGES.map((r) => (
                <SelectItem key={r.value} value={r.value}>{r.label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button size="sm" disabled={busy} onClick={() => void handleGenerate()} className="gap-2">
            <Sparkles className="h-4 w-4" /> {busy ? '분석 중...' : '리포트 생성'}
          </Button>
        </div>
      </div>

      {error && (
        <div className="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          <AlertCircle className="h-4 w-4 shrink-0" /> {error}
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-[260px_1fr]">
        <Card className="h-fit">
          <CardHeader className="pb-3">
            <CardTitle className="text-base">생성 이력</CardTitle>
            <CardDescription className="text-xs">최근 20건</CardDescription>
          </CardHeader>
          <CardContent className="p-0">
            {reports.length === 0 ? (
              <p className="px-6 pb-6 text-xs text-muted-foreground">아직 생성된 리포트가 없습니다.</p>
            ) : (
              <div className="divide-y">
                {reports.map((r) => (
                  <button
                    key={r.id}
                    onClick={() => setCurrent(r)}
                    className={cn(
                      'w-full px-5 py-3 text-left transition-colors hover:bg-muted/30',
                      current?.id === r.id && 'bg-muted/50'
                    )}
                  >
                    <p className="truncate text-xs font-medium">{r.since} ~ {r.until}</p>
                    <p className="mt-0.5 text-[10px] text-muted-foreground">{r.generatedBy}</p>
                  </button>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <CardTitle className="text-base">
                {current ? current.title : '리포트'}
              </CardTitle>
              {current && (
                <Badge variant="outline" className="text-[10px]">{current.generatedBy}</Badge>
              )}
            </div>
          </CardHeader>
          <CardContent>
            {current ? (
              <pre className="overflow-x-auto whitespace-pre-wrap break-words font-mono text-xs leading-relaxed">
                {current.body}
              </pre>
            ) : (
              <div className="py-14 text-center text-muted-foreground">
                <FileText className="mx-auto mb-3 h-10 w-10 opacity-30" />
                <p className="text-sm">「리포트 생성」을 눌러 분석을 시작하세요.</p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
