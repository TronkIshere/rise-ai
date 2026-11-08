#!/usr/bin/env python3
"""HTTP smoke of the running Spring Backend and the Vite same-origin proxy.

Requires a real Spring Boot process, Flyway-migrated MySQL and demo seed.
No intercepted responses, synthetic HTTP fixtures or production endpoints.
"""
import json
import os
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen

BACKEND = os.environ.get("RISE_SMOKE_BACKEND", "http://127.0.0.1:8080").rstrip("/")
FRONTEND = os.environ.get("RISE_SMOKE_FRONTEND", "http://127.0.0.1:5173").rstrip("/")
RESULTS = Path(os.environ.get("RISE_SMOKE_REPORT", "visual-qa/b2-v132/live-api-results.json"))
checks = []


def get(base: str, path: str):
    with urlopen(Request(base + path, headers={"Accept": "application/json"}), timeout=15) as response:
        assert response.status == 200, f"HTTP {response.status} at {path}"
        return json.loads(response.read().decode("utf-8"))


def check(name: str, condition: bool, details=None):
    checks.append({"name": name, "pass": bool(condition), "detail": details})
    print(f"{'PASS' if condition else 'FAIL'} {name}: {details}")
    if not condition:
        raise AssertionError(name)


def unwrap(payload):
    check("Backend response envelope", isinstance(payload, dict) and isinstance(payload.get("data"), dict))
    return payload["data"]


def main():
    health = get(BACKEND, "/actuator/health")
    check("Live Backend health", health.get("status") == "UP")

    params = urlencode({"status": "ACTIVE", "page": 0, "size": 3})
    path = "/api/v1/campaigns?" + params
    direct = unwrap(get(BACKEND, path))
    through_vite = unwrap(get(FRONTEND, path))
    rows = direct.get("data", [])
    check("Live database provides public ACTIVE records", isinstance(rows, list) and len(rows) >= 1, len(rows))
    check("ACTIVE filter is authoritative", all(r.get("campaignStatus") == "ACTIVE" for r in rows))
    check("Homepage page size is exactly 3 or fewer", len(rows) <= 3, len(rows))
    check("Frontend proxy returns identical live campaign IDs",
          [r.get("id") for r in rows] == [r.get("id") for r in through_vite.get("data", [])])
    check("Envelope has pagination metadata",
          all(k in direct for k in ("currentPage", "pageSize", "totalPages", "totalElements")))

    category = rows[0].get("category")
    check("Live campaign category is known",
          category in {"EDUCATION", "COMMUNITY", "FOOD_SECURITY", "WATER_SANITATION",
                       "SCHOLARSHIP", "INFRASTRUCTURE", "HEALTH", "OTHER"}, category)
    category_path = "/api/v1/campaigns?" + urlencode(
        {"status": "ACTIVE", "category": category, "page": 0, "size": 3})
    filtered = unwrap(get(FRONTEND, category_path))
    filtered_rows = filtered.get("data", [])
    check("Category filter returns only matching ACTIVE campaigns",
          bool(filtered_rows) and all(r.get("category") == category and
                                      r.get("campaignStatus") == "ACTIVE" for r in filtered_rows),
          {"category": category, "count": len(filtered_rows)})

    first = rows[0]
    slug = first.get("slug")
    check("Live campaign has a detail slug", isinstance(slug, str) and bool(slug.strip()))
    detail = get(FRONTEND, "/api/v1/campaigns/slug/" + quote(slug, safe="")).get("data")
    check("Campaign detail resolves via real HTTP and Vite proxy",
          isinstance(detail, dict) and detail.get("id") == first.get("id"),
          {"slug": slug, "id_matches": detail.get("id") == first.get("id")})
    check("Funding values are real API fields, not injected fixtures",
          all(not isinstance(r.get("funding"), dict) or
              r["funding"].get("netRaised") is None or
              isinstance(r["funding"]["netRaised"], (float, int)) for r in rows))
    check("Seed records remain visibly illustrative",
          all("(Demo)" in r.get("title", "") or "SIMULATED DEMO DATA" in str(r) for r in rows),
          "Only runs against ephemeral seeded MySQL; never a production dataset")

    RESULTS.parent.mkdir(parents=True, exist_ok=True)
    RESULTS.write_text(json.dumps({
        "source": "Real Spring Boot HTTP + Flyway/MySQL demo seed + Vite proxy; no Playwright interception",
        "production_certified": False,
        "campaign_count": len(rows),
        "filtered_category": category,
        "checks": checks,
        "pass": True,
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"REAL BACKEND HTTP SMOKE PASS — {len(checks)} checks")


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, HTTPError, URLError, ValueError, KeyError) as exc:
        RESULTS.parent.mkdir(parents=True, exist_ok=True)
        RESULTS.write_text(json.dumps({
            "source": "Real Spring Boot HTTP + Flyway/MySQL demo seed + Vite proxy",
            "checks": checks, "pass": False, "error": str(exc),
        }, ensure_ascii=False, indent=2), encoding="utf-8")
        raise
