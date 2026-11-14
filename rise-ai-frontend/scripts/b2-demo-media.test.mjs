import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

const helperSource = readFileSync(new URL('../src/lib/b2-campaign-media.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(helperSource, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
});
const { isSyntheticPlaceholderMedia } = await import('data:text/javascript,' + encodeURIComponent(outputText));

test('B2 demo-seeded placeholder is not classified as campaign media', () => {
  for (const value of [
    'https://placehold.co/1200x630/png?text=hope-kitchen-weekly-meals-demo',
    'https://www.placehold.co/1200x630/png?text=school-demo',
    'http://placehold.co/600x400',
  ]) assert.equal(isSyntheticPlaceholderMedia(value), true, value);
});

test('B2 keeps genuine campaign cover URLs, approved fallbacks and unrelated hosts', () => {
  for (const value of [
    'https://images.example.org/campaign-photo.jpg',
    'https://placehold.co.evil.example/photo.jpg',
    'https://images.placehold.co.evil.example/photo.jpg',
    'https://placehold.com/photo.jpg',
    '/b2-home/campaign-hope.jpg',
    'sim://mock',
    '',
    undefined,
  ]) assert.equal(isSyntheticPlaceholderMedia(value), false, String(value));
});

test('B2 main and compact cards share the same media provenance decision', () => {
  const cards = readFileSync(new URL('../src/pages/B2CampaignCards.tsx', import.meta.url), 'utf8');
  assert.match(cards, /function approvedCampaignCover\(campaign: CampaignResponse\)/);
  assert.match(cards, /!isSyntheticPlaceholderMedia\(source\)/);
  assert.match(cards, /const src = approvedCampaignCover\(campaign\)/);
  assert.match(cards, /approvedCampaignCover\(campaign\) \? 'ẢNH TRONG HỒ SƠ CHIẾN DỊCH'/);
  assert.match(cards, /approvedCampaignCover\(campaign\) \? 'ẢNH HỒ SƠ CHIẾN DỊCH'/);
  assert.match(cards, /CONCEPT IMAGE/);
  assert.match(cards, /KHÔNG PHẢI MINH CHỨNG/);
  assert.doesNotMatch(cards, /ẢNH DO CHIẾN DỊCH CUNG CẤP/);
});
