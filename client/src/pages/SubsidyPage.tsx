import { useCallback, useEffect, useState } from 'react'
import { AlertCircle, Building, Check, ExternalLink, Pencil, Plus, RefreshCw, Star, Trash2, Play } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { apiMessage, subsidyApi } from '@/services/api'
import { useAuthStore } from '@/store/authStore'
import type { SubsidyMatchItem, SubsidyProfile, SubsidyStatus } from '@/types'
import { cn } from '@/lib/utils'
import { SubsidySearchPanel } from '@/features/subsidy/SubsidySearchPanel'
import { SubsidyManualPanel } from '@/features/subsidy/SubsidyManualPanel'
import { KsicPicker } from '@/features/subsidy/KsicPicker'

const SIDO = ['서울', '부산', '대구', '인천', '광주', '대전', '울산', '세종', '경기', '강원', '충북', '충남', '전북', '전남', '경북', '경남', '제주']
const CERTS = ['벤처', '이노비즈', '메인비즈', '여성기업', '장애인기업', '사회적기업', '청년창업']

const empty = (): SubsidyProfile => ({
  name: '', entityType: '개인', foundedDate: null, regionSido: null, regionSigungu: null, ksicCode: null,
  industryName: null, revenueKrw: null, employees: null, certifications: [], ceoBirthYear: null, ceoGender: null,
  keywords: null, emailOptIn: true, recipients: null,
})

const numOrNull = (v: string) => (v.trim() === '' ? null : Number(v.replace(/[^0-9]/g, '')))

export function SubsidyPage() {
  const { superAdmin, email } = useAuthStore()
  const [profiles, setProfiles] = useState<SubsidyProfile[]>([])
  const [currentId, setCurrentId] = useState<number | null>(null)
  const [editing, setEditing] = useState<SubsidyProfile | null>(null)
  const [matches, setMatches] = useState<SubsidyMatchItem[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const loadProfiles = useCallback(async () => {
    const list = await subsidyApi.profiles()
    setProfiles(list)
    setCurrentId((prev) => (prev && list.some((p) => p.id === prev) ? prev : list[0]?.id ?? null))
    if (list.length === 0) setEditing({ ...empty(), recipients: email ?? null })
  }, [email])

  useEffect(() => {
    loadProfiles().catch(() => setError('사업체 목록을 불러오지 못했습니다.'))
  }, [loadProfiles])

  const loadMatches = useCallback(async (id: number) => {
    try {
      setMatches(await subsidyApi.matches(id))
    } catch {
      setMatches([])
    }
  }, [])

  useEffect(() => {
    if (currentId) void loadMatches(currentId)
  }, [currentId, loadMatches])

  const save = async () => {
    if (!editing) return
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const saved = editing.id ? await subsidyApi.update(editing.id, editing) : await subsidyApi.create(editing)
      await loadProfiles()
      setCurrentId(saved.id ?? null)
      setEditing(null)
      if (saved.id) {
        const n = await subsidyApi.rematch(saved.id)
        await loadMatches(saved.id)
        setNotice(`저장했습니다. 지금 모아 둔 공고로 판정해 신청해 볼 만한 공고 ${n}건을 찾았습니다.`)
      }
    } catch (e) {
      setError(apiMessage(e, '저장하지 못했습니다.'))
    } finally {
      setBusy(false)
    }
  }

  const remove = async (id: number) => {
    if (!window.confirm('이 사업체와 추천 기록을 지울까요?')) return
    await subsidyApi.remove(id)
    setMatches([])
    await loadProfiles()
  }

  const rematch = async () => {
    if (!currentId) return
    setBusy(true)
    try {
      const n = await subsidyApi.rematch(currentId)
      await loadMatches(currentId)
      setNotice(`다시 판정했습니다. 신청해 볼 만한 공고 ${n}건.`)
    } catch (e) {
      setError(apiMessage(e, '판정에 실패했습니다.'))
    } finally {
      setBusy(false)
    }
  }

  const toggleStar = async (m: SubsidyMatchItem) => {
    await subsidyApi.star(m.id, !m.starred)
    setMatches((prev) => prev.map((x) => (x.id === m.id ? { ...x, starred: !x.starred } : x)))
  }

  const current = profiles.find((p) => p.id === currentId)
  const fit = matches.filter((m) => m.verdict === 'fit')
  const maybe = matches.filter((m) => m.verdict === 'maybe')
  const no = matches.filter((m) => m.verdict === 'no')
  const starred = matches.filter((m) => m.starred)
  const closing = matches.filter((m) => m.verdict !== 'no' && m.daysLeft !== null && m.daysLeft <= 7)

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-semibold">정부지원사업 맞춤 추천</h1>
        <p className="text-sm text-muted-foreground">
          기업마당 · K-Startup · 판판대로 공고와 직접 등록한 사업을 매일 아침 모아, 등록한 사업체 조건(지역 · 업력 · 업종 · 인증 등)에 맞는 지원사업과 정책자금을 골라 드립니다.
        </p>
      </div>

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

      <Tabs defaultValue="recommend">
        <TabsList>
          <TabsTrigger value="recommend">맞춤 추천</TabsTrigger>
          <TabsTrigger value="search">공고 검색</TabsTrigger>
          <TabsTrigger value="manual">지원사업 직접 등록</TabsTrigger>
        </TabsList>
        <TabsContent value="recommend" className="mt-4 space-y-6">
          {/* 사업체 고르기 */}
          <div className="flex flex-wrap items-center gap-2">
            {profiles.map((p) => (
              <Button
                key={p.id}
                size="sm"
                variant={p.id === currentId ? 'default' : 'outline'}
                onClick={() => { setCurrentId(p.id ?? null); setEditing(null) }}
                className="gap-1.5"
              >
                <Building className="h-3.5 w-3.5" /> {p.name}
                <span className="text-[10px] opacity-70">{p.entityType}</span>
              </Button>
            ))}
            <Button size="sm" variant="ghost" className="gap-1" onClick={() => setEditing({ ...empty(), recipients: email ?? null })}>
              <Plus className="h-3.5 w-3.5" /> 사업체 추가
            </Button>
          </div>

          {editing ? (
            <ProfileForm value={editing} onChange={setEditing} onSave={() => void save()} onCancel={() => setEditing(null)} busy={busy} />
          ) : current ? (
            <>
              <Card>
                <CardContent className="flex flex-wrap items-center gap-x-6 gap-y-2 p-4 text-sm">
                  <span><b>{current.name}</b> · {current.entityType}</span>
                  <span className="text-muted-foreground">
                    {[current.regionSido, current.industryName, current.foundedDate && `개업 ${current.foundedDate}`]
                      .filter(Boolean).join(' · ') || '지역·업종·개업일을 넣으면 더 정확해집니다'}
                  </span>
                  <div className="ml-auto flex gap-2">
                    <Button size="sm" variant="outline" className="gap-1" disabled={busy} onClick={() => void rematch()}>
                      <RefreshCw className={cn('h-3.5 w-3.5', busy && 'animate-spin')} /> 다시 판정
                    </Button>
                    <Button size="sm" variant="outline" className="gap-1" onClick={() => setEditing(current)}>
                      <Pencil className="h-3.5 w-3.5" /> 정보 수정
                    </Button>
                    <Button size="sm" variant="ghost" className="text-muted-foreground" onClick={() => current.id && void remove(current.id)}>
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                </CardContent>
              </Card>

              <Tabs defaultValue="fit">
                <TabsList className="flex-wrap">
                  <TabsTrigger value="fit">신청 추천 ({fit.length})</TabsTrigger>
                  <TabsTrigger value="maybe">확인 필요 ({maybe.length})</TabsTrigger>
                  <TabsTrigger value="closing">마감 임박 ({closing.length})</TabsTrigger>
                  <TabsTrigger value="star">관심 ({starred.length})</TabsTrigger>
                  <TabsTrigger value="no">조건 불일치 ({no.length})</TabsTrigger>
                </TabsList>
                {(
                  [
                    ['fit', fit, '조건에 맞고 사업 내용과 관련 있는 공고입니다.'],
                    ['maybe', maybe, '자격은 걸리지 않지만 관련성이 약하거나, 사업체 정보가 비어 판단을 못 한 조건이 있는 공고입니다.'],
                    ['closing', closing, '7일 안에 마감되는 공고입니다.'],
                    ['star', starred, '관심 표시한 공고입니다. 마감 3일 전에 메일로 한 번 더 알려 드립니다.'],
                    ['no', no, '지역 · 업력 · 유형 등 조건이 맞지 않는 공고입니다. 자동 판정이 틀릴 수 있어 숨기지 않고 보여 드립니다.'],
                  ] as [string, SubsidyMatchItem[], string][]
                ).map(([key, list, desc]) => (
                  <TabsContent key={key} value={key} className="mt-3 space-y-3">
                    <p className="text-xs text-muted-foreground">{desc}</p>
                    {list.length === 0 ? (
                      <p className="rounded-md border border-dashed px-4 py-10 text-center text-sm text-muted-foreground">
                        아직 해당하는 공고가 없습니다. 공고는 매일 아침 8시 30분에 새로 모읍니다.
                      </p>
                    ) : (
                      list.map((m) => <MatchCard key={m.id} m={m} onStar={() => void toggleStar(m)} />)
                    )}
                  </TabsContent>
                ))}
              </Tabs>
            </>
          ) : null}
        </TabsContent>
        <TabsContent value="search" className="mt-4">
          <SubsidySearchPanel />
        </TabsContent>
        <TabsContent value="manual" className="mt-4">
          <SubsidyManualPanel onChanged={() => currentId && void loadMatches(currentId)} />
        </TabsContent>
      </Tabs>

      {superAdmin && <AdminPanel />}

      <p className="border-t pt-4 text-xs text-muted-foreground">
        공고 원문 확인 필수, 매칭 결과는 참고용입니다. 이 서비스는 정보 제공만 하며 정책자금 알선 · 신청 대행을 하지 않습니다.
      </p>
    </div>
  )
}

function MatchCard({ m, onStar }: { m: SubsidyMatchItem; onStar: () => void }) {
  return (
    <Card className={cn(m.verdict === 'no' && 'opacity-70')}>
      <CardContent className="space-y-2 p-4">
        <div className="flex items-start gap-3">
          <div className="min-w-0 flex-1">
            <a href={m.url ?? '#'} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 font-medium hover:underline">
              {m.title} <ExternalLink className="h-3.5 w-3.5 shrink-0 opacity-60" />
            </a>
            <div className="mt-1 flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
              {m.agency && <span>{m.agency}</span>}
              {m.supportType && <Badge variant="outline" className="text-[10px]">{m.supportType}</Badge>}
              {m.amountText && <Badge variant="outline" className="text-[10px]">{m.amountText}</Badge>}
              <span>
                {m.applyEnd ? `마감 ${m.applyEnd}` : '마감일 원문 확인'}
              </span>
              {m.daysLeft !== null && (
                <Badge className={cn('text-[10px]', m.daysLeft <= 7 ? 'bg-rose-600 hover:bg-rose-600' : 'bg-slate-500 hover:bg-slate-500')}>
                  D-{m.daysLeft}
                </Badge>
              )}
              <span className="text-[10px]">{m.source === 'kstartup' ? 'K-Startup' : '기업마당'}</span>
            </div>
          </div>
          <div className="flex flex-col items-end gap-1">
            <span className={cn('text-lg font-semibold', m.verdict === 'fit' ? 'text-indigo-600' : 'text-muted-foreground')}>{m.score}</span>
            <Button variant="ghost" size="icon" className="h-7 w-7" onClick={onStar} title="관심 표시">
              <Star className={cn('h-4 w-4', m.starred ? 'fill-amber-400 text-amber-400' : 'text-muted-foreground')} />
            </Button>
          </div>
        </div>
        {m.summary && <p className="whitespace-pre-line text-sm leading-relaxed">{m.summary}</p>}
        {m.reasons.length > 0 && (
          <ul className="space-y-0.5 text-xs text-emerald-700">
            {m.reasons.map((r, i) => <li key={i}>✓ {r}</li>)}
          </ul>
        )}
        {m.blockers.length > 0 && (
          <ul className="space-y-0.5 text-xs text-amber-700">
            {m.blockers.map((b, i) => <li key={i}>! {b}</li>)}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}

function ProfileForm({
  value, onChange, onSave, onCancel, busy,
}: {
  value: SubsidyProfile
  onChange: (p: SubsidyProfile) => void
  onSave: () => void
  onCancel: () => void
  busy: boolean
}) {
  const set = <K extends keyof SubsidyProfile>(k: K, v: SubsidyProfile[K]) => onChange({ ...value, [k]: v })
  const toggleCert = (c: string) =>
    set('certifications', value.certifications.includes(c) ? value.certifications.filter((x) => x !== c) : [...value.certifications, c])
  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-base">{value.id ? '사업체 정보 수정' : '사업체 등록'}</CardTitle>
        <CardDescription className="text-xs">
          비워 둔 항목은 "판단 못 함"으로 처리돼 추천이 덜 정확해집니다. 대표 생년 · 성별은 청년 · 여성기업 조건 판정에만 쓰고 이 업체만 볼 수 있습니다.
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4 md:grid-cols-3">
        <F label="사업체 이름 *"><Input value={value.name} onChange={(e) => set('name', e.target.value)} className="h-9" /></F>
        <F label="사업자 유형">
          <Select value={value.entityType} onValueChange={(v) => set('entityType', v as SubsidyProfile['entityType'])}>
            <SelectTrigger className="h-9"><SelectValue /></SelectTrigger>
            <SelectContent>
              {['법인', '개인', '예비창업자'].map((t) => <SelectItem key={t} value={t}>{t}</SelectItem>)}
            </SelectContent>
          </Select>
        </F>
        <F label="개업일 (업력 계산)">
          <Input type="date" value={value.foundedDate ?? ''} onChange={(e) => set('foundedDate', e.target.value || null)} className="h-9" />
        </F>
        <F label="사업장 시 · 도">
          <Select value={value.regionSido ?? ''} onValueChange={(v) => set('regionSido', v || null)}>
            <SelectTrigger className="h-9"><SelectValue placeholder="선택" /></SelectTrigger>
            <SelectContent>{SIDO.map((s) => <SelectItem key={s} value={s}>{s}</SelectItem>)}</SelectContent>
          </Select>
        </F>
        <F label="시 · 군 · 구"><Input value={value.regionSigungu ?? ''} onChange={(e) => set('regionSigungu', e.target.value || null)} className="h-9" placeholder="수원시" /></F>
        <F label="업종 (사업자등록증 업태 · 종목)"><Input value={value.industryName ?? ''} onChange={(e) => set('industryName', e.target.value || null)} className="h-9" placeholder="자동차 임대업" /></F>
        <F label="업종 분류 (검색해서 선택)">
          <KsicPicker value={value.ksicCode} onChange={(code, name) => {
            onChange({ ...value, ksicCode: code, industryName: value.industryName || name })
          }} />
        </F>
        <F label="연 매출 (원)"><Input inputMode="numeric" value={value.revenueKrw ?? ''} onChange={(e) => set('revenueKrw', numOrNull(e.target.value))} className="h-9" /></F>
        <F label="상시근로자 (명)"><Input inputMode="numeric" value={value.employees ?? ''} onChange={(e) => set('employees', numOrNull(e.target.value))} className="h-9" /></F>
        <F label="대표 출생연도"><Input inputMode="numeric" value={value.ceoBirthYear ?? ''} onChange={(e) => set('ceoBirthYear', numOrNull(e.target.value))} className="h-9" placeholder="1990" /></F>
        <F label="대표 성별 (선택)">
          <Select value={value.ceoGender ?? ''} onValueChange={(v) => set('ceoGender', v || null)}>
            <SelectTrigger className="h-9"><SelectValue placeholder="선택 안 함" /></SelectTrigger>
            <SelectContent>{['남', '여'].map((g) => <SelectItem key={g} value={g}>{g}</SelectItem>)}</SelectContent>
          </Select>
        </F>
        <F label="관심 분야 · 사업 설명 (쉼표)">
          <Input value={value.keywords ?? ''} onChange={(e) => set('keywords', e.target.value || null)} className="h-9" placeholder="장기렌트, 온라인 마케팅, 운전자금" />
        </F>
        <div className="space-y-2 md:col-span-3">
          <Label className="text-xs text-muted-foreground">보유 인증</Label>
          <div className="flex flex-wrap gap-4">
            {CERTS.map((c) => (
              <label key={c} className="flex items-center gap-1.5 text-sm">
                <Checkbox checked={value.certifications.includes(c)} onCheckedChange={() => toggleCert(c)} /> {c}
              </label>
            ))}
          </div>
        </div>
        <F label="알림 받을 메일 (쉼표)" className="md:col-span-2">
          <Input value={value.recipients ?? ''} onChange={(e) => set('recipients', e.target.value || null)} className="h-9" />
        </F>
        <label className="flex items-center gap-2 pt-6 text-sm">
          <Checkbox checked={value.emailOptIn} onCheckedChange={(v) => set('emailOptIn', Boolean(v))} /> 매일 9시 추천 메일 받기
        </label>
        <div className="flex gap-2 md:col-span-3">
          <Button disabled={busy || !value.name.trim()} onClick={onSave}>{busy ? '저장 중...' : '저장하고 맞는 공고 찾기'}</Button>
          <Button variant="outline" onClick={onCancel}>취소</Button>
        </div>
      </CardContent>
    </Card>
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

function AdminPanel() {
  const [status, setStatus] = useState<SubsidyStatus | null>(null)
  const [running, setRunning] = useState<string | null>(null)
  const [out, setOut] = useState<string[]>([])

  const load = useCallback(() => subsidyApi.status().then(setStatus).catch(() => setStatus(null)), [])
  useEffect(() => { void load() }, [load])

  const run = async (job: string) => {
    setRunning(job)
    try {
      setOut(await subsidyApi.run(job))
    } catch (e) {
      setOut([apiMessage(e, '실행 실패')])
    } finally {
      setRunning(null)
      void load()
    }
  }

  if (!status) return null
  const flag = (ok: boolean, label: string) => (
    <Badge variant="outline" className={cn('text-[10px]', ok ? 'border-emerald-500 text-emerald-700' : 'border-rose-400 text-rose-600')}>
      {label} {ok ? '켜짐' : '꺼짐'}
    </Badge>
  )
  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-base">관리자: 수집 상태</CardTitle>
        <CardDescription className="flex flex-wrap gap-1.5 pt-1">
          {flag(status.enabled, '매일 배치')}
          {flag(status.bizinfoConfigured, '기업마당 키')}
          {flag(status.kstartupConfigured, 'K-Startup 키')}
          {flag(status.aiAvailable, 'AI 분석')}
          <span className="text-xs">공고 {status.announcements}건 · 미분석 {status.unanalyzed}건 · 사업체 {status.profiles}곳</span>
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="flex flex-wrap gap-2">
          {[['collect', '수집'], ['analyze', '분석'], ['match', '매칭'], ['notify', '메일 발송'], ['all', '수집→분석→매칭']].map(([job, label]) => (
            <Button key={job} size="sm" variant="outline" disabled={running !== null} onClick={() => void run(job)} className="gap-1">
              <Play className="h-3 w-3" /> {running === job ? '실행 중...' : label}
            </Button>
          ))}
        </div>
        {out.length > 0 && <pre className="whitespace-pre-wrap rounded bg-muted p-3 text-xs">{out.join('\n')}</pre>}
        <div className="max-h-56 overflow-y-auto text-xs">
          {status.recentRuns.map((r) => (
            <div key={r.id} className="flex gap-3 border-b py-1.5">
              <span className="w-32 shrink-0 text-muted-foreground">{r.startedAt.replace('T', ' ').slice(0, 16)}</span>
              <span className="w-16 shrink-0">{r.job}</span>
              <span className={cn('w-16 shrink-0', r.status === 'OK' ? 'text-emerald-600' : r.status === 'RUNNING' ? '' : 'text-rose-600')}>{r.status}</span>
              <span className="whitespace-pre-wrap">{r.detail}</span>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}
