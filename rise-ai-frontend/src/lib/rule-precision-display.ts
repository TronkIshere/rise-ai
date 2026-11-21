/** Display validation only. The Backend remains authoritative for review outcomes. */
interface RuleCounts {
  total?:number;open?:number;acknowledged?:number;
  concernConfirmed?:number;dismissed?:number;precision?:number|null;
}
interface PrecisionAssessment {
  state:'valid'|'no-conclusion'|'unverified';
  concluded:number|null;precisionText:string;issue:string|null;
}
const isCount=(value:unknown):value is number=>typeof value==='number'&&Number.isSafeInteger(value)&&value>=0;
const countFormat=new Intl.NumberFormat('vi-VN');
const percentFormat=new Intl.NumberFormat('vi-VN',{style:'percent',minimumFractionDigits:1,maximumFractionDigits:1});
export const ruleCount=(value:unknown)=>isCount(value)?countFormat.format(value):'—';

export function assessRulePrecision(rule:RuleCounts):PrecisionAssessment {
  const invalid=(issue:string):PrecisionAssessment=>({state:'unverified',concluded:null,precisionText:'—',issue});
  const {total,open,acknowledged,concernConfirmed,dismissed,precision}=rule;
  if(!isCount(total)||!isCount(open)||!isCount(acknowledged)||!isCount(concernConfirmed)||!isCount(dismissed)){
    return invalid('Thiếu số liệu hoặc số ca không hợp lệ.');
  }
  const concluded=concernConfirmed+dismissed;
  const sum=open+acknowledged+concluded;
  if(!Number.isSafeInteger(sum)||sum!==total)return invalid('Tổng số ca không khớp các trạng thái.');
  if(concluded===0){
    // Jackson NON_NULL omits this field for the Backend's null precision.
    return precision===null||precision===undefined
      ?{state:'no-conclusion',concluded,precisionText:'—',issue:null}
      :invalid('Chưa có ca kết luận nhưng tỷ lệ Backend chưa phù hợp.');
  }
  // FindingServiceImpl rounds BigDecimal ratios to scale 4, HALF_UP.
  const denominator=BigInt(concluded);
  const numerator=BigInt(concernConfirmed)*10_000n;
  const expected=Number((2n*numerator+denominator)/(2n*denominator))/10_000;
  if(typeof precision!=='number'||!Number.isFinite(precision)||precision<0||precision>1||precision!==expected){
    return invalid('Tỷ lệ Backend không khớp số ca đã kết luận.');
  }
  return {state:'valid',concluded,precisionText:percentFormat.format(precision),issue:null};
}
