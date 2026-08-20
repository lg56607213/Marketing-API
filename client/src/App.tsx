import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AppLayout, AdminRoute } from '@/components/layout/AppLayout'
import { LoginPage } from '@/pages/LoginPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { ProjectsPage } from '@/pages/ProjectsPage'
import { ContentWizardPage } from '@/pages/ContentWizardPage'
import { AnalyticsPage } from '@/pages/AnalyticsPage'
import { MarketIntelligencePage } from '@/pages/analytics/MarketIntelligencePage'
import { ChannelAnalyticsPage } from '@/pages/analytics/ChannelAnalyticsPage'
import { AdPerformancePage } from '@/pages/analytics/AdPerformancePage'
import { BidManagementPage } from '@/pages/analytics/BidManagementPage'
import { AdReportPage } from '@/pages/analytics/AdReportPage'
import { SeoPage } from '@/pages/analytics/SeoPage'
import { SearchQueryPage } from '@/pages/analytics/SearchQueryPage'
import { KeywordToolPage } from '@/pages/analytics/KeywordToolPage'
import { CompetitorAnalysisPage } from '@/pages/analytics/CompetitorAnalysisPage'
import { AIHistoryPage } from '@/pages/AIHistoryPage'
import { ImageLibraryPage } from '@/pages/ImageLibraryPage'
import { SettingsPage } from '@/pages/SettingsPage'
import { CompanyManagementPage } from '@/pages/admin/CompanyManagementPage'
import { UserManagementPage } from '@/pages/admin/UserManagementPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />

        <Route element={<AppLayout />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/projects" element={<ProjectsPage />} />
          <Route path="/content/new" element={<ContentWizardPage />} />
          <Route path="/analytics" element={<AnalyticsPage />} />
          <Route path="/analytics/market" element={<MarketIntelligencePage />} />
          <Route path="/analytics/channels" element={<ChannelAnalyticsPage />} />
          <Route path="/analytics/ads" element={<AdPerformancePage />} />
          <Route path="/analytics/bids" element={<BidManagementPage />} />
          <Route path="/analytics/ad-report" element={<AdReportPage />} />
          <Route path="/analytics/seo" element={<SeoPage />} />
          <Route path="/analytics/search-queries" element={<SearchQueryPage />} />
          <Route path="/analytics/keywords" element={<KeywordToolPage />} />
          <Route path="/analytics/competitors" element={<CompetitorAnalysisPage />} />
          <Route path="/history" element={<AIHistoryPage />} />
          <Route path="/images" element={<ImageLibraryPage />} />
          <Route path="/settings" element={<SettingsPage />} />

          <Route element={<AdminRoute />}>
            <Route path="/admin/companies" element={<CompanyManagementPage />} />
            <Route path="/admin/users" element={<UserManagementPage />} />
          </Route>
        </Route>

        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
