import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const root=resolve(import.meta.dirname,'../src');
const page=(name)=>readFileSync(resolve(root,'pages',name+'.tsx'),'utf8');
const css=readFileSync(resolve(root,'styles/functional-pages-b2.css'),'utf8');
const deepRoutes=[
  ['DisputeThreadPage','deep-donor-dispute'],
  ['CreatorCampaignEditorPage','deep-creator-editor'],
  ['CreatorCampaignPage','deep-creator-campaign'],
  ['AdminReviewPage','deep-admin-review'],
  ['AdminEscrowPage','deep-admin-escrow'],
  ['AdminMonitoringPage','deep-admin-monitoring'],
  ['AdminFindingPage','deep-admin-finding'],
  ['AdminDeliveryPage','deep-admin-delivery'],
  ['AdminAuditPage','deep-admin-audit'],
];

test('functional route pages share one scoped B2 editorial design system',()=>{
  for(const [name,scope] of deepRoutes){
    const text=page(name);
    assert.ok(text.includes('rise-deep-page '+scope),name+': missing page scope');
    assert.ok(text.includes('rise-deep-topline'),name+': missing overline folio');
    assert.ok(text.includes('<Container className="deep-inner'),name+': missing container');
    assert.ok(text.includes('</Container>'),name+': broken closing wrapper');
  }
  for(const [name,scope] of [
    ['LoginPage','rise-entry-page'],['WorkspacePage','rise-workspace-page'],
    ['NotFoundPage','rise-notfound-page'],
  ])assert.ok(page(name).includes('rise-deep-page '+scope),name+': missing design scope');
});

test('new functional page styling reuses runtime tokens without introducing a palette or overriding locked pages',()=>{
  assert.ok(css.includes('.rise-deep-page'));
  assert.ok(css.includes('var(--color-'));
  assert.doesNotMatch(css,/#[0-9a-f]{3,8}\b/i,'new CSS must derive colors from canonical tokens');
  for(const unauthorized of ['.b2-homepage','.campaign-discovery-page','.campaign-detail-flow','.campaign-contribution-flow']){
    assert.equal(css.includes(unauthorized),false,unauthorized+' approved visual lock should not be restyled');
  }
  assert.ok(readFileSync(resolve(root,'main.tsx'),'utf8').includes("import '@/styles/functional-pages-b2.css';"));
});

test('authentication and role routing remain frontend guards with Backend-owned permissions',()=>{
  const login=page('LoginPage');
  const workspace=page('WorkspacePage');
  assert.match(login,/useAuth\(\)/);
  assert.match(login,/await login\(/);
  assert.match(login,/const destination =/);
  assert.match(login,/<Navigate to={destination} replace \/>/);
  for(const role of ['DONOR','CAMPAIGN_CREATOR','ADMIN']){
    assert.ok(workspace.includes("roles?.includes('"+role+"')"),role+' authorization affordance missing');
  }
  const router=readFileSync(resolve(root,'app/router.tsx'),'utf8');
  for(const role of ['DONOR','CAMPAIGN_CREATOR','ADMIN']){
    assert.ok(router.includes('role="'+role+'"'),'Router guard missing for '+role);
  }
});
