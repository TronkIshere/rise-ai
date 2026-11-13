import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, ArrowUpRight, Check, Clock3, MessageCircle, Scale, Send, ShieldAlert, UserRound } from 'lucide-react';
import { Link, useParams } from 'react-router';
import { addDisputeMessage, disputeMessages, getCampaignById, getDispute, withdrawDispute } from '@/api/donor';
import { useAuth } from '@/auth/auth-context';
import { Container } from '@/components/layout/Container';
import { Button } from '@/components/ui/Button';
import { RecordPagination } from '@/components/ui/RecordPagination';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { dateLabel, safeImageUrl } from '@/lib/campaign-display';
import { disputeLabel, disputeTypeLabel } from '@/lib/donor-display';

export function DisputeThreadPage() {
  const { id } = useParams();
  if (!id) return <Container className="py-14"><StatusMessage error title="Đường dẫn không hợp lệ" /></Container>;
  return <DisputeDossier key={id} id={id} />;
}

function DisputeDossier({ id }: { id: string }) {
  const { user }=useAuth();
  const client=useQueryClient();
  const [page,setPage]=useState(0);
  const [message,setMessage]=useState('');
  const [notice,setNotice]=useState('');
  const [coverFailed, setCoverFailed] = useState(false);
  const owned=useQuery({queryKey:['donor',user?.id,'dispute-owned',id],queryFn:()=>getDispute(id)});
  const messages=useQuery({queryKey:['donor',user?.id,'messages',id,page],queryFn:()=>disputeMessages(id,page),enabled:Boolean(owned.data)});
  const invalidate=()=>{void client.invalidateQueries({queryKey:['donor',user?.id]});};
  const send=useMutation({mutationFn:(value:string)=>addDisputeMessage(id,value),
    onSuccess(){setMessage('');setNotice('Đã gửi tin nhắn.');invalidate();}});
  const withdraw=useMutation({mutationFn:()=>withdrawDispute(id),onSuccess(){setNotice('Đã rút phản ánh.');invalidate();}});
  async function submit(event:FormEvent<HTMLFormElement>){
    event.preventDefault();
    if (!message.trim() || send.isPending) return;
    try {await send.mutateAsync(message.trim());}catch{/* Error below */}
  }
  const campaignId = owned.data?.campaignId;
  const campaign = useQuery({
    queryKey: ['donor', user?.id, 'dispute-campaign', campaignId],
    queryFn: () => getCampaignById(campaignId!),
    enabled: Boolean(campaignId),
    retry: false,
  });
  const dispute = owned.data;
  const campaignCover = safeImageUrl(campaign.data?.coverImageUrl);
  const hasCasePhoto = Boolean(campaignCover && new URL(campaignCover).hostname !== 'placehold.co' && !coverFailed);
  return <div className="rise-deep-page deep-donor-dispute">
    <header className="b2-concept-banner b2-dispute-banner">
      <div>
        <Link to="/donor"><ArrowLeft size={17} aria-hidden="true" /> Về lịch sử đóng góp</Link>
        <h1>Phản ánh &amp; <em>Tranh chấp</em></h1>
        <p>Theo dõi phản ánh và trao đổi thông tin để làm rõ việc thực hiện chiến dịch.</p>
      </div>
      <svg className="b2-dossier-paper-edge" viewBox="0 0 1440 62" preserveAspectRatio="none" aria-hidden="true" focusable="false">
        <path d="M0 41C144 12 231 38 332 30c124-10 182-30 267-25 117 6 135 36 251 36 115 0 187-19 295-19 108 0 201 24 295 7v33H0Z" />
      </svg>
    </header>
    <Container className="deep-inner py-10 sm:py-14">
      {owned.isPending ? <StatusMessage title="Đang xác minh dữ liệu phản ánh…" /> : null}
      {owned.isError ? <div>
        <StatusMessage error title="Không lấy được dữ liệu" detail={owned.error.message} />
        <Button variant="outline" onClick={() => void owned.refetch()}>Tải lại hồ sơ</Button>
      </div> : null}
      {dispute ? <div className="b2-dossier">
        <aside className="b2-case-file" aria-label="Tóm tắt hồ sơ">
          <div className="b2-case-code"><span><Scale size={19} aria-hidden="true" /> Hồ sơ tranh chấp</span>{dispute.disputeCode && <strong>{dispute.disputeCode}</strong>}</div>
          {campaign.data && <div className="b2-case-campaign">
            <figure>
              {hasCasePhoto ? <img src={campaignCover} alt={campaign.data.title ?? 'Chiến dịch liên quan'} onError={() => setCoverFailed(true)} />
                : <><img src="/b2-home/story-community-pair.jpg" alt="" /><figcaption>Ảnh minh họa · Hồ sơ cộng đồng</figcaption></>}
            </figure>
            <p>Chiến dịch liên quan</p>
            {campaign.data.slug ? <Link to={'/campaigns/' + encodeURIComponent(campaign.data.slug)}>
              <span>{campaign.data.title ?? 'Xem chiến dịch'}</span><ArrowUpRight size={18} aria-hidden="true" />
            </Link> : <strong>{campaign.data.title}</strong>}
            {campaign.data.shortDescription && <p className="b2-case-campaign-description">{campaign.data.shortDescription}</p>}
          </div>}
          <dl>
            <div><UserRound aria-hidden="true" /><dt>Loại phản ánh</dt><dd>{disputeTypeLabel(dispute.disputeType)}</dd></div>
            <div><Clock3 aria-hidden="true" /><dt>Thời gian tạo</dt><dd>{dateLabel(dispute.createdAt)}</dd></div>
            <div><ShieldAlert aria-hidden="true" /><dt>Trạng thái hiện tại</dt><dd><span className="b2-case-status">{disputeLabel(dispute.disputeStatus)}</span></dd></div>
          </dl>
          {dispute.disputeStatus === 'OPEN' && <Button variant="outline" className="b2-case-withdraw" loading={withdraw.isPending} onClick={() => {
            if (window.confirm('Bạn muốn rút phản ánh này?')) withdraw.mutate();
          }}>Rút phản ánh</Button>}
          {withdraw.isError ? <p role="alert" className="text-danger">{withdraw.error.message}</p> : null}
        </aside>
        <div className="b2-case-content">
          <h2>{dispute.title ?? 'Chi tiết phản ánh'}</h2>
          <p className="whitespace-pre-line">{dispute.description}</p>
          <ol className="b2-case-timeline" aria-label="Tiến trình hồ sơ">
            <li>
              <span className="b2-case-timeline-marker" aria-hidden="true"><Check size={15} /></span>
              <strong>Đã tiếp nhận</strong>
              {dispute.createdAt && <time dateTime={dispute.createdAt}>{dateLabel(dispute.createdAt)}</time>}
            </li>
            {dispute.disputeStatus && <li aria-current="step">
              <span className="b2-case-timeline-marker" aria-hidden="true" />
              <strong>{disputeLabel(dispute.disputeStatus)}</strong>
              <small>Trạng thái hiện tại</small>
            </li>}
          </ol>
          {dispute.resolution && <p className="b2-case-resolution whitespace-pre-line"><strong>Kết luận của Admin</strong><br />{dispute.resolution}</p>}
          {notice && <p role="status" className="b2-case-notice">{notice}</p>}
          <section className="b2-conversation" aria-labelledby="dispute-messages">
            <h3 id="dispute-messages"><MessageCircle size={23} aria-hidden="true" /> Trao đổi{typeof messages.data?.totalElements === 'number' ? ' (' + messages.data.totalElements + ')' : ''}</h3>
            <div className="b2-message-list">
              {messages.isPending && <StatusMessage title="Đang tải trao đổi…" />}
              {messages.isError && <div>
                <StatusMessage error title="Không tải được tin nhắn" detail={messages.error.message} />
                <Button variant="outline" loading={messages.isFetching} onClick={() => void messages.refetch()}>Tải lại trao đổi</Button>
              </div>}
              {messages.isSuccess && !messages.data.data?.length && <StatusMessage title="Chưa có trao đổi" />}
              {messages.data?.data?.map(item => {
                const mine = Boolean(user?.id && item.authorId === user.id);
                return <article className={mine ? 'b2-message b2-message-own' : 'b2-message'} key={item.id}>
                  <UserRound size={36} aria-hidden="true" />
                  <div><p><strong>{mine ? 'Bạn' : 'Người tham gia xử lý'}</strong> <time dateTime={item.createdAt}>{dateLabel(item.createdAt)}</time></p>
                  <p className="whitespace-pre-line">{item.message}</p></div>
                </article>;
              })}
              {messages.isSuccess && <RecordPagination page={page} pages={messages.data.totalPages ?? 0} onChange={setPage} />}
            </div>
            {(dispute.disputeStatus === 'OPEN' || dispute.disputeStatus === 'UNDER_REVIEW') && <form className="b2-dispute-reply" onSubmit={event => void submit(event)}>
              <label htmlFor="dispute-message">Gửi thông tin bổ sung</label>
              <div><textarea id="dispute-message" rows={2} maxLength={5000} required value={message} onChange={event => setMessage(event.target.value)} placeholder="Nhập phản hồi của bạn…" />
              <Button type="submit" loading={send.isPending}><Send size={18} aria-hidden="true" /> Gửi tin nhắn</Button></div>
              {send.isError && <p role="alert" className="text-danger">{send.error.message}</p>}
            </form>}
          </section>
        </div>
      </div> : null}
    </Container>
  </div>;
}
