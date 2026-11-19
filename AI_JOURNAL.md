# AI Working Journal — Engineering Governance & Decision Log

This journal records how LLM-assisted tooling was directed, verified against system invariants, challenged on architectural and environmental assumptions, and overridden where domain accuracy required it.

---

## Executive Decision Matrix

| Area | AI Proposal | QA Verification & Challenge | Final Engineering Decision |
| :--- | :--- | :--- | :--- |
| **Event Pipeline (`BUG-02`)** | Assumed Direct Kafka publishing was active based on `README.md`. | Audited `claims-service/src/main/resources/application.yml` and Kafka consumer offsets; confirmed `app.events.cdc-enabled: true` by default. | **Challenged & Logged:** Preserved production config untouched and documented the CDC-vs-Direct drift in `BUG_REPORT.md`. |
| **Backend Unit Mocks (`BUG-01`)** | Suggested regenerating mock stubs across failing `claims-service` tests. | Inspected `CreateClaimUseCaseImpl` and `UpdateClaimStatusUseCaseImpl` to verify domain event signatures and `ArchUnit` rules. | **Accepted with Verification:** Updated stale constructor/method mocks in 3 test classes (`199/199` passing). |
| **Theme Persistence (`BUG-04`)** | Proposed mocking `localStorage` globally to resolve `use-theme-store.test.ts` failures. | Inspected `src/stores/use-theme-store.ts`; confirmed Zustand intentionally uses `sessionStorage` for tab-isolated theme persistence. | **Overridden:** Aligned test assertions with `sessionStorage` and `persist.rehydrate()` rather than falsifying storage mocks. |
| **Wizard Test Timeout** | Suggested increasing global Vitest timeouts across the entire frontend suite. | Profiled `wizard-step-2.test.tsx`; isolated latency to a single 1,001-character `userEvent.paste` boundary test. | **Overridden:** Applied a targeted `15_000ms` timeout solely to that test so regressions elsewhere fail fast. |
| **E2E Concurrency & Locators** | Generated parallel workers (`workers: 4`) and generic `getByRole('alert')` / `Next` selectors. | Observed Keycloak session contention on shared seeded accounts and Next.js DevTools / route-announcer DOM collisions. | **Overridden:** Scoped locators to form containers, extracted `helpers/auth.ts` (DRY), and set `workers: 1` for deterministic local execution. |

---

## 1. System Architecture & Risk Reconnaissance
* **Objective:** Map trust boundaries, state transitions, and integration seams across `demo-app-ui` (Next.js 16), `bff-service` (Spring Boot 4), `claims-service` (Spring Boot 4), Keycloak 26, PostgreSQL 15, Redis 7, and Kafka prior to writing tests.
* **Accepted:** Risk prioritization centered on BFF cookie/CSRF/RBAC enforcement, `Claim` domain boundary validation (`$0.01`–`$1,000,000`), and illegal status transitions (`SUBMITTED` to `APPROVED` bypass).
* **Challenged & Overrode:** Cross-referencing `README.md` against `application.yml` revealed that Debezium CDC mode defaults to `true` (`app.events.cdc-enabled`), silently dropping domain events when Debezium connectors are not deployed (`BUG-02`). Rather than masking this in application code, it was recorded as a P1 configuration defect in `BUG_REPORT.md`.

---

## 2. Backend Domain & Service Layer Verification
* **Objective:** Establish a reliable unit and architectural baseline across both Spring Boot microservices.
* **Accepted:** Root-cause diagnosis of compilation and mocking regressions in `KafkaDomainEventPublisherTest`, `CreateClaimUseCaseTest`, and `UpdateClaimStatusUseCaseTest` caused by enriched domain event metadata (`BUG-01`).
* **Verification Gate:** Manually verified each updated assertion against production use-case contracts and confirmed hexagonal architecture rules (`ArchUnit`) remained strict and green (`199/199` in `claims-service`, `213/213` in `bff-service`).

---

## 3. Frontend Component & State Store Isolation
* **Objective:** Restore deterministic Vitest execution and validate user-facing behavior using Testing Library best practices.
* **Accepted:** Polyfilling Node 22+'s non-standard global `localStorage` getter and enforcing explicit DOM `cleanup()` in `tests/setup.ts` (`BUG-03`).
* **Overridden:**
  * **Storage Contract Integrity:** Rejected generic `localStorage` stubbing for `use-theme-store.test.ts` after verifying the store's `createJSONStorage(() => sessionStorage)` implementation (`BUG-04`).
  * **Timeout Discipline:** Replaced a blanket global timeout increase with a single test-level timeout on `wizard-step-2.test.tsx` (`userEvent.paste` of 1,001 characters).

---

## 4. End-to-End Browser Automation & DRY Refactoring
* **Objective:** Implement maintainable, non-flaky Playwright journeys covering Authentication/RBAC, Claimant 3-step submission, and Admin claim management.
* **Accepted:** Pre-flight health gating in `global-setup.ts` (`GET /actuator/health` and `GET /login`) to fail fast if Docker services are unavailable.
* **Overridden:**
  * **Framework DOM Collisions:** Replaced broad role queries (`getByRole('alert')`, `getByRole('button', { name: 'Next' })`) with container-scoped exact locators to avoid matching Next.js internal announcer and devtools nodes.
  * **Shared Auth Helper (DRY):** Consolidated duplicated login flows into `tests/e2e/helpers/auth.ts` (`loginAs`) and serialized worker execution (`workers: 1`) to eliminate concurrent session invalidation on shared demo credentials.