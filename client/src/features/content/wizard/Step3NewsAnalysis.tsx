import { useState } from 'react'
import { Checkbox } from '@/components/ui/checkbox'
import { Badge } from '@/components/ui/badge'
import { Newspaper, ExternalLink } from 'lucide-react'

interface NewsItem {
  id: string
  title: string
  summary: string
  source: string
  date: string
  relevance: 'high' | 'medium' | 'low'
}

const RELEVANCE_LABELS: Record<string, string> = {
  high: '높은 관련성',
  medium: '보통 관련성',
  low: '낮은 관련성',
}

function generateMockNews(topic: string, keywords: string[]): NewsItem[] {
  const kw = keywords[0] ?? topic
  return [
    {
      id: '1',
      title: `${topic} 시장, 2026년 2분기 역대 최대 성장`,
      summary: `업계 분석가들은 ${topic.toLowerCase()} 활동이 23% 급증했다고 보고했습니다. 유리한 시장 환경과 소비자 신뢰 증가가 주요 원인이며, ${kw}이(가) 핵심 성장 동력으로 부상했습니다.`,
      source: '파이낸셜 타임스',
      date: '2026-07-01',
      relevance: 'high',
    },
    {
      id: '2',
      title: `새로운 규제, ${topic} 업계에 영향`,
      summary: `이번 주 발표된 규제 변경 사항은 ${topic.toLowerCase()} 산업 지형을 재편할 것으로 예상됩니다. 전문가들은 기업들이 적응 전략을 마련해야 한다고 조언합니다.`,
      source: '블룸버그',
      date: '2026-06-29',
      relevance: 'high',
    },
    {
      id: '3',
      title: `소비자 트렌드: 2026년 ${kw}의 의미`,
      summary: `${kw.toLowerCase()}에 대한 소비자 선호도가 변화하고 있다는 연구 결과가 발표됐습니다. 특히 젊은 세대를 중심으로 디지털 우선 접근 방식이 확산되고 있습니다.`,
      source: '로이터',
      date: '2026-06-27',
      relevance: 'medium',
    },
    {
      id: '4',
      title: `전문가 분석: 2026년 하반기 ${topic} 전망`,
      summary: `주요 경제학자들이 2026년 하반기 전망을 공유했습니다. 글로벌 불확실성 요인이 있지만 전반적으로 낙관적인 시각이 우세합니다.`,
      source: '월스트리트 저널',
      date: '2026-06-25',
      relevance: 'medium',
    },
  ]
}

const relevanceBadge = (r: string) =>
  r === 'high' ? 'default' : r === 'medium' ? 'warning' : 'secondary'

interface Props {
  topic: string
  keywords: string[]
  included: string[]
  onChange: (ids: string[]) => void
}

export function Step3NewsAnalysis({ topic, keywords, included, onChange }: Props) {
  const [news] = useState<NewsItem[]>(() => generateMockNews(topic, keywords))

  const toggle = (id: string) => {
    onChange(included.includes(id) ? included.filter((i) => i !== id) : [...included, id])
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        <span className="font-medium text-foreground">"{topic}"</span>과(와) 관련된 최신 뉴스입니다. 관련 기사를 선택하면 AI 콘텐츠 생성에 활용됩니다.
      </p>

      <div className="space-y-3">
        {news.map((item) => (
          <div
            key={item.id}
            className={`flex gap-4 p-4 rounded-lg border transition-colors cursor-pointer ${
              included.includes(item.id) ? 'border-indigo-400 bg-indigo-50/50' : 'hover:border-border/80'
            }`}
            onClick={() => toggle(item.id)}
          >
            <Checkbox
              checked={included.includes(item.id)}
              onCheckedChange={() => toggle(item.id)}
              onClick={(e) => e.stopPropagation()}
              className="mt-0.5 shrink-0"
            />
            <div className="flex-1 min-w-0 space-y-1">
              <div className="flex items-start gap-2 flex-wrap">
                <p className="text-sm font-medium leading-tight">{item.title}</p>
                <Badge variant={relevanceBadge(item.relevance) as 'default'} className="shrink-0 text-[10px]">
                  {RELEVANCE_LABELS[item.relevance]}
                </Badge>
              </div>
              <p className="text-xs text-muted-foreground leading-relaxed">{item.summary}</p>
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <Newspaper className="h-3 w-3" />
                <span>{item.source}</span>
                <span>·</span>
                <span>{item.date}</span>
                <ExternalLink className="h-3 w-3 ml-auto opacity-50" />
              </div>
            </div>
          </div>
        ))}
      </div>

      <p className="text-xs text-muted-foreground">
        * 뉴스 콘텐츠는 데모용으로 제공됩니다. 실제 서비스에서는 실시간 뉴스 연동이 가능합니다.
      </p>
    </div>
  )
}
