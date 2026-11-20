import axios from 'axios'
import type {
  ApiResponse,
  CompetitorReport,
  HourOfDay,
  MarketOverview,
  OnboardingProfile,
  OnboardingProposal,
  AutopilotRun,
  AutopilotSettings,
  AutopilotSettingsRequest,
  EfficiencyAnalysis,
  AuthResponse,
  BrandRequest,
  BrandResponse,
  ContentResponse,
  GenerateRequest,
  UserSummary,
  AdSummary,
  KeywordPerformance,
  AdSyncResult,
  BidRecommendation,
  BidApplyResult,
  AdReport,
  AdHealth,
  PlanProposal,
  PlanRequest,
  Briefing,
  BriefingPeriod,
  KeywordIdea,
  SearchQueryPerformance,
  SearchQuerySyncResult,
  RestrictedKeyword,
  AdGroupSummary,
  SeoRow,
  SeoHealth,
  AdAccount,
  AdAccountRequest,
  AdCampaign,
  BusinessModel,
  GoalView,
  GoalCheckinRequest,
  GoalCheckinResult,
} from '@/types'
import { useAdScopeStore } from '@/store/adScopeStore'

const http = axios.create({ baseURL: '/api' })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('auth')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

const unwrap = <T>(res: { data: ApiResponse<T> }) => res.data.data

/** 서버가 보낸 오류 문장을 꺼낸다. 없으면 fallback 을 쓴다. */
export const apiMessage = (err: unknown, fallback: string): string => {
  if (axios.isAxiosError(err)) {
    const message = (err.response?.data as { message?: string } | undefined)?.message
    return message || fallback
  }
  return err instanceof Error && err.message ? err.message : fallback
}

// 화면 상단에서 고른 광고 계정 / 캠페인. 광고 API 는 모두 이 값을 붙여 보낸다.
const account = () => ({ accountId: useAdScopeStore.getState().accountId ?? undefined })
const scope = () => {
  const { accountId, campaignId } = useAdScopeStore.getState()
  return { accountId: accountId ?? undefined, campaignId: campaignId ?? undefined }
}

// Auth
export const authApi = {
  login: (email: string, password: string) =>
    http.post<ApiResponse<AuthResponse>>('/auth/login', { email, password }).then(unwrap),
  /** 회원가입하면 새 업체가 만들어지고 가입자가 그 업체의 관리자가 된다 */
  register: (email: string, password: string, companyName?: string) =>
    http.post<ApiResponse<AuthResponse>>('/auth/register', { email, password, companyName }).then(unwrap),
}

// Brand
export const brandApi = {
  list: () => http.get<ApiResponse<BrandResponse[]>>('/brand').then(unwrap),
  get: (id: number) => http.get<ApiResponse<BrandResponse>>(`/brand/${id}`).then(unwrap),
  create: (data: BrandRequest) =>
    http.post<ApiResponse<BrandResponse>>('/brand', data).then(unwrap),
  update: (id: number, data: BrandRequest) =>
    http.put<ApiResponse<BrandResponse>>(`/brand/${id}`, data).then(unwrap),
  delete: (id: number) => http.delete(`/brand/${id}`),
}

// Content
export const contentApi = {
  list: (brandId?: number) =>
    http
      .get<ApiResponse<ContentResponse[]>>('/content', { params: brandId ? { brandId } : {} })
      .then(unwrap),
  get: (id: number) => http.get<ApiResponse<ContentResponse>>(`/content/${id}`).then(unwrap),
  generate: (data: GenerateRequest) =>
    http.post<ApiResponse<ContentResponse>>('/content/generate', data).then(unwrap),
  approve: (id: number) =>
    http.post<ApiResponse<ContentResponse>>(`/content/${id}/approve`, {}).then(unwrap),
  reject: (id: number, reason?: string) =>
    http.post<ApiResponse<ContentResponse>>(`/content/${id}/reject`, { reason }).then(unwrap),
  publish: (id: number) =>
    http.post<ApiResponse<ContentResponse>>(`/content/${id}/publish`, {}).then(unwrap),
  rewrite: (id: number, instructions: string) =>
    http
      .post<ApiResponse<ContentResponse>>(`/content/${id}/rewrite`, { instructions })
      .then(unwrap),
}

// Admin (우리 업체 사용자)
export const adminApi = {
  listUsers: () => http.get<ApiResponse<UserSummary[]>>('/admin/users').then(unwrap),
  createUser: (email: string, password: string) =>
    http.post<ApiResponse<UserSummary>>('/admin/users', { email, password }).then(unwrap),
}

// 광고 계정 (업체가 등록한 네이버 검색광고 계정)
export const adAccountApi = {
  list: () => http.get<ApiResponse<AdAccount[]>>('/ads/accounts').then(unwrap),
  create: (data: AdAccountRequest) =>
    http.post<ApiResponse<AdAccount>>('/ads/accounts', data).then(unwrap),
  update: (id: number, data: AdAccountRequest) =>
    http.put<ApiResponse<AdAccount>>(`/ads/accounts/${id}`, data).then(unwrap),
  delete: (id: number) => http.delete(`/ads/accounts/${id}`),
  health: (id: number) =>
    http.get<ApiResponse<AdHealth>>('/ads/health', { params: { accountId: id } }).then(unwrap),
  campaigns: (id: number) =>
    http.get<ApiResponse<AdCampaign[]>>('/ads/campaigns', { params: { accountId: id } }).then(unwrap),
  setCampaignTargetCpa: (id: number, nccCampaignId: string, targetCpa: number) =>
    http
      .put<ApiResponse<AdCampaign>>(`/ads/campaigns/${nccCampaignId}/target-cpa`, null, {
        params: { accountId: id, targetCpa },
      })
      .then(unwrap),
}

// Ads (네이버 검색광고)
export const adsApi = {
  sync: (days = 30) =>
    http
      .post<ApiResponse<AdSyncResult>>('/ads/sync', null, { params: { ...account(), days } })
      .then(unwrap),
  campaigns: () =>
    http.get<ApiResponse<AdCampaign[]>>('/ads/campaigns', { params: account() }).then(unwrap),
  summary: (since?: string, until?: string) =>
    http
      .get<ApiResponse<AdSummary>>('/ads/summary', { params: { ...scope(), since, until } })
      .then(unwrap),
  keywords: (since?: string, until?: string) =>
    http
      .get<ApiResponse<KeywordPerformance[]>>('/ads/keywords', { params: { ...scope(), since, until } })
      .then(unwrap),
}

// 입찰가 조정 (추천 -> 승인 -> 반영)
export const bidApi = {
  recommend: () =>
    http
      .post<ApiResponse<BidRecommendation[]>>('/ads/bids/recommend', null, { params: scope() })
      .then(unwrap),
  pending: () =>
    http.get<ApiResponse<BidRecommendation[]>>('/ads/bids', { params: scope() }).then(unwrap),
  history: () =>
    http.get<ApiResponse<BidRecommendation[]>>('/ads/bids/history', { params: scope() }).then(unwrap),
  approve: (id: number) =>
    http.post<ApiResponse<BidApplyResult>>(`/ads/bids/${id}/approve`).then(unwrap),
  reject: (id: number) =>
    http.post<ApiResponse<BidApplyResult>>(`/ads/bids/${id}/reject`).then(unwrap),
  approveAll: (ids: number[]) =>
    http.post<ApiResponse<BidApplyResult[]>>('/ads/bids/approve', ids).then(unwrap),
}

// 광고 분석 리포트 / 연동 진단
export const adReportApi = {
  generate: (days = 7) =>
    http
      .post<ApiResponse<AdReport>>('/ads/reports', null, { params: { ...scope(), days } })
      .then(unwrap),
  list: () => http.get<ApiResponse<AdReport[]>>('/ads/reports', { params: scope() }).then(unwrap),
  get: (id: number) => http.get<ApiResponse<AdReport>>(`/ads/reports/${id}`).then(unwrap),
  health: () => http.get<ApiResponse<AdHealth>>('/ads/health', { params: account() }).then(unwrap),
}

// 키워드 발굴 · 등록 · 제외
export const keywordToolApi = {
  ideas: (hints: string, excludeRegistered = false, mustContainHint = true) =>
    http
      .get<ApiResponse<KeywordIdea[]>>('/ads/keywords/ideas', {
        params: { ...account(), hints, excludeRegistered, mustContainHint },
      })
      .then(unwrap),
  add: (nccAdgroupId: string, keywords: string[], position?: number, device = 'MOBILE') =>
    http
      .post<ApiResponse<unknown[]>>('/ads/keywords', keywords, {
        params: { ...account(), nccAdgroupId, position, device },
      })
      .then(unwrap),
  remove: (nccKeywordId: string) => http.delete(`/ads/keywords/${nccKeywordId}`, { params: account() }),
  restricted: (nccAdgroupId: string) =>
    http
      .get<ApiResponse<RestrictedKeyword[]>>('/ads/keywords/restricted', {
        params: { ...account(), nccAdgroupId },
      })
      .then(unwrap),
  addRestricted: (nccAdgroupId: string, keywords: string[], type = 'EXP_SEARCH') =>
    http
      .post<ApiResponse<RestrictedKeyword[]>>('/ads/keywords/restricted', keywords, {
        params: { ...account(), nccAdgroupId, type },
      })
      .then(unwrap),
  removeRestricted: (nccAdgroupId: string, id: string) =>
    http.delete(`/ads/keywords/restricted/${id}`, { params: { ...account(), nccAdgroupId } }),
  adgroups: () => http.get<ApiResponse<AdGroupSummary[]>>('/ads/adgroups', { params: scope() }).then(unwrap),
}

// 유입 검색어 리포트
export const searchQueryApi = {
  sync: (days = 7) =>
    http
      .post<ApiResponse<SearchQuerySyncResult>>('/ads/search-queries/sync', null, {
        params: { ...account(), days },
      })
      .then(unwrap),
  list: (since?: string, until?: string) =>
    http
      .get<ApiResponse<SearchQueryPerformance[]>>('/ads/search-queries', {
        params: { ...scope(), since, until },
      })
      .then(unwrap),
}

// 운영 계획 · 일간/주간 브리핑
export const plannerApi = {
  propose: (request: PlanRequest) =>
    http.post<ApiResponse<PlanProposal>>('/ads/plans/propose', request, { params: account() }).then(unwrap),
  currentPlan: () => http.get<ApiResponse<PlanProposal | null>>('/ads/plans/current', { params: account() }).then(unwrap),
  generateBriefing: (period: BriefingPeriod) =>
    http.post<ApiResponse<Briefing>>('/ads/briefings', null, { params: { ...account(), period } }).then(unwrap),
  briefings: () => http.get<ApiResponse<Briefing[]>>('/ads/briefings', { params: account() }).then(unwrap),
}

// 시장(키워드 수요·경쟁) · 경쟁사 비교
export const marketApi = {
  keywords: (seeds?: string) =>
    http.get<ApiResponse<MarketOverview>>('/market/keywords', { params: { ...account(), seeds } }).then(unwrap),
  competitors: (urls: string[]) =>
    http.post<ApiResponse<CompetitorReport>>('/market/competitors', { urls }, { params: account() }).then(unwrap),
  hourly: (since?: string, until?: string) =>
    http.get<ApiResponse<HourOfDay[]>>('/ads/stat-reports/hourly', { params: { ...account(), since, until } }).then(unwrap),
}

// 업체 정보 등록 · 예산 3안 제안
export const onboardingApi = {
  current: () => http.get<ApiResponse<OnboardingProposal | null>>('/onboarding', { params: account() }).then(unwrap),
  submit: (profile: OnboardingProfile) =>
    http.post<ApiResponse<OnboardingProposal>>('/onboarding', profile, { params: account() }).then(unwrap),
  select: (option: string) =>
    http.post<ApiResponse<OnboardingProposal>>('/onboarding/select', null, { params: { ...account(), option } }).then(unwrap),
}

// 자동 운영 · 광고비 대비 효율
export const autopilotApi = {
  settings: () => http.get<ApiResponse<AutopilotSettings>>('/ads/autopilot', { params: account() }).then(unwrap),
  update: (request: AutopilotSettingsRequest) =>
    http.put<ApiResponse<AutopilotSettings>>('/ads/autopilot', request, { params: account() }).then(unwrap),
  run: (preview: boolean) =>
    http.post<ApiResponse<AutopilotRun>>('/ads/autopilot/run', null, { params: { ...account(), preview } }).then(unwrap),
  runs: () => http.get<ApiResponse<AutopilotRun[]>>('/ads/autopilot/runs', { params: account() }).then(unwrap),
  efficiency: () => http.get<ApiResponse<EfficiencyAnalysis>>('/ads/efficiency', { params: account() }).then(unwrap),
}

// 목표 상담: 대화로 사업 구조를 파악하고 목표 CPA·예산을 정한다
export const goalApi = {
  current: () => http.get<ApiResponse<GoalView | null>>('/goals', { params: account() }).then(unwrap),
  start: () => http.post<ApiResponse<GoalView>>('/goals/start', null, { params: account() }).then(unwrap),
  send: (text: string) =>
    http.post<ApiResponse<GoalView>>('/goals/messages', { text }, { params: account() }).then(unwrap),
  updateModel: (model: BusinessModel) =>
    http.put<ApiResponse<GoalView>>('/goals/model', model, { params: account() }).then(unwrap),
  analyze: () => http.post<ApiResponse<GoalView>>('/goals/analyze', null, { params: account() }).then(unwrap),
  confirm: (option: string, enableAutopilot: boolean) =>
    http
      .post<ApiResponse<GoalView>>('/goals/confirm', { option, enableAutopilot }, { params: account() })
      .then(unwrap),
  checkin: (request: GoalCheckinRequest) =>
    http.post<ApiResponse<GoalCheckinResult>>('/goals/checkins', request, { params: account() }).then(unwrap),
  applyTarget: () =>
    http.post<ApiResponse<GoalView>>('/goals/target/apply', null, { params: account() }).then(unwrap),
}

// 자연검색 (SEO)
export const seoApi = {
  health: () => http.get<ApiResponse<SeoHealth>>('/seo/health').then(unwrap),
  searchAnalytics: (dimension: 'query' | 'page' = 'query', limit = 100) =>
    http
      .get<ApiResponse<SeoRow[]>>('/seo/search-analytics', { params: { dimension, limit } })
      .then(unwrap),
}
