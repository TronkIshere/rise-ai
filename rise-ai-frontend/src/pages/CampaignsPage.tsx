import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import type { FormEvent } from 'react';
import { RotateCcw, Search } from 'lucide-react';
import type { CampaignCategory, CampaignResponse } from '@/api/contracts';
import { listActiveCampaigns } from '@/api/campaigns';
import { CampaignMedia } from '@/components/ui/CampaignMedia';
import { amountLabel, categoryLabel, categoryOptions, safeImageUrl } from '@/lib/campaign-display';
import {
  campaignHeroArt, campaignLeadArt, campaignMeadowArt,
  campaignPaperTopArt, campaignPaperBottomArt, campaignPagerArt,
  campaignFooterWaveArt, campaignCategoryArt,
} from './campaign-discovery-art';

const categoryDescriptions: Record<CampaignCategory, string> = {
  EDUCATION: 'Học tập và trường lớp',
  COMMUNITY: 'Sáng kiến địa phương',
  FOOD_SECURITY: 'Bữa ăn và dinh dưỡng',
  WATER_SANITATION: 'Nước sạch và vệ sinh',
  SCHOLARSHIP: 'Hỗ trợ học tập',
  INFRASTRUCTURE: 'Không gian và thiết bị',
  HEALTH: 'Chăm sóc sức khỏe',
  OTHER: 'Sáng kiến khác',
};

const categoryVibes: Record<CampaignCategory, string> = {
  EDUCATION: 'education', COMMUNITY: 'community', FOOD_SECURITY: 'food',
  WATER_SANITATION: 'water', SCHOLARSHIP: 'scholarship',
  INFRASTRUCTURE: 'infrastructure', HEALTH: 'health', OTHER: 'other',
};

/** Only trusted, checked-in artwork uses static HTML SVG markup; no API strings enter it. */
function StaticArt({ html }: { html: string }) {
  return <span className="cd-art" aria-hidden="true" dangerouslySetInnerHTML={{ __html: html }} />;
}

function Brand() {
  return <Link to="/" className="wordmark" aria-label="Riser AI — Trang chủ">
    <span className="logo-leaf" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M21 3c-9 0-16 4-16 12a5 5 0 0 0 5 5c8 0 11-9 11-17Z" /><path d="M4 21c2-7 7-12 14-15" /></svg></span>
    <span>Riser <span className="cd-logo-accent">AI</span></span>
  </Link>;
}

function CampaignHeader() {
  return <header className="site-header" id="top"><div className="site-content">
    <Brand />
    <nav className="site-links" aria-label="Điều hướng chính">
      <a href="#search">Khám phá</a>
      <Link to="/campaigns" aria-current="page">Chiến dịch</Link>
      <Link to="/#story">Câu chuyện</Link>
      <Link to="/#milestones">Tiến độ</Link>
      <Link to="/#transparency">Minh bạch</Link>
    </nav>
    <div className="site-actions">
      <a href="#search" className="search-link" aria-label="Tìm chiến dịch"><Search size={19} aria-hidden="true" /></a>
      <a className="nav-cta" href="#results">Khám phá chiến dịch ↗</a>
    </div>
  </div></header>;
}

function CampaignFooter() {
  return <>
    <StaticArt html={campaignFooterWaveArt} />
    <footer id="footer" className="site-footer"><div className="site-content">
      <div className="footer-grid">
        <div className="footer-brand"><Brand />
          <p>Khám phá các sáng kiến cộng đồng và theo dõi từng cột mốc thực hiện.</p>
          <span className="footer-caption">Community Signature / Hướng thiết kế B2</span>
        </div>
        <div className="footer-col"><h2>Khám phá</h2>
          <Link to="/">Trang chủ</Link><Link to="/campaigns">Tất cả chiến dịch</Link><a href="#search">Tìm chiến dịch</a>
        </div>
        <div className="footer-col"><h2>Tài khoản</h2>
          <Link to="/login">Đăng nhập</Link><Link to="/workspace">Khu vực cá nhân</Link>
          <small>Cần có phiên đăng nhập<br />và quyền hợp lệ.</small>
        </div>
        <div className="footer-disclaimer"><span className="footer-warning">BẢN TRÌNH DIỄN MÔ PHỎNG</span>
          <p>Không nhận hoặc chuyển tiền thật. AI chỉ đưa cảnh báo tham khảo; con người có thẩm quyền xét duyệt và quyết định.</p>
          <a href="#results">Quay lại danh sách chiến dịch →</a>
        </div>
      </div>
      <div className="footer-bottom">
        <span>© 2026 Riser AI · CAMPAIGN DISCOVERY · DESKTOP 1440</span>
        <span>Danh sách từ Backend · Giao dịch và tiến độ tài trợ mô phỏng</span>
        <a href="#top">Về đầu trang ↑</a>
      </div>
    </div></footer>
  </>;
}

function CategoryFilter({ selected, onSelect }: { selected?: CampaignCategory; onSelect: (value?: CampaignCategory) => void }) {
  return <section className="filter-section" aria-label="Bộ lọc tám lĩnh vực">
    <div className="toolbar"><div className="filtergroup">
      <button className="all" type="button" aria-pressed={!selected} onClick={() => onSelect(undefined)}>Tất cả lĩnh vực</button>
      <button className="reset" type="button" disabled={!selected} onClick={() => onSelect(undefined)}>Xóa bộ lọc</button>
    </div>
      <div className="current" aria-live="polite">Đang chọn: <strong>{selected ? categoryLabel(selected) : 'Tất cả lĩnh vực'}</strong></div>
    </div>
    <div className="cards" role="group" aria-label="Tám lĩnh vực">
      {categoryOptions.map((item, index) => {
        const code = item.value;
        return <button key={code} className="cat" type="button" data-category={code}
          data-vibe={categoryVibes[code]} aria-pressed={selected === code}
          aria-label={'Lọc chiến dịch theo ' + item.label} onClick={() => onSelect(selected === code ? undefined : code)}>
          <span className="surface"><span className="cat-visual" aria-hidden="true"><StaticArt html={campaignCategoryArt[code]} /></span>
            <span className="cat-copy"><span className="cat-index">{String(index + 1).padStart(2, '0')} / LĨNH VỰC</span>
              <strong>{item.label}</strong><small>{categoryDescriptions[code]}</small></span>
            <span className="cat-check" aria-hidden="true">✓</span><span className="cat-corner" aria-hidden="true">↗</span>
          </span>
        </button>;
      })}
    </div>
  </section>;
}

function CampaignLead({ campaign }: { campaign: CampaignResponse }) {
  const slug = campaign.slug;
  const name = campaign.title?.trim() || 'Chiến dịch đang cập nhật tên';
  return <div className="lead-section cd-lead" id="lead-section">
    <section className="stage" data-variant="b" aria-label="Hồ sơ chiến dịch chính">
      <div className="silhouette">
        <StaticArt html={campaignLeadArt} />
        <div className="story">
          <div><div className="tags">
            <span className="tag">{categoryLabel(campaign.category)}</span>
            <span className="tag">Đang kêu gọi</span>
            <span className="tag">GIAO DỊCH MÔ PHỎNG</span>
          </div>
            <h2>{name}</h2>
            <p className="summary">{campaign.shortDescription?.trim() || 'Nội dung chiến dịch đang được cập nhật từ hệ thống.'}</p>
            <div className="facts">
              <div><strong>Ai cần được hỗ trợ?</strong><p>{campaign.beneficiaryDescription?.trim() || 'Đối tượng thụ hưởng đang được cập nhật.'}</p></div>
              <div><strong>Tiến độ tài trợ (mô phỏng)</strong>
                <p>{amountLabel(campaign.funding?.netRaised, campaign.currency)} / {amountLabel(campaign.targetAmount, campaign.currency)}</p></div>
            </div>
          </div>
          <div className="action-row"><div className="org"><small>ĐƠN VỊ THỰC HIỆN</small>
            <strong>{campaign.organization?.name || 'Chưa công bố đơn vị thực hiện'}</strong></div>
            {slug ? <Link className="read-link" to={'/campaigns/' + encodeURIComponent(slug)}>Đọc hồ sơ đầy đủ ↗</Link>
              : <span className="read-link" aria-disabled="true">Chưa có đường dẫn hồ sơ</span>}
          </div>
        </div>
        <div className="art-label">ẢNH CONCEPT B2 · KHÔNG PHẢI MINH CHỨNG CHIẾN DỊCH</div>
        <div className="base-guide" aria-hidden="true" />
      </div>
    </section>
  </div>;
}

function CampaignDossier({ campaign }: { campaign: CampaignResponse }) {
  const slug = campaign.slug;
  const image = safeImageUrl(campaign.coverImageUrl);
  const title = campaign.title?.trim() || 'Chiến dịch chưa có tiêu đề';
  return <article className="dossier dossier-v11" data-vibe={categoryVibes[campaign.category ?? 'OTHER']}
    aria-label={'Hồ sơ chiến dịch ' + title}>
    <div className="dossier-paper">
      <div className="paper-fibers" aria-hidden="true" /><div className="holes" aria-hidden="true" />
      <div className="photo-frame">
        {image ? <CampaignMedia src={image} alt={'Ảnh đại diện do chiến dịch cung cấp: ' + title} className="cd-media" />
          : <div className="cd-art-fallback" role="img" aria-label={'Minh họa lĩnh vực ' + categoryLabel(campaign.category)}>
            <StaticArt html={campaignCategoryArt[campaign.category ?? 'OTHER']} />
          </div>}
        <span className="photo-caption">{image ? 'ẢNH TỪ HỒ SƠ' : 'MINH HỌA LĨNH VỰC'}</span>
      </div>
      <span className="washi-tape" aria-hidden="true" />
      <div className="dossier-copy">
        <div className="dossier-meta"><span className="category-chip">
          <span className="category-glyph" aria-hidden="true">✦</span>{categoryLabel(campaign.category)}
        </span>
          <span className="status"><span className="live-dot" aria-hidden="true" /> ĐANG KÊU GỌI</span>
          <span className="demo-stamp">GIAO DỊCH MÔ PHỎNG</span>
        </div>
        <h2>{title}</h2>
        <p>{campaign.shortDescription?.trim() || 'Nội dung chiến dịch đang được cập nhật.'}</p>
        <section className="dossier-narrative" aria-label="Nhu cầu và cách hỗ trợ">
          <h3 className="narrative-title"><span className="narrative-symbol" aria-hidden="true">✦</span> Nhu cầu &amp; cách hỗ trợ</h3>
          <p className="narrative-copy">
            {campaign.beneficiaryDescription?.trim() || 'Nhu cầu hỗ trợ sẽ được cập nhật từ Backend.'}
            {campaign.solutionDescription?.trim() ? ' · ' + campaign.solutionDescription.trim() : ''}
          </p>
        </section>
        <div className="org">
          <strong>{campaign.organization?.name || 'Đơn vị thực hiện chưa cập nhật'}</strong>
          <span> · Tài trợ mô phỏng: {amountLabel(campaign.funding?.netRaised, campaign.currency)}</span>
        </div>
      </div>
      {slug ? <Link className="read-profile" to={'/campaigns/' + encodeURIComponent(slug)}
        aria-label={'Đọc hồ sơ chiến dịch ' + title}>Đọc hồ sơ <span aria-hidden="true">↗</span></Link>
        : <span className="read-profile" aria-disabled="true">Chưa có hồ sơ</span>}
      <span className="paper-corner-fold" aria-hidden="true" /><span className="paper-crease" aria-hidden="true" />
    </div>
    <span className="paperclip" aria-hidden="true" />
  </article>;
}

function visiblePageNumbers(page: number, pages: number): (number | string)[] {
  if (pages <= 7) return Array.from({ length: pages }, (_, n) => n);
  const set = new Set([0, pages - 1, page - 1, page, page + 1]);
  if (page <= 2) [1, 2, 3].forEach((n) => set.add(n));
  if (page >= pages - 3) [pages - 4, pages - 3, pages - 2].forEach((n) => set.add(n));
  const sorted = [...set].filter((n) => n >= 0 && n < pages).sort((a, b) => a - b);
  const result: (number | string)[] = [];
  sorted.forEach((n, i) => {
    if (i && n - sorted[i - 1]! > 1) result.push('…');
    result.push(n);
  });
  return result;
}

function CampaignPager({ page, pages, total, shown, onPage }: { page: number; pages: number; total?: number; shown: number; onPage: (n: number) => void }) {
  const start = page * 9 + 1, end = total === undefined ? page * 9 + shown : Math.min(total, page * 9 + shown);
  return <section className="pagination-landscape" id="pagination-landscape" aria-label="Điều hướng trang kết quả">
    <StaticArt html={campaignPagerArt} />
    <p className="pagination-overline">HÀNH TRÌNH KHÁM PHÁ</p>
    <p className="pagination-count" aria-live="polite">
      {pages > 0 ? 'Trang ' + (page + 1) + ' / ' + pages + ' · ' + start + '–' + end + (total !== undefined ? ' / ' + total : '') + ' hồ sơ' : 'Đã hiển thị kết quả'}
    </p>
    <div id="pagination-slot" className="cd-pager">
      {pages > 1 ? <nav className="pagination-controls" aria-label="Phân trang chiến dịch">
        <button type="button" className="nav-button" disabled={page === 0} onClick={() => onPage(page - 1)}>← Trước</button>
        {visiblePageNumbers(page, pages).map((n, index) => typeof n === 'number'
          ? <button key={n} type="button" data-current={n === page ? 'true' : undefined}
            aria-current={n === page ? 'page' : undefined} aria-label={'Trang ' + (n + 1)}
            onClick={() => onPage(n)}>{n + 1}</button>
          : <span key={'ellipsis-' + index} className="ellipsis" aria-hidden="true">…</span>)}
        <button type="button" className="nav-button" disabled={page >= pages - 1} onClick={() => onPage(page + 1)}>Tiếp →</button>
      </nav> : <span className="pager-hidden">Đã hiển thị toàn bộ kết quả</span>}
    </div>
  </section>;
}

/** Design baseline: Campaign Discovery HTML PR #33 · Desktop 1440. API data is never replaced by mock fixtures. */
export function CampaignsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const query = (searchParams.get('q') ?? '').trim().slice(0, 120);
  const selected = categoryOptions.find(({ value }) => value === searchParams.get('category'))?.value;
  const pageParam = Number(searchParams.get('page') ?? 0);
  const page = Number.isSafeInteger(pageParam) && pageParam >= 0 ? Math.min(pageParam, 10000) : 0;
  const hasFilters = Boolean(query || selected);
  const campaigns = useQuery({
    queryKey: ['public-campaigns', 'campaign-discovery', query, selected, page],
    queryFn: () => listActiveCampaigns({ q: query, category: selected, page, size: 9 }),
  });
  const results = campaigns.data?.data ?? [];
  const [lead, ...dossiers] = results.slice(0, 9);
  const total = campaigns.data?.totalElements;
  const pages = campaigns.data?.totalPages ?? (results.length ? page + 1 : 0);

  function updateParams(next: { q?: string; category?: CampaignCategory; page?: number }) {
    const params = new URLSearchParams();
    const q = next.q ?? query;
    const category = 'category' in next ? next.category : selected;
    const newPage = next.page ?? 0;
    if (q.trim()) params.set('q', q.trim().slice(0, 120));
    if (category) params.set('category', category);
    if (newPage > 0) params.set('page', String(newPage));
    setSearchParams(params);
  }
  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const q = String(new FormData(event.currentTarget).get('q') ?? '');
    updateParams({ q, page: 0 });
  }
  function changePage(next: number) {
    if (next < 0 || next >= pages || next === page) return;
    updateParams({ page: next });
    document.getElementById('results')?.scrollIntoView({ behavior: 'auto', block: 'start' });
  }
  function clearFilters() { setSearchParams(new URLSearchParams()); }

  return <div className="campaign-discovery" data-design="campaign-discovery-b2-desktop-1440">
    <a className="sr-only focus:not-sr-only" href="#campaign-main">Bỏ qua điều hướng</a>
    <div className="preview-ribbon" role="note">
      Riser AI · CAMPAIGN DISCOVERY · DỮ LIỆU TỪ BACKEND · GIAO DỊCH MÔ PHỎNG
    </div>
    <CampaignHeader />
    <div className="main-body">
      <main className="site-content" id="campaign-main">
        <section className="introduction" aria-labelledby="page-title">
          <div className="intro-landscape" aria-hidden="true"><StaticArt html={campaignHeroArt} /></div>
          <p className="eyebrow-integrated">KHÁM PHÁ / CHIẾN DỊCH CỘNG ĐỒNG</p>
          <h1 id="page-title">Khám phá các sáng kiến cộng đồng</h1>
          <p>Khám phá các sáng kiến cộng đồng trong hệ thống. Hồ sơ lấy từ Backend; các giao dịch và số liệu tài trợ trong nền tảng là mô phỏng.</p>
        </section>
        <section className="search-area" id="search" aria-label="Tìm kiếm chiến dịch">
          <form className="search-form" role="search" onSubmit={submitSearch}>
            <Search size={20} aria-hidden="true" />
            <label htmlFor="campaign-query" className="sr-only">Tìm theo tên hoặc nội dung chiến dịch</label>
            <input key={query} type="search" id="campaign-query" name="q" defaultValue={query}
              placeholder="Tìm kiếm chiến dịch, vấn đề hoặc lĩnh vực…" maxLength={120} />
            <button type="submit">Tìm kiếm ↗</button>
          </form>
        </section>
        <CategoryFilter selected={selected} onSelect={(category) => updateParams({ category, page: 0 })} />
        <section id="results" aria-label="Kết quả khám phá">
          <div className="result-utility">
            <p className="result-count" aria-live="polite">
              {campaigns.isPending ? 'Đang tải chiến dịch…'
                : campaigns.isError ? 'Chưa tải được chiến dịch'
                  : results.length ? 'Đang hiển thị ' + (page * 9 + 1) + '–' + (page * 9 + results.length) +
                    (typeof total === 'number' ? ' / ' + new Intl.NumberFormat('vi-VN').format(total) : '') + ' chiến dịch'
                    : 'Không có chiến dịch phù hợp'}
            </p>
            <p className="result-quiet">Danh sách ACTIVE từ Backend · Đóng góp mô phỏng</p>
          </div>
          {hasFilters ? <button type="button" className="cd-clear-filter" onClick={clearFilters}>
            <RotateCcw size={15} aria-hidden="true" /> Xóa bộ lọc hiện tại
          </button> : null}
          {campaigns.isPending ? <div className="cd-loading" role="status">Đang tải danh sách chiến dịch từ Backend…</div> : null}
          {campaigns.isError ? <div className="cd-error" role="alert">
            <strong>Không thể tải chiến dịch</strong><p>{campaigns.error.message}</p>
            <button type="button" className="cd-status-action" onClick={() => void campaigns.refetch()}>Thử lại</button>
          </div> : null}
          {campaigns.isSuccess && !results.length ? <div className="cd-empty" role="status">
            <strong>{hasFilters ? 'Không tìm thấy chiến dịch phù hợp' : 'Chưa có chiến dịch công khai'}</strong>
            <p>Không thêm dữ liệu minh họa vào kết quả thật. Hãy thử từ khóa hoặc lĩnh vực khác.</p>
            {hasFilters ? <button type="button" className="cd-status-action" onClick={clearFilters}>Xem toàn bộ chiến dịch</button> : null}
          </div> : null}
          {campaigns.isSuccess && lead ? <>
            <CampaignLead campaign={lead} />
            <div className="campaign-natural-continuum">
              <StaticArt html={campaignMeadowArt} />
              <section id="paper-section" className="review-section campaign-page-dossier" aria-labelledby="paper-title">
                <StaticArt html={campaignPaperTopArt} />
                <div className="content">
                  <header className="section-header"><div className="header-copy">
                    <h2 id="paper-title">Hồ sơ chiến dịch cộng đồng</h2>
                    <p>Các hồ sơ được hiển thị theo dữ liệu từ Backend; thông tin tài trợ và giao dịch trong hệ thống là mô phỏng.</p>
                  </div></header>
                  {dossiers.length ? <div className="dossiers" id="reading-results" aria-label="Các hồ sơ tiếp theo">
                    {dossiers.map((campaign, i) => <CampaignDossier key={campaign.id ?? campaign.slug ?? i} campaign={campaign} />)}
                  </div> : <p className="cd-page-info">Không còn hồ sơ phụ trên trang này.</p>}
                </div>
                <StaticArt html={campaignPaperBottomArt} />
              </section>
              <CampaignPager page={page} pages={pages} total={total} shown={results.length} onPage={changePage} />
            </div>
          </> : null}
        </section>
      </main>
    </div>
    <CampaignFooter />
  </div>;
}
