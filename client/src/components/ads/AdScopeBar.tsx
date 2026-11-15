import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Building2, AlertCircle } from 'lucide-react'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { adAccountApi, adsApi } from '@/services/api'
import { useAdScopeStore } from '@/store/adScopeStore'
import type { AdAccount, AdCampaign } from '@/types'

const ALL_CAMPAIGNS = '__all__'

/**
 * 광고 계정과 캠페인을 고르는 공통 선택 막대.
 * 고른 값은 저장소에 들어가고, 각 화면은 그 값이 바뀌면 다시 조회한다.
 */
export function AdScopeBar({ showCampaign = true }: { showCampaign?: boolean }) {
  const { accountId, campaignId, version, setAccount, setCampaign } = useAdScopeStore()
  const [accounts, setAccounts] = useState<AdAccount[] | null>(null)
  const [campaigns, setCampaigns] = useState<AdCampaign[]>([])

  useEffect(() => {
    adAccountApi
      .list()
      .then((list) => {
        setAccounts(list)
        const current = useAdScopeStore.getState().accountId
        if (!list.some((a) => a.id === current)) {
          setAccount(list.length > 0 ? list[0].id : null)
        }
      })
      .catch(() => setAccounts([]))
  }, [setAccount])

  useEffect(() => {
    if (!accountId || !showCampaign) {
      setCampaigns([])
      return
    }
    let cancelled = false
    adsApi
      .campaigns()
      .then((list) => {
        if (cancelled) return
        setCampaigns(list)
        const current = useAdScopeStore.getState().campaignId
        if (current && !list.some((c) => c.nccCampaignId === current)) {
          setCampaign(null)
        }
      })
      .catch(() => {
        if (!cancelled) setCampaigns([])
      })
    return () => {
      cancelled = true
    }
  }, [accountId, version, showCampaign, setCampaign])

  if (accounts === null) return null

  if (accounts.length === 0) {
    return (
      <div className="flex items-center gap-2 rounded-md border border-amber-500/30 bg-amber-50 px-4 py-3 text-sm text-amber-800">
        <AlertCircle className="h-4 w-4 shrink-0" />
        <span>
          등록된 광고 계정이 없습니다.{' '}
          <Link to="/analytics/accounts" className="font-medium underline">
            광고 계정 관리
          </Link>
          에서 네이버 검색광고 계정을 먼저 등록하세요.
        </span>
      </div>
    )
  }

  return (
    <div className="flex flex-wrap items-center gap-3 rounded-md border bg-muted/30 px-4 py-3">
      <Building2 className="h-4 w-4 text-muted-foreground" />
      <div className="flex items-center gap-2">
        <span className="text-xs text-muted-foreground">광고 계정</span>
        <Select
          value={accountId ? String(accountId) : undefined}
          onValueChange={(value) => setAccount(Number(value))}
        >
          <SelectTrigger className="h-9 w-56">
            <SelectValue placeholder="계정 선택" />
          </SelectTrigger>
          <SelectContent>
            {accounts.map((account) => (
              <SelectItem key={account.id} value={String(account.id)}>
                {account.name}
                {account.provider === 'stub' ? ' (예시 데이터)' : ''}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {showCampaign && (
        <div className="flex items-center gap-2">
          <span className="text-xs text-muted-foreground">캠페인</span>
          <Select
            value={campaignId ?? ALL_CAMPAIGNS}
            onValueChange={(value) => setCampaign(value === ALL_CAMPAIGNS ? null : value)}
          >
            <SelectTrigger className="h-9 w-64">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_CAMPAIGNS}>전체 캠페인</SelectItem>
              {campaigns.map((campaign) => (
                <SelectItem key={campaign.nccCampaignId} value={campaign.nccCampaignId}>
                  {campaign.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {campaigns.length === 0 && (
            <span className="text-xs text-muted-foreground">동기화하면 캠페인이 나타납니다.</span>
          )}
        </div>
      )}
    </div>
  )
}
