import { useEffect, useState } from 'react'
import { ExternalLink, Search } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { apiMessage, subsidyApi } from '@/services/api'
import type { SubsidyProfile, SubsidySearchItem } from '@/types'
import { cn } from '@/lib/utils'

const NONE = 'none'
const SOURCE_LABEL: Record<string, string> = {
  bizinfo: '기업마당', kstartup: 'K-Startup', fanfandaero: '판판대로', manual: '직접 등록',
}
const VERDICT: Record<string, { label: string; cls: string }> = {
  fit: { label: '신청 추천', cls: 'bg-indigo-600 hover:bg-indigo-600' },
  maybe: { label: '확인 필요', cls: 'bg-amber-500 hover:bg-amber-500' },
  no: { label: '조건 불일치', cls: 'bg-slate-400 hover:bg-slate-400' },
}

/** 업무 말로 전체 공고를 찾고, 사업자를 고르면 그 조건으로 판정을 붙인다. */
export function SubsidySearchPanel() {
  const [profiles, setProfiles] = useState<SubsidyProfile[]>([])
  const [profileId, setProfileId] = useState<string>(NONE)
  const [q, setQ] = useState('')
  const [includeClosed, setIncludeClosed] = useState(false)
  const [hideNo, setHideNo] = useState(true)
  const [items, setItems] = useState<SubsidySearchItem[] | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    subsidyApi.profiles().then(setProfiles).catch(() => setProfiles([]))
  }, [])

  const search = async () => {
    setBusy(true)
    setError(null)
    try {
      setItems(await subsidyApi.search({
        q: q.trim() || undefined,
        profileId: profileId === NONE ? undefined : Number(profileId),
        includeClosed,
        hideNo: profileId !== NONE && hideNo,
      }))
    } catch (e) {
      setError(apiMessage(e, '검색하지 못했습니다.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-4">
      <Card>
        <CardContent className="flex flex-wrap items-end gap-3 p-4">
          <div className="min-w-[240px] flex-1 space-y-1">
            <Label className="text-xs text-muted-foreground">업무 · 사업 말 (여러 개는 띄어쓰기나 쉼표)</Label>
            <Input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && void search()}
              placeholder="렌터카, 온라인 마케팅, 수출, 운전자금"
              className="h-9"
            />
          </div>
          <div className="w-56 space-y-1">
            <Label className="text-xs text-muted-foreground">사업자 조건으로 판정</Label>
            <Select value={profileId} onValueChange={setProfileId}>
              <SelectTrigger className="h-9"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value={NONE}>사업자 고르지 않음</SelectItem>
                {profiles.map((p) => (
                  <SelectItem key={p.id} value={String(p.id)}>{p.name} ({p.entityType})</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <label className="flex items-center gap-1.5 pb-2 text-xs">
            <Checkbox checked={includeClosed} onCheckedChange={(v) => setIncludeClosed(Boolean(v))} /> 마감된 공고 포함
          </label>
          {profileId !== NONE && (
            <label className="flex items-center gap-1.5 pb-2 text-xs">
              <Checkbox checked={hideNo} onCheckedChange={(v) => setHideNo(Boolean(v))} /> 조건 불일치 숨기기
            </label>
          )}
          <Button size="sm" disabled={busy || (!q.trim() && profileId === NONE)} onClick={() => void search()} className="gap-2">
            <Search className="h-4 w-4" /> {busy ? '찾는 중...' : '검색'}
          </Button>
        </CardContent>
      </Card>

      {error && <p className="text-sm text-destructive">{error}</p>}

      {items && (
        <p className="text-xs text-muted-foreground">
          {items.length}건{items.length >= 300 && ' (상위 300건만 표시)'}
          {profileId !== NONE ? ' · 사업자 조건 적합도 순' : ' · 검색어가 제목에 나오는 공고 먼저'}
        </p>
      )}
      {items?.length === 0 && (
        <p className="rounded-md border border-dashed px-4 py-10 text-center text-sm text-muted-foreground">
          맞는 공고가 없습니다. 말을 바꾸거나 줄여 보세요.
        </p>
      )}
      {items?.map((m) => (
        <Card key={m.announcementId} className={cn(m.verdict === 'no' && 'opacity-70')}>
          <CardContent className="space-y-2 p-4">
            <div className="flex items-start gap-3">
              <div className="min-w-0 flex-1">
                <a href={m.url ?? '#'} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 font-medium hover:underline">
                  {m.title} {m.url && <ExternalLink className="h-3.5 w-3.5 shrink-0 opacity-60" />}
                </a>
                <div className="mt-1 flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
                  {m.agency && <span>{m.agency}</span>}
                  {m.regionText && <span>· {m.regionText.split(',').slice(0, 3).join(', ')}</span>}
                  {m.supportType && <Badge variant="outline" className="text-[10px]">{m.supportType}</Badge>}
                  {m.amountText && <Badge variant="outline" className="text-[10px]">{m.amountText}</Badge>}
                  <span>{m.applyEnd ? `마감 ${m.applyEnd}` : '상시 · 마감일 원문 확인'}</span>
                  {m.daysLeft !== null && m.daysLeft >= 0 && (
                    <Badge className={cn('text-[10px]', m.daysLeft <= 7 ? 'bg-rose-600 hover:bg-rose-600' : 'bg-slate-500 hover:bg-slate-500')}>
                      D-{m.daysLeft}
                    </Badge>
                  )}
                  <Badge variant="outline" className={cn('text-[10px]', m.manual && 'border-indigo-400 text-indigo-700')}>
                    {SOURCE_LABEL[m.source] ?? m.source}
                  </Badge>
                </div>
              </div>
              {m.verdict && (
                <div className="flex flex-col items-end gap-1">
                  <Badge className={cn('text-[10px]', VERDICT[m.verdict].cls)}>{VERDICT[m.verdict].label}</Badge>
                  <span className="text-sm font-semibold">{m.score}</span>
                </div>
              )}
            </div>
            {m.summary && <p className="line-clamp-3 whitespace-pre-line text-sm leading-relaxed">{m.summary}</p>}
            {m.reasons.length > 0 && (
              <ul className="space-y-0.5 text-xs text-emerald-700">{m.reasons.map((r, i) => <li key={i}>✓ {r}</li>)}</ul>
            )}
            {m.blockers.length > 0 && (
              <ul className="space-y-0.5 text-xs text-amber-700">{m.blockers.map((b, i) => <li key={i}>! {b}</li>)}</ul>
            )}
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
