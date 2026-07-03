import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { TrendingUp, Sparkles, Newspaper, Calendar, Wand2 } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

const CATEGORIES = ['전체', '금융', '부동산', '규제', '대출']

const TRENDING_KEYWORDS = [
  { kw: '담보대출', score: 92, volume: 45000, change: +12, category: '금융', hot: true },
  { kw: '전세자금대출', score: 88, volume: 38000, change: +8, category: '부동산' },
  { kw: 'DSR 규제', score: 85, volume: 29000, change: +23, category: '규제', hot: true },
  { kw: '금리인하', score: 79, volume: 52000, change: -3, category: '금융' },
  { kw: '아파트 담보대출', score: 76, volume: 31000, change: +15, category: '부동산' },
  { kw: 'LTV 완화', score: 71, volume: 18000, change: +45, category: '규제', hot: true },
  { kw: '생애최초 대출', score: 68, volume: 22000, change: +9, category: '대출' },
  { kw: '신용대출 한도', score: 64, volume: 27000, change: -8, category: '금융' },
  { kw: '대출 갈아타기', score: 61, volume: 19000, change: +31, category: '대출' },
  { kw: '보금자리론', score: 58, volume: 15000, change: +5, category: '부동산' },
  { kw: '중도상환수수료', score: 54, volume: 12000, change: +2, category: '대출' },
  { kw: '변동금리 위험', score: 51, volume: 16000, change: +18, category: '금융' },
]

const AI_RECOMMENDATIONS = [
  {
    title: '2026 하반기 담보대출 완벽 가이드',
    reason: 'DSR·LTV 규제 변화 후 검색량 23% 급증. 지금이 최적의 콘텐츠 타이밍입니다.',
    keywords: ['담보대출', 'DSR', 'LTV', '금리'],
    contentType: '블로그',
    score: 96,
  },
  {
    title: '이사철 전세자금대출 받는 법 총정리',
    reason: '7~8월 이사 성수기 진입. 전세자금 관련 검색이 매년 이 시기 최고조.',
    keywords: ['전세자금대출', '이사철', '전세'],
    contentType: '카드뉴스',
    score: 88,
  },
  {
    title: '대출 갈아타기 타이밍 잡는 법 (금리 비교)',
    reason: '"대출 갈아타기" 검색량 31% 급증. 금리 변동기 실용적 콘텐츠 수요 높음.',
    keywords: ['대출 갈아타기', '금리비교', '중도상환'],
    contentType: 'SNS',
    score: 82,
  },
  {
    title: '생애최초 주택 구입자를 위한 대출 로드맵',
    reason: '생애최초 특별 혜택 확대 정책 이후 관련 검색 지속 증가 중.',
    keywords: ['생애최초 대출', '주택구입', 'LTV'],
    contentType: '유튜브',
    score: 78,
  },
]

const NEWS_ITEMS = [
  {
    id: '1',
    title: '금융위, DSR 규제 완화 검토... 1주택자 한도 확대 논의',
    source: '한국경제',
    date: '2026-07-03',
    sentiment: 'positive' as const,
    summary: '금융당국이 1주택자에 한해 DSR 산정 방식을 완화하는 방안을 검토 중인 것으로 알려졌다. 시장에서는 실수요자 대출 여력 확대 효과를 기대하고 있다.',
    keywords: ['DSR', '규제완화', '담보대출'],
  },
  {
    id: '2',
    title: '한국은행, 기준금리 동결... 하반기 인하 가능성 열어둬',
    source: '조선비즈',
    date: '2026-07-02',
    sentiment: 'neutral' as const,
    summary: '한국은행 금통위가 기준금리를 3.50%로 동결했다. 총재는 하반기 물가 안정 추이에 따라 인하 여부를 검토할 것이라 밝혔다.',
    keywords: ['기준금리', '금리인하', '한국은행'],
  },
  {
    id: '3',
    title: '7월 이사철 전세 수요 급증... 전세자금대출 한도 부족 호소',
    source: '매일경제',
    date: '2026-07-01',
    sentiment: 'negative' as const,
    summary: '하반기 이사 성수기를 맞아 전세 수요가 급증했지만 대출 한도 제한으로 실수요자들이 어려움을 겪고 있다.',
    keywords: ['전세자금대출', '이사철', '부동산'],
  },
  {
    id: '4',
    title: 'LTV 완화 정책 수혜 지역 확대... 수도권 외곽 수혜',
    source: '부동산114',
    date: '2026-06-30',
    sentiment: 'positive' as const,
    summary: 'LTV 완화 적용 지역이 수도권 외곽과 지방 광역시까지 확대됐다. 실수요자들의 대출 여력이 소폭 개선될 전망이다.',
    keywords: ['LTV', '규제완화', '부동산'],
  },
]

const SEASONAL = [
  { month: '7월', topics: ['이사철 대출', '하반기 금리 전망', '전세 vs 매매 비교'], current: true },
  { month: '8월', topics: ['여름 재테크', '하반기 부동산 전망', '학군 지역 대출'], current: false },
  { month: '9월', topics: ['가을 이사철', '연말 자금 계획', '전세자금 대출'], current: false },
  { month: '10월', topics: ['연말 세금 정산', '내집마련 전략', '대출 갈아타기'], current: false },
]

const SENTIMENT_META = {
  positive: { label: '긍정', cls: 'bg-emerald-50 text-emerald-700 border border-emerald-200' },
  neutral:  { label: '중립', cls: 'bg-slate-50 text-slate-600 border border-slate-200' },
  negative: { label: '부정', cls: 'bg-rose-50 text-rose-700 border border-rose-200' },
}

export function MarketIntelligencePage() {
  const [category, setCategory] = useState('전체')
  const navigate = useNavigate()

  const filtered = category === '전체'
    ? TRENDING_KEYWORDS
    : TRENDING_KEYWORDS.filter(k => k.category === category)

  const prefillAndNavigate = (topic: string, keywords: string[]) => {
    localStorage.setItem('wizard_prefill', JSON.stringify({ topic, keywords }))
    navigate('/content/new')
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Badge variant="warning" className="shrink-0">실시간 트렌드</Badge>
        <p className="text-xs text-muted-foreground">
          데이터 출처: 네이버 검색광고, 구글 트렌드 (연동 후 실시간 반영)
        </p>
      </div>

      <div className="grid lg:grid-cols-3 gap-6">
        {/* ── 왼쪽: 키워드 트렌드 + 뉴스 ── */}
        <div className="lg:col-span-2 space-y-5">

          {/* 키워드 트렌드 */}
          <Card>
            <CardHeader className="pb-3">
              <div className="flex items-center justify-between flex-wrap gap-2">
                <div className="flex items-center gap-2">
                  <TrendingUp className="h-4 w-4 text-indigo-500" />
                  <CardTitle className="text-base">키워드 트렌드</CardTitle>
                </div>
                <div className="flex flex-wrap gap-1">
                  {CATEGORIES.map(c => (
                    <button
                      key={c}
                      onClick={() => setCategory(c)}
                      className={cn(
                        'px-2.5 py-1 text-xs rounded-full font-medium transition-colors',
                        category === c
                          ? 'bg-indigo-500 text-white'
                          : 'bg-muted text-muted-foreground hover:bg-muted/70'
                      )}
                    >
                      {c}
                    </button>
                  ))}
                </div>
              </div>
              <CardDescription className="text-xs">최근 30일 기준 · 마우스를 올리면 콘텐츠 바로 생성</CardDescription>
            </CardHeader>
            <CardContent className="space-y-1">
              {filtered.map((k, i) => (
                <div
                  key={k.kw}
                  className="flex items-center gap-3 px-2 py-2 rounded-lg hover:bg-muted/40 group transition-colors cursor-default"
                >
                  <span className="text-xs text-muted-foreground w-4 text-right shrink-0">{i + 1}</span>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-1.5 mb-1">
                      <span className="text-sm font-medium">{k.kw}</span>
                      {k.hot && (
                        <Badge variant="destructive" className="text-[10px] px-1.5 py-0 h-4">HOT</Badge>
                      )}
                      <Badge variant="outline" className="text-[10px] px-1.5 py-0 h-4">{k.category}</Badge>
                    </div>
                    <div className="flex items-center gap-3">
                      <div className="h-1.5 flex-1 bg-muted rounded-full overflow-hidden">
                        <div
                          className="h-full bg-indigo-500 rounded-full transition-all"
                          style={{ width: `${k.score}%` }}
                        />
                      </div>
                      <span className="text-[10px] text-muted-foreground shrink-0 w-12">
                        월 {(k.volume / 1000).toFixed(0)}K
                      </span>
                      <span className={cn(
                        'text-[10px] font-semibold shrink-0 w-10 text-right',
                        k.change > 0 ? 'text-emerald-600' : 'text-rose-500'
                      )}>
                        {k.change > 0 ? '+' : ''}{k.change}%
                      </span>
                    </div>
                  </div>
                  <Button
                    variant="outline"
                    size="sm"
                    className="h-7 text-xs gap-1 opacity-0 group-hover:opacity-100 transition-opacity shrink-0"
                    onClick={() => prefillAndNavigate(k.kw, [k.kw])}
                  >
                    <Wand2 className="h-3 w-3" /> 생성
                  </Button>
                </div>
              ))}
            </CardContent>
          </Card>

          {/* 뉴스 분석 */}
          <Card>
            <CardHeader className="pb-3">
              <div className="flex items-center gap-2">
                <Newspaper className="h-4 w-4 text-violet-500" />
                <CardTitle className="text-base">뉴스 분석</CardTitle>
                <Badge variant="secondary" className="text-[10px]">AI 감성 분석</Badge>
              </div>
              <CardDescription className="text-xs">주요 키워드에 관한 최신 기사 · 키워드 클릭 시 콘텐츠 생성</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              {NEWS_ITEMS.map(news => {
                const s = SENTIMENT_META[news.sentiment]
                return (
                  <div
                    key={news.id}
                    className="p-3.5 rounded-xl border space-y-2 hover:border-violet-200 transition-colors"
                  >
                    <div className="flex items-start gap-2">
                      <p className="text-sm font-medium leading-snug flex-1">{news.title}</p>
                      <span className={cn('text-[10px] px-1.5 py-0.5 rounded-full shrink-0 font-medium', s.cls)}>
                        {s.label}
                      </span>
                    </div>
                    <p className="text-xs text-muted-foreground leading-relaxed">{news.summary}</p>
                    <div className="flex items-center justify-between gap-2">
                      <div className="flex flex-wrap gap-1">
                        {news.keywords.map(kw => (
                          <button
                            key={kw}
                            onClick={() => prefillAndNavigate(kw, [kw])}
                            className="text-[10px] bg-indigo-50 text-indigo-600 border border-indigo-100 px-2 py-0.5 rounded-full hover:bg-indigo-100 transition-colors font-medium"
                          >
                            {kw}
                          </button>
                        ))}
                      </div>
                      <span className="text-[10px] text-muted-foreground shrink-0">
                        {news.source} · {news.date}
                      </span>
                    </div>
                  </div>
                )
              })}
            </CardContent>
          </Card>
        </div>

        {/* ── 오른쪽: AI 추천 + 계절성 ── */}
        <div className="space-y-5">

          {/* AI 추천 주제 */}
          <Card>
            <CardHeader className="pb-3">
              <div className="flex items-center gap-2">
                <Sparkles className="h-4 w-4 text-amber-500" />
                <CardTitle className="text-base">AI 추천 주제</CardTitle>
              </div>
              <CardDescription className="text-xs">트렌드 + 계절성 분석 기반 실시간 추천</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              {AI_RECOMMENDATIONS.map((rec, i) => (
                <div
                  key={i}
                  className="p-3 rounded-xl border space-y-2 hover:border-amber-300 transition-colors"
                >
                  <div className="flex items-start justify-between gap-2">
                    <p className="text-sm font-semibold leading-snug flex-1">{rec.title}</p>
                    <span className="text-sm font-bold text-amber-600 shrink-0">{rec.score}</span>
                  </div>
                  <p className="text-xs text-muted-foreground leading-relaxed">{rec.reason}</p>
                  <div className="flex flex-wrap gap-1 mb-1">
                    {rec.keywords.map(k => (
                      <span key={k} className="text-[10px] bg-muted px-1.5 py-0.5 rounded">{k}</span>
                    ))}
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-[10px] text-muted-foreground">{rec.contentType} 추천</span>
                    <Button
                      size="sm"
                      className="h-6 text-xs gap-1 px-2"
                      onClick={() => prefillAndNavigate(rec.title, rec.keywords)}
                    >
                      <Wand2 className="h-3 w-3" /> 바로 생성
                    </Button>
                  </div>
                </div>
              ))}
            </CardContent>
          </Card>

          {/* 계절성 인사이트 */}
          <Card>
            <CardHeader className="pb-3">
              <div className="flex items-center gap-2">
                <Calendar className="h-4 w-4 text-teal-500" />
                <CardTitle className="text-base">계절성 인사이트</CardTitle>
              </div>
              <CardDescription className="text-xs">월별 핫토픽 · 클릭하면 콘텐츠 생성으로 이동</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              {SEASONAL.map(s => (
                <div
                  key={s.month}
                  className={cn(
                    'p-3 rounded-xl border',
                    s.current ? 'border-amber-300 bg-amber-50/60' : 'bg-muted/20'
                  )}
                >
                  <div className="flex items-center gap-2 mb-2">
                    <span className="text-sm font-semibold">{s.month}</span>
                    {s.current && <Badge variant="warning" className="text-[10px] px-1.5">이번 달</Badge>}
                  </div>
                  <div className="flex flex-wrap gap-1">
                    {s.topics.map(t => (
                      <button
                        key={t}
                        onClick={() => prefillAndNavigate(t, [t])}
                        className="text-xs bg-white border px-2 py-0.5 rounded-full hover:border-indigo-400 hover:text-indigo-600 transition-colors"
                      >
                        {t}
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}
