import { create } from 'zustand'
import { persist } from 'zustand/middleware'

/**
 * 광고 화면들이 함께 쓰는 조회 범위(광고 계정 + 캠페인).
 * API 호출부가 여기서 값을 읽어 accountId / campaignId 를 붙이므로,
 * 화면을 옮겨 다녀도 같은 계정·캠페인을 계속 본다.
 */
interface AdScopeState {
  accountId: number | null
  /** null 이면 계정의 전체 캠페인 */
  campaignId: string | null
  /** 동기화 등으로 캠페인 목록이 바뀌었을 때 올려서 선택 상자를 다시 불러오게 한다 */
  version: number
  setAccount: (accountId: number | null) => void
  setCampaign: (campaignId: string | null) => void
  bump: () => void
  reset: () => void
}

export const useAdScopeStore = create<AdScopeState>()(
  persist(
    (set) => ({
      accountId: null,
      campaignId: null,
      version: 0,
      setAccount: (accountId) => set({ accountId, campaignId: null }),
      setCampaign: (campaignId) => set({ campaignId }),
      bump: () => set((state) => ({ version: state.version + 1 })),
      reset: () => set({ accountId: null, campaignId: null }),
    }),
    {
      name: 'ad-scope',
      partialize: (state) => ({ accountId: state.accountId, campaignId: state.campaignId }),
    }
  )
)
