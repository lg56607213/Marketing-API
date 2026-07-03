import { useState, useEffect } from 'react'
import { Plus, Trash2, Globe, Sparkles, RefreshCw, TrendingUp, TrendingDown, Minus, Wand2 } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { cn } from '@/lib/utils'

interface Competitor {
  id: string
  url: string
  brand: string
  addedAt: string
}

interface CompetitorInsight {
  domainAuthority: number
  estimatedTraffic: number
  keywordCount: number
  contentFreq: string
  topKeywords: string[]
  strengths: string[]
  weaknesses: string[]
  gapTopics: string[]
}

const MOCK_INSIGHTS: CompetitorInsight[] = [
  {
    domainAuthority: 72,
    estimatedTraffic: 125000,
    keywordCount: 3420,
    contentFreq: '주 3~4회',
    topKeywords: ['담보대출 금리', '아파트 담보대출', 'LTV 계산기', '대출 한도'],
    strengths: ['높은 도메인 권위도', '풍부한 롱테일 키워드', '빠른 콘텐츠 업데이트'],
    weaknesses: ['모바일 UX 취약', '영상 콘텐츠 부재', 'SNS 채널 미활용'],
    gapTopics: ['DSR 계산 방법', '생애최초 대출 혜택', '규제지역 투자 전략', '대출 갈아타기 타이밍'],
  },
  {
    domainAuthority: 65,
    estimatedTraffic: 89000,
    keywordCount: 2180,
    contentFreq: '주 1~2회',
    topKeywords: ['전세 대출', '주택담보대출 비교', '금리 인하 전망'],
    strengths: ['브랜드 인지도 높음', 'SEO 최적화 우수', '인포그래픽 활용'],
    weaknesses: ['콘텐츠 업데이트 느림', '키워드 다양성 부족', '영어 콘텐츠 없음'],
    gapTopics: ['규제지역 완화 대응', '40대 담보대출 전략', '재융자 타이밍'],
  },
  {
    domainAuthority: 58,
    estimatedTraffic: 61000,
    keywordCount: 1540,
    contentFreq: '주 2~3회',
    topKeywords: ['담보대출 비교', '은행 대출 금리', '모기지 계산기'],
    strengths: ['계산기 도구 제공', '비교 콘텐츠 강점', '네이버 노출 우수'],
    weaknesses: ['전문성 부족', '콘텐츠 깊이 얕음', '유저 신뢰도 낮음'],
    gapTopics: ['임대사업자 대출', '법인 담보대출', '해외 자산 담보'],
  },
]

const STORAGE_KEY = 'intel_competitors'

function loadCompetitors(): Competitor[] {
  try { return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]') } catch { return [] }
}
function saveCompetitors(data: Competitor[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
}

export function CompetitorAnalysisPage() {
  const [competitors, setCompetitors] = useState<Competitor[]>([])
  const [open, setOpen] = useState(false)
  const [selected, setSelected] = useState<number>(0)
  const [form, setForm] = useState({ url: '', brand: '' })
  const navigate = useNavigate()

  useEffect(() => { setCompetitors(loadCompetitors()) }, [])

  const handleAdd = () => {
    if (!form.url.trim() || !form.brand.trim()) return
    const newC: Competitor = {
      id: Date.now().toString(),
      url: form.url.trim(),
      brand: form.brand.trim(),
      addedAt: new Date().toISOString().slice(0, 10),
    }
    const updated = [...competitors, newC]
    saveCompetitors(updated)
    setCompetitors(updated)
    setForm({ url: '', brand: '' })
    setOpen(false)
  }

  const handleDelete = (id: string) => {
    const updated = competitors.filter(c => c.id !== id)
    saveCompetitors(updated)
    setCompetitors(updated)
  }

  const prefillAndNavigate = (topic: string, keywords: string[]) => {
    localStorage.setItem('wizard_prefill', JSON.stringify({ topic, keywords }))
    navigate('/content/new')
  }

  const insight = competitors.length > 0 ? MOCK_INSIGHTS[selected % MOCK_INSIGHTS.length] : null
  const activeComp = competitors[selected]

  return (
    <div className="space-y-6">
      {/* 헤더 */}
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          경쟁사 URL을 등록하면 AI가 공개 데이터 기반으로 분석합니다.
        </p>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => setCompetitors(loadCompetitors())} className="gap-2">
            <RefreshCw className="h-4 w-4" /> 새로고침
          </Button>
          <Button size="sm" onClick={() => setOpen(true)} className="gap-2">
            <Plus className="h-4 w-4" /> 경쟁사 추가
          </Button>
        </div>
      </div>

      {competitors.length === 0 ? (
        <Card className="border-dashed">
          <CardContent className="flex flex-col items-center justify-center py-20 text-center">
            <Globe className="h-12 w-12 text-muted-foreground/30 mb-3" />
            <p className="font-medium">등록된 경쟁사가 없습니다</p>
            <p className="text-sm text-muted-foreground mt-1">
              분석할 경쟁사 URL을 등록하면 AI 인사이트를 제공합니다.
            </p>
            <Button className="mt-4 gap-2" onClick={() => setOpen(true)}>
              <Plus className="h-4 w-4" /> 경쟁사 등록
            </Button>
          </CardContent>
        </Card>
      ) : (
        <div className="grid lg:grid-cols-4 gap-5">
          {/* 경쟁사 목록 */}
          <div className="space-y-2">
            <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider px-1">
              등록 경쟁사 ({competitors.length})
            </p>
            {competitors.map((c, i) => {
              const ins = MOCK_INSIGHTS[i % MOCK_INSIGHTS.length]
              return (
                <button
                  key={c.id}
                  onClick={() => setSelected(i)}
                  className={cn(
                    'w-full text-left p-3 rounded-xl border transition-all',
                    selected === i
                      ? 'border-indigo-500 bg-indigo-50'
                      : 'hover:border-indigo-200 hover:bg-muted/30'
                  )}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="text-sm font-semibold truncate">{c.brand}</p>
                      <p className="text-[10px] text-muted-foreground truncate">{c.url}</p>
                    </div>
                    <button
                      onClick={e => { e.stopPropagation(); handleDelete(c.id) }}
                      className="text-muted-foreground hover:text-destructive transition-colors shrink-0"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  </div>
                  <div className="flex gap-2 mt-2">
                    <span className="text-[10px] bg-violet-100 text-violet-700 px-1.5 py-0.5 rounded font-medium">
                      DA {ins.domainAuthority}
                    </span>
                    <span className="text-[10px] bg-sky-100 text-sky-700 px-1.5 py-0.5 rounded font-medium">
                      {(ins.estimatedTraffic / 1000).toFixed(0)}K 트래픽
                    </span>
                  </div>
                </button>
              )
            })}
          </div>

          {/* 상세 분석 */}
          {insight && activeComp && (
            <div className="lg:col-span-3 space-y-5">
              {/* 핵심 지표 */}
              <div className="grid grid-cols-4 gap-3">
                {[
                  { label: '도메인 권위도', value: insight.domainAuthority, unit: '/100', color: 'text-violet-600' },
                  { label: '예상 월 트래픽', value: (insight.estimatedTraffic / 1000).toFixed(0) + 'K', unit: '', color: 'text-sky-600' },
                  { label: '보유 키워드', value: insight.keywordCount.toLocaleString(), unit: '개', color: 'text-indigo-600' },
                  { label: '콘텐츠 빈도', value: insight.contentFreq, unit: '', color: 'text-emerald-600' },
                ].map(m => (
                  <Card key={m.label}>
                    <CardContent className="pt-4 pb-3">
                      <p className="text-[10px] text-muted-foreground">{m.label}</p>
                      <p className={cn('text-xl font-bold mt-0.5', m.color)}>
                        {m.value}<span className="text-xs font-normal text-muted-foreground">{m.unit}</span>
                      </p>
                    </CardContent>
                  </Card>
                ))}
              </div>

              <div className="grid sm:grid-cols-2 gap-5">
                {/* 상위 키워드 */}
                <Card>
                  <CardHeader className="pb-2">
                    <CardTitle className="text-sm">상위 노출 키워드</CardTitle>
                    <CardDescription className="text-xs">{activeComp.brand}의 주요 검색 키워드</CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-2">
                    {insight.topKeywords.map((kw, i) => (
                      <div key={kw} className="flex items-center gap-3">
                        <span className="text-xs text-muted-foreground w-4 text-right">{i + 1}</span>
                        <span className="text-sm flex-1">{kw}</span>
                        <button
                          onClick={() => prefillAndNavigate(kw, [kw])}
                          className="text-[10px] text-indigo-500 hover:text-indigo-700 font-medium"
                        >
                          생성 →
                        </button>
                      </div>
                    ))}
                  </CardContent>
                </Card>

                {/* 강점 / 약점 */}
                <Card>
                  <CardHeader className="pb-2">
                    <CardTitle className="text-sm">AI 분석: 강점 / 약점</CardTitle>
                    <CardDescription className="text-xs">공개 데이터 기반 자동 분석</CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-3">
                    <div>
                      <p className="text-xs font-semibold text-emerald-600 mb-1.5 flex items-center gap-1">
                        <TrendingUp className="h-3 w-3" /> 강점
                      </p>
                      <ul className="space-y-1">
                        {insight.strengths.map(s => (
                          <li key={s} className="text-xs text-muted-foreground flex items-center gap-1.5">
                            <span className="h-1 w-1 bg-emerald-400 rounded-full shrink-0" />{s}
                          </li>
                        ))}
                      </ul>
                    </div>
                    <div>
                      <p className="text-xs font-semibold text-rose-500 mb-1.5 flex items-center gap-1">
                        <TrendingDown className="h-3 w-3" /> 약점 (우리의 기회)
                      </p>
                      <ul className="space-y-1">
                        {insight.weaknesses.map(w => (
                          <li key={w} className="text-xs text-muted-foreground flex items-center gap-1.5">
                            <span className="h-1 w-1 bg-rose-400 rounded-full shrink-0" />{w}
                          </li>
                        ))}
                      </ul>
                    </div>
                  </CardContent>
                </Card>
              </div>

              {/* 콘텐츠 갭 분석 */}
              <Card>
                <CardHeader className="pb-3">
                  <div className="flex items-center gap-2">
                    <Sparkles className="h-4 w-4 text-amber-500" />
                    <CardTitle className="text-sm">콘텐츠 갭 분석</CardTitle>
                  </div>
                  <CardDescription className="text-xs">
                    {activeComp.brand}이(가) 다루는데 우리는 아직 다루지 않은 주제 — 바로 생성해 격차를 좁히세요
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <div className="grid sm:grid-cols-2 gap-3">
                    {insight.gapTopics.map(topic => (
                      <div
                        key={topic}
                        className="flex items-center justify-between gap-3 p-3 rounded-lg border border-amber-200 bg-amber-50/50 hover:border-amber-400 transition-colors"
                      >
                        <div className="flex items-center gap-2 min-w-0">
                          <Minus className="h-3 w-3 text-amber-500 shrink-0" />
                          <span className="text-sm font-medium truncate">{topic}</span>
                        </div>
                        <Button
                          size="sm"
                          variant="outline"
                          className="h-7 text-xs gap-1 shrink-0 border-amber-300 hover:bg-amber-100"
                          onClick={() => prefillAndNavigate(topic, [topic])}
                        >
                          <Wand2 className="h-3 w-3" /> 생성
                        </Button>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            </div>
          )}
        </div>
      )}

      {/* 경쟁사 추가 다이얼로그 */}
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>경쟁사 추가</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label>브랜드명 *</Label>
              <Input
                placeholder="예: A금융"
                value={form.brand}
                onChange={e => setForm({ ...form, brand: e.target.value })}
              />
            </div>
            <div className="space-y-1.5">
              <Label>웹사이트 URL *</Label>
              <Input
                placeholder="예: competitor.co.kr"
                value={form.url}
                onChange={e => setForm({ ...form, url: e.target.value })}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>취소</Button>
            <Button onClick={handleAdd} disabled={!form.url.trim() || !form.brand.trim()}>추가</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
