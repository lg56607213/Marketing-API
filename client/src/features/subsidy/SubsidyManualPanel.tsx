import { useCallback, useEffect, useState } from 'react'
import { Check, ExternalLink, Pencil, Plus, Trash2 } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Checkbox } from '@/components/ui/checkbox'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { apiMessage, subsidyApi } from '@/services/api'
import { useAuthStore } from '@/store/authStore'
import type { SubsidyManual } from '@/types'
import { cn } from '@/lib/utils'

const TYPES = ['자금', '보증', 'R&D', '사업화', '마케팅', '교육', '인력', '수출', '기타']

const empty = (): SubsidyManual => ({
  title: '', agency: null, supportType: null, amountText: null, targetText: null, regionText: null,
  applyStart: null, applyEnd: null, url: null, summary: null, shared: false,
})

/** 수집되지 않는 지원사업(신보·기보 상품, 지자체 공고 등)을 직접 넣는다. 넣으면 자동 매칭에도 들어간다. */
export function SubsidyManualPanel({ onChanged }: { onChanged?: () => void }) {
  const { superAdmin } = useAuthStore()
  const [list, setList] = useState<SubsidyManual[]>([])
  const [editing, setEditing] = useState<SubsidyManual | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const load = useCallback(() => subsidyApi.manual().then(setList).catch(() => setList([])), [])
  useEffect(() => { void load() }, [load])

  const save = async () => {
    if (!editing) return
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      if (editing.id) await subsidyApi.updateManual(editing.id, editing)
      else await subsidyApi.createManual(editing)
      setNotice('저장했습니다. 자격요건을 분석해 내 사업자들을 다시 판정했습니다. "맞춤 추천" 탭에서 확인하세요.')
      setEditing(null)
      await load()
      onChanged?.()
    } catch (e) {
      setError(apiMessage(e, '저장하지 못했습니다.'))
    } finally {
      setBusy(false)
    }
  }

  const remove = async (id: number) => {
    if (!window.confirm('이 지원사업을 지울까요? 이 사업으로 생긴 추천도 함께 지워집니다.')) return
    await subsidyApi.removeManual(id)
    await load()
    onChanged?.()
  }

  const set = <K extends keyof SubsidyManual>(k: K, v: SubsidyManual[K]) => editing && setEditing({ ...editing, [k]: v })

  return (
    <div className="space-y-4">
      {notice && (
        <div className="flex items-center gap-2 rounded-md border border-emerald-500/30 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
          <Check className="h-4 w-4 shrink-0" /> {notice}
        </div>
      )}
      {error && <p className="text-sm text-destructive">{error}</p>}

      {editing ? (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">{editing.id ? '지원사업 수정' : '지원사업 직접 등록'}</CardTitle>
            <CardDescription className="text-xs">
              지원 대상 · 내용을 자세히 적을수록 자격요건(지역 · 업력 · 업종 등)을 정확히 뽑아 사업자와 맞춰 봅니다. 마감일을 비우면 상시 사업으로 봅니다.
            </CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4 md:grid-cols-2">
            <F label="사업 이름 *" className="md:col-span-2">
              <Input value={editing.title} onChange={(e) => set('title', e.target.value)} className="h-9" placeholder="신용보증기금 창업기업 보증" />
            </F>
            <F label="운영 기관"><Input value={editing.agency ?? ''} onChange={(e) => set('agency', e.target.value || null)} className="h-9" placeholder="신용보증기금" /></F>
            <F label="지원 유형">
              <Select value={editing.supportType ?? ''} onValueChange={(v) => set('supportType', v || null)}>
                <SelectTrigger className="h-9"><SelectValue placeholder="선택" /></SelectTrigger>
                <SelectContent>{TYPES.map((t) => <SelectItem key={t} value={t}>{t}</SelectItem>)}</SelectContent>
              </Select>
            </F>
            <F label="지원 규모"><Input value={editing.amountText ?? ''} onChange={(e) => set('amountText', e.target.value || null)} className="h-9" placeholder="최대 3억원, 보증비율 95%" /></F>
            <F label="지역"><Input value={editing.regionText ?? ''} onChange={(e) => set('regionText', e.target.value || null)} className="h-9" placeholder="전국 / 경기" /></F>
            <F label="신청 시작일"><Input type="date" value={editing.applyStart ?? ''} onChange={(e) => set('applyStart', e.target.value || null)} className="h-9" /></F>
            <F label="마감일 (비우면 상시)"><Input type="date" value={editing.applyEnd ?? ''} onChange={(e) => set('applyEnd', e.target.value || null)} className="h-9" /></F>
            <F label="지원 대상" className="md:col-span-2">
              <Input value={editing.targetText ?? ''} onChange={(e) => set('targetText', e.target.value || null)} className="h-9" placeholder="창업 7년 이내 중소기업, 대표자 만 39세 이하 우대" />
            </F>
            <F label="내용 (공고 본문을 붙여 넣어도 됩니다)" className="md:col-span-2">
              <Textarea value={editing.summary ?? ''} onChange={(e) => set('summary', e.target.value || null)} rows={5} />
            </F>
            <F label="공고 · 안내 링크" className="md:col-span-2">
              <Input value={editing.url ?? ''} onChange={(e) => set('url', e.target.value || null)} className="h-9" placeholder="https://" />
            </F>
            {superAdmin && (
              <label className="flex items-center gap-2 text-sm md:col-span-2">
                <Checkbox checked={editing.shared} onCheckedChange={(v) => set('shared', Boolean(v))} />
                모든 회원에게 공개 (최고 관리자 전용)
              </label>
            )}
            <div className="flex gap-2 md:col-span-2">
              <Button disabled={busy || !editing.title.trim()} onClick={() => void save()}>
                {busy ? '분석 · 판정 중...' : '저장하고 내 사업자와 맞춰 보기'}
              </Button>
              <Button variant="outline" onClick={() => setEditing(null)}>취소</Button>
            </div>
          </CardContent>
        </Card>
      ) : (
        <Button size="sm" className="gap-1" onClick={() => setEditing(empty())}>
          <Plus className="h-4 w-4" /> 지원사업 직접 등록
        </Button>
      )}

      {list.length === 0 ? (
        <p className="rounded-md border border-dashed px-4 py-10 text-center text-sm text-muted-foreground">
          직접 등록한 지원사업이 없습니다. 기업마당 · K-Startup · 판판대로에 없는 사업(예: 신보 · 기보 보증상품)을 넣어 두면 자동으로 함께 추천합니다.
        </p>
      ) : (
        list.map((m) => (
          <Card key={m.id}>
            <CardContent className="flex items-start gap-3 p-4">
              <div className="min-w-0 flex-1 space-y-1">
                <div className="flex flex-wrap items-center gap-1.5">
                  <span className="font-medium">{m.title}</span>
                  {m.url && (
                    <a href={m.url} target="_blank" rel="noopener noreferrer" className="text-muted-foreground hover:text-foreground">
                      <ExternalLink className="h-3.5 w-3.5" />
                    </a>
                  )}
                  {m.shared && <Badge variant="outline" className="border-indigo-400 text-[10px] text-indigo-700">공용</Badge>}
                  {!m.analyzed && <Badge variant="outline" className="text-[10px]">분석 대기</Badge>}
                </div>
                <p className="text-xs text-muted-foreground">
                  {[m.agency, m.supportType, m.amountText, m.regionText, m.applyEnd ? `마감 ${m.applyEnd}` : '상시'].filter(Boolean).join(' · ')}
                </p>
                {m.targetText && <p className="text-xs">대상: {m.targetText}</p>}
              </div>
              {(!m.shared || superAdmin) && (
                <div className="flex gap-1">
                  <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => setEditing(m)}><Pencil className="h-3.5 w-3.5" /></Button>
                  <Button variant="ghost" size="icon" className={cn('h-7 w-7 text-muted-foreground hover:text-destructive')} onClick={() => m.id && void remove(m.id)}>
                    <Trash2 className="h-3.5 w-3.5" />
                  </Button>
                </div>
              )}
            </CardContent>
          </Card>
        ))
      )}
    </div>
  )
}

function F({ label, children, className }: { label: string; children: React.ReactNode; className?: string }) {
  return (
    <div className={cn('space-y-1', className)}>
      <Label className="text-xs text-muted-foreground">{label}</Label>
      {children}
    </div>
  )
}
