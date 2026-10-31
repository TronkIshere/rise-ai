import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, AlertTriangle, Save } from 'lucide-react';
import { Link, useNavigate, useParams } from 'react-router';
import { useAuth } from '@/auth/auth-context';
import { createCreatorCampaign, getCreatorCampaign, myOrganizations, updateCreatorCampaign } from '@/api/creator';
import type { CampaignCategory, CampaignRequest, CampaignResponse } from '@/api/contracts';
import { Container } from '@/components/layout/Container';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { RecordPagination } from '@/components/ui/RecordPagination';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { categoryOptions } from '@/lib/campaign-display';
import { editableCampaign, localInput, toInstant, verificationState } from '@/lib/creator-display';

type Draft = Omit<CampaignRequest,'startAt'|'endAt'> & {startAt:string;endAt:string};
function initialDraft(initial?:CampaignResponse):Draft {
  return {
    organizationId:initial?.organization?.id??'', title:initial?.title??'',
    shortDescription:initial?.shortDescription??'',fullDescription:initial?.fullDescription??'',
    category:initial?.category??'COMMUNITY',beneficiaryDescription:initial?.beneficiaryDescription??'',
    beneficiaryCount:initial?.beneficiaryCount??0,targetAmount:initial?.targetAmount??0,
    minimumDonationAmount:initial?.minimumDonationAmount??1,
    startAt:localInput(initial?.startAt),endAt:localInput(initial?.endAt),
    location:initial?.location??'',problemStatement:initial?.problemStatement??'',
    solutionDescription:initial?.solutionDescription??'',impactGoals:initial?.impactGoals??'',
    coverImageUrl:initial?.coverImageUrl??'',currency:initial?.currency??'VND',
    galleryImageUrls:initial?.galleryImageUrls??[],budgetBreakdown:initial?.budgetBreakdown??[],
  };
}
function EditForm({ initial, id }: {initial?:CampaignResponse;id?:string}) {
  const navigate=useNavigate();
  const {user}=useAuth();
  const queryClient=useQueryClient();
  const [form,setForm]=useState<Draft>(()=>initialDraft(initial));
  const [orgPage,setOrgPage]=useState(0);
  const [error,setError]=useState('');
  const organizations=useQuery({queryKey:['creator',user?.id,'orgs',orgPage],queryFn:()=>myOrganizations(orgPage)});
  const save=useMutation({
    mutationFn:(data:CampaignRequest)=>id?updateCreatorCampaign(id,data):createCreatorCampaign(data),
    onSuccess(data) {
      void queryClient.invalidateQueries({queryKey:['creator',user?.id]});
      if(data.id)navigate('/creator/campaigns/'+encodeURIComponent(data.id),{replace:true});
    },
  });
  const change=<K extends keyof Draft>(key:K,value:Draft[K])=>setForm(prev=>({...prev,[key]:value}));
  const requiredText=(label:string,key:keyof Pick<Draft,'shortDescription'|'fullDescription'|'problemStatement'|'solutionDescription'|'impactGoals'|'beneficiaryDescription'>,maxLength:number,rows=3)=><div>
    <label className="mb-2 block text-sm font-semibold" htmlFor={'creator-'+key}>{label}</label>
    <textarea id={'creator-'+key} required rows={rows} maxLength={maxLength} className="w-full rounded-xl border border-border bg-surface p-3 text-sm" value={String(form[key]??'')} onChange={e=>change(key,e.target.value)}/>
  </div>;
  async function submit(e:FormEvent<HTMLFormElement>){
    e.preventDefault();if(save.isPending)return;
    if(!form.organizationId){setError('Chọn tổ chức thuộc tài khoản của bạn.');return;}
    if(!Number.isSafeInteger(form.targetAmount)||form.targetAmount<1000||!Number.isSafeInteger(form.minimumDonationAmount)||form.minimumDonationAmount<1||form.minimumDonationAmount>form.targetAmount){setError('Mục tiêu phải từ 1.000 VND; khoản tối thiểu từ 1 VND và không vượt mục tiêu.');return;}
    if(!Number.isInteger(form.beneficiaryCount)||form.beneficiaryCount<0){setError('Số người thụ hưởng phải là số nguyên không âm.');return;}
    try {
      const start=toInstant(form.startAt),end=toInstant(form.endAt);
      if(new Date(end)<=new Date(start)||new Date(end)<=new Date()){setError('Ngày kết thúc phải sau ngày bắt đầu và ở trong tương lai.');return;}
      if(form.budgetBreakdown?.length){
        const total=form.budgetBreakdown.reduce((sum,item)=>sum+item.amount,0);
        if(form.budgetBreakdown.some(item=>!item.item.trim()||!Number.isFinite(item.amount)||item.amount<=0)||Math.abs(total-form.targetAmount)>0.0001){setError('Dự toán phải có tên và số tiền dương; tổng dự toán phải đúng mục tiêu chiến dịch.');return;}
      }
      setError('');
      await save.mutateAsync({...form,startAt:start,endAt:end,coverImageUrl:form.coverImageUrl?.trim()||'',currency:'VND'});
    }catch(reason){if(reason instanceof Error && reason.message==='Ngày giờ không hợp lệ.')setError(reason.message);}
  }
  return <form onSubmit={e=>void submit(e)} className="mt-8 grid gap-6 rounded-card border border-border bg-surface p-6 sm:p-8">
    <div className="rounded-xl bg-soft-mint p-4 text-xs leading-6 text-brand-ink">
      Giai đoạn này chỉ tạo/sửa bản nháp. Để gửi Admin duyệt, tổng kinh phí các cột mốc phải đúng mục tiêu và tổ chức không bị từ chối xác minh.
    </div>
    <section><h2 className="text-lg font-bold text-brand-ink">1. Đơn vị triển khai</h2>
      <label className="mt-4 mb-2 block text-sm font-semibold" htmlFor="creator-org">Tổ chức do bạn quản lý</label>
      <select id="creator-org" required value={form.organizationId} className="min-h-11 w-full rounded-xl border border-border bg-surface px-3" onChange={e=>change('organizationId',e.target.value)}>
        <option value="">Chọn tổ chức</option>
        {initial?.organization?.id && !organizations.data?.data?.some(o=>o.id===initial.organization?.id)?<option value={initial.organization.id}>{initial.organization.name} (hiện tại)</option>:null}
        {organizations.data?.data?.map(org=><option key={org.id} value={org.id} disabled={org.verificationStatus==='REJECTED'}>{org.name} — {verificationState(org.verificationStatus)}</option>)}
      </select>
      {organizations.isPending?<p className="mt-2 text-sm text-muted">Đang tải tổ chức…</p>:null}
      {organizations.isError?<p role="alert" className="mt-2 text-sm text-danger">{organizations.error.message}</p>:null}
      {organizations.isSuccess?<RecordPagination page={orgPage} pages={organizations.data.totalPages??0} onChange={setOrgPage}/>:null}
      <Link to="/creator" className="mt-2 inline-block text-xs font-semibold text-brand underline">Quản lý tổ chức / yêu cầu xác minh</Link>
    </section>
    <section className="grid gap-5 sm:grid-cols-2">
      <h2 className="text-lg font-bold text-brand-ink sm:col-span-2">2. Nội dung chiến dịch</h2>
      <div className="sm:col-span-2"><Input id="creator-title" label="Tên chiến dịch" required maxLength={200} value={form.title} onChange={e=>change('title',e.target.value)}/></div>
      <div><label className="mb-2 block text-sm font-semibold" htmlFor="creator-category">Lĩnh vực</label>
        <select id="creator-category" className="min-h-11 w-full rounded-xl border border-border bg-surface px-3" value={form.category} onChange={e=>change('category',e.target.value as CampaignCategory)}>{categoryOptions.map(o=><option key={o.value} value={o.value}>{o.label}</option>)}</select>
      </div>
      <Input id="creator-location" label="Khu vực triển khai" required maxLength={255} value={form.location} onChange={e=>change('location',e.target.value)}/>
      <div className="sm:col-span-2">{requiredText('Tóm tắt', 'shortDescription',500,2)}</div>
      <div className="sm:col-span-2">{requiredText('Câu chuyện đầy đủ','fullDescription',20000,5)}</div>
      {requiredText('Vấn đề','problemStatement',5000)}
      {requiredText('Giải pháp','solutionDescription',5000)}
      <div className="sm:col-span-2">{requiredText('Mục tiêu tác động','impactGoals',5000)}</div>
      <div className="sm:col-span-2">{requiredText('Đối tượng thụ hưởng','beneficiaryDescription',2000)}</div>
      <Input id="creator-beneficiaries" label="Số người thụ hưởng dự kiến" type="number" required min={0} step={1} value={String(form.beneficiaryCount)} onChange={e=>change('beneficiaryCount',Number(e.target.value))}/>
      <Input id="creator-cover" label="URL ảnh bìa HTTPS (tùy chọn)" type="url" pattern="https://.*" maxLength={500} value={form.coverImageUrl} onChange={e=>change('coverImageUrl',e.target.value)}/>
    </section>
    <section className="grid gap-5 sm:grid-cols-2"><h2 className="text-lg font-bold text-brand-ink sm:col-span-2">3. Ngân sách & thời gian — VND mô phỏng</h2>
      <Input id="creator-target" label="Mục tiêu tài trợ (VND)" type="number" min={1000} step={1} required value={String(form.targetAmount)} onChange={e=>change('targetAmount',Number(e.target.value))}/>
      <Input id="creator-minimum" label="Khoản đóng góp tối thiểu" type="number" min={1} step={1} required value={String(form.minimumDonationAmount)} onChange={e=>change('minimumDonationAmount',Number(e.target.value))}/>
      <Input id="creator-start" label="Ngày bắt đầu" type="datetime-local" required value={form.startAt} onChange={e=>change('startAt',e.target.value)}/>
      <Input id="creator-end" label="Ngày kết thúc" type="datetime-local" required value={form.endAt} onChange={e=>change('endAt',e.target.value)}/>
      <div className="sm:col-span-2">
        <h3 className="font-bold text-brand-ink">Chi tiết dự toán (tùy chọn)</h3>
        <p className="mt-2 text-xs text-muted">Nếu nhập các hạng mục, tổng phải bằng mục tiêu. Có thể để trống và quản lý phân bổ bằng các cột mốc ở bước sau.</p>
        {form.budgetBreakdown?.map((item,i)=><div key={i} className="mt-3 grid items-end gap-3 sm:grid-cols-[1fr_190px_auto]">
          <Input id={'budget-item-'+i} label={'Hạng mục '+(i+1)} maxLength={200} required value={item.item} onChange={e=>change('budgetBreakdown',form.budgetBreakdown!.map((x,j)=>j===i?{...x,item:e.target.value}:x))}/>
          <Input id={'budget-amount-'+i} label="Số tiền" type="number" min="1" step="1" required value={String(item.amount)} onChange={e=>change('budgetBreakdown',form.budgetBreakdown!.map((x,j)=>j===i?{...x,amount:Number(e.target.value)}:x))}/>
          <Button type="button" variant="outline" onClick={()=>change('budgetBreakdown',form.budgetBreakdown!.filter((_,j)=>i!==j))}>Xóa</Button>
        </div>)}
        <div className="mt-4"><Button type="button" variant="outline" onClick={()=>change('budgetBreakdown',[...(form.budgetBreakdown??[]),{item:'',amount:0}])} disabled={(form.budgetBreakdown?.length??0)>=50}>+ Hạng mục</Button></div>
      </div>
    </section>
    {error?<p role="alert" className="text-sm text-danger">{error}</p>:null}
    {save.isError?<p role="alert" className="text-sm text-danger">{save.error.message}</p>:null}
    <div className="flex flex-wrap gap-3"><Button type="submit" loading={save.isPending}><Save size={17} aria-hidden="true"/> Lưu bản nháp</Button><Link to="/creator" className="inline-flex min-h-11 items-center rounded-xl border border-border px-5 text-sm font-semibold text-brand">Quay lại</Link></div>
  </form>;
}
export function CreatorCampaignEditorPage() {
  const {id}=useParams();
  const {user}=useAuth();
  const campaign=useQuery({queryKey:['creator',user?.id,'campaign',id],queryFn:()=>getCreatorCampaign(id!),enabled:Boolean(id)});
  const owned=!!id && campaign.data?.creatorId===user?.id;
  return <Container className="py-10 sm:py-14">
    <Link to={id?'/creator/campaigns/'+encodeURIComponent(id):'/creator'} className="inline-flex min-h-11 items-center gap-2 text-sm font-bold text-brand"><ArrowLeft size={17} aria-hidden="true"/> Quay lại</Link>
    <h1 className="mt-4 text-3xl font-extrabold text-brand-ink">{id?'Sửa chiến dịch':'Tạo chiến dịch mới'}</h1>
    {id&&campaign.isPending?<div className="mt-6"><StatusMessage title="Đang tải bản nháp…"/></div>:null}
    {id&&campaign.isError?<div className="mt-6"><StatusMessage error title="Không tải được chiến dịch" detail={campaign.error.message}/></div>:null}
    {id&&campaign.isSuccess&&!owned?<div className="mt-6"><StatusMessage error title="Không phải chiến dịch bạn quản lý"/></div>:null}
    {id&&campaign.isSuccess&&owned&&!editableCampaign(campaign.data.campaignStatus)?<div className="mt-6 flex items-start gap-3 rounded-xl bg-soft-sand p-5 text-sm text-warning"><AlertTriangle size={20} aria-hidden="true"/> Chỉ có thể sửa khi chiến dịch ở trạng thái DRAFT hoặc REJECTED.</div>:null}
    {(!id || (campaign.isSuccess&&owned&&editableCampaign(campaign.data.campaignStatus)))?<EditForm key={id??'new'} id={id} initial={campaign.data}/>:null}
  </Container>;
}
