import { FileText, Image, Share2, Youtube, Mail, Globe } from 'lucide-react'
import type { ContentType } from '@/types'

const types: { type: ContentType; label: string; desc: string; icon: React.ComponentType<{ className?: string }> }[] = [
  { type: 'BLOG', label: '블로그 포스트', desc: 'SEO 최적화된 장문 콘텐츠, 800~2000자', icon: FileText },
  { type: 'CARDNEWS', label: '카드뉴스', desc: '카드별 간결한 텍스트로 구성된 비주얼 스토리', icon: Image },
  { type: 'SNS', label: 'SNS 게시물', desc: '인스타그램·페이스북·X 등 소셜 미디어용 짧은 게시물', icon: Share2 },
  { type: 'YOUTUBE', label: '유튜브 스크립트', desc: '인트로·본문·CTA를 포함한 전체 영상 대본', icon: Youtube },
  { type: 'EMAIL', label: '이메일 캠페인', desc: '제목과 본문이 포함된 개인화 이메일', icon: Mail },
  { type: 'LANDING_PAGE', label: '랜딩 페이지', desc: 'CTA 중심의 전환율 최적화 페이지 카피', icon: Globe },
]

interface Props {
  value: ContentType | null
  onChange: (v: ContentType) => void
}

export function Step4ContentType({ value, onChange }: Props) {
  return (
    <div className="grid sm:grid-cols-2 gap-3">
      {types.map(({ type, label, desc, icon: Icon }) => {
        const selected = value === type
        return (
          <button
            key={type}
            onClick={() => onChange(type)}
            className={`flex items-start gap-4 p-4 rounded-xl border text-left transition-all ${
              selected
                ? 'border-indigo-500 bg-indigo-50 shadow-sm'
                : 'border-border hover:border-indigo-300 hover:bg-accent/50'
            }`}
          >
            <div className={`p-2.5 rounded-lg shrink-0 ${selected ? 'bg-indigo-500 text-white' : 'bg-muted text-muted-foreground'}`}>
              <Icon className="h-5 w-5" />
            </div>
            <div className="min-w-0">
              <p className={`font-medium text-sm ${selected ? 'text-indigo-700' : ''}`}>{label}</p>
              <p className="text-xs text-muted-foreground mt-0.5 leading-relaxed">{desc}</p>
            </div>
          </button>
        )
      })}
    </div>
  )
}
