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
