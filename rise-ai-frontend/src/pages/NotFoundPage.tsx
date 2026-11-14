import { ArrowRight, MoveUpRight, Radio, SkipForward } from 'lucide-react';
import { useEffect, useRef, useState, type MouseEvent, type PointerEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { B2IconSymbols } from '@/components/ui/B2IconSymbols';
import './NotFoundPage.css';

const reducedMotion = '(prefers-reduced-motion: reduce)';
const introDuration = 2550;
const exitDuration = 560;

function PortalLandscape() {
  return <>
    <svg className="lost-valley" viewBox="0 0 1440 900" preserveAspectRatio="xMidYMid slice" aria-hidden="true" focusable="false">
      <defs>
        <linearGradient id="lost-sky" x2="0" y2="1">
          <stop className="lost-sky-top"/>
          <stop className="lost-sky-bottom" offset="1"/>
        </linearGradient>
        <radialGradient id="lost-halo">
          <stop className="lost-halo-color"/>
          <stop className="lost-halo-color" offset="1" stopOpacity="0"/>
        </radialGradient>
        <pattern id="lost-stars" width="131" height="113" patternUnits="userSpaceOnUse">
          <circle cx="17" cy="23" r="1.2"/><circle cx="71" cy="66" r=".85"/><circle cx="113" cy="10" r="1.6"/>
          <circle cx="24" cy="97" r=".8"/><circle cx="99" cy="102" r="1"/>
        </pattern>
        <symbol id="lost-fir" viewBox="0 0 68 142">
          <path d="M34 2 52 45 43 42 62 84 51 79 67 112 39 108 39 140 29 140 29 108 2 114 18 79 7 84 25 42 16 46Z"/>
        </symbol>
      </defs>
      <path className="lost-sky" d="M0 0H1440V900H0Z"/>
      <path className="lost-stars" d="M0 0H1440V530H0Z"/>
      <ellipse className="lost-atmosphere" cx="1000" cy="444" rx="580" ry="415"/>
      <g className="lost-ridges">
        <path d="M0 544 94 434 149 464 234 363 312 442 411 320 487 395 559 360 628 440 721 363 807 458 887 354 952 397 1034 286 1142 402 1236 326 1352 462 1440 395V900H0Z"/>
        <path d="M0 664 132 544 225 568 338 488 471 625 580 538 720 611 877 523 1008 596 1126 499 1216 549 1337 483 1440 554V900H0Z"/>
        <path d="M0 743Q198 612 298 684T530 703Q659 668 784 708T1051 696Q1210 644 1440 677V900H0Z"/>
      </g>
      <g className="lost-contours" fill="none">
        <path d="M0 778Q224 652 347 735T697 760Q918 685 1004 751T1440 714"/>
        <path d="M0 811Q224 685 347 768T697 793Q918 718 1004 784T1440 747"/>
        <path d="M0 843Q224 717 347 800T697 825Q918 750 1004 816T1440 779"/>
      </g>
      <g className="lost-trees-far">
        <use href="#lost-fir" x="628" y="562" width="75" height="158"/><use href="#lost-fir" x="689" y="629" width="42" height="88"/>
        <use href="#lost-fir" x="1230" y="547" width="76" height="162"/><use href="#lost-fir" x="1310" y="563" width="67" height="141"/>
        <use href="#lost-fir" x="1370" y="500" width="88" height="184"/><use href="#lost-fir" x="1184" y="622" width="40" height="85"/>
      </g>
      <g className="lost-trees-near">
        <use href="#lost-fir" x="-62" y="569" width="175" height="366"/><use href="#lost-fir" x="80" y="701" width="103" height="214"/>
        <use href="#lost-fir" x="1294" y="684" width="112" height="233"/><use href="#lost-fir" x="1361" y="542" width="176" height="369"/>
      </g>
      <path className="lost-foreground" d="M0 828Q180 820 328 862T589 900H0ZM1440 835Q1300 825 1158 900H1440Z"/>
    </svg>

    <div className="lost-station" aria-hidden="true">
      <svg className="lost-portal-art" viewBox="0 0 740 740" focusable="false">
        <defs>
          <radialGradient id="lost-door">
            <stop className="lost-door-center"/>
            <stop className="lost-door-edge" offset="1"/>
          </radialGradient>
          <linearGradient id="lost-rock" x2="0" y2="1">
            <stop className="lost-rock-top"/><stop className="lost-rock-bottom" offset="1"/>
          </linearGradient>
          <clipPath id="lost-opening"><ellipse cx="383" cy="354" rx="152" ry="203"/></clipPath>
          <symbol id="lost-leaf" viewBox="0 0 30 19">
            <path d="M2 15C6 4 17 1 28 3 24 14 14 21 2 15Z"/><path d="M2 15 20 7" fill="none"/>
          </symbol>
        </defs>
        <ellipse className="lost-station-shadow" cx="370" cy="713" rx="266" ry="24"/>
        <g className="lost-debris">
          <path d="m79 438 29-26 32 7 13 25-37 18Z"/>
          <path d="m584 103 26-11 21 19-17 29-31-7Z"/>
          <path d="m629 428 30-19 23 16-16 29-29 3Z"/>
        </g>

        <g className="lost-plinth">
          <path className="lost-island" d="m119 578 116-48 300 5 90 59-53 63-102 40-116 28-57-36-75-9-38-45-45-12Z"/>
          <path className="lost-island-face" d="m119 578 90 34 122 19 115-21 179-16-53 63-102 40-116 28-57-36-75-9-38-45-45-12Z"/>
          <path className="lost-island-seams" d="m205 614 17 66m111-47 21 88m92-112 24 88m67-95 35 55m-259 34 31-28 28 9"/>
          <path className="lost-island-lawn" d="m119 578 116-48 300 5 90 59-123 35-164 18-144-21Z"/>
          <path className="lost-island-path" d="m311 644 40-50 13-48 54-1 17 49 40 36-125 18Z"/>
          <path className="lost-island-steps" d="m341 613 111-7m-124 24 143-13m-119-22 79-4"/>
          <path className="lost-foliage" d="M164 589Q142 548 130 546q32-1 48 27-10-30 4-50 24 31 16 54 21-18 39-9-12 23-46 29Zm365 12q15-36 39-46-2 17-10 30 21-24 44-25-4 33-43 48Z"/>
        </g>

        <g className="lost-ring">
          <ellipse className="lost-ring-depth" cx="395" cy="355" rx="182" ry="234"/>
          <ellipse className="lost-ring-rim" cx="383" cy="354" rx="182" ry="234"/>
          <ellipse className="lost-ring-metal" cx="383" cy="354" rx="170" ry="222"/>
          <ellipse className="lost-door" cx="383" cy="354" rx="152" ry="203"/>
          <g clipPath="url(#lost-opening)">
            <g className="lost-inner-world">
              <circle className="lost-inner-moon" cx="410" cy="278" r="54"/>
              <path className="lost-inner-hills" d="M190 430 274 362 320 383 370 333 425 389 465 355 550 413V600H190Z"/>
              <path className="lost-inner-meadow" d="M209 466Q298 400 375 442T558 449V603H209Z"/>
              <path className="lost-inner-stream" d="M394 445Q347 464 405 473T391 512Q360 533 373 559H451Q415 537 452 511T428 469Q385 463 402 444Z"/>
            </g>
            <path className="lost-door-sweep" d="M212 222h340v28H212Z"/>
          </g>
          <ellipse className="lost-ring-light" cx="383" cy="354" rx="156" ry="207"/>
          <ellipse className="lost-ring-dashes" cx="383" cy="354" rx="165" ry="216"/>
          <path className="lost-ring-cap" d="m365 113 18-17 18 17-6 24-12 8-12-8Z"/>
          <path className="lost-ring-cap" d="m198 331 17 11v24l-17 11-9-13v-20Zm371 0 9 13v20l-9 13-17-11v-24Z"/>
          <path className="lost-ring-cap" d="m365 580 18-17 18 17-6 24-12 8-12-8Z"/>
        </g>

        <g className="lost-compass-frame" transform="translate(111 227) rotate(-14)">
          <circle cx="4" cy="5" r="50"/><circle r="50"/><circle r="39"/>
          <path d="M0-33v8M33 0h-8M0 33v-8M-33 0h8" fill="none"/>
          <g className="lost-compass-needle">
            <path d="M0-30 11 0 0 30-11 0Z"/><path d="M0-30 11 0 0 3-11 0Z"/><circle r="4"/>
          </g>
        </g>

        <g className="lost-leaves">
          <use href="#lost-leaf" x="130" y="340" width="23" height="16"/>
          <use href="#lost-leaf" x="566" y="207" width="27" height="17"/>
          <use href="#lost-leaf" x="619" y="367" width="19" height="12"/>
          <use href="#lost-leaf" x="202" y="188" width="18" height="12"/>
        </g>
        <g className="lost-sparks">
          <path d="m176 343 4 9 9 4-9 4-4 9-4-9-9-4 9-4Zm431-67 3 7 7 3-7 3-3 7-3-7-7-3 7-3Zm-71-135 3 7 7 3-7 3-3 7-3-7-7-3 7-3Z"/>
          <circle cx="214" cy="490" r="3"/><circle cx="610" cy="512" r="4"/><circle cx="146" cy="405" r="3"/>
          <circle cx="559" cy="453" r="3"/><circle cx="308" cy="164" r="2"/><circle cx="623" cy="183" r="3"/>
        </g>

        <g className="lost-map" strokeLinejoin="round">
          <g className="lost-map-left"><path d="m255 318 93-39 34 34-17 83-114 26Z"/><path d="m267 388 34-43 39 7 21-29" fill="none"/></g>
          <g className="lost-map-right"><path d="m382 313 107-32 22 108-89 34-57-27Z"/><path d="m388 384 37-27 34 6 28-48" fill="none"/></g>
          <g className="lost-map-low"><path d="m251 422 114-26 57 27-21 44-78-26-63 24Z"/></g>
        </g>
      </svg>
    </div>
  </>;
}

export function NotFoundPage() {
  const navigate = useNavigate();
  const root = useRef<HTMLElement>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const frame = useRef<number | null>(null);
  const exitTimer = useRef<number | null>(null);
  const [intro, setIntro] = useState(() => !window.matchMedia(reducedMotion).matches);
  const [exiting, setExiting] = useState(false);

  useEffect(() => {
    if (!intro) return;
    const timer = window.setTimeout(() => {
      if (document.activeElement?.classList.contains('lost-skip')) heading.current?.focus({preventScroll: true});
      setIntro(false);
    }, introDuration);
    return () => window.clearTimeout(timer);
  }, [intro]);

  useEffect(() => {
    const preference = window.matchMedia(reducedMotion);
    const change = () => {
      if (preference.matches) {
        resetPointer();
        setIntro(false);
        if (exitTimer.current !== null) {
          window.clearTimeout(exitTimer.current);
          exitTimer.current = null;
          void navigate('/');
        }
      }
    };
    const previousTitle = document.title;
    document.title = '404 · Không tìm thấy trang | Rise AI';
    preference.addEventListener('change', change);
    return () => {
      preference.removeEventListener('change', change);
      if (frame.current !== null) window.cancelAnimationFrame(frame.current);
      if (exitTimer.current !== null) window.clearTimeout(exitTimer.current);
      document.title = previousTitle;
    };
  }, [navigate]);

  function move(event: PointerEvent<HTMLElement>) {
    if (event.pointerType !== 'mouse' || window.matchMedia(reducedMotion).matches || !root.current) return;
    const bounds = root.current.getBoundingClientRect();
    const x = (event.clientX - bounds.left) / bounds.width - .5;
    const y = (event.clientY - bounds.top) / bounds.height - .5;
    if (frame.current !== null) window.cancelAnimationFrame(frame.current);
    frame.current = window.requestAnimationFrame(() => {
      frame.current = null;
      root.current?.style.setProperty('--lost-x', `${x * 24}px`);
      root.current?.style.setProperty('--lost-y', `${y * 16}px`);
    });
  }

  function resetPointer() {
    if (frame.current !== null) {
      window.cancelAnimationFrame(frame.current);
      frame.current = null;
    }
    root.current?.style.removeProperty('--lost-x');
    root.current?.style.removeProperty('--lost-y');
  }

  function goHome(event: MouseEvent<HTMLAnchorElement>) {
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || window.matchMedia(reducedMotion).matches) return;
    event.preventDefault();
    if (exitTimer.current !== null) return;
    setIntro(false);
    setExiting(true);
    exitTimer.current = window.setTimeout(() => {
      exitTimer.current = null;
      void navigate('/');
    }, exitDuration);
  }

  return <main ref={root} className="rise-deep-page rise-notfound-page lost-page" data-intro={intro} data-exiting={exiting}
    onPointerMove={move} onPointerLeave={resetPointer}>
    <B2IconSymbols/>
    <div className="lost-scene"><PortalLandscape/></div>
    <div className="lost-top">
      <Link to="/" className="lost-brand" aria-label="Rise AI — Trang chủ">
        <svg viewBox="0 0 24 24" aria-hidden="true"><use href="#i-leaf"/></svg>
        <strong>Riser <span>AI</span></strong>
      </Link>
      {intro && <button type="button" className="lost-skip" onClick={() => {
        setIntro(false);
        heading.current?.focus({preventScroll: true});
      }}>Bỏ qua <SkipForward size={17} aria-hidden="true"/></button>}
    </div>
    <div className="lost-copy">
      <p className="lost-signal"><Radio size={18} aria-hidden="true"/> Mất tín hiệu</p>
      <p className="lost-code" data-code="404" aria-hidden="true">404</p>
      <h1 ref={heading} tabIndex={-1}><span className="sr-only">404. </span>Lạc khỏi bản đồ</h1>
      <p className="lost-description">Trang bạn tìm không tồn tại. Hãy trở về trang chủ hoặc tiếp tục khám phá các chiến dịch của Rise AI.</p>
      <nav className="lost-actions" aria-label="Điều hướng từ trang 404">
        <Link to="/" className="lost-home" onClick={goHome}>Về trang chủ <ArrowRight size={19} aria-hidden="true"/></Link>
        <Link to="/campaigns" className="lost-campaigns">Khám phá chiến dịch <MoveUpRight size={17} aria-hidden="true"/></Link>
      </nav>
    </div>
    <div className="lost-exit" aria-hidden="true"/>
  </main>;
}
