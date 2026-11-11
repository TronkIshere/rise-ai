#!/usr/bin/env bash
# Read-only check of the local tools Rise AI needs. Changes nothing: no environment variable, file or install.
# Usage (Linux, macOS, or Git Bash on Windows): bash scripts/check-env.sh
set -u
fail=0
ok() { printf '  OK    %s\n' "$1"; }
bad() { printf '  FAIL  %s\n        fix: %s\n' "$1" "$2"; fail=1; }
warn() { printf '  WARN  %s\n        note: %s\n' "$1" "$2"; }

# true when version $1 >= $2 (dotted numbers)
version_ge() { [ "$(printf '%s\n%s\n' "$2" "$1" | sort -t. -k1,1n -k2,2n -k3,3n | head -1)" = "$2" ]; }

echo "Rise AI local environment check"

# Node >= 22.12 (rise-ai-frontend/package.json "engines")
if command -v node >/dev/null 2>&1; then
  node_v=$(node -v | sed 's/^v//')
  if version_ge "$node_v" 22.12.0; then ok "Node $node_v (>= 22.12)"; else bad "Node $node_v is older than 22.12" "install Node 22.12+ (or 24 LTS) and reopen the terminal"; fi
else
  bad "node not found" "install Node 22.12+ (frontend only)"
fi

# JDK 21 for the three Maven projects. Maven uses JAVA_HOME when it is set, otherwise java on PATH.
java_bin=java
if [ -n "${JAVA_HOME:-}" ]; then java_bin="$JAVA_HOME/bin/java"; fi
if "$java_bin" -version >/dev/null 2>&1; then
  java_major=$("$java_bin" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')
  where="${JAVA_HOME:+JAVA_HOME=$JAVA_HOME}"; where="${where:-java on PATH}"
  if [ "$java_major" = 21 ]; then ok "JDK 21 ($where)"; else bad "Maven would use Java $java_major ($where)" "point JAVA_HOME at a JDK 21 for this terminal only, e.g. export JAVA_HOME=\"/c/Program Files/Java/jdk-21\""; fi
  path_major=$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')
  if [ -n "${JAVA_HOME:-}" ] && [ "$path_major" != "$java_major" ]; then warn "java on PATH is $path_major but JAVA_HOME is $java_major" "Maven uses JAVA_HOME; other tools may use PATH"; fi
else
  bad "no working java (${JAVA_HOME:+JAVA_HOME=$JAVA_HOME})" "install JDK 21 and set JAVA_HOME"
fi

# Maven: a global mvn 3.9+, or the monitor's wrapper (works from any module directory)
if command -v mvn >/dev/null 2>&1; then
  ok "mvn on PATH ($(mvn -v 2>/dev/null | head -1))"
else
  warn "no mvn on PATH" "use the wrapper from any module: sh ../rise-ai-monitoring/mvnw -B clean test (downloads Maven 3.9.11 once into ~/.m2/wrapper)"
fi

# Docker: Testcontainers tests, Foundry contract tests and the Compose stack
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  ok "Docker $(docker version --format '{{.Server.Version}}' 2>/dev/null) is running"
  if docker compose version >/dev/null 2>&1; then ok "docker compose $(docker compose version --short 2>/dev/null)"; else bad "docker compose plugin missing" "install Docker Compose v2.17+"; fi
else
  bad "Docker is not running" "start Docker Desktop (needed by backend and gateway tests, contract tests and docker-compose.yml)"
fi

echo
if [ "$fail" = 0 ]; then echo "All required tools found."; else echo "Some checks failed; see the fix lines above."; fi
exit "$fail"
