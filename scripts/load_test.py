#!/usr/bin/env python3
import argparse
import json
import statistics
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor


def request(base, method, path, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            payload = response.read()
            return response.status, time.perf_counter() - started, payload
    except urllib.error.HTTPError as error:
        return error.code, time.perf_counter() - started, error.read()
    except OSError:
        return 0, time.perf_counter() - started, b""


def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int(len(ordered) * fraction))]


def main():
    parser = argparse.ArgumentParser(description="Read-path load test against a running rise-ai-backend")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--requests", type=int, default=500)
    parser.add_argument("--concurrency", type=int, default=20)
    parser.add_argument("--path", action="append", default=None)
    parser.add_argument("--max-p95-seconds", type=float, default=1.0)
    parser.add_argument("--max-error-rate", type=float, default=0.0)
    args = parser.parse_args()
    paths = args.path or ["/actuator/health/readiness", "/api/v1/campaigns?size=10"]

    jobs = [paths[i % len(paths)] for i in range(args.requests)]
    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        results = list(pool.map(lambda path: (path,) + request(args.base_url, "GET", path)[:2], jobs))
    elapsed = time.perf_counter() - started

    latencies = [r[2] for r in results]
    failures = [r for r in results if r[1] != 200]
    summary = {
        "requests": len(results),
        "concurrency": args.concurrency,
        "elapsedSeconds": round(elapsed, 2),
        "throughputPerSecond": round(len(results) / elapsed, 1),
        "errorRate": round(len(failures) / len(results), 4),
        "statusCodes": {str(code): sum(1 for r in results if r[1] == code) for code in sorted({r[1] for r in results})},
        "latencySeconds": {
            "median": round(statistics.median(latencies), 4),
            "p95": round(percentile(latencies, 0.95), 4),
            "max": round(max(latencies), 4),
        },
    }
    summary["gate"] = {
        "met": summary["errorRate"] <= args.max_error_rate and summary["latencySeconds"]["p95"] <= args.max_p95_seconds,
        "maxP95Seconds": args.max_p95_seconds,
        "maxErrorRate": args.max_error_rate,
        "note": "Read-path only; invariant checks after financial load are in the backend ConcurrencyIntegrationTest and the reconciliation endpoint",
    }
    print(json.dumps(summary, indent=2))
    return 0 if summary["gate"]["met"] else 1


if __name__ == "__main__":
    sys.exit(main())
