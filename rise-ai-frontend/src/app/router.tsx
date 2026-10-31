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
import { HomePage } from '@/pages/HomePage';
import { CampaignsPage } from '@/pages/CampaignsPage';
import { CampaignDetailPage } from '@/pages/CampaignDetailPage';
import { LoginPage } from '@/pages/LoginPage';
import { WorkspacePage } from '@/pages/WorkspacePage';
import { NotFoundPage } from '@/pages/NotFoundPage';

export const router = createBrowserRouter([
  {
    element: <SiteShell />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/campaigns', element: <CampaignsPage /> },
      { path: '/campaigns/:slug', element: <CampaignDetailPage /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/campaigns/:slug/contribute', element: <RequireAuth><RequireRole role="DONOR"><ContributePage /></RequireRole></RequireAuth> },
      { path: '/donor', element: <RequireAuth><RequireRole role="DONOR"><DonorDashboardPage /></RequireRole></RequireAuth> },
      { path: '/donor/disputes/:id', element: <RequireAuth><RequireRole role="DONOR"><DisputeThreadPage /></RequireRole></RequireAuth> },
      { path: '/creator', element: <RequireAuth><RequireRole role="CAMPAIGN_CREATOR"><CreatorDashboardPage /></RequireRole></RequireAuth> },
      { path: '/creator/campaigns/new', element: <RequireAuth><RequireRole role="CAMPAIGN_CREATOR"><CreatorCampaignEditorPage /></RequireRole></RequireAuth> },
      { path: '/creator/campaigns/:id/edit', element: <RequireAuth><RequireRole role="CAMPAIGN_CREATOR"><CreatorCampaignEditorPage /></RequireRole></RequireAuth> },
      { path: '/creator/campaigns/:id', element: <RequireAuth><RequireRole role="CAMPAIGN_CREATOR"><CreatorCampaignPage /></RequireRole></RequireAuth> },
      { path: '/workspace', element: <RequireAuth><WorkspacePage /></RequireAuth> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
