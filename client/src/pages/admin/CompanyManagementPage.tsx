import { useEffect, useState } from 'react'
import { Plus, Building2, Loader2, RefreshCw, Pencil, PowerOff } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { adminApi } from '@/services/api'
import type { CompanyMeta } from '@/types'
import { toApiEmail, toDisplayId } from '@/types'
import { formatDate } from '@/lib/utils'

const STORAGE_KEY = 'company_meta'

function loadMeta(): CompanyMeta[] {
  try { return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]') } catch { return [] }
}
function saveMeta(data: CompanyMeta[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
}

const planColors: Record<string, 'default' | 'secondary' | 'success'> = {
  STARTER: 'secondary',
  PRO: 'default',
  ENTERPRISE: 'success',
}

const PLAN_LABELS: Record<string, string> = {
  STARTER: '스타터',
  PRO: '프로',
  ENTERPRISE: '엔터프라이즈',
}

export function CompanyManagementPage() {
  const [companies, setCompanies] = useState<CompanyMeta[]>([])
  const [open, setOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<CompanyMeta | null>(null)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState({ loginId: '', companyName: '', password: '', plan: 'PRO' as CompanyMeta['plan'], expiresAt: '' })

  const load = () => setCompanies(loadMeta())

  useEffect(() => { load() }, [])

  const openCreate = () => {
    setEditTarget(null)
    setForm({ loginId: '', companyName: '', password: '', plan: 'PRO', expiresAt: '' })
    setOpen(true)
  }

  const openEdit = (c: CompanyMeta) => {
    setEditTarget(c)
    setForm({ loginId: c.loginId, companyName: c.companyName, password: '', plan: c.plan, expiresAt: c.expiresAt })
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.loginId.trim() || !form.companyName.trim()) return
    setSaving(true)
    try {
      let current = loadMeta()
      if (editTarget) {
        current = current.map((c) =>
          c.loginId === editTarget.loginId
            ? { ...c, companyName: form.companyName, plan: form.plan, expiresAt: form.expiresAt }
            : c
        )
      } else {
        await adminApi.createUser(toApiEmail(form.loginId), form.password || 'Temp1234!')
        const meta: CompanyMeta = {
          loginId: form.loginId,
          companyName: form.companyName,
          plan: form.plan,
          expiresAt: form.expiresAt,
          status: 'ACTIVE',
          createdAt: new Date().toISOString(),
        }
        current = [...current, meta]
      }
      saveMeta(current)
      setCompanies(current)
      setOpen(false)
    } catch {
    } finally {
      setSaving(false)
    }
  }

  const toggleStatus = (loginId: string) => {
    const updated = loadMeta().map((c) =>
      c.loginId === loginId ? { ...c, status: c.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE' } as CompanyMeta : c
    )
    saveMeta(updated)
    setCompanies(updated)
  }

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">총 {companies.length}개 기업</p>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={load} className="gap-2">
            <RefreshCw className="h-4 w-4" />
            새로고침
          </Button>
          <Button size="sm" onClick={openCreate} className="gap-2">
            <Plus className="h-4 w-4" />
            기업 추가
          </Button>
        </div>
      </div>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">기업 계정 목록</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {companies.length === 0 ? (
            <div className="text-center py-16 text-muted-foreground">
              <Building2 className="h-10 w-10 mx-auto mb-3 opacity-30" />
              <p className="text-sm">등록된 기업이 없습니다. 첫 번째 계정을 생성하세요.</p>
            </div>
          ) : (
            <div className="divide-y">
              <div className="grid grid-cols-12 gap-3 px-6 py-2.5 text-xs font-semibold text-muted-foreground uppercase tracking-wide bg-muted/40">
                <span className="col-span-3">기업명</span>
                <span className="col-span-2">로그인 ID</span>
                <span className="col-span-2">플랜</span>
                <span className="col-span-2">만료일</span>
                <span className="col-span-2">상태</span>
                <span className="col-span-1"></span>
              </div>
              {companies.map((c) => (
                <div key={c.loginId} className="grid grid-cols-12 gap-3 px-6 py-3.5 items-center hover:bg-muted/20 transition-colors">
                  <div className="col-span-3 font-medium text-sm">{c.companyName}</div>
                  <div className="col-span-2 text-sm text-muted-foreground font-mono">{c.loginId}</div>
                  <div className="col-span-2">
                    <Badge variant={planColors[c.plan]}>{PLAN_LABELS[c.plan] ?? c.plan}</Badge>
                  </div>
                  <div className="col-span-2 text-sm text-muted-foreground">{c.expiresAt ? formatDate(c.expiresAt) : '—'}</div>
                  <div className="col-span-2">
                    <Badge variant={c.status === 'ACTIVE' ? 'success' : 'destructive'}>
                      {c.status === 'ACTIVE' ? '활성' : '정지'}
                    </Badge>
                  </div>
                  <div className="col-span-1 flex gap-1 justify-end">
                    <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => openEdit(c)}>
                      <Pencil className="h-3.5 w-3.5" />
                    </Button>
                    <Button variant="ghost" size="icon" className="h-7 w-7 text-muted-foreground hover:text-destructive" onClick={() => toggleStatus(c.loginId)}>
                      <PowerOff className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editTarget ? '기업 정보 수정' : '기업 추가'}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <Label>로그인 ID *</Label>
                <Input
                  placeholder="예: acme-corp"
                  value={form.loginId}
                  onChange={(e) => setForm({ ...form, loginId: e.target.value })}
                  disabled={!!editTarget}
                />
                {!editTarget && <p className="text-[10px] text-muted-foreground">로그인 ID: {form.loginId || '<id>'}</p>}
              </div>
              <div className="space-y-1.5">
                <Label>기업명 *</Label>
                <Input placeholder="아크메 코프" value={form.companyName} onChange={(e) => setForm({ ...form, companyName: e.target.value })} />
              </div>
            </div>
            {!editTarget && (
              <div className="space-y-1.5">
                <Label>초기 비밀번호</Label>
                <Input type="password" placeholder="비워두면 Temp1234! 사용" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
              </div>
            )}
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <Label>구독 플랜</Label>
                <Select value={form.plan} onValueChange={(v) => setForm({ ...form, plan: v as CompanyMeta['plan'] })}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="STARTER">스타터</SelectItem>
                    <SelectItem value="PRO">프로</SelectItem>
                    <SelectItem value="ENTERPRISE">엔터프라이즈</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <Label>만료일</Label>
                <Input type="date" value={form.expiresAt} onChange={(e) => setForm({ ...form, expiresAt: e.target.value })} />
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>취소</Button>
            <Button onClick={handleSave} disabled={saving || !form.loginId.trim() || !form.companyName.trim()}>
              {saving && <Loader2 className="h-4 w-4 animate-spin" />}
              {editTarget ? '저장' : '계정 생성'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
