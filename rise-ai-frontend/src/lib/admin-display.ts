export const adminStatusFilters:Record<string,{value:string;label:string}[]>={
  organizations:['PENDING_REVIEW','UNVERIFIED','VERIFIED','REJECTED'].map(value=>({value,label:value})),
  campaigns:['PENDING_REVIEW','APPROVED','ACTIVE','PAUSED','REJECTED','COMPLETED','CANCELLED'].map(value=>({value,label:value})),
  donations:['PENDING','CONFIRMED','FAILED','CANCELLED','REFUND_PENDING','PARTIALLY_REFUNDED','REFUNDED'].map(value=>({value,label:value})),
  refunds:['REQUESTED','APPROVED','REJECTED','CANCELLED','RECORDED'].map(value=>({value,label:value})),
  disbursements:['SUBMITTED','UNDER_REVIEW','NEEDS_MORE_EVIDENCE','APPROVED','REJECTED','RELEASED','CANCELLED','DISPUTED'].map(value=>({value,label:value})),
  disputes:['OPEN','UNDER_REVIEW','RESOLVED_UPHELD','RESOLVED_DISMISSED','WITHDRAWN'].map(value=>({value,label:value})),
  adjustments:['REQUESTED','APPLIED','REJECTED','CANCELLED'].map(value=>({value,label:value})),
  settlements:['PROPOSED','EXECUTED','REJECTED','CANCELLED'].map(value=>({value,label:value})),
};
export interface ReviewChoice { id:string;label:string;required:boolean;hint?:string;critical?:boolean }
const act=(id:string,label:string,required=false,critical=false,hint?:string):ReviewChoice=>({id,label,required,critical,hint});
export function reviewChoices(kind:string,status:string):ReviewChoice[]{
  switch(kind){
    case 'organizations': return status==='PENDING_REVIEW'?[act('verify','Xác minh tổ chức',true),act('reject-verification','Từ chối xác minh',true,true)]:[];
    case 'campaigns': return status==='PENDING_REVIEW'?[act('approve','Phê duyệt chiến dịch',false),act('reject','Từ chối chiến dịch',true,true)]:
      status==='ACTIVE'?[act('pause','Tạm dừng chiến dịch',true,true),act('complete','Ghi nhận hoàn thành',false,true)]:
      status==='PAUSED'?[act('resume','Tiếp tục chiến dịch',false)]:[];
    case 'donations':return status==='PENDING'?[act('confirm','Xác nhận đóng góp mô phỏng',false,true,'Tham chiếu mô phỏng (tùy chọn), không phải giao dịch ngân hàng.'),act('fail','Đánh dấu đóng góp thất bại',true,true)]:[];
    case 'refunds':return status==='REQUESTED'?[act('approve','Chấp thuận hoàn tiền mô phỏng'),act('reject','Từ chối hoàn tiền',true,true)]:
      status==='APPROVED'?[act('record','Ghi sổ hoàn tiền mô phỏng',false,true),act('reject','Từ chối hoàn tiền',true,true)]:[];
    case 'disbursements':return status==='SUBMITTED'?[act('start-review','Bắt đầu xét duyệt'),act('reject','Từ chối đề nghị',true,true)]:
      status==='UNDER_REVIEW'?[act('approve','Phê duyệt đề nghị',false,true),act('request-evidence','Yêu cầu thêm minh chứng',true),act('reject','Từ chối đề nghị',true,true)]:
      status==='NEEDS_MORE_EVIDENCE'?[act('reject','Từ chối đề nghị',true,true)]:status==='APPROVED'?[act('release','Ghi sổ giải ngân mô phỏng',false,true),act('reject','Từ chối đề nghị',true,true)]:[];
    case 'disputes':return status==='OPEN'?[act('assign','Nhận xử lý phản ánh',false)]:status==='UNDER_REVIEW'?[act('assign','Giao phản ánh cho tôi',false),act('resolve-upheld','Chấp nhận phản ánh',true,true),act('resolve-dismissed','Bác bỏ phản ánh',true,true)]:[];
    case 'adjustments':return status==='REQUESTED'?[act('approve','Áp dụng điều chỉnh sổ mô phỏng',false,true),act('reject','Từ chối điều chỉnh',true,true),act('cancel','Hủy đề nghị điều chỉnh')]:[];
    case 'settlements':return status==='PROPOSED'?[act('approve','Ghi nhận tất toán mô phỏng',false,true),act('reject','Từ chối tất toán',true,true),act('cancel','Hủy đề nghị tất toán')]:[];
    default:return [];
  }
}
export const shortId=(value?:string)=>value?value.slice(0,8):'—';
