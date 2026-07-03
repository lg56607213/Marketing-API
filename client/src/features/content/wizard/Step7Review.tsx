import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Copy, Download, Printer, CheckCircle2, ThumbsUp, ThumbsDown } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { contentApi } from '@/services/api'
import type { ContentResponse } from '@/types'
import { CONTENT_TYPE_LABELS, STATUS_LABELS } from '@/types'
import { formatDateTime } from '@/lib/utils'

interface Props {
  content: ContentResponse
}

export function Step7Review({ content: initial }: Props) {
  const [content, setContent] = useState(initial)
  const [copied, setCopied] = useState(false)
  const navigate = useNavigate()

  const handleCopy = () => {
    navigator.clipboard.writeText(content.body)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const handleDownload = () => {
    const blob = new Blob([content.body], { type: 'text/plain' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${content.topic.slice(0, 30)}.txt`
    a.click()
    URL.revokeObjectURL(url)
  }

  const handlePrint = () => {
    const w = window.open('', '_blank')
    if (!w) return
    w.document.write(`<pre style="font-family:sans-serif;white-space:pre-wrap">${content.body}</pre>`)
    w.document.close()
    w.print()
  }

  const handleApprove = async () => {
    const updated = await contentApi.approve(content.id)
    setContent(updated)
  }

  const handleReject = async () => {
    const updated = await contentApi.reject(content.id)
    setContent(updated)
  }

  return (
    <div className="space-y-5">
      {/* 메타 정보 */}
      <div className="flex items-center gap-3 flex-wrap">
        <Badge variant="default">{CONTENT_TYPE_LABELS[content.contentType]}</Badge>
        <Badge variant={content.status === 'APPROVED' ? 'success' : content.status === 'REJECTED' ? 'destructive' : 'secondary'}>
          {STATUS_LABELS[content.status]}
        </Badge>
        <span className="text-xs text-muted-foreground">{formatDateTime(content.createdAt)}</span>
      </div>

      {/* 콘텐츠 본문 */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-sm text-muted-foreground font-normal flex items-center justify-between">
            생성된 콘텐츠
            <div className="flex gap-1.5">
              <Button variant="outline" size="sm" className="h-7 gap-1.5 text-xs" onClick={handleCopy}>
                {copied ? <CheckCircle2 className="h-3.5 w-3.5 text-emerald-500" /> : <Copy className="h-3.5 w-3.5" />}
                {copied ? '복사됨!' : '복사'}
              </Button>
              <Button variant="outline" size="sm" className="h-7 gap-1.5 text-xs" onClick={handleDownload}>
                <Download className="h-3.5 w-3.5" />
                다운로드
              </Button>
              <Button variant="outline" size="sm" className="h-7 gap-1.5 text-xs" onClick={handlePrint}>
                <Printer className="h-3.5 w-3.5" />
                인쇄
              </Button>
            </div>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <pre className="whitespace-pre-wrap text-sm leading-relaxed font-sans">{content.body}</pre>
        </CardContent>
      </Card>

      {/* 승인/반려 액션 */}
      {content.status === 'DRAFT' && (
        <div className="flex gap-3">
          <Button onClick={handleApprove} className="gap-2">
            <ThumbsUp className="h-4 w-4" />
            승인
          </Button>
          <Button variant="outline" onClick={handleReject} className="gap-2">
            <ThumbsDown className="h-4 w-4" />
            반려
          </Button>
        </div>
      )}

      <div className="flex gap-3 pt-2">
        <Button variant="outline" onClick={() => navigate('/history')}>AI 히스토리 보기</Button>
        <Button variant="ghost" onClick={() => navigate('/content/new')}>새 콘텐츠 생성</Button>
      </div>
    </div>
  )
}
