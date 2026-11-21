import type { ReactNode } from 'react';
import { ArrowUpRight } from 'lucide-react';
import { Link } from 'react-router';
import type { DonationResponse } from '@/api/contracts';
import { amountLabel, dateLabel } from '@/lib/campaign-display';
import { donationLabel } from '@/lib/donor-display';

/** Existing Backend donation record, never a payment or on-chain receipt. */
export function DonorReceiptCard({donation,campaignTitle,children}:{donation:DonationResponse;campaignTitle:string;children?:ReactNode}){
  const status=donation.donationStatus??'UNKNOWN';
  return <div className="donor-receipt-b2 u3-receipt" data-donation-status={status} data-donation-id={donation.id} aria-label="Thông tin biên nhận đóng góp mô phỏng">
    <div className="u3-receipt-head"><span>02 / RECORD · BIÊN NHẬN</span><span aria-hidden="true">♡</span></div>
    <div className="u3-receipt-body"><span className="u3-status" data-status={status}>{donationLabel(donation.donationStatus)??'Chưa xác định'}</span>
      <h3 className="u3-receipt-title">{campaignTitle}</h3><p className="u3-bigamount">{amountLabel(donation.amount,donation.currency)}</p>
      <dl className="u3-fields">
        <div className="u3-field"><dt>Mã khoản đóng góp</dt><dd>{donation.donationCode||'Chưa có mã từ Backend'}</dd></div>
        <div className="u3-field"><dt>Ngày tạo yêu cầu</dt><dd>{dateLabel(donation.createdAt)}</dd></div>
        {donation.confirmedAt?<div className="u3-field"><dt>Thời điểm xác nhận</dt><dd>{dateLabel(donation.confirmedAt)}</dd></div>:null}
        {donation.simulatedReference?<div className="u3-field"><dt>Tham chiếu giả lập</dt><dd>{donation.simulatedReference}</dd></div>:null}
        {donation.anonymous?<div className="u3-field"><dt>Quyền riêng tư</dt><dd>Ẩn tên trên danh sách công khai</dd></div>:null}
      </dl>
      <p className="u3-note">Đây là bản ghi mô phỏng từ Backend, không phải chứng từ thanh toán thật. Chưa có biên nhận on-chain riêng cho khoản đóng góp này; trạng thái giải ngân không thay thế xác nhận đóng góp.</p>
      {children}
      <Link className="u3-smallbtn" to="/transparency">Xem nguyên tắc đối chiếu chung <ArrowUpRight size={16} aria-hidden="true"/></Link>
    </div>
  </div>;
}
