import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { RefreshCw, Wand2, Search } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { contentApi } from '@/services/api'
import type { ContentResponse } from '@/types'
import { CONTENT_TYPE_LABELS, STATUS_LABELS } from '@/types'
import { formatDateTime } from '@/lib/utils'

const statusVariant = (s: string): 'default' | 'success' | 'destructive' | 'secondary' | 'warning' => {
  if (s === 'PUBLISHED') return 'success'
  if (s === 'APPROVED') return 'default'
  if (s === 'REJECTED') return 'destructive'
  return 'secondary'
}

export function AIHistoryPage() {
  const [contents, setContents] = useState<ContentResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const navigate = useNavigate()

  const load = () => {
    setLoading(true)
    contentApi.list().then(setContents).catch(() => {}).finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const filtered = contents.filter(
    (c) =>
      c.topic.toLowerCase().includes(search.toLowerCase()) ||
      c.brandName.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <div className="relative w-64">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="주제 검색..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9"
          />
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={load} className="gap-2">
            <RefreshCw className="h-4 w-4" />
            새로고침
          </Button>
          <Button size="sm" onClick={() => navigate('/content/new')} className="gap-2">
            <Wand2 className="h-4 w-4" />
            새 콘텐츠 생성
          </Button>
        </div>
      </div>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">AI 생성 히스토리</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {loading ? (
            <div className="space-y-0 divide-y">
              {[...Array(5)].map((_, i) => (
                <div key={i} className="flex items-center gap-4 px-6 py-4">
                  <div className="h-4 w-48 bg-muted rounded animate-pulse" />
                  <div className="h-4 w-24 bg-muted rounded animate-pulse ml-auto" />
                </div>
              ))}
            </div>
          ) : filtered.length === 0 ? (
            <div className="text-center py-16 text-muted-foreground">
              <Wand2 className="h-10 w-10 mx-auto mb-3 opacity-30" />
              <p className="text-sm">아직 생성된 콘텐츠가 없습니다.</p>
              <Button variant="outline" size="sm" className="mt-3" onClick={() => navigate('/content/new')}>
                첫 콘텐츠 생성하기
              </Button>
            </div>
          ) : (
            <div className="divide-y">
              {/* 헤더 */}
              <div className="grid grid-cols-12 gap-4 px-6 py-2.5 text-xs font-semibold text-muted-foreground uppercase tracking-wide bg-muted/40">
                <span className="col-span-4">주제</span>
                <span className="col-span-2">브랜드</span>
                <span className="col-span-2">유형</span>
                <span className="col-span-2">상태</span>
                <span className="col-span-2">생성일</span>
              </div>
              {filtered.map((c) => (
                <div key={c.id} className="grid grid-cols-12 gap-4 px-6 py-3.5 hover:bg-muted/30 transition-colors items-center">
                  <div className="col-span-4 min-w-0">
                    <p className="text-sm font-medium truncate">{c.topic}</p>
                  </div>
                  <div className="col-span-2">
                    <p className="text-sm text-muted-foreground truncate">{c.brandName}</p>
                  </div>
                  <div className="col-span-2">
                    <Badge variant="outline" className="text-xs">{CONTENT_TYPE_LABELS[c.contentType]}</Badge>
                  </div>
                  <div className="col-span-2">
                    <Badge variant={statusVariant(c.status)}>{STATUS_LABELS[c.status]}</Badge>
                  </div>
                  <div className="col-span-2 text-xs text-muted-foreground">
                    {formatDateTime(c.createdAt)}
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
