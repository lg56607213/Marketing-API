import { useCallback, useEffect, useState } from 'react'
import { Search, Plus, AlertCircle, Check, TrendingUp, Ban, Trash2 } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { keywordToolApi } from '@/services/api'
import type { KeywordIdea, AdGroupSummary, RestrictedKeyword } from '@/types'
import { cn } from '@/lib/utils'

const num = (v: number) => v.toLocaleString()

const COMPETITION_TONE: Record<string, string> = {
  높음: 'text-rose-600',
  중간: 'text-amber-600',
  낮음: 'text-emerald-600',
}

export function KeywordToolPage() {
  const [hints, setHints] = useState('')
  const [ideas, setIdeas] = useState<KeywordIdea[]>([])
  const [selected, setSelected] = useState<string[]>([])
  const [adgroups, setAdgroups] = useState<AdGroupSummary[]>([])
  const [targetGroup, setTargetGroup] = useState('')
  const [position, setPosition] = useState('3')
  const [hideRegistered, setHideRegistered] = useState(true)
  const [onlyRelated, setOnlyRelated] = useState(true)
  const [restricted, setRestricted] = useState<RestrictedKeyword[]>([])
  const [newRestricted, setNewRestricted] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    keywordToolApi
      .adgroups()
      .then((list) => {
        setAdgroups(list)
        if (list.length > 0) setTargetGroup(list[0].nccAdgroupId)
      })
      .catch(() => setError('광고그룹을 불러오지 못했습니다. 먼저 검색광고 동기화를 실행하세요.'))
  }, [])

  const loadRestricted = useCallback(async (groupId: string) => {
    if (!groupId) return
    try {
      setRestricted(await keywordToolApi.restricted(groupId))
    } catch {
      setRestricted([])
    }
  }, [])

  useEffect(() => {
    void loadRestricted(targetGroup)
  }, [targetGroup, loadRestricted])

  const search = async () => {
    if (!hints.trim()) return
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const result = await keywordToolApi.ideas(hints.trim(), hideRegistered, onlyRelated)
      setIdeas(result)
      setSelected([])
      setNotice(`연관 키워드 ${result.length}개를 찾았습니다.`)
    } catch {
      setError('키워드 조회에 실패했습니다. 힌트는 쉼표로 구분해 최대 5개까지 넣을 수 있습니다.')
    } finally {
      setBusy(false)
    }
  }

  const register = async () => {
    if (selected.length === 0 || !targetGroup) return
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      await keywordToolApi.add(targetGroup, selected, Number(position))
      setNotice(`${selected.length}개를 등록했습니다. 네이버 검수 후 노출됩니다.`)
      setSelected([])
      const result = await keywordToolApi.ideas(hints.trim(), hideRegistered, onlyRelated)
      setIdeas(result)
    } catch {
      setError('키워드 등록에 실패했습니다.')
    } finally {
      setBusy(false)
    }
  }

  const addRestricted = async () => {
    const words = newRestricted.split(',').map((w) => w.trim()).filter(Boolean)
    if (words.length === 0 || !targetGroup) return
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const added = await keywordToolApi.addRestricted(targetGroup, words)
      setNotice(added.length === 0 ? '이미 등록된 키워드입니다.' : `제외 키워드 ${added.length}개를 추가했습니다.`)
      setNewRestricted('')
      await loadRestricted(targetGroup)
    } catch {
      setError('제외 키워드 추가에 실패했습니다.')
    } finally {
      setBusy(false)
    }
  }

  const removeRestricted = async (id: string) => {
    setBusy(true)
    try {
      await keywordToolApi.removeRestricted(targetGroup, id)
      await loadRestricted(targetGroup)
    } catch {
      setError('제외 키워드 삭제에 실패했습니다.')
    } finally {
      setBusy(false)
    }
  }

  const toggle = (keyword: string) =>
    setSelected((prev) => (prev.includes(keyword) ? prev.filter((k) => k !== keyword) : [...prev, keyword]))

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end gap-3">
        <div className="min-w-[240px] flex-1 space-y-1">
          <Label htmlFor="hints" className="text-xs text-muted-foreground">
            힌트 키워드 (쉼표로 구분, 최대 5개)
          </Label>
          <Input
            id="hints"
            value={hints}
            onChange={(e) => setHints(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && void search()}
            placeholder="1톤트럭, 화물차리스"
            className="h-9"
          />
        </div>
        <div className="flex items-center gap-2 pb-2">
          <Checkbox
            id="hide"
            checked={hideRegistered}
            onCheckedChange={(v) => setHideRegistered(Boolean(v))}
          />
          <Label htmlFor="hide" className="text-xs">등록된 키워드 숨기기</Label>
        </div>
        <div className="flex items-center gap-2 pb-2">
          <Checkbox
            id="related"
            checked={onlyRelated}
            onCheckedChange={(v) => setOnlyRelated(Boolean(v))}
          />
          <Label htmlFor="related" className="text-xs">힌트 포함 키워드만</Label>
        </div>
        <Button size="sm" disabled={busy} onClick={() => void search()} className="gap-2">
          <Search className="h-4 w-4" /> {busy ? '조회 중...' : '검색량 조회'}
        </Button>
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

      <Tabs defaultValue="ideas">
        <TabsList>
          <TabsTrigger value="ideas">키워드 발굴 {ideas.length > 0 && `(${ideas.length})`}</TabsTrigger>
          <TabsTrigger value="restricted">제외 키워드 {restricted.length > 0 && `(${restricted.length})`}</TabsTrigger>
        </TabsList>

        <TabsContent value="ideas" className="mt-4 space-y-4">
          {selected.length > 0 && (
            <Card>
              <CardContent className="flex flex-wrap items-end gap-3 p-4">
                <div className="space-y-1">
                  <Label className="text-xs text-muted-foreground">등록할 광고그룹</Label>
                  <Select value={targetGroup} onValueChange={setTargetGroup}>
                    <SelectTrigger className="h-9 w-44"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {adgroups.map((g) => (
                        <SelectItem key={g.nccAdgroupId} value={g.nccAdgroupId}>{g.name}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1">
                  <Label className="text-xs text-muted-foreground">목표 노출순위</Label>
                  <Select value={position} onValueChange={setPosition}>
                    <SelectTrigger className="h-9 w-28"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {['1', '2', '3', '4', '5'].map((p) => (
                        <SelectItem key={p} value={p}>{p}위</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <Button size="sm" disabled={busy} onClick={() => void register()} className="gap-2">
                  <Plus className="h-4 w-4" /> 선택 {selected.length}개 등록
                </Button>
                <p className="pb-2 text-xs text-muted-foreground">
                  입찰가는 목표순위 추정치로 설정됩니다.
                </p>
              </CardContent>
            </Card>
          )}

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base">연관 키워드</CardTitle>
              <CardDescription className="text-xs">
                네이버 키워드도구 기준 월간 검색수입니다. 총 검색량 내림차순.
              </CardDescription>
            </CardHeader>
            <CardContent className="p-0">
              {ideas.length === 0 ? (
                <div className="py-14 text-center text-muted-foreground">
                  <TrendingUp className="mx-auto mb-3 h-10 w-10 opacity-30" />
                  <p className="text-sm">힌트 키워드를 넣고 조회해 보세요.</p>
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
                        <th className="px-6 py-2.5 text-left font-semibold">선택</th>
                        <th className="px-3 py-2.5 text-left font-semibold">키워드</th>
                        <th className="px-3 py-2.5 text-right font-semibold">PC</th>
                        <th className="px-3 py-2.5 text-right font-semibold">모바일</th>
                        <th className="px-3 py-2.5 text-right font-semibold">합계</th>
                        <th className="px-6 py-2.5 text-right font-semibold">경쟁도</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y">
                      {ideas.slice(0, 200).map((k) => (
                        <tr key={k.keyword} className="transition-colors hover:bg-muted/20">
                          <td className="px-6 py-2.5">
                            <Checkbox
                              checked={selected.includes(k.keyword)}
                              onCheckedChange={() => toggle(k.keyword)}
                              disabled={k.registered}
                            />
                          </td>
                          <td className="px-3 py-2.5">
                            <span className="font-medium">{k.keyword}</span>
                            {k.registered && (
                              <Badge variant="outline" className="ml-2 text-[10px]">등록됨</Badge>
                            )}
                          </td>
                          <td className="px-3 py-2.5 text-right">{num(k.pcCount)}</td>
                          <td className="px-3 py-2.5 text-right">{num(k.mobileCount)}</td>
                          <td className="px-3 py-2.5 text-right font-medium">{num(k.totalCount)}</td>
                          <td className={cn('px-6 py-2.5 text-right', COMPETITION_TONE[k.competition] ?? '')}>
                            {k.competition}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {ideas.length > 200 && (
                    <p className="px-6 py-3 text-xs text-muted-foreground">
                      상위 200개만 표시했습니다. 전체 {num(ideas.length)}개.
                    </p>
                  )}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="restricted" className="mt-4 space-y-4">
          <Card>
            <CardContent className="flex flex-wrap items-end gap-3 p-4">
              <div className="space-y-1">
                <Label className="text-xs text-muted-foreground">광고그룹</Label>
                <Select value={targetGroup} onValueChange={setTargetGroup}>
                  <SelectTrigger className="h-9 w-44"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {adgroups.map((g) => (
                      <SelectItem key={g.nccAdgroupId} value={g.nccAdgroupId}>{g.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="min-w-[200px] flex-1 space-y-1">
                <Label htmlFor="restricted" className="text-xs text-muted-foreground">
                  제외할 검색어 (쉼표로 구분)
                </Label>
                <Input
                  id="restricted"
                  value={newRestricted}
                  onChange={(e) => setNewRestricted(e.target.value)}
                  placeholder="이메일, 수리비, 5톤"
                  className="h-9"
                />
              </div>
              <Button size="sm" variant="outline" disabled={busy} onClick={() => void addRestricted()} className="gap-2">
                <Ban className="h-4 w-4" /> 제외 추가
              </Button>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base">등록된 제외 키워드</CardTitle>
              <CardDescription className="text-xs">
                확장검색 제외는 그 단어가 들어간 검색어 전체를 막습니다. 키워드확장 제외는 확장 매칭만 막습니다.
              </CardDescription>
            </CardHeader>
            <CardContent className="p-0">
              {restricted.length === 0 ? (
                <p className="px-6 pb-6 text-sm text-muted-foreground">등록된 제외 키워드가 없습니다.</p>
              ) : (
                <div className="divide-y">
                  {restricted.map((r) => (
                    <div key={r.id} className="flex items-center gap-3 px-6 py-2.5">
                      <span className="flex-1 text-sm">{r.keyword}</span>
                      <Badge variant="outline" className="text-[10px]">
                        {r.type === 'EXP_SEARCH' ? '확장검색 제외' : '키워드확장 제외'}
                      </Badge>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-7 w-7 text-muted-foreground hover:text-destructive"
                        disabled={busy}
                        onClick={() => void removeRestricted(r.id)}
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </Button>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
