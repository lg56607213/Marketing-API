import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { TrendingUp, Sparkles, ArrowRight } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Card, CardContent } from '@/components/ui/card'
import { brandApi } from '@/services/api'
import type { BrandResponse } from '@/types'
import { cn } from '@/lib/utils'

const EXAMPLE_TOPICS = ['대출 상품', '모기지 금리', '개인 재무 팁', '부동산 시장', '투자 전략', '보험 상품']

const MI_TRENDING = [
  { topic: '담보대출', score: 92, keywords: ['담보대출', 'LTV', 'DSR'] },
  { topic: 'DSR 규제', score: 85, keywords: ['DSR', '규제완화', '대출한도'] },
  { topic: '대출 갈아타기', score: 61, keywords: ['갈아타기', '금리비교', '중도상환'] },
  { topic: '생애최초 대출', score: 68, keywords: ['생애최초', 'LTV', '주택구입'] },
]

interface Props {
  topic: string
  brandId: number | null
  onTopicChange: (v: string) => void
  onBrandChange: (id: number) => void
  onKeywordsChange?: (kws: string[]) => void
}

export function Step1Topic({ topic, brandId, onTopicChange, onBrandChange, onKeywordsChange }: Props) {
  const [brands, setBrands] = useState<BrandResponse[]>([])
  const navigate = useNavigate()

  useEffect(() => {
    brandApi.list().then((list) => {
      setBrands(list)
      if (list.length > 0 && !brandId) onBrandChange(list[0].id)
    }).catch(() => {})
  }, [])

  const handleMiSelect = (item: typeof MI_TRENDING[0]) => {
    onTopicChange(item.topic)
    onKeywordsChange?.(item.keywords)
  }

  return (
    <div className="space-y-6">
      {/* 마켓 인텔리전스 추천 배너 */}
      <div className="rounded-xl border border-indigo-200 bg-indigo-50/60 p-4">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <Sparkles className="h-4 w-4 text-indigo-500" />
            <p className="text-sm font-semibold text-indigo-700">마켓 인텔리전스 추천</p>
          </div>
          <button
            onClick={() => navigate('/analytics/market')}
            className="flex items-center gap-1 text-xs text-indigo-500 hover:text-indigo-700 font-medium transition-colors"
          >
            전체 보기 <ArrowRight className="h-3 w-3" />
          </button>
        </div>
        <div className="grid grid-cols-2 gap-2">
          {MI_TRENDING.map((item) => (
            <button
              key={item.topic}
              onClick={() => handleMiSelect(item)}
              className={cn(
                'flex items-center justify-between gap-2 rounded-lg border px-3 py-2 text-left transition-all hover:border-indigo-400 hover:bg-white',
                topic === item.topic ? 'border-indigo-500 bg-white' : 'border-indigo-100 bg-white/60'
              )}
            >
              <div className="min-w-0">
                <p className="text-xs font-semibold text-slate-800 truncate">{item.topic}</p>
                <p className="text-[10px] text-muted-foreground truncate">{item.keywords.join(' · ')}</p>
              </div>
              <div className="flex items-center gap-1 shrink-0">
                <TrendingUp className="h-3 w-3 text-emerald-500" />
                <span className="text-[10px] font-bold text-emerald-600">{item.score}</span>
              </div>
            </button>
          ))}
        </div>
      </div>

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
          placeholder="예: 대출, 모기지, 금융, 부동산..."
          value={topic}
          onChange={(e) => onTopicChange(e.target.value)}
          className="text-base"
        />
      </div>

      <div className="space-y-2">
        <p className="text-sm text-muted-foreground">주제 예시:</p>
        <div className="flex flex-wrap gap-2">
          {EXAMPLE_TOPICS.map((t) => (
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
    </div>
  )
}
