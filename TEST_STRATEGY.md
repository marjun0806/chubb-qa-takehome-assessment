# Test Strategy — Insurance Claims Management Platform

## 1. Executive Summary & System Under Test (SUT)
The **Demo App — Insurance Claims Management** platform is a three-tier, event-driven microservices system:
* **Frontend (`demo-app-ui`):** Next.js 16 + React 19 + Zustand (Port `3001`), handling multi-step claim submission, role-based routing, and real-time WebSocket updates.
* **Backend-for-Frontend (`bff-service`):** Spring Boot 4 + Java 21 (Port `8090`), managing OAuth2/OIDC cookie authentication via Keycloak 26, CSRF validation, RBAC enforcement, REST orchestration, and Kafka-to-WebSocket event fan-out.
* **Core Domain Service (`claims-service`):** Spring Boot 4 + Java 21 (Port `8080`), enforcing claim invariants, status transition state machines, PostgreSQL 15 persistence, Redis 7 caching, and domain event publishing to Apache Kafka (`claim-events`).

---

## 2. Test Pyramid & Current Verification Baseline

| Layer | Scope & Responsibilities | Tooling | Current Suite Status |
| :--- | :--- | :--- | :--- |
| **Unit & Architecture Tests** | Domain invariants, status transition state machines, use-case orchestration, mappers, hexagonal layer boundaries, UI components & Zustand stores | JUnit 5, Mockito, AssertJ, ArchUnit, Vitest, Testing Library | **678 / 678 Passing** (`claims-service`: 199, `bff-service`: 213, `demo-app-ui`: 266) |
| **Contract & Schema Tests** | Consumer-driven HTTP contracts (`bff-service` ↔ `claims-service`) via OpenAPI/WireMock stubs and Kafka event payload validation via AsyncAPI JSON Schemas | Pact / WireMock (`code/bff-service/mocks/`), AsyncAPI (`asyncapi/schemas/`) | Scaffolded; disabled via `skipWiremockGeneration=true` and `kafka.producer.schema-validation.enabled=false` |
| **Integration Tests** | Repository persistence against PostgreSQL 15, Redis cache invalidation, Keycloak token verification, and Kafka producer/consumer serialization | Spring Boot Test, Embedded Kafka, Testcontainers | Embedded Kafka active in BFF; Testcontainers scaffolded for PostgreSQL/Redis |
| **End-to-End (E2E) Tests** | Full-stack browser journeys across UI → BFF → Keycloak → Claims Service → PostgreSQL | Playwright (`code/demo-app-ui/tests/e2e/`) | **5 / 5 Passing** (Auth, RBAC, 3-step Claim Wizard, Admin Dashboard/Claims) |
| **Performance & Resilience** | Concurrent claim creation throughput, WebSocket fan-out latency under load, and Debezium CDC vs. Direct Kafka failover | K6 / Gatling | Planned (Phase 4) |

---

## 3. Risk-Based Test Prioritization

### P0 — Critical Business & Security Flows (Blocking Release)
1. **Authentication & RBAC Enforcement:**
   * Verify `CLAIMANT` users cannot access `/admin/*` UI routes or BFF admin endpoints (`403 Forbidden`).
   * Verify `ADMIN` users can inspect cross-tenant claims and execute valid status transitions (`SUBMITTED` → `UNDER_REVIEW` → `APPROVED` / `REJECTED`).
   * Verify session cookie flags (`HttpOnly`, `SameSite`) and CSRF protection on mutating requests.
2. **Claim Domain Invariants & State Machine:**
   * Enforce boundary constraints (`$0.01` ≤ `claimAmount` ≤ `$1,000,000.00`, non-future `incidentDate`, trimmed `incidentLocation` 5–200 chars, `description` 10–1000 chars).
   * Block illegal state transitions (e.g., transitioning directly from `SUBMITTED` to `APPROVED` without `UNDER_REVIEW`, or mutating terminal states `APPROVED`/`REJECTED`).
3. **Real-Time Event Delivery (Dual-Mode Integrity):**
   * Validate **Mode 1 (Direct Kafka)** vs. **Mode 2 (Debezium CDC)** mutually exclusive configuration (`APP_EVENTS_CDC_ENABLED`). Running both simultaneously causes duplicate WebSocket notifications to connected clients.

### P1 — High-Priority Functional & Integration Flows
* **3-Step Claim Wizard UX & Validation:** Client-side Zod validation, character counters, back/forward state preservation, and post-submit list refresh.
* **Service-to-Service Resilience:** BFF automatic token refresh and single retry upon `401 Unauthorized` from `claims-service`, plus graceful degradation when downstream services or Redis are temporarily degraded.
* **Poison-Pill Resilience:** Kafka consumer error handling for malformed JSON payloads on `claim-events` and `cdc.demo-app.public.claims` without halting partition consumption.

---

## 4. Environment & Test Data Management
* **Pre-Flight Health Gate (`global-setup.ts`):** Playwright verifies BFF readiness (`/actuator/health`) and UI reachability (`/login`) prior to launching browser sessions.
* **Test Data Isolation:** E2E tests generate timestamp-suffixed amounts and locations (`Date.now()`) to avoid collisions across repeated runs against persistent PostgreSQL volumes.
* **Clean-Slate Reset:** Full environment resets are executed via `./stop-app.sh && ./stop-infra.sh -v` followed by `./start-infra.sh && ./start-app.sh`.
* **Seeded Test Identities:**
  * Claimant: `claimant@demo.com` (`CLAIMANT` role)
  * Administrator: `admin@demo.com` (`ADMIN` role)

---

## 5. Defect Management & Root-Cause Findings
During baseline verification and exploratory testing, 7 defects/configuration discrepancies were identified and documented in **`BUG_REPORT.md`**, including:
* Stale unit test mocks in `claims-service` blocking `mvn test` (`BUG-01`).
* Default `app.events.cdc-enabled: true` in `claims-service/src/main/resources/application.yml` conflicting with `README.md`'s documented default (`false`), silently dropping domain events when Debezium is not running (`BUG-02`).
* Node 22+ `localStorage` global getter breaking Zustand persistence middleware in Vitest (`BUG-03`) and `use-theme-store` `sessionStorage` assertion mismatch (`BUG-04`).

---

## 6. CI/CD Quality Gates & Roadmap
1. **Pull Request Gate (Fast Feedback — < 3 min):**
   * Run `mvn test` in `claims-service` and `bff-service` (including ArchUnit architectural rules and JaCoCo coverage checks).
   * Run `npm test` in `demo-app-ui` with V8 coverage thresholds (`statements >= 60%`, `branches >= 50%`).
2. **Staging / Merge Gate (Full Stack — < 10 min):**
   * Spin up Docker Compose stack (`APP_EVENTS_CDC_ENABLED=false`), execute `npx playwright test`, and archive HTML + JUnit XML (`playwright-report/results.xml`) artifacts on failure.
3. **Next-Phase Extensions:**
   * Re-enable `skipWiremockGeneration=false` and `kafka.producer.schema-validation.enabled=true` in CI to enforce Pact/OpenAPI and AsyncAPI schema contracts automatically.
   * Expand Playwright coverage to include WebSocket live-toast verification between concurrent Admin and Claimant browser contexts.
---

## 7. What Was Deliberately Left Out (Risk-Based Scope Exclusions)
In line with sprint prioritization, the following areas were intentionally excluded from this iteration:
* **Visual Regression & Theme CSS Snapshots:** Functional theme state persistence (`useThemeStore`) and picker interactions are covered in unit tests, while pixel-diff visual regression (Storybook/Chromatic) was deferred because functional RBAC, claim state transitions, and event delivery carry significantly higher business risk.
* **Cross-Browser Matrix Execution Locally (Firefox/WebKit):** Configured in `playwright.config.ts` for CI pipelines (`process.env.CI`), while local runs target Chromium/Edge for rapid feedback.
* **K6 Load & WebSocket Fan-Out Stress Testing:** Deferred to post-functional baseline verification once the Debezium CDC vs. Direct Kafka configuration discrepancy (`BUG-02`) is resolved.
* **Full Testcontainers PostgreSQL/Redis Repository Suite:** Repository contracts and service orchestration are thoroughly verified via unit/slice tests (`199` tests in `claims-service`), avoiding Docker-in-Docker overhead during rapid local PR loops.