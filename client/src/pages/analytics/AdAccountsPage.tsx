import { useCallback, useEffect, useState } from 'react'
import { AlertCircle, Check, KeyRound, Plug, Plus, Trash2 } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useNavigate } from 'react-router-dom'
import { adAccountApi, adsApi, apiMessage } from '@/services/api'
import { useAuthStore } from '@/store/authStore'
import { useAdScopeStore } from '@/store/adScopeStore'
import type { AdAccount, AdCampaign } from '@/types'
import { cn } from '@/lib/utils'

const won = (n: number | null) => (n && n > 0 ? `${n.toLocaleString('ko-KR')}원` : '미지정')

const emptyForm = { name: '', customerId: '', apiKey: '', secretKey: '', targetCpa: '' }

/**
 * 업체가 쓰는 네이버 검색광고 계정을 등록하고, 캠페인별 목표 CPA 를 정하는 화면.
 * 계정은 여러 개 등록할 수 있고, 등록·수정·삭제는 업체 관리자만 한다.
 */
export function AdAccountsPage() {
  const { role } = useAuthStore()
  const { accountId, setAccount, bump } = useAdScopeStore()
  const isAdmin = role === 'ADMIN'
  const navigate = useNavigate()

  const [accounts, setAccounts] = useState<AdAccount[]>([])
  const [campaigns, setCampaigns] = useState<AdCampaign[]>([])
  const [form, setForm] = useState(emptyForm)
  const [accountCpa, setAccountCpa] = useState<Record<number, string>>({})
  const [campaignCpa, setCampaignCpa] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const selected = accounts.find((a) => a.id === accountId) ?? null

  const loadAccounts = useCallback(async () => {
    try {
      const list = await adAccountApi.list()
      setAccounts(list)
      setAccountCpa(Object.fromEntries(list.map((a) => [a.id, a.targetCpa ? String(a.targetCpa) : ''])))
      const current = useAdScopeStore.getState().accountId
      if (!list.some((a) => a.id === current)) {
        setAccount(list.length > 0 ? list[0].id : null)
      }
    } catch (err) {
      setError(apiMessage(err, '광고 계정을 불러오지 못했습니다.'))
    }
  }, [setAccount])

  const loadCampaigns = useCallback(async (id: number | null) => {
    if (!id) {
      setCampaigns([])
      return
    }
    try {
      const list = await adAccountApi.campaigns(id)
      setCampaigns(list)
      setCampaignCpa(Object.fromEntries(list.map((c) => [c.nccCampaignId, c.targetCpa ? String(c.targetCpa) : ''])))
    } catch {
      setCampaigns([])
    }
  }, [])

  useEffect(() => {
    void loadAccounts()
  }, [loadAccounts])

  useEffect(() => {
    void loadCampaigns(accountId)
  }, [accountId, loadCampaigns])

  const run = async (work: () => Promise<string>, fallback: string) => {
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      setNotice(await work())
    } catch (err) {
      setError(apiMessage(err, fallback))
    } finally {
      setBusy(false)
    }
  }

  const addNaver = () =>
    run(async () => {
      const created = await adAccountApi.create({
        name: form.name.trim(),
        provider: 'naver',
        customerId: form.customerId.trim(),
        apiKey: form.apiKey.trim(),
        secretKey: form.secretKey.trim(),
        targetCpa: form.targetCpa ? Number(form.targetCpa) : undefined,
      })
      setForm(emptyForm)
      await loadAccounts()
      setAccount(created.id)
      // 키는 서버가 저장 전에 네이버로 확인했다. 곧바로 캠페인·키워드·최근 30일 성과를 받아 두고 목표 상담으로 넘어간다.
      setNotice(`"${created.name}" 키를 확인했습니다. 캠페인과 최근 30일 성과를 가져오는 중입니다…`)
      try {
        await adsApi.sync(30)
        bump()
      } catch (err) {
        return `"${created.name}" 계정은 등록했지만 성과를 가져오지 못했습니다: ${apiMessage(err, '동기화 실패')}. 아래 「동기화」로 다시 시도하세요.`
      }
      navigate('/goals')
      return `"${created.name}" 연결을 마쳤습니다.`
    }, '계정 등록에 실패했습니다.')

  const addStub = () =>
    run(async () => {
      const created = await adAccountApi.create({ name: `예시 계정 ${accounts.length + 1}`, provider: 'stub' })
      await loadAccounts()
      setAccount(created.id)
      return '예시 데이터 계정을 추가했습니다. 실제 광고와는 무관한 가짜 데이터입니다.'
    }, '예시 계정을 추가하지 못했습니다.')

  const checkHealth = (account: AdAccount) =>
    run(async () => {
      const health = await adAccountApi.health(account.id)
      if (!health.reachable) throw new Error(health.message)
      return `${account.name}: ${health.message}`
    }, `${account.name}: 연결에 실패했습니다. 자격증명을 확인하세요.`)

  const saveAccountCpa = (account: AdAccount) =>
    run(async () => {
      await adAccountApi.update(account.id, {
        name: account.name,
        targetCpa: accountCpa[account.id] ? Number(accountCpa[account.id]) : 0,
      })
      await loadAccounts()
      return `${account.name} 의 기본 목표 CPA 를 저장했습니다.`
    }, '목표 CPA 를 저장하지 못했습니다.')

  const remove = (account: AdAccount) => {
    if (!window.confirm(`"${account.name}" 계정과 이 계정으로 쌓은 성과·추천·리포트를 모두 지웁니다. 네이버의 광고 자체는 그대로입니다. 계속할까요?`)) {
      return
    }
    void run(async () => {
      await adAccountApi.delete(account.id)
      await loadAccounts()
      return `"${account.name}" 계정을 삭제했습니다.`
    }, '계정을 삭제하지 못했습니다.')
  }

  const saveCampaignCpa = (campaign: AdCampaign) =>
    run(async () => {
      if (!accountId) return ''
      const value = campaignCpa[campaign.nccCampaignId]
      await adAccountApi.setCampaignTargetCpa(accountId, campaign.nccCampaignId, value ? Number(value) : 0)
      await loadCampaigns(accountId)
      bump()
      return `${campaign.name} 의 목표 CPA 를 저장했습니다. 다음 입찰가 분석부터 적용됩니다.`
    }, '목표 CPA 를 저장하지 못했습니다.')

  const formReady = form.name.trim() && form.customerId.trim() && form.apiKey.trim() && form.secretKey.trim()

  return (
    <div className="space-y-6">
      {error && (
        <div className="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          <AlertCircle className="h-4 w-4 shrink-0" />
          {error}
        </div>
      )}
      {notice && (
        <div className="flex items-center gap-2 rounded-md border border-emerald-500/30 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
          <Check className="h-4 w-4 shrink-0" />
          {notice}
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle>등록된 광고 계정</CardTitle>
          <CardDescription>
            계정을 누르면 다른 광고 화면도 그 계정을 기준으로 바뀝니다. 계정 기본 목표 CPA 는 캠페인에 목표가 없을 때 쓰입니다.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {accounts.length === 0 && (
            <p className="text-sm text-muted-foreground">아직 등록된 계정이 없습니다. 아래에서 추가하세요.</p>
          )}
          {accounts.map((account) => (
            <div
              key={account.id}
              className={cn(
                'flex flex-wrap items-center justify-between gap-3 rounded-md border px-4 py-3',
                account.id === accountId && 'border-indigo-500 bg-indigo-50/50'
              )}
            >
              <button type="button" className="min-w-0 text-left" onClick={() => setAccount(account.id)}>
                <div className="flex items-center gap-2">
                  <span className="font-medium">{account.name}</span>
                  <Badge variant={account.provider === 'stub' ? 'secondary' : 'default'}>
                    {account.provider === 'stub' ? '예시 데이터' : '네이버'}
                  </Badge>
                  {account.id === accountId && <Badge variant="outline">선택됨</Badge>}
                </div>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  {account.provider === 'stub'
                    ? '자격증명 없이 화면을 확인하기 위한 가짜 데이터'
                    : `고객 ID ${account.customerId ?? '-'} · 액세스 라이선스 ${account.apiKeyPreview ?? '-'}`}
                </p>
              </button>

              <div className="flex flex-wrap items-center gap-2">
                <span className="text-xs text-muted-foreground">기본 목표 CPA</span>
                <Input
                  type="number"
                  min={0}
                  placeholder="미지정"
                  className="h-9 w-28"
                  disabled={!isAdmin}
                  value={accountCpa[account.id] ?? ''}
                  onChange={(e) => setAccountCpa((prev) => ({ ...prev, [account.id]: e.target.value }))}
                />
                {isAdmin && (
                  <Button size="sm" variant="outline" disabled={busy} onClick={() => void saveAccountCpa(account)}>
                    저장
                  </Button>
                )}
                <Button size="sm" variant="outline" disabled={busy} onClick={() => void checkHealth(account)}>
                  <Plug className="h-4 w-4" />
                  연결 확인
                </Button>
                {isAdmin && (
                  <Button size="sm" variant="outline" disabled={busy} onClick={() => remove(account)}>
                    <Trash2 className="h-4 w-4" />
                    삭제
                  </Button>
                )}
              </div>
            </div>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>캠페인별 목표 CPA{selected ? ` — ${selected.name}` : ''}</CardTitle>
          <CardDescription>
            사이트(캠페인)마다 전환 한 건에 쓸 수 있는 금액이 다르므로 따로 정합니다. 입찰가 추천은 캠페인 목표 → 계정 기본 목표 →
            그 캠페인의 실제 평균 CPA 순으로 기준을 찾습니다.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-2">
          {campaigns.length === 0 && (
            <p className="text-sm text-muted-foreground">
              캠페인이 없습니다. 「검색광고 성과」 화면에서 동기화하면 이 계정의 캠페인이 나타납니다.
            </p>
          )}
          {campaigns.map((campaign) => (
            <div key={campaign.nccCampaignId} className="flex flex-wrap items-center justify-between gap-3 rounded-md border px-4 py-2.5">
              <div className="min-w-0">
                <p className="text-sm font-medium">{campaign.name}</p>
                <p className="text-xs text-muted-foreground">
                  현재 목표 {won(campaign.targetCpa)}
                  {campaign.dailyBudget ? ` · 하루 예산 ${campaign.dailyBudget.toLocaleString('ko-KR')}원` : ''}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <Input
                  type="number"
                  min={0}
                  placeholder="계정 기본값 사용"
                  className="h-9 w-40"
                  value={campaignCpa[campaign.nccCampaignId] ?? ''}
                  onChange={(e) =>
                    setCampaignCpa((prev) => ({ ...prev, [campaign.nccCampaignId]: e.target.value }))
                  }
                />
                <Button size="sm" disabled={busy} onClick={() => void saveCampaignCpa(campaign)}>
                  저장
                </Button>
              </div>
            </div>
          ))}
        </CardContent>
      </Card>

      {isAdmin ? (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <KeyRound className="h-4 w-4" />
              네이버 검색광고 계정 추가
            </CardTitle>
            <CardDescription>
              네이버 검색광고 관리자 → 도구 → API 사용 관리에서 발급한 값을 넣으세요. 비밀키는 암호화해 저장하며 다시 보여 주지 않습니다.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-1">
                <Label htmlFor="acc-name">계정 이름</Label>
                <Input
                  id="acc-name"
                  placeholder="예: 제이디엔드 통합 / 에이스렌트카"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="acc-customer">고객 ID</Label>
                <Input
                  id="acc-customer"
                  value={form.customerId}
                  onChange={(e) => setForm({ ...form, customerId: e.target.value })}
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="acc-key">액세스 라이선스</Label>
                <Input
                  id="acc-key"
                  autoComplete="off"
                  value={form.apiKey}
                  onChange={(e) => setForm({ ...form, apiKey: e.target.value })}
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="acc-secret">비밀키</Label>
                <Input
                  id="acc-secret"
                  type="password"
                  autoComplete="new-password"
                  value={form.secretKey}
                  onChange={(e) => setForm({ ...form, secretKey: e.target.value })}
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="acc-cpa">기본 목표 CPA (선택)</Label>
                <Input
                  id="acc-cpa"
                  type="number"
                  min={0}
                  placeholder="원 단위"
                  value={form.targetCpa}
                  onChange={(e) => setForm({ ...form, targetCpa: e.target.value })}
                />
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button disabled={busy || !formReady} onClick={() => void addNaver()}>
                <Plus className="h-4 w-4" />
                계정 등록
              </Button>
              <Button variant="outline" disabled={busy} onClick={() => void addStub()}>
                예시 데이터 계정 추가
              </Button>
            </div>
          </CardContent>
        </Card>
      ) : (
        <p className="text-sm text-muted-foreground">광고 계정 등록과 삭제는 업체 관리자만 할 수 있습니다.</p>
      )}
    </div>
  )
}
