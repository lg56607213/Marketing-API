import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Zap, Eye, EyeOff, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { authApi } from '@/services/api'
import { useAuthStore } from '@/store/authStore'
import { toApiEmail } from '@/types'

const LOGIN_ERROR = '아이디 또는 비밀번호가 올바르지 않습니다.'

const signupError = (err: unknown) => {
  const status = (err as { response?: { status?: number } })?.response?.status
  if (status === 409) return '이미 사용 중인 아이디입니다.'
  if (status === 400) return '입력값을 확인해 주세요. 비밀번호는 8자 이상이어야 합니다.'
  return '회원가입에 실패했습니다. 잠시 후 다시 시도해 주세요.'
}

export function LoginPage() {
  const [mode, setMode] = useState<'login' | 'signup'>('login')
  const [companyName, setCompanyName] = useState('')
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [showPw, setShowPw] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const { login } = useAuthStore()
  const navigate = useNavigate()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!loginId || !password) { setError('아이디와 비밀번호를 모두 입력해 주세요.'); return }
    if (mode === 'signup') {
      if (!companyName.trim()) { setError('업체명을 입력해 주세요.'); return }
      if (password.length < 8) { setError('비밀번호는 8자 이상이어야 합니다.'); return }
    }
    setError('')
    setLoading(true)
    try {
      const auth = mode === 'signup'
        ? await authApi.register(toApiEmail(loginId), password, companyName.trim())
        : await authApi.login(toApiEmail(loginId), password)
      login(auth)
      navigate('/dashboard')
    } catch (err) {
      setError(mode === 'signup' ? signupError(err) : LOGIN_ERROR)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex">
      {/* 좌측 패널 */}
      <div className="hidden lg:flex w-1/2 bg-slate-900 flex-col justify-between p-12">
        <div className="flex items-center gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-indigo-500">
            <Zap className="h-5 w-5 text-white" />
          </div>
          <span className="text-lg font-bold text-white">AI 마케팅 에이전트</span>
        </div>

        <div className="space-y-6">
          <h2 className="text-4xl font-bold text-white leading-tight">
            AI로 마케팅 콘텐츠를<br />
            <span className="text-indigo-400">10배 빠르게</span> 만드세요
          </h2>
          <p className="text-slate-400 text-base leading-relaxed max-w-sm">
            블로그, SNS 게시물, 이메일 캠페인, 랜딩 페이지 — 브랜드 톤에 맞춰 AI가 자동으로 생성합니다.
          </p>
          <div className="flex gap-8 pt-2">
            {[['10,000+', '생성된 콘텐츠'], ['500+', '활성 사용자'], ['98%', '만족도']].map(([v, l]) => (
              <div key={l}>
                <p className="text-2xl font-bold text-white">{v}</p>
                <p className="text-xs text-slate-500 mt-0.5">{l}</p>
              </div>
            ))}
          </div>
        </div>

        <p className="text-xs text-slate-600">© 2026 AI 마케팅 에이전트. All rights reserved.</p>
      </div>

      {/* 우측 패널 */}
      <div className="flex flex-1 items-center justify-center bg-background p-8">
        <div className="w-full max-w-sm space-y-8">
          <div className="space-y-2">
            <div className="flex items-center gap-2 lg:hidden mb-6">
              <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-indigo-500">
                <Zap className="h-4 w-4 text-white" />
              </div>
              <span className="font-bold">AI 마케팅 에이전트</span>
            </div>
            <h1 className="text-2xl font-bold">{mode === 'signup' ? '업체 회원가입' : '로그인'}</h1>
            <p className="text-sm text-muted-foreground">
              {mode === 'signup'
                ? '가입하면 업체 전용 공간이 만들어지고, 가입한 계정이 그 업체의 관리자가 됩니다.'
                : '가입한 계정 또는 업체 관리자에게 발급받은 계정으로 로그인하세요.'}
            </p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            {mode === 'signup' && (
              <div className="space-y-1.5">
                <Label htmlFor="companyName">업체명</Label>
                <Input
                  id="companyName"
                  placeholder="예: 제이디엔드"
                  value={companyName}
                  onChange={(e) => setCompanyName(e.target.value)}
                  maxLength={100}
                />
              </div>
            )}

            <div className="space-y-1.5">
              <Label htmlFor="loginId">아이디</Label>
              <Input
                id="loginId"
                placeholder="아이디를 입력하세요"
                value={loginId}
                onChange={(e) => setLoginId(e.target.value)}
                autoComplete="username"
                autoFocus
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="password">비밀번호</Label>
              <div className="relative">
                <Input
                  id="password"
                  type={showPw ? 'text' : 'password'}
                  placeholder="비밀번호를 입력하세요"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="current-password"
                  className="pr-10"
                />
                <button
                  type="button"
                  onClick={() => setShowPw(!showPw)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                >
                  {showPw ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>

            {error && (
              <p className="text-sm text-destructive">{error}</p>
            )}

            <Button type="submit" className="w-full" disabled={loading}>
              {loading && <Loader2 className="h-4 w-4 animate-spin" />}
              {mode === 'signup' ? '가입하고 시작하기' : '로그인'}
            </Button>
          </form>

          <p className="text-center text-xs text-muted-foreground">
            {mode === 'signup' ? '이미 계정이 있으신가요? ' : '처음 이용하는 업체인가요? '}
            <button
              type="button"
              className="font-medium text-indigo-600 hover:underline"
              onClick={() => { setMode(mode === 'signup' ? 'login' : 'signup'); setError('') }}
            >
              {mode === 'signup' ? '로그인' : '업체 회원가입'}
            </button>
          </p>
        </div>
      </div>
    </div>
  )
}
