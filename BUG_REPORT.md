# Bug Report & Quality Assessment

## BUG-01: Broken Unit Test Compilation in `UpdateClaimStatusUseCaseTest`
- **Severity:** High
- **Component:** `claims-service` (`src/test/java/.../UpdateClaimStatusUseCaseTest.java`)
- **Steps to Reproduce:** Run `mvn test` inside `code/claims-service`.
- **Expected Behavior:** The test suite compiles and executes cleanly.
- **Actual Behavior:** Compilation fails due to a constructor mismatch—`UpdateClaimStatusUseCase` expects `ApplicationEventPublisher` as a 4th constructor argument, which is omitted in the test setup.

## BUG-02: Silent Domain Event Drop & Broken Real-Time Sync in Default Startup
- **Severity:** High
- **Component:** `claims-service` (`application.yml` / `CreateClaimUseCase.java`) & `docker-compose.apps.yml`
- **Steps to Reproduce:** Start the stack with `docker-compose.infra.yml` and `docker-compose.apps.yml`, log in as Claimant, and submit a claim. Check Kafka UI (`http://localhost:9093`) or observe the Claimant UI when an Admin updates the claim status.
- **Expected Behavior:** Claim events are published to Kafka and pushed to the UI via WebSocket in real time.
- **Actual Behavior:** `app.events.cdc-enabled` defaults to `true` in `application.yml`, causing `CreateClaimUseCase` to skip direct Kafka publishing and rely on Debezium CDC (`docker-compose.cdc.yml`), which is not started by default. Events are silently dropped and live WebSocket updates do not fire.

## BUG-03: UI Container Fails After Login on Fresh Build Due to Missing Generated API Client
- **Severity:** High
- **Component:** `demo-app-ui` (`package.json` / `docker-compose.apps.yml`)
- **Steps to Reproduce:** On a fresh clone without `src/lib/api/generated`, start the containers via `docker-compose.apps.yml`, open `http://localhost:3001`, and log in.
- **Expected Behavior:** The `/claims` dashboard loads cleanly.
- **Actual Behavior:** Next.js throws a build error (`Module not found: Can't resolve './generated'`) because the container runs `npm run dev`, bypassing the `prebuild` script (`npm run generate:api`).

## BUG-04: Unrestricted Spring Boot Actuator Endpoint Exposure
- **Severity:** Medium (Security / Configuration)
- **Component:** `claims-service` (`src/main/resources/application.yml`)
- **Steps to Reproduce:** Inspect `management.endpoints.web.exposure.include` in `application.yml`.
- **Expected Behavior:** Only non-sensitive operational endpoints (`health`, `info`, `prometheus`) are exposed by default.
- **Actual Behavior:** Wildcard (`'*'`) exposure is configured, exposing sensitive diagnostic endpoints such as `/actuator/env`, `/actuator/beans`, and `/actuator/heapdump`.

## BUG-05: Test-Only Controller Bundled in Production Source Tree
- **Severity:** Low (Code Quality / Architecture)
- **Component:** `claims-service` (`src/main/java/.../TestEventController.java`)
- **Steps to Reproduce:** Inspect `TestEventController.java` (`POST /api/test/trigger-event`).
- **Expected Behavior:** Test utilities and mock event triggers reside under `src/test/java`.
- **Actual Behavior:** `TestEventController` lives in `src/main/java`. Although guarded by `@Profile("test")`, keeping test endpoints in production source increases risk if profiles are misconfigured.