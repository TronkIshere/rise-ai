import { test } from 'node:test';
import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';

const spec = JSON.parse(readFileSync(new URL('../../rise-ai-backend/docs/api/openapi-v1.json', import.meta.url)));
test('FE-02 donation initiation is simulated and admin confirmation is separate', () => {
  const create = spec.paths['/api/v1/campaigns/{campaignId}/donations']?.post;
  const confirm = spec.paths['/api/v1/admin/donations/{id}/confirm']?.post;
  assert.ok(create && confirm);
  assert.equal(create.requestBody.content['application/json'].schema.$ref, '#/components/schemas/CreateDonationRequest');
  const methods = spec.components.schemas.CreateDonationRequest.properties.paymentMethod.enum;
  assert.deepEqual(methods, ['SIMULATED_BANK_TRANSFER', 'SIMULATED_CARD', 'SIMULATED_E_WALLET']);
  assert.ok(spec.components.schemas.DonationResponse.properties.donationStatus.enum.includes('PENDING'));
});
test('FE-02 personal records, refund and dispute contracts exist', () => {
  for (const [path, method] of [
    ['/api/v1/donations/me','get'],['/api/v1/refunds/me','get'],
    ['/api/v1/disputes/me','get'],['/api/v1/donations/{id}/cancel','post'],
    ['/api/v1/donations/{donationId}/refunds','post'],
    ['/api/v1/refunds/{id}/cancel','post'],
    ['/api/v1/campaigns/{campaignId}/disputes','post'],
    ['/api/v1/disputes/{id}','get'],
    ['/api/v1/disputes/{id}/messages','get'],
    ['/api/v1/disputes/{id}/messages','post'],
    ['/api/v1/disputes/{id}/withdraw','post'],
  ]) assert.ok(spec.paths[path]?.[method], path + ' ' + method);
  assert.equal(spec.components.schemas.RefundResponse.properties.externalPaymentStatus.enum[0], 'NOT_EXECUTED_SIMULATION');
  assert.equal(spec.components.schemas.CreateDonationRequest.required.includes('amount'), true);
});
