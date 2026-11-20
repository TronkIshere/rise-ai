import React from 'react';
import ReactDOM from 'react-dom/client';
import { RouterProvider } from 'react-router';
import { AppProviders } from '@/app/AppProviders';
import { router } from '@/app/router';
import '@/styles/global.css';
import '@/styles/campaign-discovery.css';
import '@/styles/campaign-flow.css';
import '@/styles/dashboard-suite.css';
import '@/styles/functional-pages-b2.css';
import '@/styles/b2-sample.css';
import '@/styles/admin-operations.css';
import '@/styles/design-freeze-u1-u6.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>
  </React.StrictMode>,
);
