import { useState } from 'react'
import { AlertCircle, CheckCircle2, Loader2, XCircle } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { marketApi } from '@/services/api'
import type { CompetitorReport } from '@/types'
import { cn } from '@/lib/utils'

const won = (v: number | null) => (v == null ? '-' : `${Math.round(v).toLocaleString()}원`)
const STORAGE_KEY = 'competitor_urls'

function Mark({ ok }: { ok: boolean }) {
  return ok
    ? <CheckCircle2 className="mx-auto h-4 w-4 text-emerald-600" />
    : <XCircle className="mx-auto h-4 w-4 text-muted-foreground/50" />
}

/** 경쟁사 홈페이지를 내 홈페이지와 같은 기준으로 확인하고, 내 키워드의 자리 다툼을 보여 준다. */
export function CompetitorAnalysisPage() {
  const [urls, setUrls] = useState<string[]>(() => {
    try {
      const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]') as string[]
      return [saved[0] ?? '', saved[1] ?? '', saved[2] ?? '']
    } catch {
      return ['', '', '']
    }
  })
  const [report, setReport] = useState<CompetitorReport | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    const targets = urls.map((u) => u.trim()).filter(Boolean)
    if (targets.length === 0) {
      setError('경쟁사 홈페이지 주소를 1개 이상 입력하세요.')
      return
    }
    setError(null)
    setLoading(true)
    try {
      setReport(await marketApi.competitors(targets))
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(targets))
      } catch {
        // 저장하지 못해도 결과는 보여 준다.
      }
    } catch (err) {
      const message = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      setError(message ?? '경쟁사를 확인하지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-6">
      <p className="text-sm text-muted-foreground">
        경쟁사 홈페이지를 실제로 열어 내 홈페이지와 같은 기준으로 비교하고, 내 키워드에서 광고 자리 다툼이 얼마나 센지 보여 줍니다.
      </p>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">경쟁사 홈페이지</CardTitle>
          <CardDescription className="text-xs">최대 3곳. 내 홈페이지는 업체 정보에 등록한 주소를 씁니다.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={submit} className="space-y-4">
            <div className="grid gap-3 sm:grid-cols-3">
              {urls.map((url, i) => (
                <div key={i} className="space-y-1.5">
                  <Label htmlFor={`comp-${i}`}>경쟁사 {i + 1}</Label>
                  <Input id={`comp-${i}`} placeholder="예: www.example.com" value={url}
                    onChange={(e) => setUrls((prev) => prev.map((u, j) => (j === i ? e.target.value : u)))} />
                </div>
              ))}
            </div>
            {error && (
              <div className="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
                <AlertCircle className="h-4 w-4 shrink-0" /> {error}
              </div>
            )}
            <Button type="submit" disabled={loading} className="gap-2">
              {loading && <Loader2 className="h-4 w-4 animate-spin" />} 비교하기
            </Button>
          </form>
        </CardContent>
      </Card>

      {report && (
        <>
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base">비교해서 알 수 있는 점</CardTitle>
            </CardHeader>
            <CardContent>
              <ul className="list-disc space-y-2 pl-5 text-sm">
                {report.findings.map((f) => <li key={f}>{f}</li>)}
              </ul>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base">홈페이지 비교</CardTitle>
              <CardDescription className="text-xs">각 홈페이지 첫 화면을 방금 열어 확인한 결과입니다.</CardDescription>
            </CardHeader>
            <CardContent className="p-0">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-muted/40 text-xs text-muted-foreground">
                      <th className="px-6 py-2.5 text-left font-semibold">홈페이지</th>
                      <th className="px-3 py-2.5 text-center font-semibold">접속</th>
                      <th className="px-3 py-2.5 text-center font-semibold">네이버 전환 추적</th>
                      <th className="px-3 py-2.5 text-center font-semibold">구글 애널리틱스</th>
                      <th className="px-6 py-2.5 text-center font-semibold">문의 양식·전화</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {report.sites.map((s) => (
                      <tr key={s.url} className={cn(s.mine && 'bg-indigo-50/50')}>
                        <td className="max-w-xs px-6 py-2.5">
                          <p className="truncate font-medium">
                            {s.check.title ?? s.url}
                            {s.mine && <Badge className="ml-2 text-[10px]">내 홈페이지</Badge>}
                          </p>
                          <p className="truncate text-xs text-muted-foreground">{s.url}</p>
                          {s.check.description && <p className="mt-1 line-clamp-2 text-xs text-muted-foreground">{s.check.description}</p>}
                        </td>
                        <td className="px-3 py-2.5"><Mark ok={s.check.reachable} /></td>
                        <td className="px-3 py-2.5"><Mark ok={s.check.naverTracking} /></td>
                        <td className="px-3 py-2.5"><Mark ok={s.check.googleAnalytics} /></td>
                        <td className="px-6 py-2.5"><Mark ok={s.check.hasForm} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </CardContent>
          </Card>

          {report.keywordCompetition.length > 0 && (
            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="text-base">내 키워드의 자리 다툼</CardTitle>
                <CardDescription className="text-xs">
                  그 순위에 노출되려면 필요한 클릭당 입찰가(네이버 추정, 모바일). 1위와 5위의 차이가 클수록 상위 자리를 두고 경쟁이 심합니다.
                </CardDescription>
              </CardHeader>
              <CardContent className="p-0">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="bg-muted/40 text-xs text-muted-foreground">
                        <th className="px-6 py-2.5 text-left font-semibold">키워드</th>
                        <th className="px-3 py-2.5 text-left font-semibold">경쟁</th>
                        <th className="px-3 py-2.5 text-right font-semibold">1위</th>
                        <th className="px-3 py-2.5 text-right font-semibold">3위</th>
                        <th className="px-3 py-2.5 text-right font-semibold">5위</th>
                        <th className="px-6 py-2.5 text-right font-semibold">1위 ÷ 5위</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y">
                      {report.keywordCompetition.map((k) => (
                        <tr key={k.keyword}>
                          <td className="px-6 py-2.5 font-medium">{k.keyword}</td>
                          <td className="px-3 py-2.5">{k.competition}</td>
                          <td className="px-3 py-2.5 text-right">{won(k.bidRank1)}</td>
                          <td className="px-3 py-2.5 text-right">{won(k.bidRank3)}</td>
                          <td className="px-3 py-2.5 text-right">{won(k.bidRank5)}</td>
                          <td className="px-6 py-2.5 text-right">{k.crowdRatio != null ? `${k.crowdRatio}배` : '-'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </CardContent>
            </Card>
          )}

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base">이 화면이 알려 줄 수 없는 것</CardTitle>
            </CardHeader>
            <CardContent>
              <ul className="list-disc space-y-2 pl-5 text-sm text-muted-foreground">
                {report.limits.map((l) => <li key={l}>{l}</li>)}
              </ul>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  )
}
