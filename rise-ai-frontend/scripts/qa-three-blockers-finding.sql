-- ISOLATED QA DATA ONLY. CI job creates a disposable MySQL 8.4 instance and drops it after the job.
-- This is not an authentic Monitoring detector event. It tests Backend->React rendering and human-review guard.
INSERT INTO monitoring_findings
  (id, finding_key, contract_version, detector_type, rule_id, rule_version, category, severity,
   subject_type, subject_id, campaign_id, milestone_id, disbursement_request_id, explanation,
   recommended_action, facts, review_status, occurrence_count, new_occurrences_since_review,
   first_seen_at, last_seen_at, reviewed_by, reviewed_at, review_note, created_at, updated_at, version)
VALUES
  ('5f0e9c0f-0c1e-4a50-b7da-55a1a89c9221',
   SHA2('RISE_ISOLATED_QA_VISUAL_ONLY_20261122', 256), 'v1', 'RULE', 'QA_UI_ONLY',
   'v1', 'QA_TEST_FIXTURE', 'MEDIUM', 'QA_FIXTURE', 'qa-not-a-real-case',
   NULL, NULL, NULL, '[SIMULATED QA FIXTURE] Test-only finding to verify the Admin UI dossier.',
   'REVIEW_EVIDENCE',
   JSON_ARRAY(JSON_OBJECT('name','qaFixture','value','Disposable QA data, not a real detector alert')),
   'OPEN', 1, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), NULL, NULL, NULL,
   UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0);
INSERT INTO monitoring_finding_occurrences
  (id, finding_id, source_event_id, actor_id, severity, observed_at, ai_note, created_at)
VALUES
  ('cebb99c8-4d2d-449f-86b7-1c8f912555e6',
   '5f0e9c0f-0c1e-4a50-b7da-55a1a89c9221',
   'f547e056-a83c-4d41-adc6-bc35625d4878', NULL, 'MEDIUM', UTC_TIMESTAMP(6),
   'Simulated QA fixture; no AI inference performed.', UTC_TIMESTAMP(6));
