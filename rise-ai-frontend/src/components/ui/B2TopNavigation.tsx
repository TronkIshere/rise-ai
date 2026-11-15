import { Link } from 'react-router';
import { B2SectionLink } from './B2SectionLink';

const sections = [
  ['categories', 'Khám phá'],
  ['campaigns', 'Chiến dịch'],
  ['story', 'Câu chuyện'],
  ['milestones', 'Tiến độ'],
  ['transparency', 'Minh bạch'],
] as const;

export function B2TopNavigation({ site = false, showLogin = false }: { site?: boolean; showLogin?: boolean }) {
  const brand = <><span className="mark"><svg aria-hidden="true"><use href="#i-leaf" /></svg></span>{' '}<b>Riser <span>AI</span></b></>;
  const searchIcon = <svg aria-hidden="true"><use href="#i-search" /></svg>;
  const explore = <>Khám phá chiến dịch <span>↗</span></>;

  return <nav className="site-nav content" aria-label="Điều hướng chính">
    {site
      ? <Link to="/" className="wordmark" aria-label="Riser AI — Trang chủ">{brand}</Link>
      : <a className="wordmark" href="#top" aria-label="Riser AI về đầu trang">{brand}</a>}
    <div className="nav-links">
      {sections.map(([target, label]) => site
        ? target === 'campaigns'
          ? <Link key={target} to="/campaigns">{label}</Link>
          : <B2SectionLink key={target} path="/" target={target}>{label}</B2SectionLink>
        : <a key={target} href={'#' + target}>{label}</a>)}
    </div>
    <div className="nav-actions">
      {site
        ? <><B2SectionLink path="/campaigns" target="search" className="nav-search" label="Tìm chiến dịch">{searchIcon}</B2SectionLink><Link className="nav-cta" to="/campaigns">{explore}</Link></>
        : <><a className="nav-search" href="#campaigns" aria-label="Tìm chiến dịch">{searchIcon}</a><a className="nav-cta" href="#campaigns">{explore}</a></>}
      {showLogin ? <Link className="b2-sample-login-link" to="/login">Đăng nhập</Link> : null}
    </div>
  </nav>;
}
