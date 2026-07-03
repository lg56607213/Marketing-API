import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus, Folder, Wand2, Loader2 } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription, CardFooter } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter as DFooter } from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import { brandApi } from '@/services/api'
import type { BrandResponse, BrandRequest } from '@/types'
import { formatDate } from '@/lib/utils'

export function ProjectsPage() {
  const [brands, setBrands] = useState<BrandResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState<BrandRequest>({ name: '', description: '', toneAndManner: '' })
  const navigate = useNavigate()

  const load = () => {
    brandApi.list().then(setBrands).catch(() => {}).finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const handleCreate = async () => {
    if (!form.name.trim()) return
    setSaving(true)
    try {
      await brandApi.create(form)
      setOpen(false)
      setForm({ name: '', description: '', toneAndManner: '' })
      load()
    } catch {
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">총 {brands.length}개의 브랜드 프로젝트</p>
        <Button onClick={() => setOpen(true)} className="gap-2" size="sm">
          <Plus className="h-4 w-4" />
          새 프로젝트
        </Button>
      </div>

      {loading ? (
        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {[...Array(3)].map((_, i) => (
            <div key={i} className="h-40 bg-muted rounded-xl animate-pulse" />
          ))}
        </div>
      ) : brands.length === 0 ? (
        <Card className="border-dashed">
          <CardContent className="flex flex-col items-center justify-center py-20 text-center">
            <Folder className="h-12 w-12 text-muted-foreground/30 mb-3" />
            <p className="font-medium">프로젝트가 없습니다</p>
            <p className="text-sm text-muted-foreground mt-1">브랜드 프로젝트를 생성하여 콘텐츠 제작을 시작하세요.</p>
            <Button className="mt-4 gap-2" onClick={() => setOpen(true)}>
              <Plus className="h-4 w-4" /> 프로젝트 생성
            </Button>
          </CardContent>
        </Card>
      ) : (
        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {brands.map((b) => (
            <Card key={b.id} className="hover:shadow-md transition-shadow">
              <CardHeader className="pb-2">
                <div className="flex items-start justify-between">
                  <div className="h-9 w-9 rounded-lg bg-indigo-100 flex items-center justify-center">
                    <Folder className="h-5 w-5 text-indigo-600" />
                  </div>
                  <Badge variant="success">활성</Badge>
                </div>
                <CardTitle className="text-base mt-2">{b.name}</CardTitle>
                <CardDescription className="text-xs line-clamp-2">
                  {b.description ?? b.toneAndManner ?? '설명 없음'}
                </CardDescription>
              </CardHeader>
              <CardFooter className="flex items-center justify-between pt-0">
                <span className="text-xs text-muted-foreground">생성일 {formatDate(b.createdAt)}</span>
                <Button variant="outline" size="sm" className="gap-1.5 text-xs h-7"
                  onClick={() => navigate('/content/new')}>
                  <Wand2 className="h-3 w-3" />
                  콘텐츠 생성
                </Button>
              </CardFooter>
            </Card>
          ))}
        </div>
      )}

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>새 브랜드 프로젝트</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label>브랜드 이름 *</Label>
              <Input placeholder="예: 아크메 파이낸셜" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </div>
            <div className="space-y-1.5">
              <Label>설명</Label>
              <Textarea placeholder="이 브랜드를 간단히 설명해 주세요..." rows={2} value={form.description ?? ''} onChange={(e) => setForm({ ...form, description: e.target.value })} className="resize-none" />
            </div>
            <div className="space-y-1.5">
              <Label>톤 & 매너</Label>
              <Input placeholder="예: 전문적, 친근한, 신뢰감 있는" value={form.toneAndManner ?? ''} onChange={(e) => setForm({ ...form, toneAndManner: e.target.value })} />
            </div>
          </div>
          <DFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>취소</Button>
            <Button onClick={handleCreate} disabled={saving || !form.name.trim()}>
              {saving && <Loader2 className="h-4 w-4 animate-spin" />}
              생성
            </Button>
          </DFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
