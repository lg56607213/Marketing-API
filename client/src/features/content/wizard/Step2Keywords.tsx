import { useEffect, useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Plus, X } from 'lucide-react'

const KEYWORD_MAP: Record<string, string[]> = {
  default: ['마케팅 전략', '브랜드 인지도', '타겟 오디언스', '콘텐츠 플랜', '투자수익률', '참여도', '전환율'],
  '대출': ['저금리', '빠른 승인', '무담보', '유연한 조건', '신용 점수', '월 납입금', '사전 승인'],
  '모기지': ['주택 담보 대출', '고정 금리', '변동 금리', '계약금', '부동산 가치', '재융자', '상환 일정'],
  '금융': ['투자', '포트폴리오', '리스크 관리', '복리', '재무 계획', '분산 투자', '수익률'],
  '부동산': ['부동산 투자', '시장 동향', '집값', '임대 수익', '입지 분석', '자본 이익', '재산세'],
  '보험': ['보장 범위', '보험료', '약관', '보험금 청구', '공제액', '수익자', '리스크 평가'],
}

function suggestKeywords(topic: string): string[] {
  const lower = topic.toLowerCase()
  for (const [key, kws] of Object.entries(KEYWORD_MAP)) {
    if (key !== 'default' && lower.includes(key)) return kws
  }
  return KEYWORD_MAP.default
}

interface Props {
  topic: string
  keywords: string[]
  onChange: (kws: string[]) => void
}

export function Step2Keywords({ topic, keywords, onChange }: Props) {
  const [suggestions, setSuggestions] = useState<string[]>([])
  const [custom, setCustom] = useState('')

  useEffect(() => {
    setSuggestions(suggestKeywords(topic))
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
        <p className="text-sm text-muted-foreground mb-3">
          <span className="font-medium text-foreground">"{topic}"</span>에 대한 AI 추천 키워드입니다. 포함할 키워드를 선택하세요.
        </p>
        <div className="flex flex-wrap gap-2">
          {suggestions.map((kw) => {
            const selected = keywords.includes(kw)
            return (
              <button
                key={kw}
                onClick={() => toggle(kw)}
                className={`px-3 py-1.5 rounded-full text-sm font-medium border transition-all ${
                  selected
                    ? 'bg-indigo-500 text-white border-indigo-500'
                    : 'bg-background border-border hover:border-indigo-400 text-foreground'
                }`}
              >
                {kw}
              </button>
            )
          })}
        </div>
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
