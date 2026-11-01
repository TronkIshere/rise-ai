/** Local display-only strings; lib must not import API layer types. */
type FindingReviewStatus='OPEN'|'ACKNOWLEDGED'|'CONCERN_CONFIRMED'|'DISMISSED';
type FindingSeverity='INFO'|'LOW'|'MEDIUM'|'HIGH'|'CRITICAL';

export const reviewStates:FindingReviewStatus[]=['OPEN','ACKNOWLEDGED','CONCERN_CONFIRMED','DISMISSED'];
export const severityStates:FindingSeverity[]=['INFO','LOW','MEDIUM','HIGH','CRITICAL'];
export const findingStatusText:Record<FindingReviewStatus,string>={
  OPEN:'Chưa đánh giá',ACKNOWLEDGED:'Đã ghi nhận',CONCERN_CONFIRMED:'Xác nhận quan ngại',DISMISSED:'Không đủ căn cứ',
};
export const severityText:Record<FindingSeverity,string>={
  INFO:'Thông tin',LOW:'Thấp',MEDIUM:'Trung bình',HIGH:'Cao',CRITICAL:'Rất cao',
};
export const reviewTargets=(status?:FindingReviewStatus):Exclude<FindingReviewStatus,'OPEN'>[]=>{
  if(status==='OPEN')return ['ACKNOWLEDGED','CONCERN_CONFIRMED','DISMISSED'];
  if(status==='ACKNOWLEDGED')return ['CONCERN_CONFIRMED','DISMISSED'];
  return [];
};
export function timeText(value?:string){
  if(!value)return 'Không có thời điểm';
  const date=new Date(value);
  return Number.isNaN(date.valueOf())?'Không có thời điểm':new Intl.DateTimeFormat('vi-VN',{
    day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit',
  }).format(date);
}
export const shortRef=(value?:string)=>value?value.slice(0,12):'—';
export const countText=(value?:number)=>typeof value==='number'?new Intl.NumberFormat('vi-VN').format(value):'—';
export function readableJson(value:unknown){
  if(value==null)return 'Không có dữ liệu';
  try{return JSON.stringify(value,null,2)??'Không có dữ liệu';}catch{return 'Không thể hiển thị dữ liệu';}
}
