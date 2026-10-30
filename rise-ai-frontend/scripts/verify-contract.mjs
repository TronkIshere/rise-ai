import { readFileSync } from 'node:fs';
const url = new URL('../../rise-ai-backend/docs/api/openapi-v1.json', import.meta.url);
const spec = JSON.parse(readFileSync(url, 'utf8'));
const errors = [];
const endpoints = [
  ['/api/v1/auth/login','post'],
  ['/api/v1/auth/logout','post'],
  ['/api/v1/auth/me','get'],
  ['/api/v1/campaigns','get'],
  ['/api/v1/campaigns/slug/{slug}','get'],
];
for (const [path, method] of endpoints) {
  if (!spec.paths?.[path]?.[method]) errors.push(method + ' ' + path + ' missing');
}
for (const [schema, fields] of Object.entries({
  LoginRequest: ['email','password'],
  TokenResponse: ['accessToken','refreshToken','user'],
  UserResponse: ['roles','email'],
  CampaignResponse: ['slug','title','campaignStatus','targetAmount'],
  PageResponseCampaignResponse: ['data','currentPage','totalPages'],
})) {
  const props = spec.components?.schemas?.[schema]?.properties;
  for (const field of fields) if (!props?.[field]) errors.push(schema + '.' + field + ' missing');
}
const roles = spec.components?.schemas?.UserResponse?.properties?.roles?.items?.enum ?? [];
for (const role of ['ADMIN','DONOR','CAMPAIGN_CREATOR']) {
  if (!roles.includes(role)) errors.push('role ' + role + ' missing');
}
if (errors.length) {
  console.error('Contract FAILED: ' + errors.join('; '));
  process.exit(1);
}
console.log('Backend contract PASS (' + Object.keys(spec.paths).length + ' paths)');
