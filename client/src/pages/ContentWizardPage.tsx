import { useState, useEffect } from 'react'
import { ChevronRight, ChevronLeft, Check } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Step1Topic } from '@/features/content/wizard/Step1Topic'
import { Step2Keywords } from '@/features/content/wizard/Step2Keywords'
import { Step3NewsAnalysis } from '@/features/content/wizard/Step3NewsAnalysis'
import { Step4ContentType } from '@/features/content/wizard/Step4ContentType'
import { Step5WritingStyle } from '@/features/content/wizard/Step5WritingStyle'
import { Step6Generate } from '@/features/content/wizard/Step6Generate'
import { Step7Review } from '@/features/content/wizard/Step7Review'
import type { ContentResponse, ContentType } from '@/types'
import { cn } from '@/lib/utils'

const STEPS = [
  { id: 1, title: '주제 입력', desc: '어떤 내용을 작성할지 입력하세요' },
  { id: 2, title: '키워드 선택', desc: 'AI 추천 키워드를 선택하세요' },
  { id: 3, title: '뉴스 분석', desc: '관련 최신 뉴스를 참고하세요' },
  { id: 4, title: '콘텐츠 유형', desc: '형식과 채널을 선택하세요' },
  { id: 5, title: '작성 스타일', desc: '톤과 보이스를 설정하세요' },
  { id: 6, title: 'AI 생성 중', desc: 'AI가 콘텐츠를 작성합니다' },
  { id: 7, title: '검토 및 내보내기', desc: '생성된 콘텐츠를 확인하세요' },
]

export function ContentWizardPage() {
  const [step, setStep] = useState(1)
  const [topic, setTopic] = useState('')
  const [brandId, setBrandId] = useState<number | null>(null)
  const [keywords, setKeywords] = useState<string[]>([])

  useEffect(() => {
    const raw = localStorage.getItem('wizard_prefill')
    if (raw) {
      try {
        const p = JSON.parse(raw)
        localStorage.removeItem('wizard_prefill')
        if (p.topic) setTopic(p.topic)
        if (p.keywords?.length) setKeywords(p.keywords)
      } catch {}
    }
  }, [])
  const [includedNews, setIncludedNews] = useState<string[]>([])
  const [contentType, setContentType] = useState<ContentType | null>(null)
  const [style, setStyle] = useState('')
  const [generatedContent, setGeneratedContent] = useState<ContentResponse | null>(null)
  const [genError, setGenError] = useState('')
  const [generating, setGenerating] = useState(false)

  const canNext = () => {
    if (step === 1) return topic.trim().length > 0 && brandId !== null
    if (step === 2) return keywords.length > 0
    if (step === 4) return contentType !== null
    if (step === 6) return false
    return true
  }

  const next = () => { if (step < 7) { if (step === 5) setGenerating(true); setStep(step + 1) } }
  const prev = () => { if (step > 1 && step !== 6) setStep(step - 1) }

  const handleGenDone = (c: ContentResponse) => {
    setGeneratedContent(c)
    setGenerating(false)
    setStep(7)
  }

  const handleGenError = (msg: string) => {
    setGenError(msg)
    setGenerating(false)
    setStep(5)
  }

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      {/* 단계 표시 */}
      <div className="flex items-center gap-0">
        {STEPS.map((s, i) => (
          <div key={s.id} className="flex items-center flex-1 last:flex-none">
            <div className="flex flex-col items-center gap-1">
              <div
                className={cn(
                  'flex h-8 w-8 items-center justify-center rounded-full text-xs font-bold transition-colors',
                  step > s.id
                    ? 'bg-emerald-500 text-white'
                    : step === s.id
                    ? 'bg-indigo-500 text-white'
                    : 'bg-muted text-muted-foreground'
                )}
              >
                {step > s.id ? <Check className="h-4 w-4" /> : s.id}
              </div>
              <span className={cn('text-[10px] font-medium hidden sm:block', step === s.id ? 'text-indigo-600' : 'text-muted-foreground')}>
                {s.title}
              </span>
            </div>
            {i < STEPS.length - 1 && (
              <div className={cn('h-0.5 flex-1 mx-1 mb-3', step > s.id ? 'bg-emerald-400' : 'bg-border')} />
            )}
          </div>
        ))}
      </div>

      {/* 단계 콘텐츠 */}
      <Card>
        <CardHeader>
          <CardTitle>{STEPS[step - 1].title}</CardTitle>
          <CardDescription>{STEPS[step - 1].desc}</CardDescription>
        </CardHeader>
        <CardContent>
          {genError && (
            <div className="mb-4 rounded-md border border-destructive/50 bg-destructive/10 px-4 py-3 text-sm text-destructive">
              {genError}
            </div>
          )}

          {step === 1 && (
            <Step1Topic topic={topic} brandId={brandId} onTopicChange={setTopic} onBrandChange={setBrandId} onKeywordsChange={setKeywords} />
          )}
          {step === 2 && (
            <Step2Keywords topic={topic} keywords={keywords} onChange={setKeywords} />
          )}
          {step === 3 && (
            <Step3NewsAnalysis topic={topic} keywords={keywords} included={includedNews} onChange={setIncludedNews} />
          )}
          {step === 4 && (
            <Step4ContentType value={contentType} onChange={setContentType} />
          )}
          {step === 5 && (
            <Step5WritingStyle value={style} onChange={setStyle} />
          )}
          {step === 6 && brandId && contentType && (
            <Step6Generate
              brandId={brandId}
              contentType={contentType}
              topic={topic}
              keywords={keywords}
              style={style}
              onDone={handleGenDone}
              onError={handleGenError}
            />
          )}
          {step === 7 && generatedContent && (
            <Step7Review content={generatedContent} />
          )}
        </CardContent>
      </Card>

      {/* 이동 버튼 */}
      {step !== 6 && step !== 7 && (
        <div className="flex justify-between">
          <Button variant="outline" onClick={prev} disabled={step === 1} className="gap-2">
            <ChevronLeft className="h-4 w-4" />
            이전
          </Button>
          <Button onClick={next} disabled={!canNext()} className="gap-2">
            {step === 5 ? 'AI 생성 시작' : '다음'}
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      )}
    </div>
  )
}
