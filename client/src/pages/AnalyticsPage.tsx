import { useEffect, useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { AdOverview } from '@/features/ads/AdOverview'
import { adsApi, marketApi } from '@/services/api'
import type { HourOfDay, KeywordPerformance } from '@/types'

const won = (v: number) => `${Math.round(v).toLocaleString()}원`
const num = (v: number) => Math.round(v).toLocaleString()

/** 광고 실적 개요. 수집된 실제 데이터만 보여 준다. */
export function AnalyticsPage() {
  const [keywords, setKeywords] = useState<KeywordPerformance[]>([])
  const [hours, setHours] = useState<HourOfDay[]>([])

  useEffect(() => {
    adsApi.keywords().then((rows) => setKeywords([...rows].sort((a, b) => b.salesAmt - a.salesAmt).slice(0, 8))).catch(() => {})
    marketApi.hourly().then(setHours).catch(() => {})
  }, [])

  const maxClicks = Math.max(1, ...hours.map((h) => h.clkCnt))
  const busiest = [...hours].sort((a, b) => b.clkCnt - a.clkCnt)[0]

  return (
    <div className="space-y-6">
      <p className="text-sm text-muted-foreground">
        네이버 검색광고에서 실제로 수집한 최근 30일 실적입니다. 숫자가 비어 있으면 아직 수집된 데이터가 없는 것입니다.
      </p>

      <AdOverview />

      {hours.some((h) => h.clkCnt > 0) && (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">하루 중 언제 클릭되는가</CardTitle>
            <CardDescription className="text-xs">
              최근 7일 · 0시~23시{busiest ? ` · 가장 많은 시간대는 ${busiest.hour}시 (클릭 ${num(busiest.clkCnt)}회)` : ''}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex h-24 items-end gap-1">
              {hours.map((h) => (
                <div key={h.hour} className="flex flex-1 flex-col items-center gap-1"
                  title={`${h.hour}시 · 클릭 ${num(h.clkCnt)}회 · 광고비 ${won(h.salesAmt)} · 전환 ${num(h.convCnt)}건`}>
                  <span className="w-full rounded-t bg-indigo-400" style={{ height: `${Math.max(2, (h.clkCnt / maxClicks) * 72)}px` }} />
                  <span className="text-[9px] text-muted-foreground">{h.hour % 3 === 0 ? h.hour : ''}</span>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {keywords.length > 0 && (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">광고비를 가장 많이 쓴 키워드</CardTitle>
            <CardDescription className="text-xs">최근 30일</CardDescription>
          </CardHeader>
          <CardContent className="p-0">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-muted/40 text-xs text-muted-foreground">
                    <th className="px-6 py-2.5 text-left font-semibold">키워드</th>
                    <th className="px-3 py-2.5 text-right font-semibold">광고비</th>
                    <th className="px-3 py-2.5 text-right font-semibold">클릭</th>
                    <th className="px-3 py-2.5 text-right font-semibold">클릭률</th>
                    <th className="px-3 py-2.5 text-right font-semibold">전환</th>
                    <th className="px-6 py-2.5 text-right font-semibold">전환 1건당</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {keywords.map((k) => (
                    <tr key={k.nccKeywordId}>
                      <td className="px-6 py-2.5 font-medium">{k.keyword}</td>
                      <td className="px-3 py-2.5 text-right">{won(k.salesAmt)}</td>
                      <td className="px-3 py-2.5 text-right">{num(k.clkCnt)}</td>
                      <td className="px-3 py-2.5 text-right">{k.ctr.toFixed(2)}%</td>
                      <td className="px-3 py-2.5 text-right">{num(k.ccnt)}</td>
                      <td className="px-6 py-2.5 text-right">{k.ccnt > 0 ? won(k.salesAmt / k.ccnt) : '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
