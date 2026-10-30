# Rise AI Frontend — UI branch

Frontend work lives at [`rise-ai-frontend/`](../../rise-ai-frontend/). Read its [README](../../rise-ai-frontend/README.md), [architecture](../../rise-ai-frontend/docs/ARCHITECTURE.md), [design tokens](../../rise-ai-frontend/docs/DESIGN_SYSTEM.md), [progress](../../rise-ai-frontend/docs/PROGRESS.md) and [agent rules](../../rise-ai-frontend/AGENTS.md).

**Scope:** public charity experience; simulated payments and milestone escrow; Admin-reviewed advisory AI findings. **Not SaaS**. FE-00 is a small foundation, not a complete donor/creator/admin product.

FE-00 has a dedicated [frontend-ci workflow](../../.github/workflows/frontend-ci.yml), which must pass before merge. Backend and Monitoring are unchanged by this branch.

**Reference reviewed:** `TronkIshere/Advanced-Web-Programming` (hotel booking Next.js). Only reuse its ideas of single API boundary, shared layouts and providers. Do not import its hotel domain, MUI stack, localStorage sessions, tracked secrets, or multi-library theme.
