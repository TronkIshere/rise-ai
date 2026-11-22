import React from 'react';
import ReactDOM from 'react-dom/client';
import { RouterProvider } from 'react-router';
import { AppProviders } from '@/app/AppProviders';
import { router } from '@/app/router';
import '@/styles/global.css';
import '@/styles/campaign-discovery.css';
import '@/styles/campaign-flow.css';
import '@/styles/campaign-detail-alignment-b2.css';
import '@/styles/dashboard-suite.css';
import '@/styles/donor-receipts-b2.css';
import '@/styles/functional-pages-b2.css';
import '@/styles/b2-sample.css';
import '@/styles/admin-operations.css';
import '@/styles/campaign-detail-u2-b2.css';
import '@/styles/campaign-detail-u2-visual-fixes.css';
import '@/styles/approved-be-vietnam-pro.css';
import '@/styles/transparency-approved-v11.css';
import '@/styles/admin-escrow-approved-v12.css';
import '@/styles/donor-dashboard-approved-v2.css';
import '@/styles/admin-rule-card-approved-v122.css';
import '@/styles/final-functional-completion.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>
  </React.StrictMode>,
);
