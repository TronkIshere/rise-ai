import { createBrowserRouter } from 'react-router';
import { SiteShell } from '@/components/layout/SiteShell';
import { RequireAuth } from '@/auth/RequireAuth';
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
      { path: '/workspace', element: <RequireAuth><WorkspacePage /></RequireAuth> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
