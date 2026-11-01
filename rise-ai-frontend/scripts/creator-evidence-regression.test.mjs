import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

// Exercise the production decision function, rather than a duplicate implementation.
const source = readFileSync(new URL('../src/lib/creator-evidence.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } }).outputText;
const { creatorEvidenceSummary } = await import('data:text/javascript;base64,' + Buffer.from(compiled).toString('base64'));
const success = data => ({ data, isSuccess: true, isError: false, isFetching: false });
const row = { id: 'evidence-1', evidenceStatus: 'SUBMITTED' };

test('Creator: evidence endpoint enables proposal without milestone evidenceCount', () => {
  assert.deepEqual(creatorEvidenceSummary(success({ data: [row], totalElements: 1 })),
    { status: 'ready', hasEvidence: true, count: 1 });
});

test('Creator: authoritative empty evidence remains empty and blocks proposal', () => {
  assert.deepEqual(creatorEvidenceSummary(success({ data: [], totalElements: 0 })),
    { status: 'empty', hasEvidence: false, count: 0 });
});

test('Creator: missing evidence data is unknown, never fabricated zero or eligibility', () => {
  for (const data of [undefined, null, {}, { totalElements: 2 }, { data: [] }, { data: [{}] }, { data: [null], totalElements: 1 }]) {
    assert.deepEqual(creatorEvidenceSummary(success(data)), { status: 'unknown', hasEvidence: false });
  }
});

test('Creator: an actual evidence row is sufficient without optional total, but total stays unknown', () => {
  assert.deepEqual(creatorEvidenceSummary(success({ data: [row] })),
    { status: 'ready', hasEvidence: true, count: undefined });
});

test('Creator: a paginated empty slice does not erase authoritative evidence total', () => {
  assert.deepEqual(creatorEvidenceSummary(success({ data: [], totalElements: 11, currentPage: 2 })),
    { status: 'ready', hasEvidence: true, count: 11 });
});

test('Creator: malformed or conflicting counts fail closed', () => {
  for (const totalElements of [-1, NaN, 1.5, '1', 0]) {
    assert.deepEqual(creatorEvidenceSummary(success({ data: [row], totalElements })),
      { status: 'unknown', hasEvidence: false });
  }
});

test('Creator: initial load, refetch and failed refetch never use stale evidence for a proposal', () => {
  const data = { data: [row], totalElements: 1 };
  assert.deepEqual(creatorEvidenceSummary({ isSuccess: false, isError: false, isFetching: true }),
    { status: 'loading', hasEvidence: false });
  assert.deepEqual(creatorEvidenceSummary({ ...success(data), isFetching: true }),
    { status: 'loading', hasEvidence: false });
  assert.deepEqual(creatorEvidenceSummary({ data, isSuccess: false, isError: true, isFetching: false }),
    { status: 'error', hasEvidence: false });
});
