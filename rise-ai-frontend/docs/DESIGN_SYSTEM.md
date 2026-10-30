# Rise AI UI design contract

Rise AI is a *public charity platform*, not a SaaS application. Public pages are open and readable; future creator/admin workflows may use a denser utility layout but must share the same tokens.

- Typography: Be Vietnam Pro preferred, Segoe UI/system fallback. Respect Vietnamese diacritics.
- Palette and radius are defined once in `src/styles/tokens.css` (forest green, warm near-white, readable dark ink, accessible error).
- Reuse `Button`, `Card`, `Container`; keep buttons/inputs 44px minimum hit targets.
- Use semantic HTML, focus outlines, form labels, `aria-live` for errors, and responsive layouts starting at 320px.
- No MUI, hotel black/gold aesthetic, arbitrary hex colors in pages, invented charts or financial balances.
- All monetary values indicate **simulation**. AI is an advisor; never label its findings as final fraud proof.
- Add a new token/component only when needed by two screens or a documented pattern.
