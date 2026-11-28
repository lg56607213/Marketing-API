import { useEffect, useMemo, useRef, useState } from 'react'
import { ChevronDown, X } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

/**
 * 한국표준산업분류(KSIC 10차) 중 소상공인·중소기업이 많이 쓰는 세분류(5자리)와 중분류(2자리).
 * 자격 판정은 앞 2자리(예: 64~66 금융·보험 제외)만 쓰므로, 세부를 모르면 중분류만 골라도 된다.
 * 국세청 업종코드(6자리, 사업자등록증명)와는 다른 번호다.
 */
type Ksic = { code: string; name: string; words?: string }

const KSIC: Ksic[] = [
  { code: '76110', name: '자동차 임대업', words: '렌터카 렌트카 장기렌트 단기렌트 리스 차량대여' },
  { code: '45110', name: '자동차 신품 판매업', words: '신차 자동차판매 딜러' },
  { code: '45120', name: '중고 자동차 판매업', words: '중고차 매매상사' },
  { code: '49301', name: '일반 화물자동차 운송업', words: '화물 운송 트럭 물류' },
  { code: '49302', name: '용달 및 개별 화물자동차 운송업', words: '용달 개별화물 포터 봉고' },
  { code: '49401', name: '택배업', words: '택배 배송' },
  { code: '47912', name: '전자상거래 소매업', words: '온라인쇼핑몰 스마트스토어 쿠팡 인터넷판매 통신판매' },
  { code: '47911', name: '전자상거래 소매 중개업', words: '오픈마켓 플랫폼 중개' },
  { code: '56111', name: '한식 일반 음식점업', words: '식당 음식점 한식' },
  { code: '56221', name: '커피 전문점', words: '카페 커피' },
  { code: '56191', name: '제과점업', words: '빵집 베이커리 제과' },
  { code: '55101', name: '호텔업', words: '호텔 숙박' },
  { code: '58222', name: '응용 소프트웨어 개발 및 공급업', words: '앱개발 소프트웨어 SaaS 솔루션 IT' },
  { code: '62010', name: '컴퓨터 프로그래밍 서비스업', words: '개발 외주 프로그래밍 웹개발 홈페이지제작 IT' },
  { code: '63120', name: '포털 및 기타 인터넷 정보 매개 서비스업', words: '플랫폼 포털 커뮤니티 인터넷' },
  { code: '71310', name: '광고 대행업', words: '광고 마케팅 대행사 온라인광고 바이럴' },
  { code: '73203', name: '시각 디자인업', words: '디자인 그래픽 로고 편집디자인' },
  { code: '73201', name: '인테리어 디자인업', words: '인테리어' },
  { code: '68221', name: '부동산 자문 및 중개업', words: '부동산 공인중개사 중개' },
  { code: '85501', name: '일반 교과 학원', words: '학원 교육 과외' },
  { code: '86201', name: '일반 의원', words: '병원 의원 의료' },
  // 중분류(2자리): 세부 업종을 모를 때
  { code: '01', name: '농업 (세부 모름)', words: '농업 농장 재배' },
  { code: '10', name: '식료품 제조업 (세부 모름)', words: '식품 제조 가공' },
  { code: '14', name: '의복, 의복 액세서리 및 모피제품 제조업 (세부 모름)', words: '의류 봉제 패션 제조' },
  { code: '20', name: '화학 물질 및 화학제품 제조업 (세부 모름)', words: '화장품 화학 제조' },
  { code: '25', name: '금속 가공제품 제조업 (세부 모름)', words: '금속 가공 제조' },
  { code: '26', name: '전자 부품, 컴퓨터, 영상, 음향 및 통신장비 제조업 (세부 모름)', words: '전자 부품 제조' },
  { code: '29', name: '기타 기계 및 장비 제조업 (세부 모름)', words: '기계 장비 제조' },
  { code: '30', name: '자동차 및 트레일러 제조업 (세부 모름)', words: '자동차부품 제조' },
  { code: '41', name: '종합 건설업 (세부 모름)', words: '건설 시공' },
  { code: '42', name: '전문직별 공사업 (세부 모름)', words: '공사 전기공사 설비 인테리어공사' },
  { code: '45', name: '자동차 및 부품 판매업 (세부 모름)', words: '자동차 판매 부품' },
  { code: '46', name: '도매 및 상품 중개업 (세부 모름)', words: '도매 유통 무역 수출입' },
  { code: '47', name: '소매업; 자동차 제외 (세부 모름)', words: '소매 가게 매장 상점' },
  { code: '49', name: '육상 운송 및 파이프라인 운송업 (세부 모름)', words: '운송 운수 택시 버스' },
  { code: '52', name: '창고 및 운송관련 서비스업 (세부 모름)', words: '창고 물류 보관' },
  { code: '55', name: '숙박업 (세부 모름)', words: '숙박 펜션 모텔 게스트하우스' },
  { code: '56', name: '음식점 및 주점업 (세부 모름)', words: '음식점 식당 주점 술집' },
  { code: '58', name: '출판업 (세부 모름)', words: '출판 소프트웨어' },
  { code: '59', name: '영상·오디오 기록물 제작 및 배급업 (세부 모름)', words: '영상제작 유튜브 콘텐츠 방송' },
  { code: '62', name: '컴퓨터 프로그래밍, 시스템 통합 및 관리업 (세부 모름)', words: 'IT 개발 시스템' },
  { code: '63', name: '정보서비스업 (세부 모름)', words: '정보 데이터 인터넷' },
  { code: '64', name: '금융업 (세부 모름)', words: '금융 대부 투자' },
  { code: '65', name: '보험 및 연금업 (세부 모름)', words: '보험' },
  { code: '66', name: '금융 및 보험 관련 서비스업 (세부 모름)', words: '보험대리점 금융중개' },
  { code: '68', name: '부동산업 (세부 모름)', words: '부동산 임대 분양' },
  { code: '70', name: '연구개발업 (세부 모름)', words: '연구 R&D' },
  { code: '71', name: '전문 서비스업 (세부 모름)', words: '컨설팅 법무 회계 세무 광고' },
  { code: '72', name: '건축 기술, 엔지니어링 및 기타 과학기술 서비스업 (세부 모름)', words: '건축설계 엔지니어링' },
  { code: '73', name: '기타 전문, 과학 및 기술 서비스업 (세부 모름)', words: '디자인 사진' },
  { code: '75', name: '사업 지원 서비스업 (세부 모름)', words: '인력 파견 경비 콜센터 여행사' },
  { code: '76', name: '임대업; 부동산 제외 (세부 모름)', words: '렌탈 대여 임대' },
  { code: '85', name: '교육 서비스업 (세부 모름)', words: '교육 학원' },
  { code: '86', name: '보건업 (세부 모름)', words: '병원 의료' },
  { code: '87', name: '사회복지 서비스업 (세부 모름)', words: '복지 요양 어린이집' },
  { code: '90', name: '창작, 예술 및 여가관련 서비스업 (세부 모름)', words: '공연 예술 창작' },
  { code: '91', name: '스포츠 및 오락관련 서비스업 (세부 모름)', words: '헬스장 체육관 스포츠 PC방' },
  { code: '95', name: '개인 및 소비용품 수리업 (세부 모름)', words: '수리 정비 자동차정비' },
  { code: '96', name: '기타 개인 서비스업 (세부 모름)', words: '미용실 네일 피부관리 세탁' },
]

export function ksicName(code: string | null | undefined) {
  return KSIC.find((k) => k.code === code)?.name.replace(' (세부 모름)', '') ?? null
}

/** 업종 이름 · 흔한 말(렌터카, 카페 등)로 찾아서 고른다. 목록에 없으면 번호를 직접 넣어도 된다. */
export function KsicPicker({ value, onChange }: { value: string | null; onChange: (code: string | null, name: string | null) => void }) {
  const [open, setOpen] = useState(false)
  const [q, setQ] = useState('')
  const box = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const close = (e: MouseEvent) => { if (box.current && !box.current.contains(e.target as Node)) setOpen(false) }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [])

  const hits = useMemo(() => {
    const t = q.replace(/\s/g, '')
    if (!t) return KSIC
    return KSIC.filter((k) => (k.code + k.name + (k.words ?? '')).replace(/\s/g, '').includes(t))
  }, [q])

  const label = value ? `${value} ${ksicName(value) ?? ''}`.trim() : ''
  const typedCode = /^\d{2,5}$/.test(q.trim()) && !KSIC.some((k) => k.code === q.trim())

  return (
    <div ref={box} className="relative">
      <div className="relative">
        <Input
          value={open ? q : label}
          onFocus={() => { setQ(''); setOpen(true) }}
          onChange={(e) => { setQ(e.target.value); setOpen(true) }}
          placeholder="업종 검색 (예: 렌터카, 카페, 광고)"
          className="h-9 pr-14"
        />
        <div className="absolute inset-y-0 right-2 flex items-center gap-1 text-muted-foreground">
          {value && !open && (
            <button type="button" aria-label="지우기" onClick={() => onChange(null, null)} className="hover:text-foreground">
              <X className="h-3.5 w-3.5" />
            </button>
          )}
          <ChevronDown className="h-4 w-4" />
        </div>
      </div>
      {open && (
        <div className="absolute z-50 mt-1 max-h-72 w-full min-w-[18rem] overflow-y-auto rounded-md border bg-popover p-1 text-sm shadow-md">
          {typedCode && (
            <Option code={q.trim()} name="입력한 번호 그대로 쓰기" active={false}
              onPick={() => { onChange(q.trim(), null); setOpen(false) }} />
          )}
          {hits.length === 0 && !typedCode && (
            <p className="px-2 py-3 text-xs text-muted-foreground">맞는 업종이 없습니다. 비슷한 말로 찾거나 "(세부 모름)" 항목을 고르세요.</p>
          )}
          {hits.map((k) => (
            <Option key={k.code} code={k.code} name={k.name} active={k.code === value}
              onPick={() => { onChange(k.code, k.name.replace(' (세부 모름)', '')); setOpen(false) }} />
          ))}
        </div>
      )}
    </div>
  )
}

function Option({ code, name, active, onPick }: { code: string; name: string; active: boolean; onPick: () => void }) {
  return (
    <button type="button" onMouseDown={(e) => e.preventDefault()} onClick={onPick}
      className={cn('flex w-full items-baseline gap-2 rounded px-2 py-1.5 text-left hover:bg-accent', active && 'bg-accent font-medium')}>
      <span className="w-12 shrink-0 font-mono text-xs text-muted-foreground">{code}</span>
      <span>{name}</span>
    </button>
  )
}
