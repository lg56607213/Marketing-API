import { useEffect, useState } from 'react'
import { Progress } from '@/components/ui/progress'
import { Loader2, CheckCircle2 } from 'lucide-react'
import { contentApi } from '@/services/api'
import type { ContentResponse, ContentType } from '@/types'
import { CONTENT_TYPE_LABELS } from '@/types'

const STEPS_ANIMATION = [
  { label: '브랜드 보이스 분석 중...', duration: 800 },
  { label: '키워드와 뉴스 처리 중...', duration: 900 },
  { label: '콘텐츠 구조 설계 중...', duration: 700 },
  { label: 'AI로 콘텐츠 생성 중...', duration: 1200 },
  { label: '타겟 오디언스에 맞게 최적화 중...', duration: 600 },
  { label: '콘텐츠 마무리 중...', duration: 500 },
]

interface Props {
  brandId: number
  contentType: ContentType
  topic: string
  keywords: string[]
  style: string
  onDone: (content: ContentResponse) => void
  onError: (msg: string) => void
}

export function Step6Generate({ brandId, contentType, topic, keywords, style, onDone, onError }: Props) {
  const [progress, setProgress] = useState(0)
  const [stepLabel, setStepLabel] = useState(STEPS_ANIMATION[0].label)
  const [done, setDone] = useState(false)

  useEffect(() => {
    let cancelled = false
    let elapsed = 0
    const total = STEPS_ANIMATION.reduce((s, x) => s + x.duration, 0)

    const timers: ReturnType<typeof setTimeout>[] = []
    STEPS_ANIMATION.forEach((s, i) => {
      const t = setTimeout(() => {
        if (!cancelled) {
          setStepLabel(s.label)
          setProgress(Math.round(((elapsed + s.duration) / total) * 100))
        }
      }, elapsed)
      timers.push(t)
      elapsed += s.duration
    })

    const fullTopic = [topic, ...keywords].join(', ') + (style ? `. 스타일: ${style}` : '')
    contentApi
      .generate({ brandId, contentType, topic: fullTopic })
      .then((c) => {
        if (!cancelled) { setProgress(100); setDone(true); setTimeout(() => onDone(c), 400) }
      })
      .catch(() => {
        if (!cancelled) onError('콘텐츠 생성에 실패했습니다. 다시 시도해 주세요.')
      })

    return () => { cancelled = true; timers.forEach(clearTimeout) }
  }, [])

  return (
    <div className="flex flex-col items-center justify-center py-10 space-y-8">
      <div className="relative">
        {done ? (
          <CheckCircle2 className="h-16 w-16 text-emerald-500" />
        ) : (
          <Loader2 className="h-16 w-16 text-indigo-500 animate-spin" />
        )}
      </div>

      <div className="w-full max-w-sm space-y-3 text-center">
        <p className="text-sm font-medium text-foreground">
          {done ? '콘텐츠 생성 완료!' : stepLabel}
        </p>
        <Progress value={progress} className="h-2" />
        <p className="text-xs text-muted-foreground">{progress}%</p>
      </div>

      <div className="text-center space-y-1">
        <p className="text-sm text-muted-foreground">
          <span className="font-medium text-foreground">{CONTENT_TYPE_LABELS[contentType]}</span> 생성 중
        </p>
        <p className="text-xs text-muted-foreground">주제: {topic}</p>
      </div>
    </div>
  )
}
