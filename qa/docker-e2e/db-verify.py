# Read-only MySQL verification of the entities created by e2e.mjs. Only SELECT statements.
# Runs: docker exec rise-ai-mysql mysql (root password taken from the container's own env, never printed).
import json, subprocess, sys, pathlib

OUT = pathlib.Path(sys.argv[1])
ids = json.loads((OUT / 'results/e2e-results.json').read_text(encoding='utf-8'))['ids']

def q(sql):
    cmd = ['docker', 'exec', 'rise-ai-mysql', 'sh', '-c', 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -B rise_ai -e "$0"', sql]
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode:
        return {'error': r.stderr.strip()[:300]}
    return [line.split('\t') for line in r.stdout.strip().splitlines() if line]

def assert_eq(name, got, want):
    return {'check': name, 'got': got, 'want': want, 'pass': got == want}

c, d, d2, dis, rf, dp, org = (ids.get(k) for k in ['campaignId', 'donationId', 'donor2DonationId', 'disbursementId', 'refundId', 'disputeId', 'orgId'])
checks, raw = [], {}
raw['organization'] = q(f"select verification_status from organizations where id='{org}'")
raw['campaign'] = q(f"select campaign_status from campaigns where id='{c}'")
raw['donations'] = q(f"select id, donation_status, amount, refunded_amount from donations where id in ('{d}','{d2}') order by created_at")
raw['refund'] = q(f"select refund_status, amount from refund_requests where id='{rf}'")
raw['disbursement'] = q(f"select request_status, requested_amount from disbursement_requests where id='{dis}'")
raw['dispute'] = q(f"select dispute_status from disputes where id='{dp}'")
raw['escrow_account'] = q(
    "select total_confirmed_contributions, total_reserved, total_refund_reserved, total_disbursed, total_refunded, available_balance, "
    "total_adjustment_credits, total_adjustment_debits, "
    "(available_balance = total_confirmed_contributions + total_adjustment_credits - total_adjustment_debits - total_reserved - total_refund_reserved - total_disbursed - total_refunded) "
    f"from escrow_accounts where campaign_id='{c}'")
raw['ledger_by_type'] = q(f"select transaction_type, direction, count(*), sum(amount) from escrow_ledger_entries where campaign_id='{c}' group by transaction_type, direction order by 1,2")
raw['audit_rows'] = q(f"select count(*) from audit_logs where entity_id in ('{c}','{d}','{dis}','{rf}','{dp}','{org}')")
raw['outbox_by_status'] = q(f"select status, count(*) from outbox_events where envelope like '%{c}%' group by status")
raw['findings_for_campaign'] = q(f"select rule_id, severity, review_status from monitoring_findings where campaign_id='{c}'")
raw['ledger_update_blocked'] = None  # append-only guard is covered by backend tests; this script never writes

def first(k, i=0, j=0):
    v = raw.get(k)
    return v[i][j] if isinstance(v, list) and len(v) > i and len(v[i]) > j else None

checks.append(assert_eq('organization VERIFIED', first('organization'), 'VERIFIED'))
checks.append(assert_eq('campaign ACTIVE', first('campaign'), 'ACTIVE'))
dmap = {row[0]: row for row in raw['donations']} if isinstance(raw['donations'], list) else {}
checks.append(assert_eq('donor1 donation PARTIALLY_REFUNDED after the recorded 1,000,000 refund', dmap.get(d, [None, None])[1], 'PARTIALLY_REFUNDED'))
checks.append(assert_eq('donor1 refunded_amount 1000000.00', dmap.get(d, [None] * 4)[3], '1000000.00'))
checks.append(assert_eq('donor2 donation CONFIRMED (idempotent create, one row)', dmap.get(d2, [None, None])[1], 'CONFIRMED'))
checks.append(assert_eq('refund RECORDED', first('refund'), 'RECORDED'))
checks.append(assert_eq('disbursement RELEASED', first('disbursement'), 'RELEASED'))
checks.append(assert_eq('escrow balance identity holds', first('escrow_account', 0, 8), '1'))
checks.append(assert_eq('confirmed contributions 6100000.00', first('escrow_account', 0, 0), '6100000.00'))
checks.append(assert_eq('disbursed 4000000.00', first('escrow_account', 0, 3), '4000000.00'))
checks.append(assert_eq('refunded 1000000.00', first('escrow_account', 0, 4), '1000000.00'))
checks.append(assert_eq('available 1100000.00', first('escrow_account', 0, 5), '1100000.00'))
checks.append({'check': 'audit rows exist for QA entities', 'got': first('audit_rows'), 'pass': (int(first('audit_rows') or 0) > 0)})
checks.append({'check': 'outbox has events for the QA campaign', 'got': raw['outbox_by_status'], 'pass': isinstance(raw['outbox_by_status'], list) and len(raw['outbox_by_status']) > 0})
idem_rows = q(f"select count(*) from donations where idempotency_key is not null and id='{d2}'")
raw['idempotency_rows'] = idem_rows
(OUT / 'results/db-verification.json').write_text(json.dumps({'ids': {k: ids.get(k) for k in ['campaignId','donationId','donor2DonationId','disbursementId','refundId','disputeId','orgId']}, 'checks': checks, 'raw': raw}, indent=2), encoding='utf-8')
for ch in checks:
    print(('PASS ' if ch['pass'] else 'FAIL ') + ch['check'], '->', ch['got'])
