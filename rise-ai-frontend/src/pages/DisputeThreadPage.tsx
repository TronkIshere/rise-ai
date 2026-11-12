import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, MessageCircle, ShieldAlert } from 'lucide-react';
import { Link, useParams } from 'react-router';
import { addDisputeMessage, disputeMessages, getDispute, withdrawDispute } from '@/api/donor';
import { useAuth } from '@/auth/auth-context';
import { Container } from '@/components/layout/Container';
import { Button } from '@/components/ui/Button';
import { RecordPagination } from '@/components/ui/RecordPagination';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { dateLabel } from '@/lib/campaign-display';
import { disputeLabel } from '@/lib/donor-display';

export function DisputeThreadPage() {
  const { id }=useParams();
  const { user }=useAuth();
  const client=useQueryClient();
  const [page,setPage]=useState(0);
  const [message,setMessage]=useState('');
  const [notice,setNotice]=useState('');
  const owned=useQuery({queryKey:['donor',user?.id,'dispute-owned',id],queryFn:()=>getDispute(id!),enabled:Boolean(id)});
  const messages=useQuery({queryKey:['donor',user?.id,'messages',id,page],queryFn:()=>disputeMessages(id!,page),enabled:Boolean(id&&owned.data)});
  const invalidate=()=>{void client.invalidateQueries({queryKey:['donor',user?.id]});};
  const send=useMutation({mutationFn:(value:string)=>addDisputeMessage(id!,value),
    onSuccess(){setMessage('');setNotice('Đã gửi tin nhắn.');invalidate();}});
  const withdraw=useMutation({mutationFn:()=>withdrawDispute(id!),onSuccess(){setNotice('Đã rút phản ánh.');invalidate();}});
  async function submit(event:FormEvent<HTMLFormElement>){
    event.preventDefault();
    if (!message.trim() || send.isPending) return;
    try {await send.mutateAsync(message.trim());}catch{/* Error below */}
  }
  if (!id) return <Container className="py-14"><StatusMessage error title="Đường dẫn không hợp lệ"/></Container>;
  return <div className="rise-deep-page deep-donor-dispute">
    <div className="rise-deep-topline" aria-hidden="true">
      <span>DONOR / PHẢN ÁNH</span><span>01 · HỒ SƠ TRAO ĐỔI</span>
    </div>
    <Container className="deep-inner py-10 sm:py-14">
    <Link to="/donor" className="inline-flex min-h-11 items-center gap-2 text-sm font-bold text-brand"><ArrowLeft size={17} aria-hidden="true"/> Về lịch sử đóng góp</Link>
    <h1 className="mt-5 text-3xl font-extrabold text-brand-ink">Chi tiết phản ánh</h1>
    {owned.isPending ? <div className="mt-5"><StatusMessage title="Đang xác minh dữ liệu phản ánh…"/></div>:null}
    {owned.isError ? <div className="mt-5"><StatusMessage error title="Không lấy được dữ liệu" detail={owned.error.message}/></div>:null}
    {owned.data ? <>
      <section className="mt-8 rounded-card border border-border bg-surface p-6">
        <p className="text-xs text-muted">{owned.data.disputeCode??id} · {dateLabel(owned.data.createdAt)}</p>
        <h2 className="mt-2 text-xl font-bold text-brand-ink">{owned.data.title??'Phản ánh'}</h2>
        <p className="mt-2 text-sm font-semibold text-brand">{disputeLabel(owned.data.disputeStatus)}</p>
        <p className="mt-4 whitespace-pre-line text-sm leading-7 text-muted">{owned.data.description}</p>
        {owned.data.resolution ? <p className="mt-4 rounded-xl bg-soft-mint p-4 text-sm">Kết luận của Admin: {owned.data.resolution}</p>:null}
        {owned.data.disputeStatus==='OPEN' && <div className="mt-5"><Button variant="outline" loading={withdraw.isPending} onClick={()=>{if(window.confirm('Bạn muốn rút phản ánh này?'))withdraw.mutate();}}>Rút phản ánh</Button></div>}
      </section>
      {notice?<p className="mt-5 rounded-xl bg-soft-mint p-4 text-sm" role="status">{notice}</p>:null}
      {withdraw.isError?<p role="alert" className="mt-3 text-sm text-danger">{withdraw.error.message}</p>:null}
      <section className="mt-9">
        <div className="flex items-center gap-2"><MessageCircle size={24} className="text-brand" aria-hidden="true"/><h2 className="text-xl font-bold text-brand-ink">Trao đổi</h2></div>
        <div className="mt-5 space-y-3">
          {messages.isPending?<StatusMessage title="Đang tải trao đổi…"/>:null}
          {messages.isError?<StatusMessage error title="Không tải được tin nhắn" detail={messages.error.message}/>:null}
          {messages.isSuccess && !messages.data.data?.length?<StatusMessage title="Chưa có trao đổi"/>:null}
          {messages.data?.data?.map(item=><article className="rounded-xl border border-border bg-surface p-5" key={item.id}>
            <p className="text-xs font-semibold text-muted">{item.authorId===user?.id?'Bạn':'Người tham gia xử lý'} · {dateLabel(item.createdAt)}</p>
            <p className="mt-2 whitespace-pre-line text-sm leading-7 text-copy">{item.message}</p>
          </article>)}
        </div>
        {messages.isSuccess?<RecordPagination page={page} pages={messages.data.totalPages??0} onChange={setPage}/>:null}
      </section>
      {(owned.data.disputeStatus==='OPEN'||owned.data.disputeStatus==='UNDER_REVIEW')&&<form className="mt-8 rounded-card bg-soft-mint p-6" onSubmit={(e)=>void submit(e)}>
        <div className="flex items-center gap-2"><ShieldAlert size={20} className="text-brand" aria-hidden="true"/><label htmlFor="dispute-message" className="font-bold text-brand-ink">Gửi thông tin bổ sung</label></div>
        <textarea id="dispute-message" rows={4} maxLength={5000} required value={message} onChange={e=>setMessage(e.target.value)} className="mt-4 w-full rounded-xl border border-border bg-surface p-3 text-sm"/>
        {send.isError?<p role="alert" className="my-3 text-sm text-danger">{send.error.message}</p>:null}
        <div className="mt-3"><Button type="submit" loading={send.isPending}>Gửi tin nhắn</Button></div>
      </form>}
    </>:null}
    </Container>
  </div>;
}
