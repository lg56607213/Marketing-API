import { useCallback, useEffect, useState } from 'react'
import { AlertCircle, Check, Link2, Loader2, Unlink } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { naverAccountApi } from '@/services/api'
import type { NaverAccountStatus } from '@/types'

/** 로그인한 회원이 자기 네이버 검색광고 계정을 연결하는 카드. 키는 서버에 암호화돼 저장된다. */
export function NaverAccountCard() {
  const [status, setStatus] = useState<NaverAccountStatus | null>(null)
  const [editing, setEditing] = useState(false)
  const [customerId, setCustomerId] = useState('')
  const [apiKey, setApiKey] = useState('')
  const [secretKey, setSecretKey] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      setStatus(await naverAccountApi.status())
    } catch {
      setError('연결 상태를 불러오지 못했습니다.')
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const resetForm = () => {
    setCustomerId('')
    setApiKey('')
    setSecretKey('')
    setEditing(false)
  }

  const connect = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setNotice(null)
    if (!/^\d+$/.test(customerId.trim())) {
      setError('고객 ID 는 숫자만 입력하세요.')
      return
    }
    if (!apiKey.trim() || !secretKey.trim()) {
      setError('액세스 라이선스와 비밀키를 모두 입력하세요.')
      return
    }
    setSaving(true)
    try {
      const result = await naverAccountApi.connect(customerId.trim(), apiKey.trim(), secretKey.trim())
      setStatus(result)
      setNotice(result.message)
      resetForm()
    } catch (err) {
      const message = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      setError(message ?? '연결하지 못했습니다. 입력값을 다시 확인하세요.')
    } finally {
      setSaving(false)
    }
  }

  const disconnect = async () => {
    if (!window.confirm('네이버 계정 연결을 끊을까요? 저장된 키만 지워지고, 이미 받은 광고 데이터는 남습니다.')) {
      return
    }
    setError(null)
    setNotice(null)
    setSaving(true)
    try {
      setStatus(await naverAccountApi.disconnect())
      setNotice('연결을 끊었습니다.')
    } catch {
      setError('연결을 끊지 못했습니다.')
    } finally {
      setSaving(false)
    }
  }

  const connected = status?.connected ?? false
  const showForm = editing || (status !== null && !connected)

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between gap-2">
          <CardTitle>네이버 검색광고 연결</CardTitle>
          {status && (
            <Badge variant={connected ? 'default' : 'secondary'}>{connected ? '연결됨' : '연결 안 됨'}</Badge>
          )}
        </div>
        <CardDescription>
          내 광고 계정을 연결하면 이 아이디로 로그인했을 때 내 계정의 데이터만 보입니다.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {status?.provider !== 'naver' && status && (
          <div className="rounded-md border border-amber-500/30 bg-amber-50 px-4 py-3 text-sm text-amber-800">
            서버가 데모(Stub) 모드라 지금은 가짜 데이터가 보입니다. 키를 미리 저장해 둘 수는 있습니다.
          </div>
        )}
        {error && (
          <div className="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
            <AlertCircle className="h-4 w-4 shrink-0" /> {error}
          </div>
        )}
        {notice && (
          <div className="flex items-center gap-2 rounded-md border border-emerald-500/30 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
            <Check className="h-4 w-4 shrink-0" /> {notice}
          </div>
        )}

        {connected && status && !editing && (
          <div className="space-y-3">
            <dl className="grid grid-cols-[7rem_1fr] gap-y-2 text-sm">
              <dt className="text-muted-foreground">고객 ID</dt>
              <dd className="font-medium">{status.customerId}</dd>
              <dt className="text-muted-foreground">액세스 라이선스</dt>
              <dd className="font-medium">{status.apiKeyMasked}</dd>
              <dt className="text-muted-foreground">연결한 날</dt>
              <dd>{status.connectedAt?.slice(0, 10) ?? '-'}</dd>
            </dl>
            <div className="flex gap-2">
              <Button size="sm" variant="outline" disabled={saving} onClick={() => setEditing(true)} className="gap-2">
                <Link2 className="h-4 w-4" /> 키 교체
              </Button>
              <Button size="sm" variant="outline" disabled={saving} onClick={() => void disconnect()} className="gap-2">
                <Unlink className="h-4 w-4" /> 연결 끊기
              </Button>
            </div>
          </div>
        )}

        {showForm && (
          <form onSubmit={connect} className="space-y-3" autoComplete="off">
            <p className="text-xs text-muted-foreground">
              네이버 검색광고 관리자 &gt; 도구 &gt; API 사용 관리에서 발급한 값을 입력하세요. 비밀키는 저장 후 다시 보여주지 않습니다.
            </p>
            <div className="space-y-1.5">
              <Label htmlFor="naver-customer-id">고객 ID</Label>
              <Input
                id="naver-customer-id"
                inputMode="numeric"
                placeholder="숫자만"
                value={customerId}
                onChange={(e) => setCustomerId(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="naver-api-key">액세스 라이선스</Label>
              <Input
                id="naver-api-key"
                autoComplete="off"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="naver-secret-key">비밀키</Label>
              <Input
                id="naver-secret-key"
                type="password"
                autoComplete="new-password"
                value={secretKey}
                onChange={(e) => setSecretKey(e.target.value)}
              />
            </div>
            <div className="flex gap-2">
              <Button type="submit" size="sm" disabled={saving} className="gap-2">
                {saving && <Loader2 className="h-4 w-4 animate-spin" />}
                {connected ? '교체하기' : '연결하기'}
              </Button>
              {editing && (
                <Button type="button" size="sm" variant="ghost" disabled={saving} onClick={resetForm}>
                  취소
                </Button>
              )}
            </div>
          </form>
        )}
      </CardContent>
    </Card>
  )
}
