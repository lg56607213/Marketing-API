import { useEffect, useState } from 'react'
import { Check, ExternalLink, KeyRound, Loader2, Trash2 } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { affiliateApi, apiMessage } from '@/services/api'
import type { AffiliateSettings } from '@/types'

/** 업체마다 자기 네이버 개발자센터 "검색" API 키를 넣는다. Secret 은 저장 후 다시 보여 주지 않는다. */
export function AffiliateKeyPanel({ onSaved }: { onSaved?: () => void }) {
  const [settings, setSettings] = useState<AffiliateSettings | null>(null)
  const [clientId, setClientId] = useState('')
  const [secret, setSecret] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    affiliateApi.settings().then((s) => { setSettings(s); setClientId(s.clientId ?? '') }).catch(() => undefined)
  }, [])

  const save = async () => {
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const s = await affiliateApi.saveSettings({ clientId: clientId.trim(), clientSecret: secret.trim() || null })
      setSettings(s)
      setSecret('')
      setNotice('네이버에서 키를 확인했고 암호화해 저장했습니다. 이제 글을 만들 때 상품 가격 · 브랜드 · 대표 사진을 자동으로 확인합니다.')
      onSaved?.()
    } catch (e) {
      setError(apiMessage(e, '키를 저장하지 못했습니다.'))
    } finally {
      setBusy(false)
    }
  }

  const remove = async () => {
    if (!window.confirm('저장한 네이버 키를 지울까요?')) return
    await affiliateApi.removeSettings()
    setSettings({ configured: false, clientId: null, verifiedAt: null })
    setClientId('')
    onSaved?.()
  }

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="flex items-center gap-2 text-base"><KeyRound className="h-4 w-4" /> 네이버 검색 API 키 넣기</CardTitle>
          <CardDescription className="text-xs">
            {settings?.configured
              ? `연결됨 · ${settings.verifiedAt?.slice(0, 16).replace('T', ' ')} 확인`
              : '아직 연결 전입니다. 키가 없어도 글은 만들 수 있지만, 상품 정보는 직접 붙여 넣은 설명만 씁니다.'}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground">Client ID</Label>
            <Input value={clientId} onChange={(e) => setClientId(e.target.value)} className="h-9" autoComplete="off" />
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground">Client Secret {settings?.configured && '(바꿀 때만 입력)'}</Label>
            <Input type="password" value={secret} onChange={(e) => setSecret(e.target.value)} className="h-9" autoComplete="new-password"
              placeholder={settings?.configured ? '저장됨 · 비워 두면 그대로' : ''} />
          </div>
          {error && <p className="text-sm text-destructive">{error}</p>}
          {notice && <p className="flex items-start gap-2 text-sm text-emerald-700"><Check className="mt-0.5 h-4 w-4 shrink-0" />{notice}</p>}
          <div className="flex gap-2">
            <Button disabled={busy || !clientId.trim() || (!settings?.configured && !secret.trim())} onClick={() => void save()}>
              {busy ? <><Loader2 className="mr-1 h-4 w-4 animate-spin" />네이버에 확인 중</> : '확인하고 저장'}
            </Button>
            {settings?.configured && (
              <Button variant="ghost" className="gap-1 text-muted-foreground hover:text-destructive" onClick={() => void remove()}>
                <Trash2 className="h-4 w-4" /> 키 지우기
              </Button>
            )}
          </div>
          <p className="text-xs text-muted-foreground">Secret 은 암호화해 저장하고 화면에 다시 보여 주지 않습니다. 우리 회사 계정에서만 씁니다.</p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">키 받는 법 (무료, 3분)</CardTitle>
        </CardHeader>
        <CardContent>
          <ol className="list-decimal space-y-2 pl-4 text-sm">
            <li>
              <a href="https://developers.naver.com/apps/#/register" target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 underline">
                네이버 개발자센터 · 애플리케이션 등록 <ExternalLink className="h-3 w-3" />
              </a>{' '}에 네이버 아이디로 로그인
            </li>
            <li>애플리케이션 이름: 아무거나 (예: 우리회사 블로그)</li>
            <li>사용 API 에서 <b>검색</b> 선택</li>
            <li>환경 추가에서 <b>WEB 설정</b> → 웹 서비스 URL 에 <code className="rounded bg-muted px-1">https://marketagent.co.kr</code></li>
            <li>등록하면 나오는 <b>Client ID</b> · <b>Client Secret</b> 을 왼쪽에 붙여 넣고 "확인하고 저장"</li>
          </ol>
          <p className="mt-3 text-xs text-muted-foreground">검색 API 는 하루 25,000회까지 무료입니다. 글 1개에 1회 씁니다.</p>
        </CardContent>
      </Card>
    </div>
  )
}
