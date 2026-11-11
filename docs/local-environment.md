# Local development environment

Tools each module needs, and how to check them without changing your machine. Run the read-only check first:

```bash
bash scripts/check-env.sh      # Linux, macOS, or Git Bash on Windows; exit code 0 = all required tools found
```

The script only reads versions. It never changes environment variables, files or installed software.

## Required tools

| Tool | Version | Used by | Why this version |
|---|---|---|---|
| JDK | **21** | backend, monitoring, Avalanche gateway | All three `pom.xml` files compile for Java 21; CI uses Temurin 21 |
| Maven | 3.9+ (or the wrapper, below) | backend, monitoring, Avalanche gateway | Only `rise-ai-monitoring` ships `./mvnw` |
| Node.js | **>= 22.12** | frontend | `rise-ai-frontend/package.json` `engines`; npm prints `EBADENGINE` below it. CI uses Node 22; the frontend Docker image uses Node 24 |
| Docker | Engine running, Compose v2.17+ | backend and gateway integration tests (Testcontainers MySQL and Anvil), Foundry contract tests (`scripts/forge.sh`), `docker-compose.yml` | Without Docker, the gateway's Docker-gated tests **skip silently**; check the skipped count |

## JDK 21 without changing your system settings

Maven uses `JAVA_HOME` when it is set, and `java` on `PATH` otherwise. If your default `JAVA_HOME` points to another JDK, set it for **one terminal** only:

```bash
# Git Bash (Windows)
export JAVA_HOME="/c/Program Files/Java/jdk-21"
# PowerShell (Windows), this window only
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
# Linux / macOS
export JAVA_HOME=/path/to/jdk-21
```

Or prefix a single command: `JAVA_HOME="/c/Program Files/Java/jdk-21" sh ./mvnw -B clean test`.

## Maven through the monitor's wrapper

`rise-ai-monitoring/mvnw` reads its settings relative to its own location and builds the project in the **current directory**. So it works from every Maven module, and a global Maven is not needed:

```bash
cd rise-ai-monitoring && sh ./mvnw -B clean test
cd rise-ai-backend    && sh ../rise-ai-monitoring/mvnw -B clean test
cd rise-ai-avalanche  && sh ../rise-ai-monitoring/mvnw -B clean test
```

The first run downloads Maven 3.9.11 into `~/.m2/wrapper/dists/`. Call it with `sh` because `mvnw` is stored without the executable bit (CI does the same). On Windows, run it from Git Bash.

## Frontend

```bash
cd rise-ai-frontend
npm ci
npm run verify     # tests, contract check, production build, release check
npm run dev        # http://localhost:5173, needs the backend on :8080
```

## Contract tests

```bash
cd rise-ai-avalanche/contracts
bash scripts/install-deps.sh
bash scripts/forge.sh "forge test --summary"    # Foundry 1.8.7 in Docker
```

## Whole app

See [local-docker-stack.md](local-docker-stack.md) for `docker-compose.yml`, which runs every module, including a local Avalanche chain.
