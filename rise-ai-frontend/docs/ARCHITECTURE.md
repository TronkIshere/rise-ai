# Architecture decision — FE-00

**Choice:** one Vite React SPA with technical layers, **not** a SaaS or per-entity bundle architecture. The Backend uses technical layers (see its ADR-008). This frontend follows that principle while separating presentation from networking.

```
src/
  app/              router and top-level providers
  api/              the only HTTP boundary; typed request/response subset
  auth/             session context and route guard
  pages/            route-level screens, composition only
  components/
    ui/             reusable primitives; no API import
    layout/         shared public layout
  lib/              pure utilities
  styles/           design tokens and global CSS
```

**Import direction:** pages -> api/auth/components/lib; UI -> lib only; api cannot import UI; auth -> api; app composes everything. HTTP only in api/client.ts. Do not create `features/<entity>` or `services/<entity>` islands.

The reference [Advanced-Web-Programming](https://github.com/TronkIshere/Advanced-Web-Programming) was inspected: keep its central API client, shared layouts and providers concept. **Do not copy** Next.js, MUI plus Tailwind mixture, black/gold hotel theme, tracked `.env`, localStorage token manager or large dependencies.

**Auth:** access/refresh tokens only in JS memory in FE-00. Page reload loses session. Persistent sessions require separate security design (possibly BFF + HttpOnly cookie). The UI is not an authorization boundary.

**API:** always read `../rise-ai-backend/docs/api/openapi-v1.json`. API success has an envelope `{code,message,data}`; errors have `{status,code,message,correlationId,violations}`. No duplicated OpenAPI in the frontend.

**Integration limitation:** no binary evidence upload API; do not invent a file uploader. No real payment rails or automatic AI decisions.

**Guardrails:** `scripts/check-architecture.mjs` rejects forbidden layering, direct HTTP outside api, raw color/style values. `scripts/verify-contract.mjs` verifies documented endpoints and shapes. TypeScript/ESLint add static checks. These checks are helpful, not a complete security audit.
