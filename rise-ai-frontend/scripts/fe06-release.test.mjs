import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source=(path)=>readFileSync(new URL('../'+path,import.meta.url),'utf8');
test('FE-06 Docker staging is explicitly local-only and requires Backend upstream',()=>{
  const compose=source('compose.frontend.yml');
  const nginx=source('deploy/default.conf.template');
  const docker=source('Dockerfile');
  assert.match(compose,/127\.0\.0\.1:/);
  assert.match(compose,/RISE_FRONTEND_BACKEND_UPSTREAM:\?/);
  assert.match(docker,/ENV VITE_API_BASE_URL=""/);
  assert.match(nginx,/location \/api\//);
  assert.match(nginx,/proxy_pass \$\{BACKEND_UPSTREAM\};/);
  assert.match(nginx,/location = \/healthz/);
  assert.match(nginx,/try_files \$uri \$uri\/ \/index\.html/);
});
test('FE-06 smoke is read-only, exercises SPA deep links and Backend authorization',()=>{
  const smoke=source('scripts/smoke-deployment.mjs');
  for(const route of ['/campaigns','/admin/monitoring','/admin/audit'])assert.ok(smoke.includes(route),route);
  assert.match(smoke,/\[401,403\]/);
  assert.match(smoke,/RISE_FE06_BASE_URL/);
  assert.doesNotMatch(smoke,/method:\s*['"](?:POST|PUT|DELETE|PATCH)['"]/);
  assert.match(smoke,/AbortSignal\.timeout\(10000\)/);
});
