import axios from 'axios'
import type {
  ApiResponse,
  AuthResponse,
  BrandRequest,
  BrandResponse,
  ContentResponse,
  GenerateRequest,
  UserSummary,
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
