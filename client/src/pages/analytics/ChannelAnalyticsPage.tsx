import { useState, useEffect } from 'react'
import { Plus, Trash2, Link2, RefreshCw, ExternalLink, CheckCircle2, XCircle } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { cn } from '@/lib/utils'

interface Channel {
  id: string
  url: string
  label: string
  type: 'website' | 'blog' | 'youtube' | 'instagram' | 'other'
  addedAt: string
}

const CHANNEL_TYPES: Record<Channel['type'], string> = {
  website: '웹사이트',
  blog: '블로그',
  youtube: '유튜브',
  instagram: '인스타그램',
  other: '기타',
}

const INTEGRATIONS = [
  { name: 'Google Analytics 4', desc: '트래픽·전환율·이벤트 추적', icon: '📊', connected: false },
  { name: 'Google Search Console', desc: 'SEO 성과·키워드 순위·노출수', icon: '🔍', connected: false },
  { name: '네이버 웹마스터 도구', desc: '네이버 검색 노출 및 순위', icon: '🇰🇷', connected: false },
  { name: 'Kakao 애널리틱스', desc: '카카오 채널·광고 성과', icon: '💛', connected: false },
]

const STORAGE_KEY = 'intel_channels'

function loadChannels(): Channel[] {
  try { return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]') } catch { return [] }
}
function saveChannels(data: Channel[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
}

export function ChannelAnalyticsPage() {
  const [channels, setChannels] = useState<Channel[]>([])
  const [open, setOpen] = useState(false)
  const [integrationModal, setIntegrationModal] = useState<string | null>(null)
  const [form, setForm] = useState({ url: '', label: '', type: 'website' as Channel['type'] })

  useEffect(() => { setChannels(loadChannels()) }, [])

  const handleAdd = () => {
    if (!form.url.trim() || !form.label.trim()) return
    const newCh: Channel = {
      id: Date.now().toString(),
      url: form.url.trim(),
      label: form.label.trim(),
      type: form.type,
      addedAt: new Date().toISOString().slice(0, 10),
    }
    const updated = [...channels, newCh]
    saveChannels(updated)
    setChannels(updated)
    setForm({ url: '', label: '', type: 'website' })
    setOpen(false)
  }

  const handleDelete = (id: string) => {
    const updated = channels.filter(c => c.id !== id)
    saveChannels(updated)
    setChannels(updated)
  }

  return (
    <div className="space-y-6">
      {/* URL 관리 */}
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          채널을 등록하고 성과 데이터를 한 곳에서 관리하세요.
        </p>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => setChannels(loadChannels())} className="gap-2">
            <RefreshCw className="h-4 w-4" /> 새로고침
          </Button>
          <Button size="sm" onClick={() => setOpen(true)} className="gap-2">
            <Plus className="h-4 w-4" /> 채널 추가
          </Button>
        </div>
      </div>

      {/* 채널 목록 + 성과 테이블 */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">내 채널 목록</CardTitle>
          <CardDescription className="text-xs">채널 URL 관리용입니다. 실제 광고 성과는 「검색광고 성과」 메뉴에서 확인하세요.</CardDescription>
        </CardHeader>
        <CardContent className="p-0">
          {channels.length === 0 ? (
            <div className="text-center py-14 text-muted-foreground">
              <Link2 className="h-10 w-10 mx-auto mb-3 opacity-30" />
              <p className="text-sm">등록된 채널이 없습니다.</p>
              <Button variant="outline" size="sm" className="mt-3 gap-1" onClick={() => setOpen(true)}>
                <Plus className="h-4 w-4" /> 첫 채널 등록
              </Button>
            </div>
          ) : (
            <div className="divide-y">
              <div className="grid grid-cols-12 gap-3 px-6 py-2.5 text-xs font-semibold text-muted-foreground uppercase tracking-wide bg-muted/40">
                <span className="col-span-3">채널</span>
                <span className="col-span-2">유형</span>
                <span className="col-span-2 text-right">노출수</span>
                <span className="col-span-1 text-right">클릭</span>
                <span className="col-span-1 text-right">CTR</span>
                <span className="col-span-2">상위 콘텐츠</span>
                <span className="col-span-1"></span>
              </div>
              {channels.map((ch, i) => {
                return (
                  <div key={ch.id} className="grid grid-cols-12 gap-3 px-6 py-3.5 items-center hover:bg-muted/20 transition-colors">
                    <div className="col-span-3 min-w-0">
                      <p className="text-sm font-medium truncate">{ch.label}</p>
                      <a
                        href={ch.url.startsWith('http') ? ch.url : `https://${ch.url}`}
                        target="_blank"
                        rel="noreferrer"
                        className="text-[10px] text-muted-foreground hover:text-indigo-500 flex items-center gap-0.5 truncate"
                      >
                        {ch.url} <ExternalLink className="h-2.5 w-2.5 shrink-0" />
                      </a>
                    </div>
                    <div className="col-span-2">
                      <Badge variant="outline" className="text-xs">{CHANNEL_TYPES[ch.type]}</Badge>
                    </div>
                    <div className="col-span-2 text-right">
                      <span className="text-sm text-muted-foreground">-</span>
                    </div>
                    <div className="col-span-1 text-right">
                      <span className="text-sm text-muted-foreground">-</span>
                    </div>
                    <div className="col-span-1 text-right">
                      <span className="text-sm text-muted-foreground">-</span>
                    </div>
                    <div className="col-span-2">
                      <p className="text-xs text-muted-foreground truncate">-</p>
                    </div>
                    <div className="col-span-1 flex justify-end">
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-7 w-7 text-muted-foreground hover:text-destructive"
                        onClick={() => handleDelete(ch.id)}
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </Button>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </CardContent>
      </Card>

      {/* 성과 요약 카드 */}
      {channels.length > 0 && (
        <div className="grid sm:grid-cols-4 gap-4">
          {[
            { label: '총 노출수', value: '-', unit: '' },
            { label: '총 클릭수', value: '-', unit: '' },
            { label: '평균 CTR', value: '-', unit: '' },
            { label: '평균 순위', value: '-', unit: '' },
          ].map(stat => (
            <Card key={stat.label}>
              <CardContent className="pt-5 pb-4">
                <p className="text-xs text-muted-foreground">{stat.label}</p>
                <p className="text-2xl font-bold mt-1">{stat.value}<span className="text-sm font-normal text-muted-foreground ml-1">{stat.unit}</span></p>
                <p className="text-[10px] text-muted-foreground mt-1">* 시뮬레이션 데이터</p>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* 연동 현황 */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">애널리틱스 연동</CardTitle>
          <CardDescription className="text-xs">연동 후 실시간 데이터로 전환됩니다</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid sm:grid-cols-2 gap-3">
            {INTEGRATIONS.map(ig => (
              <div
                key={ig.name}
                className="flex items-center gap-3 p-3.5 rounded-xl border hover:border-indigo-200 transition-colors"
              >
                <span className="text-2xl shrink-0">{ig.icon}</span>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium">{ig.name}</p>
                  <p className="text-xs text-muted-foreground">{ig.desc}</p>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  {ig.connected
                    ? <CheckCircle2 className="h-4 w-4 text-emerald-500" />
                    : <XCircle className="h-4 w-4 text-muted-foreground/40" />
                  }
                  <Button
                    variant="outline"
                    size="sm"
                    className="h-7 text-xs"
                    onClick={() => setIntegrationModal(ig.name)}
                  >
                    {ig.connected ? '설정' : '연동하기'}
                  </Button>
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

      {/* 채널 추가 다이얼로그 */}
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>채널 추가</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label>채널 이름 *</Label>
              <Input
                placeholder="예: 회사 공식 블로그"
                value={form.label}
                onChange={e => setForm({ ...form, label: e.target.value })}
              />
            </div>
            <div className="space-y-1.5">
              <Label>URL *</Label>
              <Input
                placeholder="예: blog.company.com"
                value={form.url}
                onChange={e => setForm({ ...form, url: e.target.value })}
              />
            </div>
            <div className="space-y-1.5">
              <Label>채널 유형</Label>
              <Select value={form.type} onValueChange={v => setForm({ ...form, type: v as Channel['type'] })}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {Object.entries(CHANNEL_TYPES).map(([v, l]) => (
                    <SelectItem key={v} value={v}>{l}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>취소</Button>
            <Button onClick={handleAdd} disabled={!form.url.trim() || !form.label.trim()}>추가</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 연동 안내 모달 */}
      <Dialog open={!!integrationModal} onOpenChange={() => setIntegrationModal(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{integrationModal} 연동 안내</DialogTitle>
          </DialogHeader>
          <div className="space-y-3 text-sm text-muted-foreground">
            <p>실제 연동을 위해서는 백엔드 OAuth 설정이 필요합니다.</p>
            <div className="bg-muted rounded-lg p-3 space-y-1 text-xs font-mono">
              <p># application.properties에 추가</p>
              <p>ga4.measurement-id=G-XXXXXXXXXX</p>
              <p>gsc.site-url=https://yoursite.com</p>
            </div>
            <p className="text-xs">설정 후 서버를 재시작하면 이 화면에서 데이터를 확인할 수 있습니다.</p>
          </div>
          <DialogFooter>
            <Button onClick={() => setIntegrationModal(null)}>확인</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
