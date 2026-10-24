# Documentation Index

Documentation for **Local AI Event Monitoring Core** (`local-ai-event-monitor`), written so that a developer or AI coding assistant can continue work without prior conversation history.

> **Source code and tests are the source of truth.** These documents describe the code as inspected on 2026-10-24. If a document contradicts the code, trust the code. Then fix the document as part of your change.

## Recommended reading order

Read only what your task needs.

| Task size | Read |
|---|---|
| Any task | [AI_HANDOFF.md](AI_HANDOFF.md) |
| Small, isolated fix (one class, one test) | The handoff, then the source files involved |
| Change to API, behaviour or configuration | Add [ARCHITECTURE.md](ARCHITECTURE.md), the relevant topic document below, and [DECISIONS.md](DECISIONS.md) |
| New feature or architectural change | Read all of [ARCHITECTURE.md](ARCHITECTURE.md), [DECISIONS.md](DECISIONS.md), [ROADMAP.md](ROADMAP.md) and the topic documents |

## Documents

| Document | Canonical for |
|---|---|
| [AI_HANDOFF.md](AI_HANDOFF.md) | Short orientation, constraints, mandatory workflow for AI assistants |
| [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md) | Purpose, scope, non-goals, stack, implementation status |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Packages, components, dependencies, constraints, implemented vs. future architecture |
| [API_CONTRACT.md](API_CONTRACT.md) | Endpoints, DTOs, validation rules, error codes, JSON examples |
| [EVENT_LIFECYCLE.md](EVENT_LIFECYCLE.md) | Processing pipeline, normalization, deduplication, rules, result assembly |
| [AI_AND_OLLAMA.md](AI_AND_OLLAMA.md) | AI analyzer boundary, Ollama integration, output contract, failure behaviour |
| [CONFIGURATION.md](CONFIGURATION.md) | Every property, its environment variable and its default |
| [DEVELOPMENT.md](DEVELOPMENT.md) | Prerequisites, build/run/test commands, troubleshooting |
| [TESTING.md](TESTING.md) | Test classes, covered behaviour, gaps, latest verified results |
| [DECISIONS.md](DECISIONS.md) | Architectural decisions with status and rationale |
| [ROADMAP.md](ROADMAP.md) | Known limitations, technical debt, prioritized next steps |
| [TEST_SIMULATOR.md](TEST_SIMULATOR.md) | External REST test simulator (`test-simulator/`) and keeping its expectations in sync with the Core |

The root [README.md](../README.md) is a user-facing guide. Where it and this directory overlap, the topic documents here are canonical and more precise.

## Before changing anything

1. Read the source files you intend to change. Do not rely on these documents alone.
2. Search for the class, property or endpoint you are about to document or change. Example: `grep -rn "maxTrackedEvents" src`.
3. Note any conflict between documentation and code before editing. Fix the documentation as part of the change.
4. Record what you actually ran. [TESTING.md](TESTING.md) lists what has and has not been verified.
