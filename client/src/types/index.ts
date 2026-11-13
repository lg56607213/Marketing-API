export interface ApiResponse<T> {
  success: boolean
  data: T
  message: string | null
  timestamp: string
}

export interface AuthResponse {
  token: string
  email: string
  role: 'USER' | 'ADMIN'
}

export interface UserSummary {
  id: number | null
  email: string
  role: string
  createdAt: string | null
}

export interface BrandResponse {
  id: number
  name: string
  description: string | null
  toneAndManner: string | null
  cta: string | null
  forbiddenWords: string | null
  allowedWords: string | null
  seoRules: string | null
  ownerId: number
  createdAt: string
  updatedAt: string
}

export interface BrandRequest {
  name: string
  description?: string
  toneAndManner?: string
  cta?: string
  forbiddenWords?: string
  allowedWords?: string
  seoRules?: string
}

export type ContentType = 'BLOG' | 'CARDNEWS' | 'LANDING_PAGE' | 'SNS' | 'EMAIL' | 'YOUTUBE'
export type ContentStatus = 'DRAFT' | 'APPROVED' | 'REJECTED' | 'PUBLISHED'

export interface ContentResponse {
  id: number
  brandId: number
  brandName: string
  contentType: ContentType
  status: ContentStatus
  title: string | null
  topic: string
  body: string
  aiModel: string | null
  authorId: number
  createdAt: string
  updatedAt: string
}

export interface GenerateRequest {
  brandId: number
  contentType: ContentType
  topic: string
  title?: string
  aiModel?: string
}

// Company metadata stored locally (Phase 1)
export interface CompanyMeta {
  loginId: string
  companyName: string
  plan: 'STARTER' | 'PRO' | 'ENTERPRISE'
  expiresAt: string
  status: 'ACTIVE' | 'SUSPENDED'
  createdAt: string
}

export const CONTENT_TYPE_LABELS: Record<ContentType, string> = {
  BLOG: '블로그 포스트',
  CARDNEWS: '카드뉴스',
  SNS: 'SNS 게시물',
  YOUTUBE: '유튜브 스크립트',
  EMAIL: '이메일',
  LANDING_PAGE: '랜딩 페이지',
}

export const STATUS_LABELS: Record<ContentStatus, string> = {
  DRAFT: '초안',
  APPROVED: '승인됨',
  REJECTED: '반려됨',
  PUBLISHED: '게시됨',
}

export const EMAIL_DOMAIN = '@marketing.local'
export const toApiEmail = (loginId: string) =>
  loginId.includes('@') ? loginId : `${loginId}${EMAIL_DOMAIN}`
export const toDisplayId = (email: string) =>
  email.replace(EMAIL_DOMAIN, '')

export interface DailyPoint {
  statDate: string
  impCnt: number
  clkCnt: number
  salesAmt: number
  ccnt: number
}

export interface AdSummary {
  since: string
  until: string
  impCnt: number
  clkCnt: number
  ctr: number
  cpc: number
  salesAmt: number
  ccnt: number
  cvr: number
  cpa: number
  keywordCount: number
  daily: DailyPoint[]
}

export interface KeywordPerformance {
  nccKeywordId: string
  keyword: string
  adgroupName: string
  status: string | null
  bidAmt: number | null
  impCnt: number
  clkCnt: number
  ctr: number
  cpc: number
  salesAmt: number
  ccnt: number
  cvr: number
  avgRnk: number
}

export interface AdSyncResult {
  provider: string
  campaigns: number
  adgroups: number
  keywords: number
  statRows: number
  since: string
  until: string
}

export interface BidRecommendation {
  id: number
  nccKeywordId: string
  keyword: string
  currentBid: number
  recommendedBid: number
  changeAmount: number
  changeRate: number
  direction: 'UP' | 'DOWN'
  reason: string
  since: string
  until: string
  impCnt: number
  clkCnt: number
  salesAmt: number
  ccnt: number
  ctr: number
  avgRnk: number
  status: string
  appliedBid: number | null
  decidedAt: string | null
  resultMessage: string | null
}

export interface BidApplyResult {
  id: number
  keyword: string | null
  status: string
  currentBid: number | null
  appliedBid: number | null
  message: string | null
}

export interface AdReport {
  id: number
  title: string
  body: string
  since: string
  until: string
  generatedBy: string
  createdAt: string
}

export interface AdHealth {
  provider: string
  credentialsSet: boolean
  reachable: boolean
  campaignCount: number
  message: string
}

/** 회원별 네이버 검색광고 계정 연결 상태. 비밀키는 서버가 돌려주지 않는다. */
export interface NaverAccountStatus {
  provider: string
  connected: boolean
  customerId: string | null
  apiKeyMasked: string | null
  connectedAt: string | null
  message: string
}

export type GoalType = 'CONVERSIONS' | 'BUDGET'

export interface PlanRequest {
  seedKeywords: string[]
  goalType: GoalType
  targetConversions: number | null
  monthlyBudget: number | null
  saleValue: number | null
  assumedCvrPct: number | null
}

export interface PlanKeyword {
  keyword: string
  monthlySearches: number
  competition: string
  suggestedBid: number
  expectedClicks: number
  expectedCost: number
  selected: boolean
}

export interface PlanScenario {
  name: string
  cvrPct: number
  conversions: number
  cpa: number
  revenue: number | null
  roasPct: number | null
}

export interface PlanProposal {
  id: number
  goalType: GoalType
  seedKeywords: string[]
  monthlyBudget: number
  dailyBudget: number
  expectedClicks: number
  expectedConversions: number
  targetCpa: number
  cvrPct: number
  cvrSource: string
  scenarios: PlanScenario[]
  keywords: PlanKeyword[]
  assumptions: string[]
  steps: string[]
  warnings: string[]
  createdAt: string
}

export type BriefingPeriod = 'DAILY' | 'WEEKLY'
export type BriefingVerdict = 'INCREASE' | 'HOLD' | 'DECREASE' | 'NEED_DATA'

export interface BriefingMetric {
  label: string
  current: number
  previous: number
  unit: string
  changePct: number | null
}

export interface Briefing {
  id: number
  period: BriefingPeriod
  since: string
  until: string
  headline: string
  verdict: BriefingVerdict
  verdictReason: string
  currentDailyBudget: number
  suggestedDailyBudget: number | null
  metrics: BriefingMetric[]
  pacing: {
    monthlyBudget: number
    spentSoFar: number
    projectedSpend: number
    targetConversions: number
    conversionsSoFar: number
    projectedConversions: number
    comment: string
  } | null
  ctrComment: string
  cvrComment: string
  actions: { type: string; target: string; detail: string }[]
  narrative: string[]
  generatedBy: string
  createdAt: string
}

export interface KeywordIdea {
  keyword: string
  pcCount: number
  mobileCount: number
  totalCount: number
  pcClick: number
  mobileClick: number
  competition: string
  registered: boolean
}

export interface SearchQueryPerformance {
  searchQuery: string
  registered: boolean
  impCnt: number
  clkCnt: number
  ctr: number
  cpc: number
  salesAmt: number
}

export interface SearchQuerySyncResult {
  days: number
  rows: number
  since: string
  until: string
}

export interface RestrictedKeyword {
  id: string
  keyword: string
  type: string
}

export interface AdGroupSummary {
  nccAdgroupId: string
  nccCampaignId: string
  name: string
  status: string | null
  bidAmt: number | null
  dailyBudget: number | null
}

export interface SeoRow {
  key: string
  impressions: number
  clicks: number
  ctr: number
  position: number
}

export interface SeoHealth {
  provider: string
  configured: boolean
  reachable: boolean
  message: string
}
