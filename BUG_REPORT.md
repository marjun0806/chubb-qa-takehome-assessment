# Bug Report & Quality Assessment

## BUG-01: Broken Unit Test Suite & Missing Maven Wrapper Configuration (`claims-service`)
- **Severity:** High
- **Component:** `claims-service` (`UpdateClaimStatusUseCaseTest`, `CreateClaimUseCaseTest`, `KafkaDomainEventPublisherTest`, and `.mvn/wrapper/`)
- **Steps to Reproduce:** Run `.\mvnw.cmd test` or `mvn test` inside `code/claims-service`.
- **Expected Behavior:** The Maven wrapper initializes cleanly and all unit tests compile and pass.
- **Actual Behavior:**
  1. `.\mvnw.cmd` fails immediately because `.mvn/wrapper/maven-wrapper.properties` is missing from the repository.
  2. `UpdateClaimStatusUseCaseTest` fails to compile due to a constructor mismatch (`ApplicationEventPublisher` omitted as the 4th argument).
  3. `CreateClaimUseCaseTest` fails with `NullPointerException` (3 errors) because `@Mock ApplicationEventPublisher` is missing in the Mockito setup.
  4. `KafkaDomainEventPublisherTest` fails with `NullPointerException` (6 failures/errors) because it stubs `ObjectMapper.writeValueAsString(event)` on a mock instead of supporting the `ObjectNode` envelope built via `objectMapper.createObjectNode()`.

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

## BUG-06: Inaccurate & Incomplete OpenAPI / Swagger Specifications (`claims-service` & `bff-service`)
- **Severity:** Low (API Contract / Documentation)
- **Component:** OpenAPI / Swagger annotations across `claims-service` (`:8080`) and `bff-service` (`:8090`)
- **Steps to Reproduce:** Open `http://localhost:8080/swagger-ui/index.html` and `http://localhost:8090/swagger-ui/index.html`, and inspect the response schemas and examples for `POST /api/claims`, `GET /api/claims`, and `GET`/`PATCH /api/admin/claims`.
- **Expected Behavior:** All HTTP error codes (`401`, `403`, `404`) are documented consistently, error examples reflect their respective status codes, and sample request/response payloads satisfy domain validation rules.
- **Actual Behavior:**
  1. `401` is marked `Undocumented` on `GET /api/claims` and `POST /api/claims` (`:8080`), and `403` is marked `Undocumented` on `POST /api/claims` (`:8090`).
  2. Error examples for `401`, `403`, and `404` across both services copy-paste a `400 Validation Error` payload (`"status": 400`, `"detail": "User ID is required"` or `"Incident date cannot be in the future"`).
  3. Request/response examples use `"incidentLocation": "123"` (3 characters), violating `Claim.java`'s 5-character minimum length constraint.

## BUG-07: Non-RFC 7807 Error Format Returned on BFF `403 Forbidden` Responses
- **Severity:** Medium (API Contract Consistency)
- **Component:** `bff-service` (`SecurityConfig` / Spring Security access-denied handling)
- **Steps to Reproduce:** Authenticate via `POST /api/auth/login` in `http://localhost:8090/swagger-ui/index.html` (without attaching an `X-XSRF-TOKEN` header) and execute `POST /api/claims`.
- **Expected Behavior:** The BFF returns an RFC 7807 `ProblemDetail` JSON object (`type`, `title`, `status`, `detail`, `instance`) consistent with its OpenAPI error contract.
- **Actual Behavior:** Spring Security returns a default Spring Boot error map (`{"timestamp": "...", "status": 403, "error": "Forbidden", "path": "/api/claims"}`), deviating from the RFC 7807 `ProblemDetail` schema expected by API clients.