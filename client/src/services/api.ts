import axios from 'axios'
import type {
  ApiResponse,
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
  KeywordIdea,
  SearchQueryPerformance,
  SearchQuerySyncResult,
  RestrictedKeyword,
  AdGroupSummary,
  SeoRow,
  SeoHealth,
} from '@/types'

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

// Auth
export const authApi = {
  login: (email: string, password: string) =>
    http.post<ApiResponse<AuthResponse>>('/auth/login', { email, password }).then(unwrap),
  register: (email: string, password: string) =>
    http.post<ApiResponse<AuthResponse>>('/auth/register', { email, password }).then(unwrap),
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

// Admin
export const adminApi = {
  listUsers: () => http.get<ApiResponse<UserSummary[]>>('/admin/users').then(unwrap),
  createUser: (email: string, password: string) =>
    http.post<ApiResponse<UserSummary>>('/admin/users', { email, password }).then(unwrap),
}

// Ads (네이버 검색광고)
export const adsApi = {
  sync: (days = 30) =>
    http.post<ApiResponse<AdSyncResult>>('/ads/sync', null, { params: { days } }).then(unwrap),
  summary: (since?: string, until?: string) =>
    http.get<ApiResponse<AdSummary>>('/ads/summary', { params: { since, until } }).then(unwrap),
  keywords: (since?: string, until?: string) =>
    http
      .get<ApiResponse<KeywordPerformance[]>>('/ads/keywords', { params: { since, until } })
      .then(unwrap),
}

// 입찰가 조정 (추천 -> 승인 -> 반영)
export const bidApi = {
  recommend: () =>
    http.post<ApiResponse<BidRecommendation[]>>('/ads/bids/recommend').then(unwrap),
  pending: () => http.get<ApiResponse<BidRecommendation[]>>('/ads/bids').then(unwrap),
  history: () => http.get<ApiResponse<BidRecommendation[]>>('/ads/bids/history').then(unwrap),
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
    http.post<ApiResponse<AdReport>>('/ads/reports', null, { params: { days } }).then(unwrap),
  list: () => http.get<ApiResponse<AdReport[]>>('/ads/reports').then(unwrap),
  get: (id: number) => http.get<ApiResponse<AdReport>>(`/ads/reports/${id}`).then(unwrap),
  health: () => http.get<ApiResponse<AdHealth>>('/ads/health').then(unwrap),
}

// 키워드 발굴 · 등록 · 제외
export const keywordToolApi = {
  ideas: (hints: string, excludeRegistered = false, mustContainHint = true) =>
    http
      .get<ApiResponse<KeywordIdea[]>>('/ads/keywords/ideas', { params: { hints, excludeRegistered, mustContainHint } })
      .then(unwrap),
  add: (nccAdgroupId: string, keywords: string[], position?: number, device = 'MOBILE') =>
    http
      .post<ApiResponse<unknown[]>>('/ads/keywords', keywords, {
        params: { nccAdgroupId, position, device },
      })
      .then(unwrap),
  remove: (nccKeywordId: string) => http.delete(`/ads/keywords/${nccKeywordId}`),
  restricted: (nccAdgroupId: string) =>
    http
      .get<ApiResponse<RestrictedKeyword[]>>('/ads/keywords/restricted', { params: { nccAdgroupId } })
      .then(unwrap),
  addRestricted: (nccAdgroupId: string, keywords: string[], type = 'EXP_SEARCH') =>
    http
      .post<ApiResponse<RestrictedKeyword[]>>('/ads/keywords/restricted', keywords, {
        params: { nccAdgroupId, type },
      })
      .then(unwrap),
  removeRestricted: (nccAdgroupId: string, id: string) =>
    http.delete(`/ads/keywords/restricted/${id}`, { params: { nccAdgroupId } }),
  adgroups: () => http.get<ApiResponse<AdGroupSummary[]>>('/ads/adgroups').then(unwrap),
}

// 유입 검색어 리포트
export const searchQueryApi = {
  sync: (days = 7) =>
    http.post<ApiResponse<SearchQuerySyncResult>>('/ads/search-queries/sync', null, { params: { days } }).then(unwrap),
  list: (since?: string, until?: string) =>
    http
      .get<ApiResponse<SearchQueryPerformance[]>>('/ads/search-queries', { params: { since, until } })
      .then(unwrap),
}

// 자연검색 (SEO)
export const seoApi = {
  health: () => http.get<ApiResponse<SeoHealth>>('/seo/health').then(unwrap),
  searchAnalytics: (dimension: 'query' | 'page' = 'query', limit = 100) =>
    http
      .get<ApiResponse<SeoRow[]>>('/seo/search-analytics', { params: { dimension, limit } })
      .then(unwrap),
}
