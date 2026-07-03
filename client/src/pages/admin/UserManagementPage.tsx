import { useEffect, useState } from 'react'
import { Users, RefreshCw } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { adminApi } from '@/services/api'
import type { UserSummary } from '@/types'
import { toDisplayId } from '@/types'
import { formatDate } from '@/lib/utils'

export function UserManagementPage() {
  const [users, setUsers] = useState<UserSummary[]>([])
  const [loading, setLoading] = useState(true)

  const load = () => {
    setLoading(true)
    adminApi.listUsers().then(setUsers).catch(() => {}).finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">총 {users.length}명의 사용자</p>
        <Button variant="outline" size="sm" onClick={load} className="gap-2">
          <RefreshCw className="h-4 w-4" />
          새로고침
        </Button>
      </div>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">전체 사용자</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {loading ? (
            <div className="divide-y">
              {[...Array(4)].map((_, i) => (
                <div key={i} className="flex items-center gap-4 px-6 py-4">
                  <div className="h-8 w-8 rounded-full bg-muted animate-pulse" />
                  <div className="h-4 w-48 bg-muted rounded animate-pulse" />
                  <div className="h-5 w-16 bg-muted rounded animate-pulse ml-auto" />
                </div>
              ))}
            </div>
          ) : users.length === 0 ? (
            <div className="text-center py-16 text-muted-foreground">
              <Users className="h-10 w-10 mx-auto mb-3 opacity-30" />
              <p className="text-sm">등록된 사용자가 없습니다.</p>
            </div>
          ) : (
            <div className="divide-y">
              <div className="grid grid-cols-12 gap-3 px-6 py-2.5 text-xs font-semibold text-muted-foreground uppercase tracking-wide bg-muted/40">
                <span className="col-span-1">#</span>
                <span className="col-span-4">로그인 ID</span>
                <span className="col-span-4">이메일 (백엔드)</span>
                <span className="col-span-2">역할</span>
                <span className="col-span-1">가입일</span>
              </div>
              {users.map((u, i) => (
                <div key={u.email} className="grid grid-cols-12 gap-3 px-6 py-3.5 items-center hover:bg-muted/20 transition-colors">
                  <span className="col-span-1 text-sm text-muted-foreground">{i + 1}</span>
                  <div className="col-span-4">
                    <div className="flex items-center gap-2">
                      <div className="h-7 w-7 rounded-full bg-indigo-100 flex items-center justify-center text-[10px] font-bold text-indigo-600 shrink-0">
                        {toDisplayId(u.email).slice(0, 2).toUpperCase()}
                      </div>
                      <span className="text-sm font-medium">{toDisplayId(u.email)}</span>
                    </div>
                  </div>
                  <div className="col-span-4 text-xs text-muted-foreground font-mono truncate">{u.email}</div>
                  <div className="col-span-2">
                    <Badge variant={u.role === 'ADMIN' ? 'default' : 'secondary'}>
                      {u.role === 'ADMIN' ? '관리자' : '사용자'}
                    </Badge>
                  </div>
                  <div className="col-span-1 text-xs text-muted-foreground">{formatDate(u.createdAt)}</div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <p className="text-xs text-muted-foreground">
        * 사용자 계정은 기업 관리에서 생성합니다. 사용자를 추가하려면 <a href="/admin/companies" className="text-indigo-500 hover:underline">기업 관리</a>로 이동하세요.
      </p>
    </div>
  )
}
