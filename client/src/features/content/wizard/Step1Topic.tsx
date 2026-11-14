import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Search, ArrowRight } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Card, CardContent } from '@/components/ui/card'
import { brandApi, marketApi } from '@/services/api'
import type { BrandResponse, MarketKeyword } from '@/types'
import { cn } from '@/lib/utils'

interface Props {
  topic: string
  brandId: number | null
  onTopicChange: (v: string) => void
  onBrandChange: (id: number) => void
  onKeywordsChange?: (kws: string[]) => void
}

export function Step1Topic({ topic, brandId, onTopicChange, onBrandChange, onKeywordsChange }: Props) {
  const [brands, setBrands] = useState<BrandResponse[]>([])
  // 내 업체 키워드로 조회한 실제 검색 수요. 업체 정보가 없거나 조회에 실패하면 비워 둔다.
  const [popular, setPopular] = useState<MarketKeyword[]>([])
  const [seeds, setSeeds] = useState<string[]>([])
  const navigate = useNavigate()

  useEffect(() => {
    brandApi.list().then((list) => {
      setBrands(list)
      if (list.length > 0 && !brandId) onBrandChange(list[0].id)
    }).catch(() => {})
    marketApi.keywords().then((market) => {
      setSeeds(market.seeds)
      setPopular(market.keywords.filter((k) => k.relevant).slice(0, 4))
    }).catch(() => {})
  }, [])

  const pick = (item: MarketKeyword) => {
    onTopicChange(item.keyword)
    onKeywordsChange?.([item.keyword])
  }

  return (
    <div className="space-y-6">
      {popular.length > 0 ? (
        <div className="rounded-xl border border-indigo-200 bg-indigo-50/60 p-4">
          <div className="mb-3 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Search className="h-4 w-4 text-indigo-500" />
              <p className="text-sm font-semibold text-indigo-700">내 업종에서 많이 검색되는 말</p>
            </div>
            <button
              onClick={() => navigate('/analytics/market')}
              className="flex items-center gap-1 text-xs font-medium text-indigo-500 transition-colors hover:text-indigo-700"
            >
              전체 보기 <ArrowRight className="h-3 w-3" />
            </button>
          </div>
          <div className="grid grid-cols-2 gap-2">
            {popular.map((item) => (
              <button
                key={item.keyword}
                onClick={() => pick(item)}
                className={cn(
                  'flex items-center justify-between gap-2 rounded-lg border px-3 py-2 text-left transition-all hover:border-indigo-400 hover:bg-white',
                  topic === item.keyword ? 'border-indigo-500 bg-white' : 'border-indigo-100 bg-white/60'
                )}
              >
                <p className="min-w-0 truncate text-xs font-semibold text-slate-800">{item.keyword}</p>
                <span className="shrink-0 text-[10px] text-muted-foreground">
                  월 {item.totalSearches.toLocaleString()}회 검색
                </span>
              </button>
            ))}
          </div>
          <p className="mt-2 text-[10px] text-muted-foreground">네이버의 최근 한 달 검색수입니다.</p>
        </div>
      ) : (
        <p className="rounded-md border bg-muted/20 px-4 py-3 text-xs text-muted-foreground">
          <a href="/onboarding" className="underline">업체 정보</a>를 등록하면 내 업종에서 실제로 많이 검색되는 말을 여기에 보여 줍니다.
        </p>
      )}

      <div className="space-y-1.5">
        <Label>브랜드 선택</Label>
        <Select
          value={brandId?.toString() ?? ''}
          onValueChange={(v) => onBrandChange(Number(v))}
        >
          <SelectTrigger>
            <SelectValue placeholder="브랜드를 선택하세요..." />
          </SelectTrigger>
          <SelectContent>
            {brands.map((b) => (
              <SelectItem key={b.id} value={b.id.toString()}>{b.name}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        {brands.length === 0 && (
          <p className="text-xs text-amber-600">등록된 브랜드가 없습니다. <a href="/projects" className="underline">먼저 브랜드를 생성하세요</a>.</p>
        )}
      </div>

      <div className="space-y-1.5">
        <Label>콘텐츠 주제</Label>
        <Input
          placeholder="무엇에 대해 쓸지 적어 주세요. 예: 우리 상품을 고를 때 확인할 점"
          value={topic}
          onChange={(e) => onTopicChange(e.target.value)}
          className="text-base"
        />
      </div>

      {seeds.length > 0 && (
        <div className="space-y-2">
          <p className="text-sm text-muted-foreground">내 핵심 키워드로 시작하기:</p>
          <div className="flex flex-wrap gap-2">
            {seeds.map((t) => (
              <Card
                key={t}
                className={`cursor-pointer transition-all hover:border-indigo-400 ${topic === t ? 'border-indigo-500 bg-indigo-50' : ''}`}
                onClick={() => onTopicChange(t)}
              >
                <CardContent className="px-3 py-1.5 text-sm">{t}</CardContent>
              </Card>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
