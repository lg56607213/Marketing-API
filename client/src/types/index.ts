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
  companyId: number | null
  companyName: string | null
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
  nccCampaignId: string | null
  campaignName: string | null
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
  nccCampaignId: string | null
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
  nccCampaignId: string | null
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

export type GoalType = 'CONVERSIONS' | 'BUDGET'

export interface PlanRequest {
  seedKeywords: string[]
  goalType: GoalType
  targetConversions: number | null
  monthlyBudget: number | null
  saleValue: number | null
  assumedCvrPct: number | null
  siteMonthlyVisitors: number | null
  siteMonthlyConversions: number | null
  targetRoasPct: number | null
}

export interface PlanKeyword {
  keyword: string
  monthlySearches: number
  competition: string
  suggestedBid: number
  expectedClicks: number
  expectedCost: number
  selected: boolean
  expectedImpressions: number
  expectedCpc: number
}

export interface PlanBasis {
  item: string
  value: string
  source: string
  grade: string
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
  cvrLowPct: number
  cvrHighPct: number
  maxCpc: number | null
  basis: PlanBasis[]
}

export interface HourOfDay {
  hour: number
  impCnt: number
  clkCnt: number
  salesAmt: number
  ctr: number
  cpc: number
  avgRnk: number
  convCnt: number
  convAmt: number
  cpa: number
  roas: number
}

export interface MarketKeyword {
  keyword: string
  pcSearches: number
  mobileSearches: number
  totalSearches: number
  mobileSharePct: number
  monthlyClicks: number
  competition: string
  bidRank1: number | null
  bidRank3: number | null
  bidRank5: number | null
  crowdRatio: number | null
  relevant: boolean
  opportunity: boolean
}

export interface MarketOverview {
  seeds: string[]
  seedSource: string
  totalSearches: number
  medianBidRank3: number
  highCompetitionPct: number
  mobileSharePct: number
  keywords: MarketKeyword[]
  insights: string[]
}

export interface SiteCheck {
  reachable: boolean
  title: string | null
  description: string | null
  naverTracking: boolean
  googleAnalytics: boolean
  hasForm: boolean
  notes: string[]
}

export interface CompetitorReport {
  sites: { url: string; mine: boolean; check: SiteCheck }[]
  keywordCompetition: MarketKeyword[]
  findings: string[]
  limits: string[]
}

export interface OnboardingProfile {
  companyName: string
  homepageUrl: string
  businessDescription: string
  keywords: string[]
  monthlyBudget: number
  targetConversions: number
  saleValue: number | null
  siteMonthlyVisitors: number | null
  siteMonthlyConversions: number | null
}

export interface OnboardingAlternative {
  key: 'INCREASE' | 'REQUESTED' | 'DECREASE'
  label: string
  monthlyBudget: number
  dailyBudget: number
  expectedCost: number
  expectedClicks: number
  expectedConversions: number
  conversionsLow: number
  conversionsHigh: number
  goalRatePct: number
  costPerConversion: number
  revenue: number | null
  roasPct: number | null
  extraCostPerExtraConversion: number | null
  keywordCount: number
  recommended: boolean
  effect: string
  plan: PlanProposal
}

export interface OnboardingProposal {
  profile: OnboardingProfile
  siteCheck: {
    reachable: boolean
    title: string | null
    description: string | null
    naverTracking: boolean
    googleAnalytics: boolean
    hasForm: boolean
    notes: string[]
  }
  alternatives: OnboardingAlternative[]
  recommendedKey: string
  recommendation: string
  warnings: string[]
  selectedOption: string | null
  createdAt: string
}

export interface KeywordEfficiency {
  nccKeywordId: string
  keyword: string
  impressions: number
  clicks: number
  cost: number
  conversions: number
  revenue: number
  cpa: number
  roasPct: number
  cvrPct: number
  actualCpc: number
  maxCpc: number
  currentBid: number
  suggestedBid: number
  verdict: string
  reason: string
}

export interface EfficiencyAnalysis {
  since: string
  until: string
  mode: string
  targetLabel: string
  accountCvrPct: number
  keywords: KeywordEfficiency[]
}

export interface AutopilotSettingsRequest {
  enabled: boolean
  targetCpa: number
  targetRoasPct: number
  monthlyBudgetCap: number
  manageBids: boolean
  manageBudget: boolean
  pauseWasteful: boolean
}

export interface AutopilotSettings extends AutopilotSettingsRequest {
  effectiveTargetCpa: number
  effectiveMonthlyBudgetCap: number
  targetSource: string
  dryRun: boolean
  maxChangeRatePct: number
  maxDailyApplies: number
  minBid: number
  maxBid: number
}

export interface AutopilotAction {
  type: string
  target: string
  before: string
  after: string
  reason: string
  status: string
}

export interface AutopilotRun {
  id: number
  preview: boolean
  dryRun: boolean
  summary: string
  actions: AutopilotAction[]
  notes: string[]
  ranAt: string
}

export type BriefingPeriod = 'DAILY' | 'WEEKLY' | 'MONTHLY'
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
  efficiencyBasis: string | null
  efficiency: KeywordEfficiency[]
  autopilotSummary: string | null
  autopilotActions: AutopilotAction[]
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

export interface AdAccount {
  id: number
  name: string
  provider: 'naver' | 'stub'
  customerId: string | null
  apiKeyPreview: string | null
  credentialsSet: boolean
  targetCpa: number | null
  createdAt: string
}

export interface AdAccountRequest {
  name: string
  provider?: 'naver' | 'stub'
  customerId?: string
  apiKey?: string
  secretKey?: string
  targetCpa?: number
}

export interface AdCampaign {
  id: number
  adAccountId: number
  nccCampaignId: string
  name: string
  campaignTp: string | null
  status: string | null
  dailyBudget: number | null
  targetCpa: number | null
}

// ── 목표 상담 ─────────────────────────────────────────────────────
export type RevenueModel = 'DIRECT' | 'COMMISSION' | 'SUBSCRIPTION'
export type GoalKind = 'REVENUE' | 'PROFIT' | 'DEALS' | 'LEADS'
/** USER(입력) · ASSUMED(가정) · MEASURED(실측) */
export type ValueSource = 'USER' | 'ASSUMED' | 'MEASURED'

export interface GoalOffering {
  name: string
  /** 계약 1건 매출. 수수료면 수수료, 정기 결제면 한 달 결제액 */
  revenuePerDeal?: number
  /** 계약 1건으로 실제로 남는 돈 */
  profitPerDeal?: number
  profitSource?: ValueSource
  /** 전체 계약 중 이 상품 비중(%) */
  sharePct?: number
}

export interface GoalKeywordGroup {
  /** 예: 법인 · 견적 */
  name: string
  keywords: string[]
}

/** 대화로 파악한 사업 구조. 업종을 정해 두지 않는다. */
export interface BusinessModel {
  industry?: string
  revenueModel?: RevenueModel
  offerings: GoalOffering[]
  leadToDealPct?: number
  leadToDealSource?: ValueSource
  leadToDealPriorPct?: number
  dealsPerCustomer?: number
  dealsPerCustomerSource?: ValueSource
  goalType?: GoalKind
  goalAmount?: number
  adSharePct?: number
  adShareSource?: ValueSource
  /** 0 이면 상한 없음 */
  budgetLimit?: number
  seedKeywords: string[]
  /** 주 고객층 */
  segments?: string[]
  /** 지금 상황·고민 요약 */
  concerns?: string
  /** 고객층 × 검색 의도로 묶은 키워드 */
  keywordGroups?: GoalKeywordGroup[]
  /** 내 홈페이지 주소들 */
  websites?: string[]
  /** 경쟁 업체·업계 선도 업체 */
  competitors?: GoalCompetitor[]
}

export interface GoalCompetitor {
  name: string
  url?: string
  /** USER(직접 알려 줌) · AI(업계 선도 업체로 추정) */
  source?: string
}

/** HIGH · MEDIUM · LOW */
export interface DiagnosisFinding {
  severity: string
  title: string
  evidence: string
  action: string
  amount: number
  keywords: string[]
}

export interface DiagnosisKeywordIdea {
  keyword: string
  clicks: number
  cost: number
  reason: string
}

export interface PerformanceDiagnosis {
  hasData: boolean
  since: string
  until: string
  cost: number
  clicks: number
  conversions: number
  accountCpa: number
  targetCpa: number
  problems: DiagnosisFinding[]
  improvements: DiagnosisFinding[]
  newKeywords: DiagnosisKeywordIdea[]
  negativeCandidates: string[]
  notes: string[]
}

export interface ChannelSignal {
  channel: string
  /** PAID(광고) · OWNED(직접 운영) */
  kind: string
  evidence: string
}

export interface CompetitorSiteScan {
  name: string | null
  url: string
  /** USER · AI · MINE */
  source: string
  reachable: boolean
  title: string | null
  channels: ChannelSignal[]
  brandSearches: number | null
  note: string | null
}

export interface ChannelSuggestion {
  channel: string
  kind: string
  effect: string
  evidence: string[]
  adopters: number
  /** 추정 */
  confidence: string
  suggestion: string
}

export interface CompetitionAnalysis {
  sites: CompetitorSiteScan[]
  suggestions: ChannelSuggestion[]
  limits: string[]
}

export interface EconomicsLine {
  label: string
  value: string
  how: string
  /** 입력 · 가정 · 실측 · 계산 */
  grade: string
}

export interface GoalEconomicsResult {
  revenuePerDeal: number
  profitPerDeal: number
  leadToDealPct: number
  dealsPerCustomer: number
  valuePerLead: number
  breakEvenCpa: number
  targetCpa: number
  dealsNeeded: number
  leadsNeeded: number
  budgetAtTargetCpa: number
  basis: EconomicsLine[]
  /** 재계약까지 친 문의 1건의 가치 */
  ltvValuePerLead: number
  /** 재계약까지 친 참고용 CPA 상한 */
  ltvCpa: number
}

/** WITHIN(목표 이내) · OVER_TARGET(첫 계약으론 초과) · OVER_LTV(상한도 초과) · UNKNOWN(예측 없음) */
export type KeywordVerdict = 'WITHIN' | 'OVER_TARGET' | 'OVER_LTV' | 'UNKNOWN'

/** 키워드 한 건의 네이버 시세. 클릭·비용은 3위 입찰가 기준 한 달 예상 */
export interface GoalKeywordBid {
  keyword: string
  group: string
  monthlySearches: number
  competition: string
  bid1: number
  bid3: number
  bid5: number
  expectedClicks: number
  expectedCost: number
  expectedCpc: number
  expectedCpa: number
  verdict: KeywordVerdict
}

export interface GoalMarketCheck {
  /** 키워드 전체를 3위로 걸 때 문의 1건 평균 비용 */
  marketCpa: number
  targetCpa: number
  breakEvenCpa: number
  ltvCpa: number
  cvrPct: number
  cvrSource: string
  verdict: KeywordVerdict
  explanation: string
  keywords: GoalKeywordBid[]
  notes: string[]
}

export interface GoalOption {
  /** GOAL(목표 달성) · LIMIT(예산 상한) · TRIAL(시험 운영) */
  key: string
  label: string
  description: string
  monthlyBudget: number
  dailyBudget: number
  expectedCost: number
  expectedLeads: number
  leadsLow: number
  leadsHigh: number
  expectedDeals: number
  expectedRevenue: number
  expectedProfit: number
  expectedCpa: number
  meetsGoal: boolean
  goalRatePct: number
  warnings: string[]
  plan: PlanProposal | null
  error: string | null
}

export interface GoalAnalysis {
  economics: GoalEconomicsResult
  options: GoalOption[]
  recommended: string | null
  recommendReason: string
  notes: string[]
  analyzedAt: string
  market: GoalMarketCheck | null
  solution: string | null
  diagnosis: PerformanceDiagnosis | null
  competition: CompetitionAnalysis | null
}

export interface GoalMessage {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  createdAt: string
}

export interface GoalCheckin {
  id: number
  since: string
  until: string
  leads: number
  /** NAVER(네이버 전환 수) · USER(직접 입력) */
  leadsSource: string
  deals: number
  revenue: number | null
  dealRatePct: number
}

export interface GoalView {
  sessionId: number
  /** INTERVIEW · ANALYZED · CONFIRMED */
  status: string
  model: BusinessModel
  nextQuestion: string | null
  economics: GoalEconomicsResult | null
  analysis: GoalAnalysis | null
  confirmedOption: string | null
  confirmedAt: string | null
  messages: GoalMessage[]
  checkins: GoalCheckin[]
  /** ai(모델 대화) · rule(정해진 질문) */
  aiMode: string
}

export interface GoalCheckinRequest {
  since: string
  until: string
  leads?: number
  deals: number
  revenue?: number
}

export interface GoalCheckinResult {
  checkin: GoalCheckin
  totalLeads: number
  totalDeals: number
  measuredPct: number
  previousLeadToDealPct: number
  leadToDealPct: number
  appliedTargetCpa: number
  suggestedTargetCpa: number
  message: string
  view: GoalView
}
