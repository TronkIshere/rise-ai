import { Link, matchPath, useLocation } from 'react-router';
import { Activity, BookOpenText, ScanSearch, ShieldCheck } from 'lucide-react';

const items=[
  {to:'/admin',label:'Xét duyệt',icon:ShieldCheck},
  {to:'/admin/monitoring',label:'Cảnh báo AI',icon:ScanSearch},
  {to:'/admin/monitoring/delivery',label:'Truyền sự kiện',icon:Activity},
  {to:'/admin/audit',label:'Nhật ký',icon:BookOpenText},
];
const routeThemes: Readonly<Record<string, string>> = {
  '/admin/monitoring': 'monitoring',
  '/admin/monitoring/delivery': 'delivery',
  '/admin/audit': 'audit',
};

export function AdminMonitoringNav(){
  const { pathname } = useLocation();
  const isFinding = Boolean(matchPath('/admin/monitoring/findings/:id', pathname));
  const currentPath = isFinding ? '/admin/monitoring' : pathname;
  const theme = isFinding ? 'finding' : (routeThemes[currentPath] ?? 'audit');

  return <nav aria-label="Khu vực quản trị và kiểm tra" className="admin-monitoring-nav" data-theme={theme}>
    {items.map(({to,label,icon:Icon})=>
      <Link key={to} to={to} aria-current={currentPath===to?'page':undefined}>
        <Icon size={20} aria-hidden="true"/>{label}
      </Link>
    )}
  </nav>;
}
