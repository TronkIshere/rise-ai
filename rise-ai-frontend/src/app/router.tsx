import { createBrowserRouter } from 'react-router';
import { SiteShell } from '@/components/layout/SiteShell';
import { RequireAuth } from '@/auth/RequireAuth';
import { RequireRole } from '@/auth/RequireRole';
import { ContributePage } from '@/pages/ContributePage';
import { DonorDashboardPage } from '@/pages/DonorDashboardPage';
import { DisputeThreadPage } from '@/pages/DisputeThreadPage';
import { CreatorDashboardPage } from '@/pages/CreatorDashboardPage';
import { CreatorCampaignEditorPage } from '@/pages/CreatorCampaignEditorPage';
import { CreatorCampaignPage } from '@/pages/CreatorCampaignPage';
import { AdminDashboardPage } from '@/pages/AdminDashboardPage';
import { AdminReviewPage } from '@/pages/AdminReviewPage';
import { AdminEscrowPage } from '@/pages/AdminEscrowPage';
import { AdminMonitoringPage } from '@/pages/AdminMonitoringPage';
import { AdminFindingPage } from '@/pages/AdminFindingPage';
import { AdminDeliveryPage } from '@/pages/AdminDeliveryPage';
import { AdminAuditPage } from '@/pages/AdminAuditPage';
import { HomePage } from '@/pages/HomePage';
import { CampaignsPage } from '@/pages/CampaignsPage';
import { CampaignDetailPage } from '@/pages/CampaignDetailPage';
import { LoginPage } from '@/pages/LoginPage';
import { WorkspacePage } from '@/pages/WorkspacePage';
import { NotFoundPage } from '@/pages/NotFoundPage';

export const router = createBrowserRouter([
  // B2 owns its approved header and footer. Other routes retain SiteShell.
  { path: '/', element: <HomePage /> },
  // Campaign Discovery owns the approved Desktop 1440 header/footer, as Homepage B2 does.
  { path: '/campaigns', element: <CampaignsPage /> },
  {
    element: <SiteShell />,
    children: [
      { path: '/campaigns/:slug', element: <CampaignDetailPage /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/campaigns/:slug/contribute', element: <RequireAuth><RequireRole role="DONOR"><ContributePage /></RequireRole></RequireAuth> },
      { path: '/donor', element: <RequireAuth><RequireRole role="DONOR"><DonorDashboardPage /></RequireRole></RequireAuth> },
      { path: '/donor/disputes/:id', element: <RequireAuth><RequireRole role="DONOR"><DisputeThreadPage /></RequireRole></RequireAuth> },
      { path: '/creator', element: <RequireAuth><RequireRole role="CAMPAIGN_CREATOR"><CreatorDashboardPage /></RequireRole></RequireAuth> },
      { path: '/creator/campaigns/new', element: <RequireAuth><RequireRole role="CAMPAIGN_CREATOR"><CreatorCampaignEditorPage /></RequireRole></RequireAuth> },
      { path: '/creator/campaigns/:id/edit', element: <RequireAuth><RequireRole role="CAMPAIGN_CREATOR"><CreatorCampaignEditorPage /></RequireRole></RequireAuth> },
      { path: '/creator/campaigns/:id', element: <RequireAuth><RequireRole role="CAMPAIGN_CREATOR"><CreatorCampaignPage /></RequireRole></RequireAuth> },
      { path: '/admin', element: <RequireAuth><RequireRole role="ADMIN"><AdminDashboardPage /></RequireRole></RequireAuth> },
      { path: '/admin/review/:kind/:id', element: <RequireAuth><RequireRole role="ADMIN"><AdminReviewPage /></RequireRole></RequireAuth> },
      { path: '/admin/escrow', element: <RequireAuth><RequireRole role="ADMIN"><AdminEscrowPage /></RequireRole></RequireAuth> },
      { path: '/admin/monitoring', element: <RequireAuth><RequireRole role="ADMIN"><AdminMonitoringPage /></RequireRole></RequireAuth> },
      { path: '/admin/monitoring/findings/:id', element: <RequireAuth><RequireRole role="ADMIN"><AdminFindingPage /></RequireRole></RequireAuth> },
      { path: '/admin/monitoring/delivery', element: <RequireAuth><RequireRole role="ADMIN"><AdminDeliveryPage /></RequireRole></RequireAuth> },
      { path: '/admin/audit', element: <RequireAuth><RequireRole role="ADMIN"><AdminAuditPage /></RequireRole></RequireAuth> },
      { path: '/workspace', element: <RequireAuth><WorkspacePage /></RequireAuth> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
