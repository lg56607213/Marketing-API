import { useState } from 'react'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'

const PRESETS = [
  { label: '전문적', value: '전문적이고 권위 있는 톤으로 비즈니스 독자에게 적합합니다.' },
  { label: '친근한', value: '따뜻하고 대화체적이며 친근한 느낌. 일반 대중이 이해하기 쉬운 표현을 사용합니다.' },
  { label: '전문가적', value: '기술적이고 데이터 중심적인 톤으로 업계 전문가를 대상으로 합니다.' },
  { label: '광고성', value: '즉각적인 행동을 유도하도록 설계된 설득력 있고 이점 중심의 스타일입니다.' },
  { label: '스토리텔링', value: '이야기 방식으로 감성적 호소를 통해 독자와 연결되는 서사 중심 스타일입니다.' },
]

interface Props {
  value: string
  onChange: (v: string) => void
}

export function Step5WritingStyle({ value, onChange }: Props) {
  const [selected, setSelected] = useState<string | null>(null)

  const pick = (preset: typeof PRESETS[0]) => {
    setSelected(preset.label)
    onChange(preset.value)
  }

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <p className="text-sm text-muted-foreground">프리셋 스타일을 선택하거나 아래에 직접 입력하세요.</p>
        <div className="flex flex-wrap gap-2">
          {PRESETS.map((p) => (
            <button
              key={p.label}
              onClick={() => pick(p)}
              className={`px-4 py-2 rounded-full text-sm font-medium border transition-all ${
                selected === p.label
                  ? 'bg-indigo-500 text-white border-indigo-500'
                  : 'border-border hover:border-indigo-400 bg-background'
              }`}
            >
              {p.label}
            </button>
          ))}
        </div>
      </div>

      <div className="space-y-1.5">
        <Label>작성 스타일 설명</Label>
        <Textarea
          placeholder="원하는 작성 스타일을 설명하세요... 예: '쉬운 언어를 사용하고, 전문 용어를 피하며, 첫 주택 구매자에게 도움이 되는 내용에 집중하세요.'"
          value={value}
          onChange={(e) => { onChange(e.target.value); setSelected(null) }}
          rows={4}
          className="resize-none"
        />
        <p className="text-xs text-muted-foreground">{value.length}자</p>
      </div>
    </div>
  )
}
