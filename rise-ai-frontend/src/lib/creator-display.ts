export const organizationTypes = [
  {value:'NGO',label:'Tổ chức phi chính phủ'}, {value:'CHARITY_FUND',label:'Quỹ từ thiện'},
  {value:'COMMUNITY_GROUP',label:'Nhóm cộng đồng'}, {value:'SOCIAL_ENTERPRISE',label:'Doanh nghiệp xã hội'},
  {value:'EDUCATIONAL_INSTITUTION',label:'Cơ sở giáo dục'}, {value:'OTHER',label:'Khác'},
] as const;
export const evidenceTypes = [
  {value:'RECEIPT',label:'Biên nhận'}, {value:'INVOICE',label:'Hóa đơn'},
  {value:'DELIVERY_RECORD',label:'Biên bản bàn giao'}, {value:'PHOTO',label:'Ảnh minh chứng'},
  {value:'PROGRESS_REPORT',label:'Báo cáo tiến độ'}, {value:'ACTIVITY_DESCRIPTION',label:'Mô tả hoạt động'},
  {value:'EXTERNAL_REFERENCE',label:'Nguồn tham chiếu'},
] as const;
const campaignStates: Record<string,string> = {
  DRAFT:'Bản nháp', PENDING_REVIEW:'Chờ xét duyệt', APPROVED:'Đã duyệt — chưa công bố',
  REJECTED:'Cần điều chỉnh', ACTIVE:'Đang hoạt động', PAUSED:'Đã tạm dừng',
  COMPLETED:'Hoàn thành', CANCELLED:'Đã hủy',
};
const verificationStates: Record<string,string> = {
  UNVERIFIED:'Chưa xác minh', PENDING_REVIEW:'Đang chờ kiểm duyệt', VERIFIED:'Đã xác minh',
  REJECTED:'Không được xác minh',
};
const disbursementStates: Record<string,string> = {
  SUBMITTED:'Đã gửi đề nghị', UNDER_REVIEW:'Đang xét duyệt', NEEDS_MORE_EVIDENCE:'Cần thêm minh chứng',
  APPROVED:'Đã phê duyệt (mô phỏng)', REJECTED:'Bị từ chối',
  RELEASED:'Đã ghi nhận giải ngân mô phỏng', CANCELLED:'Đã hủy đề nghị', DISPUTED:'Có tranh chấp',
};
export const campaignState = (value?:string) => value ? campaignStates[value] ?? value : 'Chưa xác định';
export const verificationState = (value?:string) => value ? verificationStates[value] ?? value : 'Chưa xác định';
export const disbursementState = (value?:string) => value ? disbursementStates[value] ?? value : 'Chưa xác định';
export const evidenceState = (value?:string) => value === 'ACCEPTED' ? 'Đã được Admin duyệt' : value === 'REJECTED' ? 'Bị từ chối' : 'Đang chờ Admin duyệt';
export const editableCampaign = (status?:string) => status === 'DRAFT' || status === 'REJECTED';
/** datetime-local is local wall time; API expects an instant. */
export function toInstant(value:string): string {
  const date = new Date(value);
  if (!value || Number.isNaN(date.getTime())) throw new Error('Ngày giờ không hợp lệ.');
  return date.toISOString();
}
export function localInput(value?:string): string {
  if(!value)return '';
  const date = new Date(value);
  if(Number.isNaN(date.getTime()))return '';
  const offset = date.getTimezoneOffset()*60000;
  return new Date(date.getTime()-offset).toISOString().slice(0,16);
}
