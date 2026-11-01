import { Link, useLocation } from 'react-router';
import { Activity, BookOpenText, ScanSearch, ShieldCheck } from 'lucide-react';

const items=[
  {to:'/admin',label:'Xét duyệt',icon:ShieldCheck},
  {to:'/admin/monitoring',label:'Cảnh báo AI',icon:ScanSearch},
  {to:'/admin/monitoring/delivery',label:'Truyền sự kiện',icon:Activity},
  {to:'/admin/audit',label:'Nhật ký',icon:BookOpenText},
];
export function AdminMonitoringNav(){
  const {pathname}=useLocation();
  return <nav aria-label="Khu vực quản trị và kiểm tra" className="mt-6 flex flex-wrap gap-2">
    {items.map(({to,label,icon:Icon})=><Link key={to} to={to} aria-current={pathname===to?'page':undefined}
      className={'inline-flex min-h-11 items-center gap-2 rounded-xl border px-4 py-2 text-sm font-semibold '+(pathname===to?'border-brand bg-soft-mint text-brand':'border-border bg-surface text-copy hover:text-brand')}>
      <Icon size={17} aria-hidden="true"/>{label}
    </Link>)}
  </nav>;
}
