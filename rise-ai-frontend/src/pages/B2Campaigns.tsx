import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { listActiveCampaigns } from '@/api/campaigns';
import type { CampaignCategory } from '@/api/contracts';
import { categoryLabel } from '@/lib/campaign-display';
import { B2CampaignCards } from './B2CampaignCards';
interface Props { selected?: CampaignCategory; onSelect: (code?: CampaignCategory) => void }
export function B2Campaigns({ selected, onSelect }: Props) {
  const featured = useQuery({
    queryKey: ['public-campaigns', 'b2-homepage', selected],
    queryFn: () => listActiveCampaigns({ category: selected, size: 3 }),
  });
  return (<><section className="campaigns-section" id="campaigns" aria-labelledby="campaigns-title"> <div className="campaigns-top-wave" aria-hidden="true"><svg viewBox="0 0 1440 92" preserveAspectRatio="none"><path d="M0 40c145 19 265-31 425-11s270 55 440 20 316-38 575 13v30H0Z" fill="var(--b2-art-campaign-wave)"></path></svg></div> <div className="content campaigns-content"> <div className="section-head campaign-head"><div><div className="kicker dark-kicker">{"02 / CHIẾN DỊCH ĐANG KÊU GỌI"}</div><h2 id="campaigns-title">{"Cùng cộng đồng"}<br /><span>{"hiện thực hóa thay đổi"}</span></h2></div><div className="intro-copy"><p>{"Khám phá mục tiêu, đối tượng thụ hưởng và tiến độ đóng góp từ Backend. Dữ liệu tài chính trong hệ thống là mô phỏng."}</p><div className="demo-filter">{"BỘ LỌC XEM TRƯỚC: "}<span id="selected-category">{selected ? categoryLabel(selected) : "Tất cả lĩnh vực"}</span> <button id="clear-filter" type="button" hidden={!selected} onClick={() => onSelect(undefined)}>{"Xóa chọn"}</button></div></div></div> <div className="campaign-layout" aria-label="Một campaign chính và hai campaign phụ">{featured.isPending ? <div className="b2-api-message" role="status"><strong>Đang tải chiến dịch…</strong><p>Thông tin được cập nhật từ Backend Riser AI.</p></div> : featured.isError ? <div className="b2-api-message" role="alert"><strong>Chưa tải được chiến dịch</strong><p>Vui lòng thử lại hoặc xem danh sách công khai.</p><Link to="/campaigns">Khám phá chiến dịch</Link></div> : !(featured.data?.data?.length) ? <div className="b2-api-message" role="status"><strong>Chưa có chiến dịch đang kêu gọi</strong><p>Hãy chọn danh mục khác hoặc xem tất cả chiến dịch công khai.</p><button type="button" onClick={() => onSelect(undefined)}>Xóa bộ lọc</button></div> : <B2CampaignCards campaigns={featured.data.data} />}</div> <div className="campaign-end"><span>{"Dữ liệu chiến dịch từ Backend · Đóng góp và giải ngân là mô phỏng, không phải chuyển tiền thật."}</span><a href="#story">{"Từ chiến dịch đến câu chuyện "}<svg><use href="#i-arrow"></use></svg></a></div> </div> </section></>);
}
