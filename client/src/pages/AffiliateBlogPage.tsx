import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  AlertCircle, Check, ChevronDown, ChevronUp, Copy, Download, ExternalLink, ImagePlus, Loader2, Plus, RefreshCw,
  ShoppingBag, Trash2, X,
} from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { affiliateApi, apiMessage } from '@/services/api'
import type { AffiliatePost, AffiliatePostRequest, AffiliateStatus } from '@/types'
import { cn } from '@/lib/utils'

const MAX_ITEMS = 10
const MAX_PHOTOS = 10

/** 사용자가 올린 사진. 서버로 보내지 않고 이 브라우저에만 둔다. */
type MyPhoto = { file: File; url: string; caption: string }

type Row = {
  key: number
  productName: string
  linkUrl: string
  keyword: string
  productNotes: string
  review: string
  photos: MyPhoto[]
  state: 'idle' | 'writing' | 'done' | 'failed'
  error?: string
}

let seq = 1
const newRow = (): Row => ({
  key: seq++, productName: '', linkUrl: '', keyword: '', productNotes: '', review: '', photos: [], state: 'idle',
})

const toRequest = (r: Row): AffiliatePostRequest => ({
  productName: r.productName.trim(),
  linkUrl: r.linkUrl.trim(),
  keyword: r.keyword.trim() || null,
  productNotes: r.productNotes.trim() || null,
  review: r.review.trim() || null,
  photoCaptions: r.photos.map((p) => p.caption.trim()),
})

export function AffiliateBlogPage() {
  const [status, setStatus] = useState<AffiliateStatus | null>(null)
  const [rows, setRows] = useState<Row[]>([newRow()])
  const [posts, setPosts] = useState<AffiliatePost[]>([])
  /** 글 id → 그 글에 쓴 내 사진(새로고침하면 사라짐) */
  const [photosByPost, setPhotosByPost] = useState<Record<number, MyPhoto[]>>({})
  const [running, setRunning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [openId, setOpenId] = useState<number | null>(null)

  const load = useCallback(async () => {
    const [s, list] = await Promise.all([affiliateApi.status().catch(() => null), affiliateApi.list().catch(() => [])])
    setStatus(s)
    setPosts(list)
  }, [])
  useEffect(() => { void load() }, [load])

  const update = (key: number, patch: Partial<Row>) => setRows((rs) => rs.map((r) => (r.key === key ? { ...r, ...patch } : r)))

  const ready = rows.filter((r) => r.productName.trim() && r.linkUrl.trim() && r.state !== 'done')

  const runAll = async () => {
    setRunning(true)
    setError(null)
    const queue = [...ready]
    // 한 번에 2개씩 (AI 1건에 30초 안팎)
    const worker = async () => {
      for (let r = queue.shift(); r; r = queue.shift()) {
        const row = r
        update(row.key, { state: 'writing', error: undefined })
        try {
          const post = await affiliateApi.create(toRequest(row))
          setPhotosByPost((m) => ({ ...m, [post.id]: row.photos }))
          setPosts((ps) => [post, ...ps.filter((p) => p.id !== post.id)])
          if (post.status === 'DONE') {
            update(row.key, { state: 'done' })
            setOpenId((cur) => cur ?? post.id)
          } else {
            update(row.key, { state: 'failed', error: post.error ?? '글을 만들지 못했습니다.' })
          }
        } catch (e) {
          update(row.key, { state: 'failed', error: apiMessage(e, '글을 만들지 못했습니다.') })
        }
      }
    }
    await Promise.all([worker(), worker()])
    setRunning(false)
    affiliateApi.status().then(setStatus).catch(() => undefined)
  }

  const removePost = async (id: number) => {
    if (!window.confirm('이 글 초안을 지울까요?')) return
    await affiliateApi.remove(id)
    setPosts((ps) => ps.filter((p) => p.id !== id))
  }

  const regenerate = async (p: AffiliatePost) => {
    setPosts((ps) => ps.map((x) => (x.id === p.id ? { ...x, status: 'WRITING' } : x)))
    try {
      const next = await affiliateApi.rewrite(p.id)
      setPosts((ps) => ps.map((x) => (x.id === p.id ? next : x)))
    } catch (e) {
      setError(apiMessage(e, '다시 쓰지 못했습니다.'))
      void load()
    }
  }

  return (
    <div className="space-y-5">
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="flex items-center gap-2 text-base"><ShoppingBag className="h-4 w-4" /> 브랜드커넥트 상품 블로그 글</CardTitle>
          <CardDescription className="text-xs leading-relaxed">
            ① 브랜드커넥트에서 상품을 고르고 판매 링크를 복사 → ② 아래에 상품명 · 링크를 넣기 (최대 {MAX_ITEMS}개) →
            ③ "글 만들기" → ④ 글마다 <b>사진 내려받기</b> · <b>본문 복사</b> → 네이버 블로그에 붙여 넣고 사진을 [사진 n] 자리에 올린 뒤 발행.
            네이버는 블로그 자동 발행을 막아 두어서 마지막 발행은 직접 해야 합니다.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-2 text-xs">
          {status && !status.shopConfigured && (
            <p className="flex items-start gap-2 rounded-md border border-amber-400/40 bg-amber-50 px-3 py-2 text-amber-800">
              <AlertCircle className="mt-0.5 h-3.5 w-3.5 shrink-0" />
              네이버 쇼핑 검색 연결 전이라 가격 · 브랜드 · 대표 사진을 자동으로 확인하지 못합니다. 지금은 아래 "상품 설명"에 넣은 내용만으로 씁니다.
            </p>
          )}
          <p className="text-muted-foreground">
            AI 는 확인된 상품 정보와 넣어 주신 설명 · 후기에 있는 사실만 씁니다. 후기를 비우면 써 본 척하는 문장을 쓰지 않습니다.
            글 맨 위에는 광고 표시 문구(공정위 지침)가 자동으로 들어갑니다.
            {status && ` · 오늘 ${status.usedToday} / ${status.maxPerDay}개 사용`}
          </p>
        </CardContent>
      </Card>

      <div className="space-y-3">
        {rows.map((r, i) => (
          <RowForm key={r.key} index={i} row={r} disabled={running || r.state === 'writing'}
            onChange={(patch) => update(r.key, patch)}
            onRemove={rows.length > 1 ? () => setRows((rs) => rs.filter((x) => x.key !== r.key)) : undefined} />
        ))}
        <div className="flex flex-wrap items-center gap-2">
          <Button variant="outline" size="sm" className="gap-1" disabled={rows.length >= MAX_ITEMS || running}
            onClick={() => setRows((rs) => [...rs, newRow()])}>
            <Plus className="h-4 w-4" /> 상품 추가 ({rows.length}/{MAX_ITEMS})
          </Button>
          <Button size="sm" className="gap-1" disabled={running || ready.length === 0} onClick={() => void runAll()}>
            {running ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
            {running ? '글 쓰는 중 (상품당 30초 안팎)' : `글 ${ready.length}개 만들기`}
          </Button>
          {rows.some((r) => r.state === 'done') && !running && (
            <Button variant="ghost" size="sm" onClick={() => setRows([newRow()])}>입력 칸 비우기</Button>
          )}
        </div>
        {error && <p className="text-sm text-destructive">{error}</p>}
      </div>

      <div className="space-y-3">
        <h2 className="text-sm font-medium">만든 글 ({posts.length})</h2>
        {posts.length === 0 && (
          <p className="rounded-md border border-dashed px-4 py-10 text-center text-sm text-muted-foreground">아직 만든 글이 없습니다.</p>
        )}
        {posts.map((p) => (
          <PostCard key={p.id} post={p} photos={photosByPost[p.id] ?? []} open={openId === p.id}
            onToggle={() => setOpenId(openId === p.id ? null : p.id)}
            onRemove={() => void removePost(p.id)} onRegenerate={() => void regenerate(p)} />
        ))}
      </div>
    </div>
  )
}

function RowForm({ index, row, disabled, onChange, onRemove }: {
  index: number; row: Row; disabled: boolean; onChange: (p: Partial<Row>) => void; onRemove?: () => void
}) {
  const [more, setMore] = useState(false)

  const addPhotos = (files: FileList | null) => {
    if (!files) return
    const next = [...row.photos]
    for (const f of Array.from(files)) {
      if (!f.type.startsWith('image/') || next.length >= MAX_PHOTOS) continue
      next.push({ file: f, url: URL.createObjectURL(f), caption: '' })
    }
    onChange({ photos: next })
  }

  return (
    <Card className={cn(row.state === 'done' && 'border-emerald-500/40', row.state === 'failed' && 'border-destructive/40')}>
      <CardContent className="space-y-3 p-4">
        <div className="flex items-center gap-2">
          <span className="text-xs font-medium text-muted-foreground">상품 {index + 1}</span>
          {row.state === 'writing' && <Badge variant="outline" className="gap-1 text-[10px]"><Loader2 className="h-3 w-3 animate-spin" />쓰는 중</Badge>}
          {row.state === 'done' && <Badge variant="outline" className="border-emerald-500 text-[10px] text-emerald-700">완료 · 아래에서 확인</Badge>}
          {row.state === 'failed' && <Badge variant="outline" className="border-destructive text-[10px] text-destructive">실패</Badge>}
          {onRemove && (
            <Button variant="ghost" size="icon" className="ml-auto h-7 w-7" disabled={disabled} onClick={onRemove}><X className="h-3.5 w-3.5" /></Button>
          )}
        </div>
        {row.error && <p className="text-xs text-destructive">{row.error}</p>}
        <div className="grid gap-3 md:grid-cols-3">
          <F label="상품명 * (브랜드커넥트에 나온 이름)">
            <Input value={row.productName} disabled={disabled} onChange={(e) => onChange({ productName: e.target.value })} className="h-9" placeholder="스탠리 퀜처 텀블러 887ml" />
          </F>
          <F label="판매 링크 * (브랜드커넥트에서 복사)">
            <Input value={row.linkUrl} disabled={disabled} onChange={(e) => onChange({ linkUrl: e.target.value })} className="h-9" placeholder="https://naver.me/..." />
          </F>
          <F label="노릴 검색어 (선택)">
            <Input value={row.keyword} disabled={disabled} onChange={(e) => onChange({ keyword: e.target.value })} className="h-9" placeholder="대용량 텀블러 추천" />
          </F>
        </div>

        <button type="button" className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground" onClick={() => setMore(!more)}>
          {more ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
          상품 설명 · 내 후기 · 내 사진 {row.productNotes || row.review || row.photos.length ? '(입력함)' : '(넣을수록 글이 정확하고 좋아집니다)'}
        </button>
        {more && (
          <div className="grid gap-3 md:grid-cols-2">
            <F label="상품 설명 (상세페이지의 특징 · 구성 · 스펙을 복사해 붙여 넣기)">
              <Textarea rows={5} value={row.productNotes} disabled={disabled} onChange={(e) => onChange({ productNotes: e.target.value })}
                placeholder="용량 887ml, 18/8 스테인리스, 보냉 11시간 ..." />
            </F>
            <F label="내 후기 (직접 써 봤다면. 비우면 체험담 없이 정보 위주로 씁니다)">
              <Textarea rows={5} value={row.review} disabled={disabled} onChange={(e) => onChange({ review: e.target.value })}
                placeholder="2주 써 보니 얼음이 저녁까지 남았고, 손잡이가 있어 들고 다니기 편했음 ..." />
            </F>
            <div className="space-y-2 md:col-span-2">
              <Label className="text-xs text-muted-foreground">내가 찍은 사진 (최대 {MAX_PHOTOS}장 · 서버에 올리지 않고 이 브라우저에만 둡니다)</Label>
              <div className="flex flex-wrap gap-3">
                {row.photos.map((ph, i) => (
                  <div key={ph.url} className="w-32 space-y-1">
                    <div className="relative">
                      <img src={ph.url} alt="" className="h-24 w-32 rounded border object-cover" />
                      <button type="button" disabled={disabled} className="absolute right-1 top-1 rounded bg-black/60 p-0.5 text-white"
                        onClick={() => onChange({ photos: row.photos.filter((_, j) => j !== i) })}><X className="h-3 w-3" /></button>
                    </div>
                    <Input value={ph.caption} disabled={disabled} className="h-7 text-xs" placeholder="무슨 사진? (예: 박스 구성)"
                      onChange={(e) => onChange({ photos: row.photos.map((x, j) => (j === i ? { ...x, caption: e.target.value } : x)) })} />
                  </div>
                ))}
                {row.photos.length < MAX_PHOTOS && (
                  <label className={cn('flex h-24 w-32 cursor-pointer flex-col items-center justify-center gap-1 rounded border border-dashed text-xs text-muted-foreground hover:bg-accent', disabled && 'pointer-events-none opacity-50')}>
                    <ImagePlus className="h-5 w-5" /> 사진 추가
                    <input type="file" accept="image/*" multiple className="hidden" onChange={(e) => { addPhotos(e.target.files); e.target.value = '' }} />
                  </label>
                )}
              </div>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

/** 글에 실제로 들어가는 사진을 나오는 순서대로 번호 매긴다. [사진 1] = 내려받은 01 파일. */
type Placed = { no: number; id: string; src: string | null; product: boolean; caption: string }

function usePlaced(post: AffiliatePost, photos: MyPhoto[]): Placed[] {
  return useMemo(() => {
    const out: Placed[] = []
    post.draft?.sections.forEach((s) => {
      if (!s.photo) return
      if (s.photo === 'P1') {
        out.push({ no: out.length + 1, id: 'P1', src: post.product?.image ?? null, product: true, caption: '상품 대표 사진' })
      } else {
        const i = Number(s.photo.slice(1)) - 1
        out.push({ no: out.length + 1, id: s.photo, src: photos[i]?.url ?? null, product: false, caption: post.photoCaptions[i] || `내 사진 ${i + 1}` })
      }
    })
    return out
  }, [post, photos])
}

function PostCard({ post, photos, open, onToggle, onRemove, onRegenerate }: {
  post: AffiliatePost; photos: MyPhoto[]; open: boolean; onToggle: () => void; onRemove: () => void; onRegenerate: () => void
}) {
  const placed = usePlaced(post, photos)
  const [copied, setCopied] = useState<string | null>(null)
  const d = post.draft
  const flash = (k: string) => { setCopied(k); window.setTimeout(() => setCopied(null), 1800) }
  const photoOf = (id: string | null) => placed.find((p) => p.id === id)

  const copyText = async (k: string, text: string) => { await navigator.clipboard.writeText(text); flash(k) }

  const plain = () => {
    if (!d) return ''
    const parts = [post.disclosure, '']
    d.sections.forEach((s, i) => {
      if (s.heading) parts.push(`■ ${s.heading}`)
      const ph = photoOf(s.photo)
      if (ph) parts.push(`[사진 ${ph.no}]`)
      parts.push(s.body, '')
      if (i === 1) parts.push(`▶ 상품 자세히 보기: ${post.linkUrl}`, '')
    })
    parts.push(`▶ 구매하러 가기: ${post.linkUrl}`, '', d.tags.map((t) => `#${t}`).join(' '))
    return parts.join('\n')
  }

  const copyRich = async () => {
    if (!d) return
    const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    const para = (s: string) => s.split(/\n\s*\n/).map((x) => `<p>${esc(x).replace(/\n/g, '<br>')}</p>`).join('')
    const link = (label: string) => `<p><b>▶ ${label}</b><br><a href="${esc(post.linkUrl)}">${esc(post.linkUrl)}</a></p>`
    const html = [`<p><b>${esc(post.disclosure)}</b></p>`]
    d.sections.forEach((s, i) => {
      if (s.heading) html.push(`<h3>${esc(s.heading)}</h3>`)
      const ph = photoOf(s.photo)
      if (ph) html.push(`<p>[사진 ${ph.no}]</p>`)
      html.push(para(s.body))
      if (i === 1) html.push(link('상품 자세히 보기'))
    })
    html.push(link('구매하러 가기'), `<p>${esc(d.tags.map((t) => `#${t}`).join(' '))}</p>`)
    try {
      await navigator.clipboard.write([new ClipboardItem({
        'text/html': new Blob([html.join('')], { type: 'text/html' }),
        'text/plain': new Blob([plain()], { type: 'text/plain' }),
      })])
    } catch {
      await navigator.clipboard.writeText(plain())
    }
    flash('body')
  }

  const download = async () => {
    for (const ph of placed) {
      let blob: Blob | null = null
      if (ph.product && ph.src) blob = await affiliateApi.image(ph.src).catch(() => null)
      else if (!ph.product && ph.src) blob = await fetch(ph.src).then((r) => r.blob()).catch(() => null)
      if (!blob) continue
      const ext = blob.type.split('/')[1]?.replace('jpeg', 'jpg') || 'jpg'
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = `${String(ph.no).padStart(2, '0')}_${post.productName.slice(0, 20)}.${ext}`
      a.click()
      URL.revokeObjectURL(a.href)
      await new Promise((r) => window.setTimeout(r, 300))
    }
    flash('photos')
  }

  const missingMine = placed.some((p) => !p.product && !p.src)

  return (
    <Card>
      <CardContent className="space-y-3 p-4">
        <div className="flex flex-wrap items-start gap-2">
          <button type="button" className="min-w-0 flex-1 text-left" onClick={onToggle}>
            <div className="flex flex-wrap items-center gap-1.5">
              <span className="font-medium">{d?.title ?? post.productName}</span>
              {post.status === 'WRITING' && <Badge variant="outline" className="gap-1 text-[10px]"><Loader2 className="h-3 w-3 animate-spin" />쓰는 중</Badge>}
              {post.status === 'FAILED' && <Badge variant="outline" className="border-destructive text-[10px] text-destructive">실패</Badge>}
              {post.status === 'DONE' && (post.product
                ? <Badge variant="outline" className="border-emerald-500 text-[10px] text-emerald-700">네이버 쇼핑 정보 확인됨</Badge>
                : <Badge variant="outline" className="border-amber-500 text-[10px] text-amber-700">입력한 설명만으로 씀</Badge>)}
            </div>
            <p className="text-xs text-muted-foreground">{post.productName} · {post.createdAt?.slice(0, 16).replace('T', ' ')}</p>
          </button>
          <div className="flex gap-1">
            <Button variant="ghost" size="icon" className="h-7 w-7" title="다시 쓰기" disabled={post.status === 'WRITING'} onClick={onRegenerate}><RefreshCw className="h-3.5 w-3.5" /></Button>
            <Button variant="ghost" size="icon" className="h-7 w-7 text-muted-foreground hover:text-destructive" title="지우기" onClick={onRemove}><Trash2 className="h-3.5 w-3.5" /></Button>
            <Button variant="ghost" size="icon" className="h-7 w-7" onClick={onToggle}>{open ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}</Button>
          </div>
        </div>
        {post.error && <p className="text-xs text-destructive">{post.error}</p>}

        {open && d && (
          <div className="space-y-4">
            <div className="flex flex-wrap gap-2">
              <Button size="sm" variant="outline" className="gap-1" onClick={() => void copyText('title', d.title)}>
                {copied === 'title' ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />} 제목 복사
              </Button>
              <Button size="sm" className="gap-1" onClick={() => void copyRich()}>
                {copied === 'body' ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />} 본문 복사
              </Button>
              <Button size="sm" variant="outline" className="gap-1" disabled={placed.length === 0} onClick={() => void download()}>
                {copied === 'photos' ? <Check className="h-3.5 w-3.5" /> : <Download className="h-3.5 w-3.5" />} 사진 내려받기 ({placed.length}장)
              </Button>
              <Button size="sm" variant="outline" className="gap-1" onClick={() => void copyText('tags', d.tags.join(','))}>
                {copied === 'tags' ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />} 태그 복사
              </Button>
              <a href={post.linkUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 px-2 text-xs text-muted-foreground hover:text-foreground">
                판매 링크 <ExternalLink className="h-3 w-3" />
              </a>
            </div>
            <p className="text-xs text-muted-foreground">
              붙여 넣은 뒤 [사진 n] 자리에 내려받은 n번 파일을 올리고 그 줄을 지우세요.
              {missingMine && ' 내 사진은 이 브라우저에만 있어서, 새로고침 뒤에는 내려받기에서 빠집니다(직접 올려 주세요).'}
            </p>

            {post.product && (
              <div className="flex gap-3 rounded-md border bg-muted/30 p-3 text-xs">
                {post.product.image && <img src={post.product.image} alt="" className="h-16 w-16 rounded object-cover" />}
                <div className="space-y-0.5">
                  <p className="font-medium">{post.product.title}</p>
                  <p className="text-muted-foreground">
                    {[post.product.lowPrice && `최저 ${post.product.lowPrice.toLocaleString()}원`, post.product.mallName, post.product.brand, post.product.category].filter(Boolean).join(' · ')}
                  </p>
                  <p className="text-muted-foreground">네이버 쇼핑 검색에서 확인한 정보입니다. 브랜드커넥트 상품과 같은지 한 번 확인해 주세요.</p>
                </div>
              </div>
            )}

            <article className="space-y-4 rounded-md border p-4 text-sm leading-relaxed">
              <p className="rounded bg-muted px-3 py-2 text-xs font-medium">{post.disclosure}</p>
              <h3 className="text-lg font-semibold">{d.title}</h3>
              {d.sections.map((s, i) => {
                const ph = photoOf(s.photo)
                return (
                  <section key={i} className="space-y-2">
                    {s.heading && <h4 className="font-semibold">■ {s.heading}</h4>}
                    {ph && (
                      <figure className="space-y-1">
                        {ph.src
                          ? <img src={ph.src} alt="" className="max-h-72 rounded border object-contain" />
                          : <div className="flex h-24 items-center justify-center rounded border border-dashed text-xs text-muted-foreground">내 사진 자리 (다시 첨부 필요)</div>}
                        <figcaption className="text-xs text-muted-foreground">[사진 {ph.no}] {ph.caption}</figcaption>
                      </figure>
                    )}
                    {s.body.split(/\n\s*\n/).map((x, j) => <p key={j} className="whitespace-pre-line">{x}</p>)}
                    {i === 1 && <p className="text-xs font-medium">▶ 상품 자세히 보기: <span className="break-all text-muted-foreground">{post.linkUrl}</span></p>}
                  </section>
                )
              })}
              <p className="text-xs font-medium">▶ 구매하러 가기: <span className="break-all text-muted-foreground">{post.linkUrl}</span></p>
              <p className="text-xs text-indigo-700">{d.tags.map((t) => `#${t}`).join(' ')}</p>
            </article>
          </div>
        )}
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
