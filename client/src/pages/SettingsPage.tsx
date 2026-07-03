import { useAuthStore } from '@/store/authStore'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Shield, Zap, Database } from 'lucide-react'

export function SettingsPage() {
  const { displayId, email, role } = useAuthStore()
  const initials = (displayId ?? 'U').slice(0, 2).toUpperCase()

  return (
    <div className="max-w-2xl space-y-6">
      {/* 계정 정보 */}
      <Card>
        <CardHeader>
          <CardTitle>계정</CardTitle>
          <CardDescription>관리자가 관리하는 계정 정보입니다.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-center gap-4">
            <Avatar className="h-14 w-14">
              <AvatarFallback className="text-lg bg-indigo-500 text-white">{initials}</AvatarFallback>
            </Avatar>
            <div>
              <p className="font-semibold">{displayId}</p>
              <p className="text-sm text-muted-foreground">{email}</p>
              <Badge variant={role === 'ADMIN' ? 'default' : 'secondary'} className="mt-1.5">
                {role === 'ADMIN' ? '관리자' : '사용자'}
              </Badge>
            </div>
          </div>
          <Separator className="my-4" />
          <p className="text-xs text-muted-foreground">
            비밀번호나 계정 정보 변경은 관리자에게 문의하세요.
          </p>
        </CardContent>
      </Card>

      {/* 시스템 정보 */}
      <Card>
        <CardHeader>
          <CardTitle>시스템</CardTitle>
          <CardDescription>현재 구성 및 환경 정보입니다.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {[
            { icon: Zap, label: 'AI 제공업체', value: 'Stub AI (데모 모드)', desc: 'AI_PROVIDER=openai 설정 시 실제 AI 생성을 사용합니다' },
            { icon: Database, label: '데이터베이스', value: 'H2 인메모리', desc: '서버 재시작 시 데이터가 초기화됩니다. DB_URL 설정 시 영구 저장됩니다.' },
            { icon: Shield, label: '인증', value: 'JWT Bearer', desc: '토큰은 24시간 후 만료됩니다.' },
          ].map(({ icon: Icon, label, value, desc }) => (
            <div key={label} className="flex items-start gap-3">
              <div className="h-8 w-8 rounded-lg bg-muted flex items-center justify-center shrink-0">
                <Icon className="h-4 w-4 text-muted-foreground" />
              </div>
              <div>
                <p className="text-sm font-medium">{label}</p>
                <p className="text-sm text-foreground">{value}</p>
                <p className="text-xs text-muted-foreground mt-0.5">{desc}</p>
              </div>
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  )
}
