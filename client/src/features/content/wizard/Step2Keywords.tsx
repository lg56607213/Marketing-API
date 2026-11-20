import { useEffect, useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Loader2, Plus, X } from 'lucide-react'
import { marketApi } from '@/services/api'
import type { MarketKeyword } from '@/types'

interface Props {
  topic: string
  keywords: string[]
  onChange: (kws: string[]) => void
}

/** 주제와 함께 실제로 검색되는 연관 키워드를 네이버에서 받아 고르게 한다. 받지 못하면 직접 입력만 남긴다. */
export function Step2Keywords({ topic, keywords, onChange }: Props) {
  const [suggestions, setSuggestions] = useState<MarketKeyword[]>([])
  const [loading, setLoading] = useState(true)
  const [custom, setCustom] = useState('')

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    marketApi
      .keywords(topic)
      .then((market) => {
        if (!cancelled) setSuggestions(market.keywords.slice(0, 12))
      })
      .catch(() => {
        if (!cancelled) setSuggestions([])
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [topic])

  const toggle = (kw: string) => {
    onChange(keywords.includes(kw) ? keywords.filter((k) => k !== kw) : [...keywords, kw])
  }

  const addCustom = () => {
    const trimmed = custom.trim()
    if (trimmed && !keywords.includes(trimmed)) {
      onChange([...keywords, trimmed])
      setCustom('')
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <p className="mb-3 text-sm text-muted-foreground">
          <span className="font-medium text-foreground">"{topic}"</span>와 함께 실제로 검색되는 말입니다. 글에 넣을 키워드를 고르세요.
        </p>
        {loading ? (
          <p className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> 연관 키워드를 가져오는 중
          </p>
        ) : suggestions.length === 0 ? (
          <p className="rounded-md border bg-muted/20 px-4 py-3 text-xs text-muted-foreground">
            이 주제의 연관 키워드를 가져오지 못했습니다. 아래에 직접 입력하세요. 주제를 더 짧고 일반적인 말로 바꾸면 나올 수 있습니다.
          </p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {suggestions.map((s) => {
              const selected = keywords.includes(s.keyword)
              return (
                <button
                  key={s.keyword}
                  onClick={() => toggle(s.keyword)}
                  title={`월 ${s.totalSearches.toLocaleString()}회 검색 · 경쟁 ${s.competition}`}
                  className={`rounded-full border px-3 py-1.5 text-sm font-medium transition-all ${
                    selected
                      ? 'border-indigo-500 bg-indigo-500 text-white'
                      : 'border-border bg-background text-foreground hover:border-indigo-400'
                  }`}
                >
                  {s.keyword}
                  <span className={`ml-1.5 text-[10px] ${selected ? 'text-indigo-100' : 'text-muted-foreground'}`}>
                    {s.totalSearches.toLocaleString()}
                  </span>
                </button>
              )
            })}
          </div>
        )}
        {suggestions.length > 0 && <p className="mt-2 text-[10px] text-muted-foreground">숫자는 네이버의 최근 한 달 검색수입니다.</p>}
      </div>

      <div className="space-y-2">
        <p className="text-sm font-medium">직접 키워드 추가</p>
        <div className="flex gap-2">
          <Input
            placeholder="키워드를 입력하세요..."
            value={custom}
            onChange={(e) => setCustom(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && addCustom()}
          />
          <Button variant="outline" size="icon" onClick={addCustom}>
            <Plus className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {keywords.length > 0 && (
        <div className="space-y-2">
          <p className="text-sm font-medium">선택된 키워드 ({keywords.length}개)</p>
          <div className="flex flex-wrap gap-2">
            {keywords.map((kw) => (
              <Badge key={kw} variant="default" className="gap-1 pr-1.5">
                {kw}
                <button onClick={() => toggle(kw)} className="ml-0.5 hover:opacity-70">
                  <X className="h-3 w-3" />
                </button>
              </Badge>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
