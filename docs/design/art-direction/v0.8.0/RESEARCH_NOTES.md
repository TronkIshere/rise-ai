# Riser AI — Art Direction research v0.8.0

**Research date:** 2026-11-06. Source observations and proposals are deliberately separate.

## External sources and transfer boundaries

| Source | What it establishes | Riser AI research application | Do not claim or copy |
|---|---|---|---|
| [charity: water / Our Work](https://www.charitywater.org/our-work) | Real field stories alongside project photos and GPS project information | Study the visual handoff from **human story → concrete project proof** | Do not use their photos, 100% funding promise, GPS claims or numeric impact |
| [GlobalGiving / reporting requirements](https://support.globalgiving.org/hc/en-us/articles/360026175992-What-are-GlobalGiving-s-reporting-requirements) | Project-leader updates help participants follow progress | Separate **project updates** from **milestone completion and evidence review** | Never impose GlobalGiving's reporting cadence on Riser AI |
| [GlobalGiving / who sees reports](https://support.globalgiving.org/hc/en-us/articles/360026176532-Who-sees-my-reports) | Approved reports are visible on project pages and emailed to subscribed donors | Research the clarity of public updates and their review stage | Internal acceptance is not independent impact verification |
| [W3C / WCAG 2.2](https://www.w3.org/TR/WCAG22/) | Text contrast, use of color and accessibility constraints | Propose candidate palettes tested for readability | A preliminary swatch ratio is not WCAG conformance |
| [W3C / Non-text contrast](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html) | Meaningful graphical/UI cues generally require 3:1 against adjacent color | Check category lines, progress, focus, meaningful status cues | Colored lines alone must never indicate category/state |
| [Be Vietnam Pro / Google Fonts](https://github.com/google/fonts/blob/main/ofl/bevietnampro/METADATA.pb) | OFL font family already used by runtime's fallback stack | Candidate body and bold display typography | Candidate use here does not authorize changing `tokens.css` |
| [Source Serif 4 / Google Fonts](https://github.com/google/fonts/blob/main/ofl/sourceserif4/METADATA.pb) | Open font with Vietnamese subset | Candidate editorial headline for Field Notes | Must test actual loaded font; screenshot rendered with fallback |
| [IBM Plex / language coverage](https://www.ibm.com/design/language/typography/typeface/) | Extended Latin coverage includes Vietnamese | Candidate civic/informational type for Evidence with Empathy | Must test actual loaded font and Vietnamese line breaking |

Reference sites are **research references, not licensed campaign media**. No charity photography, names of real beneficiaries, logos, screenshots or evidence is embedded into the style tiles.

## Distinct directions

**A — Field Notes / Ký sự cộng đồng**  
Warm publication, grounded human-first documentary tone, an editorial serif headline and quiet lines. Use authentic photos with permission and provenance, otherwise a credible text-only fallback. **Risk:** feels empty without authentic imagery; never compensate by inventing humanitarian events or subjects.

**B — Community Signature / Dấu ấn kết nối**  
Bold Vietnamese typography, green ink, clearer shape hierarchy, a *connected moments* motif tied only to actual steps/milestones. Recognize all eight first-level categories with labelled accent lines, not color alone. **Risk:** repeating rounded cards, arbitrary patterns or green overlays would make the site generic again.

**C — Evidence with Empathy / Dấu vết tác động**  
Civic-editorial hierarchy, rigorous state and timeline vocabulary, purposeful borders and data. Put human stories and milestones beside evidence limits. **Risk:** resembles fintech or an admin dashboard; must avoid self-certified trust claims.

## Universal non-negotiables

- The approved **v0.7.1b Desktop 1440 composition** remains locked.
- Distinguish simulated net raised, target amount, milestone progress, internal document review and separately validated real-world impact.
- Show the **simulation notice**; no real-money checkout or fake payment confirmation.
- Preserve one main landmark, one Hero h1, accessible Story/Transparency headings and the keyboard-accessible Transparency disclosure.
- No mobile design scope, React, Backend/Monitoring, assets or app deployment.
- Only select a direction after the owner has seen actual visual comparisons. See the scorecard.
