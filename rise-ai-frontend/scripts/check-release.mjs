import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { resolve } from 'node:path';

const root=resolve(import.meta.dirname,'..');
const dist=resolve(root,'dist');
const errors=[];
const index=resolve(dist,'index.html');
if(!existsSync(index))errors.push('dist/index.html not found. Run npm run build first.');
else {
  const html=readFileSync(index,'utf8');
  if(!html.includes('id="root"'))errors.push('index.html does not contain React mount element');
  if(!/src="\/assets\/[^"]+\.js"/.test(html))errors.push('index.html missing a bundled JavaScript asset');
  if(/\/src\/main\.tsx/.test(html))errors.push('index.html points to dev TSX rather than built asset');
}
const assets=resolve(dist,'assets');
if(!existsSync(assets)||!statSync(assets).isDirectory()||!readdirSync(assets).some(x=>x.endsWith('.js')))errors.push('dist/assets missing production JS bundle');
const dockerfile=readFileSync(resolve(root,'Dockerfile'),'utf8');
const config=readFileSync(resolve(root,'deploy/default.conf.template'),'utf8');
const compose=readFileSync(resolve(root,'compose.frontend.yml'),'utf8');
if(!dockerfile.includes('COPY --from=backend_contract openapi-v1.json /rise-ai-backend/docs/api/openapi-v1.json'))errors.push('Docker build missing authoritative Backend OpenAPI named context');
if(!compose.includes('backend_contract: ../rise-ai-backend/docs/api'))errors.push('Compose missing isolated OpenAPI build context');
if(!dockerfile.includes('ENV VITE_API_BASE_URL=""'))errors.push('Docker build must use same-origin API, not a localhost URL');
if(!config.includes('location /api/')||!config.includes('proxy_pass ${BACKEND_UPSTREAM};'))errors.push('Missing Backend-only reverse proxy');
if(!config.includes('try_files $uri $uri/ /index.html'))errors.push('Missing SPA deep-link fallback');
if(!config.includes('location = /healthz'))errors.push('Missing static host health endpoint');
if(!compose.includes('RISE_FRONTEND_BACKEND_UPSTREAM:?'))errors.push('Backend upstream must be explicitly configured');
if(!compose.includes('127.0.0.1:'))errors.push('Staging container must default to local-only binding');
if(errors.length){for(const e of errors)console.error('Release check FAIL:',e);process.exit(1);}
console.log('FE-06 deploy artifact PASS — production assets, same-origin API proxy, deep links and health check');
