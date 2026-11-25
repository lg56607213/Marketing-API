import { useCallback, useEffect, useState } from 'react'
import { AlertTriangle, Check, Eye, Loader2, Mail, Send, X } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { reportMailApi, apiMessage } from '@/services/api'
import type { ReportMailSettings } from '@/types'
import { useAdScopeStore } from '@/store/adScopeStore'

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/**
 * 매일 아침 보고 메일. 받는 사람을 업체가 직접 정하고, 미리보기와 지금 보내 보기를 할 수 있다.
 */
export function ReportMailCard() {
  const { accountId } = useAdScopeStore()
  const [settings, setSettings] = useState<ReportMailSettings | null>(null)
  const [recipients, setRecipients] = useState<string[]>([])
  const [enabled, setEnabled] = useState(true)
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState<'save' | 'preview' | 'send' | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [preview, setPreview] = useState<{ subject: string; html: string } | null>(null)

  const apply = (s: ReportMailSettings) => {
    setSettings(s)
    setRecipients(s.recipients)
    setEnabled(s.enabled)
  }

  const load = useCallback(async () => {
    setError(null)
    setPreview(null)
    if (!accountId) return
    try {
      apply(await reportMailApi.settings())
    } catch (err) {
      setError(apiMessage(err, '보고 메일 설정을 불러오지 못했습니다.'))
    }
  }, [accountId])

  useEffect(() => {
    void load()
  }, [load])

  const addDraft = () => {
    const values = draft.split(/[,\s]+/).map((v) => v.trim()).filter(Boolean)
    if (values.length === 0) return
    const bad = values.find((v) => !EMAIL.test(v))
    if (bad) {
      setError(`이메일 주소 형식이 아닙니다: ${bad}`)
      return
    }
    setError(null)
    setRecipients((prev) => Array.from(new Set([...prev, ...values])))
    setDraft('')
  }

  const save = async () => {
    setBusy('save')
    setError(null)
    setNotice(null)
    try {
      const pending = draft.trim() ? [...recipients, ...draft.split(/[,\s]+/).filter(Boolean)] : recipients
      apply(await reportMailApi.update(enabled, Array.from(new Set(pending))))
      setDraft('')
      setNotice('저장했습니다. 매일 아침 수집과 자동 운영이 끝나면 보내 드립니다.')
    } catch (err) {
      setError(apiMessage(err, '저장하지 못했습니다.'))
    } finally {
      setBusy(null)
    }
  }

  const showPreview = async () => {
    setBusy('preview')
    setError(null)
    try {
      setPreview(await reportMailApi.preview())
    } catch (err) {
      setError(apiMessage(err, '미리보기를 만들지 못했습니다.'))
    } finally {
      setBusy(null)
    }
  }

  const sendNow = async () => {
    setBusy('send')
    setError(null)
    setNotice(null)
    try {
      const to = await reportMailApi.send()
      setNotice(`${to.join(', ')} 로 보냈습니다. 메일함을 확인해 주세요.`)
    } catch (err) {
      setError(apiMessage(err, '보내지 못했습니다.'))
    } finally {
      setBusy(null)
    }
  }

  if (!settings) {
    return error ? <p className="text-sm text-destructive">{error}</p> : null
  }

  const dirty = enabled !== settings.enabled || recipients.join(',') !== settings.recipients.join(',') || draft.trim() !== ''

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2 text-base">
          <Mail className="h-4 w-4" /> 매일 아침 보고 메일
        </CardTitle>
        <CardDescription className="text-xs">
          어제 광고비·클릭·클릭률·전환·전환율, 남은 광고비(비즈머니)로 며칠 버틸 수 있는지, 자동 운영이 한 일과 오늘 확인할 일을 매일 아침 보내 드립니다.
          {settings.sender && ` 보내는 주소: ${settings.sender}`}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {!settings.mailConfigured && (
          <p className="flex gap-2 rounded-md border border-amber-500/30 bg-amber-50 px-3 py-2 text-xs text-amber-900">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
            서버에 메일 발송 계정이 아직 연결되지 않았습니다. 받는 사람은 지금 저장해 두시면 연결되는 날부터 보내 드립니다.
          </p>
        )}

        <div className="space-y-2">
          <p className="text-sm font-medium">받는 사람</p>
          {recipients.length === 0 ? (
            <p className="text-xs text-muted-foreground">아직 없습니다. 아래에 이메일을 넣고 추가하세요.</p>
          ) : (
            <ul className="flex flex-wrap gap-2">
              {recipients.map((r) => (
                <li key={r} className="flex items-center gap-1 rounded-full bg-muted px-3 py-1 text-xs">
                  {r}
                  <button
                    type="button"
                    aria-label={`${r} 빼기`}
                    className="text-muted-foreground hover:text-foreground"
                    onClick={() => setRecipients((prev) => prev.filter((x) => x !== r))}
                  >
                    <X className="h-3 w-3" />
                  </button>
                </li>
              ))}
            </ul>
          )}
          <div className="flex gap-2">
            <Input
              type="email"
              placeholder="예: ceo@company.co.kr"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault()
                  addDraft()
                }
              }}
            />
            <Button type="button" variant="outline" onClick={addDraft}>추가</Button>
          </div>
        </div>

        <label className="flex cursor-pointer items-center gap-2 text-sm">
          <input type="checkbox" className="h-4 w-4" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
          매일 아침 보내기
        </label>

        {error && <p className="text-sm text-destructive">{error}</p>}
        {notice && (
          <p className="flex items-center gap-2 text-sm text-emerald-700">
            <Check className="h-4 w-4" /> {notice}
          </p>
        )}

        <div className="flex flex-wrap gap-2">
          <Button size="sm" disabled={busy !== null || !dirty} onClick={() => void save()} className="gap-2">
            {busy === 'save' && <Loader2 className="h-4 w-4 animate-spin" />}
            저장
          </Button>
          <Button size="sm" variant="outline" disabled={busy !== null} onClick={() => void showPreview()} className="gap-2">
            {busy === 'preview' ? <Loader2 className="h-4 w-4 animate-spin" /> : <Eye className="h-4 w-4" />}
            미리보기
          </Button>
          <Button
            size="sm"
            variant="outline"
            disabled={busy !== null || dirty || settings.recipients.length === 0 || !settings.mailConfigured}
            onClick={() => void sendNow()}
            className="gap-2"
            title={dirty ? '먼저 저장해 주세요' : undefined}
          >
            {busy === 'send' ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
            지금 보내 보기
          </Button>
        </div>
        {settings.lastSentOn && (
          <p className="text-xs text-muted-foreground">마지막으로 보낸 날: {settings.lastSentOn}</p>
        )}

        {preview && (
          <div className="space-y-2">
            <p className="text-xs text-muted-foreground">제목: {preview.subject}</p>
            <iframe
              title="보고 메일 미리보기"
              srcDoc={preview.html}
              sandbox=""
              className="h-[640px] w-full rounded-md border bg-white"
            />
          </div>
        )}
      </CardContent>
    </Card>
  )
}
