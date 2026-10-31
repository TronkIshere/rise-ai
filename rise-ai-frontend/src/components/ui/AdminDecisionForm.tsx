import { useState, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import type { ReviewChoice } from '@/lib/admin-display';
export function AdminDecisionForm({
  choices,onExecute,onComplete,
}:{
  choices:ReviewChoice[];
  onExecute:(operation:string,reason:string)=>Promise<unknown>;
  onComplete:()=>void;
}){
  const [selected,setSelected]=useState('');
  const [reason,setReason]=useState('');
  const [confirmed,setConfirmed]=useState(false);
  const choice=choices.find(x=>x.id===selected);
  const mutation=useMutation({mutationFn:()=>onExecute(selected,reason.trim()),onSuccess(){setSelected('');setReason('');setConfirmed(false);onComplete();}});
  async function submit(e:FormEvent<HTMLFormElement>){
    e.preventDefault();
    if(!choice||!confirmed||mutation.isPending||(choice.required&&!reason.trim()))return;
    try{await mutation.mutateAsync();}catch{/* Exact Backend error is shown below. */}
  }
  if(!choices.length)return <p className="text-sm text-muted">Không có thao tác xử lý hợp lệ ở trạng thái hiện tại.</p>;
  return <div className="mt-5">
    <div className="flex flex-wrap gap-2">
      {choices.map(item=><Button key={item.id} type="button" variant="outline" onClick={()=>{setSelected(selected===item.id?'':item.id);setReason('');setConfirmed(false);}}>
        {item.label}
      </Button>)}
    </div>
    {choice?<form className="mt-4 rounded-xl border border-border bg-soft-sand p-5" onSubmit={e=>void submit(e)}>
      <h3 className="font-bold text-brand-ink">{choice.label}</h3>
      <p className="mt-2 text-xs leading-6 text-warning">{choice.critical?'Thao tác ảnh hưởng sổ cái hoặc trạng thái nghiệp vụ mô phỏng. Chỉ thực hiện khi đã kiểm tra chứng cứ, quyền và số dư.':'Backend là nơi quyết định đủ điều kiện xử lý; mọi thay đổi đều được lưu trạng thái.'}</p>
      <label htmlFor={'admin-reason-'+choice.id} className="mt-4 mb-2 block text-sm font-semibold">{choice.hint??(choice.required?'Lý do / ghi chú bắt buộc':'Ghi chú xét duyệt (tùy chọn)')}</label>
      <textarea id={'admin-reason-'+choice.id} value={reason} onChange={e=>setReason(e.target.value)} maxLength={choice.id==='verify'||choice.id==='reject-verification'?1000:5000} rows={3}
        className="w-full rounded-xl border border-border bg-surface p-3 text-sm" required={choice.required}/>
      <label className="mt-4 flex items-start gap-3 text-sm text-copy">
        <input type="checkbox" className="mt-1 accent-brand" checked={confirmed} onChange={e=>setConfirmed(e.target.checked)}/>
        Tôi đã kiểm tra dữ liệu và xác nhận đây chỉ là quy trình nghiệp vụ mô phỏng; không phải chuyển tiền thật.
      </label>
      {mutation.isError?<p role="alert" className="mt-4 text-sm text-danger">{mutation.error.message}</p>:null}
      <div className="mt-5 flex flex-wrap gap-2">
        <Button type="submit" loading={mutation.isPending} disabled={!confirmed||(choice.required&&!reason.trim())}>Xác nhận thao tác</Button>
        <Button type="button" variant="outline" onClick={()=>setSelected('')}>Đóng</Button>
      </div>
    </form>:null}
  </div>;
}
