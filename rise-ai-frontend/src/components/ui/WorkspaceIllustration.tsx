type Illustration = 'ledger' | 'wallet' | 'gift' | 'refund' | 'lock' | 'checklist' | 'megaphone' | 'scale' | 'alert' | 'history';

export function WorkspaceIllustration({ kind }: { kind: Illustration }) {
  const outline = { stroke: 'var(--workspace-art-1)', strokeWidth: 3, strokeLinejoin: 'round' as const, strokeLinecap: 'round' as const };
  const coins = <g {...outline}><path d="M13 65v12c0 8 30 8 30 0V65" fill="var(--workspace-art-2)"/><ellipse cx="28" cy="64" rx="15" ry="6" fill="var(--workspace-art-3)"/><path d="M42 61v16c0 9 32 9 32 0V61" fill="var(--workspace-art-4)"/><ellipse cx="58" cy="60" rx="16" ry="6" fill="var(--workspace-art-5)"/><path d="M16 73c7 4 18 4 24 1m6-5c7 4 18 4 25 0" fill="none" stroke="var(--workspace-art-6)" strokeWidth="2"/></g>;
  const leaves = <g {...outline}><path d="M18 77C8 75 5 65 6 57c11 0 20 7 22 18M73 76c5-14 16-21 24-19 1 10-7 22-18 24" fill="var(--workspace-art-7)"/><path d="m12 64 12 12m66-11-11 12" stroke="var(--workspace-art-8)" strokeWidth="2"/></g>;
  const designs: Record<Illustration, React.ReactNode> = {
    ledger: <>{leaves}<g {...outline}><path d="M34 18c-4-7-1-10 5-7l11 4 11-4c7-2 9 2 4 8l-7 10H42z" fill="var(--workspace-art-9)"/><path d="M42 28C30 40 21 51 23 66c2 19 54 22 56 2 2-14-10-30-20-40z" fill="var(--workspace-art-10)"/><path d="M42 28C30 40 24 51 28 65c7 11 33 10 48 0-1-13-8-26-17-37z" fill="var(--workspace-art-11)"/><path d="M41 28h20" stroke="var(--workspace-art-12)"/><path d="M42 58c0-9 8-13 17-13-1 10-6 17-15 17m-4 2 15-14" fill="var(--workspace-art-13)"/></g>{coins}</>,
    wallet: <>{leaves}<g {...outline}><path d="m23 33 53-15 10 32-56 11z" fill="var(--workspace-art-14)"/><path d="m26 39 46-13m-38 7 3 9" stroke="var(--workspace-art-15)"/><path d="M18 37h63q7 0 7 8v27q0 8-9 9H23q-10 0-10-11V47q0-8 5-10z" fill="var(--workspace-art-16)"/><path d="M20 39h62v33H22q-5 0-5-6V47z" fill="var(--workspace-art-17)"/><path d="M66 49h24v16H67q-8 0-8-8t7-8z" fill="var(--workspace-art-18)"/><circle cx="72" cy="57" r="2" fill="var(--workspace-art-19)"/></g></>,
    gift: <>{leaves}<g {...outline}><path d="M23 52h54v25H23z" fill="var(--workspace-art-20)"/><path d="M27 51h47v20H27z" fill="var(--workspace-art-21)"/><path d="M18 42h62v12H18z" fill="var(--workspace-art-22)"/><path d="M44 41h13v37H44z" fill="var(--workspace-art-23)"/><path d="M50 42c-23-6-17-25-6-16l7 11c8-20 25-14 15-3z" fill="var(--workspace-art-24)"/></g></>,
    refund: <>{coins}<path {...outline} d="M20 43a31 31 0 0 1 52-11l6-8 4 22-24-1 7-7a21 21 0 0 0-37 7z" fill="var(--workspace-art-25)"/><path d="M27 38c9-11 24-13 34-6" fill="none" stroke="var(--workspace-art-26)" strokeWidth="2"/></>,
    lock: <g {...outline}><path d="M32 43V29a18 18 0 0 1 36 0v14H57V30a7 7 0 0 0-14 0v13z" fill="var(--workspace-art-27)"/><rect x="25" y="40" width="50" height="40" rx="9" fill="var(--workspace-art-28)"/><path d="M31 43h35q6 0 6 6v20H31z" fill="var(--workspace-art-29)"/><path d="M50 53a5 5 0 0 1 2 10v6h-4v-6a5 5 0 0 1 2-10z" fill="var(--workspace-art-30)"/></g>,
    checklist: <>{leaves}<g {...outline} transform="rotate(-8 50 45)"><rect x="21" y="23" width="57" height="60" rx="8" fill="var(--workspace-art-31)"/><path d="M27 27h47v49H27z" fill="var(--workspace-art-32)"/><path d="M35 21h10a7 7 0 0 1 14 0h9v12H35z" fill="var(--workspace-art-33)"/><path d="m32 46 4 4 8-9m-12 20 4 4 8-9m6-9h16m-16 15h16" fill="none" stroke="var(--workspace-art-34)"/></g><path {...outline} d="m63 75 20-31 8 5-20 31-11 5z" fill="var(--workspace-art-35)"/></>,
    megaphone: <>{leaves}<g {...outline}><path d="m42 57 11 20-9 5-12-21z" fill="var(--workspace-art-36)"/><path d="M25 44 62 24l12 31-41 8-10-2-5-12z" fill="var(--workspace-art-37)"/><ellipse cx="70" cy="38" rx="13" ry="21" transform="rotate(-25 70 38)" fill="var(--workspace-art-38)"/><ellipse cx="70" cy="37" rx="8" ry="15" transform="rotate(-25 70 37)" fill="var(--workspace-art-39)"/><path d="m89 20 7-5m-7 17 9-2M30 45l24-11" stroke="var(--workspace-art-40)"/></g></>,
    scale: <g {...outline}><path d="M48 23h6v46l14 6v6H32v-6l16-6z" fill="var(--workspace-art-41)"/><path d="M20 30h62M28 31l-11 24h22zM72 31 61 55h22z" fill="var(--workspace-art-42)"/><path d="M17 55q11 18 22 0m22 0q11 18 22 0" fill="var(--workspace-art-43)"/><circle cx="51" cy="27" r="6" fill="var(--workspace-art-44)"/></g>,
    alert: <g {...outline}><path d="m47 17-32 53q-5 9 6 9h59q10 0 5-9L54 17q-3-6-7 0z" fill="var(--workspace-art-45)"/><path d="m50 24-25 43h50z" fill="var(--workspace-art-46)"/><path d="M50 43v11" strokeWidth="5"/><circle cx="50" cy="62" r="2" fill="var(--workspace-art-1)"/></g>,
    history: <g {...outline}><rect x="23" y="20" width="55" height="61" rx="8" fill="var(--workspace-art-47)"/><rect x="29" y="25" width="42" height="49" rx="4" fill="var(--workspace-art-48)"/><path d="M45 38h17m-17 11h17m-17 11h17M36 38h1m-1 11h1m-1 11h1" fill="none" strokeWidth="4"/></g>,
  };
  return <svg className="b2-workspace-illustration" viewBox="0 0 100 90" aria-hidden="true" focusable="false">
    <ellipse cx="52" cy="82" rx="35" ry="5" fill="var(--workspace-art-49)" opacity=".16" />
    {designs[kind]}
  </svg>;
}


export function WorkspaceHeroIllustration() {
  return <svg className="b2-admin-hero-illustration" viewBox="0 0 410 180" aria-hidden="true" focusable="false">
    <ellipse cx="216" cy="157" rx="165" ry="12" className="art-ground" />
    <g className="art-outline">
      <path className="art-leaf" d="M109 131C59 125 47 99 48 77c35 1 55 18 61 54Zm4-22C86 88 82 63 95 44c25 15 33 40 18 65Zm199 44c14-26 37-36 66-33-7 25-31 35-66 33Z" />
      <path className="art-leaf-light" d="M119 137C87 137 65 125 62 108c28-4 50 11 57 29Zm196 11c11-30 28-45 53-45-3 23-19 42-53 45Z" />
      <path className="art-lines" d="m59 89 42 33m-1-67 15 42m246 14-33 31m33-14-30 17" />
    </g>
    <g className="art-outline">
      <path className="art-navy" d="m151 30 142-17q11-2 11 9l-9 92-146 17-12-11 7-82q1-7 7-8Z" />
      <path className="art-blue" d="m151 24 136-18q11-1 11 10l-9 91-151 18 7-93q1-6 6-8Z" />
      <path className="art-screen" d="m156 32 128-17-8 82-127 16 7-81Z" />
      <path className="art-screen-shine" d="m163 38 112-15-2 18c-42-1-78 17-113 41Z" />
      <path className="art-white" d="M198 78c-1-17 14-26 31-25-3 15-14 25-28 26m-3 1 21-17" />
      <path className="art-navy" d="m138 125 150-18 22 20-6 11-140 20q-8 1-15-3l-38-18v-8Z" />
      <path className="art-metal" d="m139 121 149-17 22 23-143 21q-8 1-13-1l-43-18Z" />
      <path className="art-blue" d="m159 123 107-13 8 8-106 14Z" />
      <path className="art-lines" d="m178 137 39-5m-60 18 12 2 132-19" />
    </g>
    <g className="art-outline" transform="translate(55 107)">
      <path className="art-ochre" d="M0 12h38v26c0 9-38 9-38 0Z" />
      <path className="art-coins" d="M0 12h32v26c-6 7-32 6-32 0Z" />
      <ellipse className="art-coin-light" cx="19" cy="12" rx="19" ry="7" />
      <path className="art-lines" d="M2 23c9 5 24 5 34 0M2 32c9 5 24 5 34 0" />
    </g>
    <g className="art-outline" transform="translate(29 124)">
      <path className="art-ochre" d="M0 12h36v20c0 9-36 9-36 0Z" />
      <path className="art-coins" d="M0 12h28v20c-4 7-28 7-28 0Z" />
      <ellipse className="art-coin-light" cx="18" cy="12" rx="18" ry="7" />
      <path className="art-lines" d="M2 24c9 5 22 5 32 0" />
    </g>
    <g className="art-outline">
      <path className="art-ochre" d="M85 92q22-3 39-19 15 10 35 8l-1 25c-1 20-12 37-30 48l-10 2q-30-12-34-41Z" />
      <path className="art-coins" d="M80 94q22-4 39-18 15 11 35 10l-1 23c-1 20-13 38-35 47q-33-12-37-39Z" />
      <path className="art-leaf" d="M89 101q17-4 30-14 12 8 26 8l-1 15q-2 23-26 36-23-11-28-31Z" />
      <path className="art-check" d="m104 115 10 9 17-18" />
    </g>
    <g className="art-outline" transform="rotate(9 325 100)">
      <rect className="art-ochre" x="284" y="53" width="81" height="107" rx="9" />
      <rect className="art-blue" x="279" y="49" width="81" height="107" rx="9" />
      <path className="art-white" d="M286 58h66v89h-66Z" />
      <path className="art-coins" d="M300 56V45h12a8 8 0 0 1 16 0h13v11Z" />
      <path className="art-ticks" d="m293 77 4 4 7-8m-11 26 4 4 7-8m-11 26 4 4 7-8m8-41h27m-27 22h27m-27 22h24" />
    </g>
    <g className="art-outline">
      <path className="art-ochre" d="m330 145 35-41 8 7-34 41-16 8Z" />
      <path className="art-coins" d="m330 145 32-37 7 6-32 37Z" />
      <path className="art-white" d="m330 145 7 6-14 9Z" />
      <path className="art-leaf" d="m360 108 8-9q5-2 8 4l-8 11Z" />
      <path className="art-rays" d="m323 23 6-11m11 19 12-5m-4 18 10 1M66 55l-9-6m5 18-12-1" />
    </g>
  </svg>;
}

export function WorkspaceAdminPortrait() {
  return <svg className="b2-admin-portrait" viewBox="0 0 60 60" aria-hidden="true" focusable="false">
    <circle cx="30" cy="30" r="28" className="art-coin-light art-outline" />
    <g className="art-outline">
      <path className="art-screen" d="M11 50c2-10 8-15 19-15 12 0 19 6 20 15a28 28 0 0 1-39 0Z" />
      <path className="art-skin" d="M25 38v5l5 4 6-4v-5m-17-16c-5-2-7 7-1 9 2 7 6 11 12 11 7 0 11-5 13-11 6-2 4-10-1-9-2-8-20-12-23 0Z" />
      <path className="art-navy" d="M18 26c-4-7-2-15 4-17 4-5 16-5 20 2 7 1 8 13 0 15-1-5-4-8-7-10-3 5-11 6-14 4Z" />
      <path className="art-lines" d="M29 34q2 2 5 0" />
      <circle cx="25" cy="28" r="1" className="art-ink" />
      <circle cx="36" cy="28" r="1" className="art-ink" />
    </g>
  </svg>;
}
