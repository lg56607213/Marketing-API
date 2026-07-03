import { ImageIcon } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'

export function ImageLibraryPage() {
  return (
    <div className="space-y-5">
      <Badge variant="secondary">준비 중</Badge>
      <Card className="border-dashed">
        <CardContent className="flex flex-col items-center justify-center py-24 text-center">
          <ImageIcon className="h-14 w-14 text-muted-foreground/30 mb-4" />
          <p className="font-medium text-muted-foreground">이미지 라이브러리</p>
          <p className="text-sm text-muted-foreground/70 mt-2 max-w-xs">
            AI 생성 이미지와 업로드한 에셋이 여기에 표시됩니다. DALL-E 또는 Stable Diffusion 연동 시 이 기능을 활성화할 수 있습니다.
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
