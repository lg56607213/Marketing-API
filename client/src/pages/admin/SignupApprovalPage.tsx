import { useEffect, useMemo, useState } from 'react'
import { UserCheck, RefreshCw, Check, X, RotateCcw } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { superAdminApi, apiMessage } from '@/services/api'
import type { SignupSummary, UserStatus } from '@/types'
import { toDisplayId } from '@/types'
import { formatDate } from '@/lib/utils'
import { cn } from '@/lib/utils'

const TABS: { status: UserStatus; label: string; empty: string }[] = [
  { status: 'PENDING', label: '승인 대기', empty: '승인을 기다리는 가입 신청이 없습니다.' },
  { status: 'ACTIVE', label: '이용 중', empty: '이용 중인 계정이 없습니다.' },
  { status: 'REJECTED', label: '거절 · 이용 중지', empty: '거절하거나 이용 중지한 계정이 없습니다.' },
]

const STATUS_BADGE: Record<UserStatus, { label: string; className: string }> = {
  PENDING: { label: '승인 대기', className: 'bg-amber-100 text-amber-800 hover:bg-amber-100' },
  ACTIVE: { label: '이용 중', className: 'bg-emerald-100 text-emerald-800 hover:bg-emerald-100' },
  REJECTED: { label: '거절됨', className: 'bg-slate-200 text-slate-700 hover:bg-slate-200' },
}

/** 최고 관리자 전용: 모든 업체의 가입 신청을 승인·거절한다. */
export function SignupApprovalPage() {
  const [signups, setSignups] = useState<SignupSummary[]>([])
  const [tab, setTab] = useState<UserStatus>('PENDING')
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [error, setError] = useState('')

  const load = () => {
    setLoading(true)
    setError('')
    superAdminApi.listSignups()
      .then(setSignups)
      .catch((err) => setError(apiMessage(err, '가입 목록을 불러오지 못했습니다.')))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const counts = useMemo(() => {
    const c: Record<UserStatus, number> = { PENDING: 0, ACTIVE: 0, REJECTED: 0 }
    signups.forEach((s) => { c[s.status] += 1 })
    return c
  }, [signups])

  const rows = signups.filter((s) => s.status === tab)
  const current = TABS.find((t) => t.status === tab)!

  const act = async (signup: SignupSummary, action: 'approve' | 'reject') => {
    const name = toDisplayId(signup.email)
    if (action === 'reject') {
      const question = signup.status === 'ACTIVE'
        ? `${name} 계정의 이용을 중지할까요? 바로 로그아웃되고 다시 로그인할 수 없습니다.`
        : `${name} 의 가입 신청을 거절할까요?`
      if (!window.confirm(question)) return
    }
    setBusyId(signup.id)
    setError('')
    try {
      const updated = action === 'approve'
        ? await superAdminApi.approve(signup.id)
        : await superAdminApi.reject(signup.id)
      setSignups((list) => list.map((s) => (s.id === updated.id ? updated : s)))
    } catch (err) {
      setError(apiMessage(err, '처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'))
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">
          회원가입한 업체는 여기서 승인해야 로그인할 수 있습니다.
        </p>
        <Button variant="outline" size="sm" onClick={load} className="gap-2" disabled={loading}>
          <RefreshCw className={cn('h-4 w-4', loading && 'animate-spin')} />
          새로고침
        </Button>
      </div>

      <div className="flex gap-1 border-b" role="tablist">
        {TABS.map((t) => (
          <button
            key={t.status}
            type="button"
            role="tab"
            aria-selected={tab === t.status}
            onClick={() => setTab(t.status)}
            className={cn(
              '-mb-px flex items-center gap-2 border-b-2 px-4 py-2 text-sm font-medium transition-colors',
              tab === t.status
                ? 'border-indigo-600 text-indigo-600'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            )}
          >
            {t.label}
            <span className={cn(
              'rounded-full px-1.5 text-xs tabular-nums',
              t.status === 'PENDING' && counts.PENDING > 0 ? 'bg-amber-500 text-white' : 'bg-muted text-muted-foreground'
            )}>
              {counts[t.status]}
            </span>
          </button>
        ))}
      </div>

      {error && <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}

      <Card>
        <CardContent className="p-0">
          {loading ? (
            <div className="divide-y">
              {[...Array(3)].map((_, i) => (
                <div key={i} className="flex items-center gap-4 px-6 py-4">
                  <div className="h-4 w-32 rounded bg-muted animate-pulse" />
                  <div className="h-4 w-40 rounded bg-muted animate-pulse" />
                  <div className="ml-auto h-8 w-28 rounded bg-muted animate-pulse" />
                </div>
              ))}
            </div>
          ) : rows.length === 0 ? (
            <div className="py-16 text-center text-muted-foreground">
              <UserCheck className="mx-auto mb-3 h-10 w-10 opacity-30" />
              <p className="text-sm">{current.empty}</p>
            </div>
          ) : (
            <div className="divide-y">
              <div className="hidden grid-cols-12 gap-3 bg-muted/40 px-6 py-2.5 text-xs font-semibold text-muted-foreground md:grid">
                <span className="col-span-3">아이디</span>
                <span className="col-span-3">업체명</span>
                <span className="col-span-2">가입일</span>
                <span className="col-span-1">상태</span>
                <span className="col-span-3 text-right">처리</span>
              </div>
              {rows.map((s) => {
                const busy = busyId === s.id
                const badge = STATUS_BADGE[s.status]
                return (
                  <div key={s.id} className="grid grid-cols-1 gap-2 px-6 py-3.5 md:grid-cols-12 md:items-center md:gap-3">
                    <div className="md:col-span-3">
                      <p className="text-sm font-medium">{toDisplayId(s.email)}</p>
                      <p className="text-xs text-muted-foreground">{s.role === 'ADMIN' ? '업체 관리자' : '업체 사용자'}</p>
                    </div>
                    <div className="text-sm md:col-span-3">{s.companyName ?? '-'}</div>
                    <div className="text-xs text-muted-foreground md:col-span-2">{formatDate(s.createdAt)}</div>
                    <div className="md:col-span-1">
                      <Badge className={badge.className}>{badge.label}</Badge>
                    </div>
                    <div className="flex gap-2 md:col-span-3 md:justify-end">
                      {s.status !== 'ACTIVE' && (
                        <Button size="sm" className="gap-1.5" disabled={busy} onClick={() => act(s, 'approve')}>
                          {s.status === 'REJECTED' ? <RotateCcw className="h-3.5 w-3.5" /> : <Check className="h-3.5 w-3.5" />}
                          {s.status === 'REJECTED' ? '다시 허용' : '승인'}
                        </Button>
                      )}
                      {s.status !== 'REJECTED' && (
                        <Button size="sm" variant="outline" className="gap-1.5" disabled={busy} onClick={() => act(s, 'reject')}>
                          <X className="h-3.5 w-3.5" />
                          {s.status === 'ACTIVE' ? '이용 중지' : '거절'}
                        </Button>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
