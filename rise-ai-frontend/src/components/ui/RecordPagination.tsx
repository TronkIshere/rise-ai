import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from './Button';

export function RecordPagination({ page, pages, onChange }: { page: number; pages: number; onChange: (page: number)=>void }) {
  if (pages <= 1) return null;
  return <nav className="mt-6 flex flex-wrap items-center justify-center gap-3" aria-label="Phân trang dữ liệu cá nhân">
    <Button variant="outline" disabled={page <= 0} onClick={()=>onChange(page-1)}><ChevronLeft size={16} aria-hidden="true"/> Trước</Button>
    <span className="text-sm text-muted">Trang {page+1} / {pages}</span>
    <Button variant="outline" disabled={page+1>=pages} onClick={()=>onChange(page+1)}>Sau <ChevronRight size={16} aria-hidden="true"/></Button>
  </nav>;
}
