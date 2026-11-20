import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AlertCircle, Loader2, Search, Sparkles } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { marketApi } from '@/services/api'
import type { MarketOverview } from '@/types'
import { cn } from '@/lib/utils'
import { AdScopeBar } from '@/components/ads/AdScopeBar'
import { useAdScopeStore } from '@/store/adScopeStore'

const won = (v: number | null) => (v == null ? '-' : `${Math.round(v).toLocaleString()}원`)
const num = (v: number) => Math.round(v).toLocaleString()

const COMPETITION_TONE: Record<string, string> = {
  높음: 'border-red-500/50 text-red-800',
  중간: 'border-amber-500/50 text-amber-800',
  낮음: 'border-emerald-500/50 text-emerald-800',
}

/** 내 업종의 검색 수요와 경쟁. 네이버가 주는 최근 한 달 수치만 쓴다. */
export function MarketIntelligencePage() {
  const navigate = useNavigate()
  const [seeds, setSeeds] = useState('')
  const [market, setMarket] = useState<MarketOverview | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const { accountId } = useAdScopeStore()

  const load = useCallback(async (input?: string) => {
    if (!accountId) {
      setMarket(null)
      setLoading(false)
      return
    }
    setLoading(true)
    setError(null)
    try {
      const result = await marketApi.keywords(input)
      setMarket(result)
      setSeeds(result.seeds.join(', '))
    } catch (err) {
      const message = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      setError(message ?? '시장 데이터를 가져오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }, [accountId])

  useEffect(() => {
    setMarket(null)
    setSeeds('')
    void load()
  }, [load])

  const writeAbout = (keyword: string) => {
    localStorage.setItem('wizard_prefill', JSON.stringify({ topic: keyword, keywords: [keyword] }))
    navigate('/content/new')
  }

  return (
    <div className="space-y-6">
      <AdScopeBar showCampaign={false} />
      <p className="text-sm text-muted-foreground">
        내 업종의 말이 한 달에 얼마나 검색되고, 그 자리를 두고 경쟁이 얼마나 센지 보여 줍니다. 네이버 검색광고의 최근 한 달 수치입니다.
      </p>

      <form
        className="flex flex-wrap gap-2"
        onSubmit={(e) => {
          e.preventDefault()
          void load(seeds)
        }}
      >
        <Input className="max-w-md" placeholder="살펴볼 키워드 (1~5개, 쉼표로 구분)" value={seeds} onChange={(e) => setSeeds(e.target.value)} />
        <Button type="submit" disabled={loading} className="gap-2">
          {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />} 조회
        </Button>
      </form>

      {error && (
        <div className="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          <AlertCircle className="h-4 w-4 shrink-0" /> {error}
        </div>
      )}

      {market && (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            {[
              { label: '월 검색수 합계', value: `${num(market.totalSearches)}회` },
              { label: '모바일 비중', value: `${market.mobileSharePct}%` },
              { label: '3위 입찰가 (중간값)', value: won(market.medianBidRank3) },
              { label: "경쟁 '높음' 비율", value: `${market.highCompetitionPct}%` },
            ].map((s) => (
              <Card key={s.label}>
                <CardContent className="p-4">
                  <p className="text-[11px] text-muted-foreground">{s.label}</p>
                  <p className="text-xl font-semibold">{s.value}</p>
                </CardContent>
              </Card>
            ))}
          </div>

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base">수치에서 읽을 수 있는 점</CardTitle>
              <CardDescription className="text-xs">기준 키워드: {market.seeds.join(', ')} ({market.seedSource})</CardDescription>
            </CardHeader>
            <CardContent>
              <ul className="list-disc space-y-2 pl-5 text-sm">
                {market.insights.map((i) => <li key={i}>{i}</li>)}
              </ul>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base">키워드별 수요와 경쟁</CardTitle>
              <CardDescription className="text-xs">
                입찰가는 모바일에서 그 순위에 노출되기 위한 네이버 추정치입니다. 「기회」는 검색이 많은데 경쟁과 입찰가가 낮은 편인 키워드입니다.
              </CardDescription>
            </CardHeader>
            <CardContent className="p-0">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-muted/40 text-xs text-muted-foreground">
                      <th className="px-6 py-2.5 text-left font-semibold">키워드</th>
                      <th className="px-3 py-2.5 text-right font-semibold">월 검색수</th>
                      <th className="px-3 py-2.5 text-right font-semibold">모바일</th>
                      <th className="px-3 py-2.5 text-left font-semibold">경쟁</th>
                      <th className="px-3 py-2.5 text-right font-semibold">1위 입찰가</th>
                      <th className="px-3 py-2.5 text-right font-semibold">3위</th>
                      <th className="px-3 py-2.5 text-right font-semibold">5위</th>
                      <th className="px-6 py-2.5 text-right font-semibold" />
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {market.keywords.map((k) => (
                      <tr key={k.keyword} className={cn(!k.relevant && 'text-muted-foreground')}>
                        <td className="px-6 py-2.5 font-medium">
                          {k.keyword}
                          {k.opportunity && (
                            <Badge className="ml-2 gap-1 text-[10px]"><Sparkles className="h-3 w-3" /> 기회</Badge>
                          )}
                        </td>
                        <td className="px-3 py-2.5 text-right">{num(k.totalSearches)}</td>
                        <td className="px-3 py-2.5 text-right">{k.mobileSharePct}%</td>
                        <td className="px-3 py-2.5">
                          <Badge variant="outline" className={cn('text-[10px]', COMPETITION_TONE[k.competition])}>{k.competition}</Badge>
                        </td>
                        <td className="px-3 py-2.5 text-right">{won(k.bidRank1)}</td>
                        <td className="px-3 py-2.5 text-right">{won(k.bidRank3)}</td>
                        <td className="px-3 py-2.5 text-right">{won(k.bidRank5)}</td>
                        <td className="whitespace-nowrap px-6 py-2.5 text-right">
                          <button className="text-xs text-indigo-600 hover:underline" onClick={() => writeAbout(k.keyword)}>
                            이 주제로 글쓰기
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </CardContent>
          </Card>

          <p className="text-xs text-muted-foreground">
            검색량의 시기별 변화와 관련 뉴스는 아직 제공하지 않습니다. 네이버 개발자센터의 데이터랩·뉴스 검색 키를 연결하면 추가할 수 있습니다.
          </p>
        </>
      )}
    </div>
  )
}
