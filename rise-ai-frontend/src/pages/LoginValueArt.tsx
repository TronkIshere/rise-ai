/** Decorative, multi-colour SVG artwork for the three /login value statements.
 * Text labels in LoginPage are the accessible content; icons are aria-hidden.
 */
type LoginValueKind = 'support' | 'community' | 'impact';

export function LoginValueArt({ kind }: { kind: LoginValueKind }) {
  return <span className={'b2-login-value-art b2-login-value-art--' + kind} aria-hidden="true">
    {kind === 'support' ? <svg className="b2-login-value-picto" viewBox="0 0 64 64" focusable="false">
      <path className="b2-picto-heart-shadow" d="M32 34 20 23c-7-7-2-16 6-14 3 .6 5 2.5 6 5 3-5 9-6.5 13-2.5 5.5 5 3.2 10.2-2 15.3Z" />
      <path className="b2-picto-heart" d="M32 32 21 21c-6.5-6.5-2-14.5 5-12.5 3 .8 5 2.7 6 5 3.4-5 9-6 13-2 5 5 2 10-3 15Z" />
      <path className="b2-picto-heart-glint" d="m24 13 3 1" />
      <path className="b2-picto-hand" d="M13 43h10l8-5c1.7-1.1 3.5-1.6 5.4-1.6H46a3.8 3.8 0 0 1 .3 7.6h-9.5l-4.3 4.3h9c3 0 5.7-1.1 7.9-3.1l4.1-3.8c2.2-2 5.2-1.2 5.9.7.6 1.7-.1 3-1.4 4.3L45.8 57H25l-6-4H13Z" />
      <path className="b2-picto-cuff" d="M8 41h11v14H8z" />
      <path className="b2-picto-detail" d="M33 48h8" />
    </svg> : null}
    {kind === 'community' ? <svg className="b2-login-value-picto" viewBox="0 0 64 64" focusable="false">
      <circle className="b2-picto-face-side" cx="17" cy="25" r="7" />
      <circle className="b2-picto-face-side-alt" cx="47" cy="25" r="7" />
      <path className="b2-picto-person-side" d="M3 49v-6c0-7.5 5.5-12 13-12 4.3 0 8 1.5 10.5 4.6L27 51H7a4 4 0 0 1-4-2Z" />
      <path className="b2-picto-person-side-alt" d="M61 49v-6c0-7.5-5.5-12-13-12-4.3 0-8 1.5-10.5 4.6L37 51h20a4 4 0 0 0 4-2Z" />
      <circle className="b2-picto-face" cx="32" cy="20" r="9" />
      <path className="b2-picto-person" d="M17 53v-8c0-9 6.5-14.5 15-14.5S47 36 47 45v8a4 4 0 0 1-4 4H21a4 4 0 0 1-4-4Z" />
      <path className="b2-picto-detail" d="M24 42v9m16-9v9" />
    </svg> : null}
    {kind === 'impact' ? <svg className="b2-login-value-picto" viewBox="0 0 64 64" focusable="false">
      <circle className="b2-picto-sun" cx="49" cy="15" r="7" />
      <path className="b2-picto-ground" d="M7 47c8-8 17-11 25-10 11 1 19 6 25 12l-2 6H9Z" />
      <path className="b2-picto-stem" d="M32 44V20" />
      <path className="b2-picto-leaf-left" d="M30.5 29C18 29 15 22 14 15c10-1 17 2 18 12Z" />
      <path className="b2-picto-leaf-right" d="M33 25c0-11 8-17 17-16-1 10-7 18-17 18Z" />
      <path className="b2-picto-leaf-vein" d="m20 19 11 9m13-13-11 12" />
      <path className="b2-picto-soil" d="M18 51c9-3 19-3 29 0" />
    </svg> : null}
    <span className="b2-login-value-shine" />
  </span>;
}
