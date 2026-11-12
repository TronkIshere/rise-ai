import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowUpRight, Building2, ClipboardList, PlusCircle, ShieldCheck } from 'lucide-react';
import { Link } from 'react-router';
import { useAuth } from '@/auth/auth-context';
import { createOrganization, myCreatorCampaigns, myOrganizations, requestOrganizationVerification, updateOrganization } from '@/api/creator';
import type { OrganizationRequest, OrganizationResponse, OrganizationType } from '@/api/contracts';
import { Container } from '@/components/layout/Container';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { RecordPagination } from '@/components/ui/RecordPagination';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { campaignState, organizationTypes, verificationState } from '@/lib/creator-display';
import { amountLabel } from '@/lib/campaign-display';

const blank: OrganizationRequest = {name:'',organizationType:'COMMUNITY_GROUP',contactEmail:'',contactPhone:'',address:'',description:''};
export function CreatorDashboardPage() {
  const {user}=useAuth();
  const queryClient=useQueryClient();
  const key=['creator',user?.id] as const;
  const [orgPage,setOrgPage]=useState(0);
  const [campaignPage,setCampaignPage]=useState(0);
  const [editing,setEditing]=useState<string|null>(null);
  const [form,setForm]=useState<OrganizationRequest>(blank);
  const [formVisible,setFormVisible]=useState(false);
  const [notice,setNotice]=useState('');
  const organizations=useQuery({queryKey:[...key,'orgs',orgPage],queryFn:()=>myOrganizations(orgPage)});
  const campaigns=useQuery({queryKey:[...key,'campaigns',campaignPage],queryFn:()=>myCreatorCampaigns(campaignPage)});
  const invalidate=()=>void queryClient.invalidateQueries({queryKey:key});
  const save=useMutation({
    mutationFn:()=>{
      const payload={...form,name:form.name.trim(),contactEmail:form.contactEmail.trim(),
        contactPhone:form.contactPhone?.trim(),address:form.address?.trim(),description:form.description?.trim()};
      return editing ? updateOrganization(editing,payload) : createOrganization(payload);
    },
    onSuccess(){setFormVisible(false);setEditing(null);setForm(blank);setNotice('Đã lưu tổ chức trong Backend.');invalidate();},
  });
  const verify=useMutation({
    mutationFn:requestOrganizationVerification,
    onSuccess(){setNotice('Đã gửi tổ chức chờ Admin xác minh; không phải đã được xác minh.');invalidate();},
  });
  function editOrg(organization:OrganizationResponse){
    if(!organization.id)return;
    save.reset();
    setEditing(organization.id);setForm({
      name:organization.name,organizationType:organization.organizationType,contactEmail:organization.contactEmail,
      contactPhone:organization.contactPhone??'',address:organization.address??'',description:organization.description??'',
    });setNotice('');setFormVisible(true);
  }
  function createOrg(){save.reset();setForm(blank);setEditing(null);setFormVisible(true);setNotice('');}
  async function submit(event:FormEvent<HTMLFormElement>){
    event.preventDefault();
    if(save.isPending)return;
    try {await save.mutateAsync();} catch {/* Backend message below. */}
  }
  return <div className="rise-dashboard creator-dashboard"><Container className="dashboard-inner py-10 sm:py-14">
    <div className="dashboard-hero flex flex-wrap items-end justify-between gap-4">
      <div>
        <p className="text-xs font-bold uppercase tracking-widest text-brand">CREATOR WORKSPACE</p>
        <h1 className="mt-3 text-3xl font-extrabold text-brand-ink sm:text-4xl">Quản lý hành trình thiện nguyện</h1>
        <p className="mt-3 max-w-2xl text-sm leading-7 text-muted">Tổ chức, dự án và minh chứng đều được quản lý bằng dữ liệu thực của Backend. Mọi dòng tiền chỉ là mô phỏng.</p>
      </div>
      <Link to="/creator/campaigns/new" className="inline-flex min-h-11 items-center gap-2 rounded-xl bg-brand px-5 text-sm font-bold text-white"><PlusCircle size={18} aria-hidden="true"/> Tạo chiến dịch</Link>
    </div>
    <section className="dashboard-metrics" aria-label="Tổng quan không gian Creator">
      <div className="dashboard-metric"><span>Tổ chức của bạn</span><strong>{organizations.isSuccess && typeof organizations.data.totalElements === 'number' ? new Intl.NumberFormat('vi-VN').format(organizations.data.totalElements) : '—'}</strong><small>Tổng số trong Backend</small></div>
      <div className="dashboard-metric"><span>Chiến dịch đã tạo</span><strong>{campaigns.isSuccess && typeof campaigns.data.totalElements === 'number' ? new Intl.NumberFormat('vi-VN').format(campaigns.data.totalElements) : '—'}</strong><small>Gồm bản nháp và đang xét duyệt</small></div>
      <div className="dashboard-metric"><span>Đã xác minh</span><strong>{organizations.isSuccess ? (organizations.data.data ?? []).filter(o=>o.verificationStatus==='VERIFIED').length : '—'}</strong><small>Tổ chức trên trang đang xem</small></div>
    </section>
    {notice?<p role="status" className="mt-6 rounded-xl bg-soft-mint p-4 text-sm text-brand-ink">{notice}</p>:null}
    <section className="dashboard-section mt-10" aria-labelledby="creator-organizations">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="creator-organizations" className="flex items-center gap-2 text-2xl font-extrabold text-brand-ink"><Building2 className="text-brand" aria-hidden="true"/> Tổ chức của tôi</h2>
        <Button variant="outline" disabled={save.isPending} onClick={createOrg}>+ Thêm tổ chức</Button>
      </div>
      {formVisible?<form className="mt-5 grid gap-5 rounded-card border border-border bg-surface p-6 sm:grid-cols-2" onSubmit={e=>void submit(e)}>
        <h3 className="text-lg font-bold text-brand-ink sm:col-span-2">{editing?'Chỉnh sửa tổ chức':'Tạo tổ chức mới'}</h3>
        <Input id="org-name" label="Tên tổ chức" required maxLength={200} value={form.name} onChange={e=>setForm({...form,name:e.target.value})}/>
        <div><label className="mb-2 block text-sm font-semibold" htmlFor="org-type">Loại hình</label><select id="org-type" className="min-h-11 w-full rounded-xl border border-border bg-surface px-3" value={form.organizationType} onChange={e=>setForm({...form,organizationType:e.target.value as OrganizationType})}>{organizationTypes.map(x=><option key={x.value} value={x.value}>{x.label}</option>)}</select></div>
        <Input id="org-email" label="Email liên hệ" type="email" required maxLength={255} value={form.contactEmail} onChange={e=>setForm({...form,contactEmail:e.target.value})}/>
        <Input id="org-phone" label="Số điện thoại (nếu có)" maxLength={30} value={form.contactPhone} onChange={e=>setForm({...form,contactPhone:e.target.value})}/>
        <div className="sm:col-span-2"><Input id="org-address" label="Địa chỉ (nếu có)" maxLength={500} value={form.address} onChange={e=>setForm({...form,address:e.target.value})}/></div>
        <div className="sm:col-span-2"><label htmlFor="org-description" className="mb-2 block text-sm font-semibold">Mô tả hoạt động</label><textarea id="org-description" className="w-full rounded-xl border border-border bg-surface p-3 text-sm" rows={3} maxLength={5000} value={form.description} onChange={e=>setForm({...form,description:e.target.value})}/></div>
        {save.isError?<p role="alert" className="text-sm text-danger sm:col-span-2">{save.error.message}</p>:null}
        <div className="flex flex-wrap gap-3 sm:col-span-2"><Button type="submit" loading={save.isPending}>Lưu tổ chức</Button><Button type="button" variant="outline" disabled={save.isPending} onClick={()=>{setFormVisible(false);save.reset();}}>Đóng</Button></div>
      </form>:null}
      <div className="mt-5 grid gap-4 md:grid-cols-2">
        {organizations.isSuccess && organizations.data.data?.length ? organizations.data.data.map(org=><article key={org.id} className="rounded-card border border-border bg-surface p-6">
          <div className="flex flex-wrap justify-between gap-3"><h3 className="text-lg font-bold text-brand-ink">{org.name}</h3><span className="h-fit rounded-full bg-soft-sand px-3 py-1.5 text-xs text-warning">{verificationState(org.verificationStatus)}</span></div>
          <p className="mt-2 text-sm text-muted">{org.contactEmail}</p>
          {org.verificationNotes?<p className="mt-3 text-xs text-muted">Ghi chú duyệt: {org.verificationNotes}</p>:null}
          <div className="mt-5 flex flex-wrap gap-2"><Button variant="outline" disabled={save.isPending} onClick={()=>editOrg(org)}>Chỉnh sửa</Button>
            {org.id && (org.verificationStatus==='UNVERIFIED'||org.verificationStatus==='REJECTED')?<Button variant="outline" loading={verify.isPending} disabled={save.isPending} onClick={()=>{if(window.confirm('Gửi tổ chức để Admin xem xét xác minh?'))verify.mutate(org.id!);}}>Gửi xác minh</Button>:null}
          </div>
        </article>):null}
      </div>
      {verify.isError?<p role="alert" className="mt-4 text-sm text-danger">{verify.error.message}</p>:null}
      {organizations.isPending?<div className="mt-5"><StatusMessage title="Đang tải tổ chức…"/></div>:null}
      {organizations.isError?<div className="mt-5"><StatusMessage error title="Không thể tải tổ chức" detail={organizations.error.message}/><Button variant="outline" onClick={()=>void organizations.refetch()}>Thử lại</Button></div>:null}
      {organizations.isSuccess && !organizations.data.data?.length?<div className="mt-5"><StatusMessage title="Chưa có tổ chức" detail="Tạo tổ chức trước khi lập dự án; xác minh được Admin xử lý riêng." /></div>:null}
      {organizations.isSuccess?<RecordPagination page={orgPage} pages={organizations.data.totalPages??0} onChange={setOrgPage}/>:null}
    </section>
    <section className="dashboard-section mt-14" aria-labelledby="creator-campaigns">
      <h2 id="creator-campaigns" className="flex items-center gap-2 text-2xl font-extrabold text-brand-ink"><ClipboardList className="text-brand" aria-hidden="true"/> Chiến dịch của tôi</h2>
      <p className="mt-2 text-sm text-muted">Các bản nháp và chiến dịch đã gửi duyệt chỉ xuất hiện trong không gian Creator, không tự động công khai.</p>
      <div className="mt-5 space-y-4">
        {campaigns.isSuccess && campaigns.data.data?.length?campaigns.data.data.map(c=><article key={c.id} className="flex flex-wrap items-center justify-between gap-5 rounded-card border border-border bg-surface p-6">
          <div><p className="text-xs font-semibold text-brand">{campaignState(c.campaignStatus)}</p><h3 className="mt-2 text-lg font-bold text-brand-ink">{c.title}</h3><p className="mt-2 text-sm text-muted">{c.organization?.name??'Chưa có tổ chức'} · {amountLabel(c.targetAmount,c.currency)}</p></div>
          {c.id?<Link className="inline-flex min-h-11 items-center gap-2 rounded-xl border border-border px-4 text-sm font-bold text-brand hover:bg-soft-mint" to={'/creator/campaigns/'+encodeURIComponent(c.id)}>Quản lý <ArrowUpRight size={16} aria-hidden="true"/></Link>:null}
        </article>):null}
      </div>
      {campaigns.isPending?<div className="mt-5"><StatusMessage title="Đang tải chiến dịch…"/></div>:null}
      {campaigns.isError?<div className="mt-5"><StatusMessage error title="Không thể tải chiến dịch" detail={campaigns.error.message}/><Button variant="outline" onClick={()=>void campaigns.refetch()}>Thử lại</Button></div>:null}
      {campaigns.isSuccess && !campaigns.data.data?.length?<div className="mt-5"><StatusMessage title="Chưa có chiến dịch" detail="Lập bản nháp từ một tổ chức do bạn quản lý." /></div>:null}
      {campaigns.isSuccess?<RecordPagination page={campaignPage} pages={campaigns.data.totalPages??0} onChange={setCampaignPage}/>:null}
    </section>
    <div className="mt-10 flex items-start gap-3 rounded-card bg-soft-mint p-5 text-sm text-brand-ink"><ShieldCheck className="shrink-0 text-brand" size={20} aria-hidden="true"/> Creator không tự duyệt tổ chức, chiến dịch hay giải ngân. Mọi bước phê duyệt thuộc Admin.</div>
  </Container></div>;
}
