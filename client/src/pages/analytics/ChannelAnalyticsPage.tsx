import { useNavigate } from 'react-router-dom'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'

const CHANNELS = [
  { name: '네이버 검색광고', status: '연동됨', detail: '광고비·클릭·전환을 실제로 수집합니다.', to: '/analytics' },
  { name: '구글 서치콘솔 (자연검색)', status: '연동 필요', detail: '구글 계정 인증 정보를 서버에 설정해야 합니다.', to: '/analytics/seo' },
  { name: '구글 애널리틱스 (GA4)', status: '미지원', detail: '아직 만들지 않았습니다. 홈페이지 방문자·유입 경로를 보려면 필요합니다.' },
  { name: '인스타그램 · 메타 광고', status: '미지원', detail: '아직 만들지 않았습니다. 메타 비즈니스 계정 연동이 필요합니다.' },
  { name: '유튜브 · 틱톡', status: '미지원', detail: '아직 만들지 않았습니다.' },
]

/** 채널별 연동 현황. 연동되지 않은 채널의 숫자는 지어내지 않고 상태만 보여 준다. */
export function ChannelAnalyticsPage() {
  const navigate = useNavigate()
  return (
    <div className="space-y-6">
      <p className="text-sm text-muted-foreground">
        채널별 실적은 그 채널의 계정을 연동해야 볼 수 있습니다. 연동되지 않은 채널은 숫자를 보여 주지 않습니다.
      </p>
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">채널 연동 현황</CardTitle>
          <CardDescription className="text-xs">지금 실제 데이터가 들어오는 것은 네이버 검색광고 하나입니다.</CardDescription>
        </CardHeader>
        <CardContent className="divide-y p-0">
          {CHANNELS.map((c) => (
            <div key={c.name} className="flex flex-wrap items-center justify-between gap-3 px-6 py-3">
              <div className="min-w-0">
                <p className="text-sm font-medium">{c.name}</p>
                <p className="text-xs text-muted-foreground">{c.detail}</p>
              </div>
              <div className="flex items-center gap-2">
                <Badge variant={c.status === '연동됨' ? 'default' : 'outline'} className="text-[10px]">{c.status}</Badge>
                {c.to && <Button size="sm" variant="outline" onClick={() => navigate(c.to!)}>보기</Button>}
              </div>
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  )
}
