# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build (skip tests)
./gradlew build -x test

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.fourimpact.sdpsinkconnector.service.MessageProcessingServiceTest"

# Run a single test method
./gradlew test --tests "com.fourimpact.sdpsinkconnector.transformer.MessageToSdpTransformerTest.toCreatePayload_mapsAllFields"

# Run the application locally (requires Kafka + env vars set)
./gradlew bootRun --args='--spring.profiles.active=local'

# Build the Docker image
docker build -t sdp-sink-connector .
```

On Windows use `gradlew.bat` instead of `./gradlew`.

## Architecture

This is a **Kafka-to-ServiceDesk Plus bridge** — a long-lived consumer process with no HTTP server of its own. The flow for every message is linear:

```
Kafka Topic
  → SdpKafkaConsumer          (receives ConsumerRecord, holds Acknowledgment)
  → MessageProcessingService  (deserialize JSON → validate → route by OperationType)
  → MessageToSdpTransformer   (maps KafkaMessage fields to the correct SDP payload POJO)
  → ServiceDeskPlusClientImpl (HTTP POST/PUT to SDP REST API with retry)

On any failure:
  → DeadLetterService         (publishes to DLQ Kafka topic, then ack)
```

### Key design decisions to understand before making changes

**Offset commit discipline** — `AckMode.MANUAL_IMMEDIATE` is set. `ack.acknowledge()` is called in *every* branch of the try/catch in `SdpKafkaConsumer`, including DLQ dispatch paths. This is intentional: once something lands on DLQ the offset is committed to prevent infinite replay. Do not refactor the catch blocks without preserving this guarantee.

**Exception routing** — Two custom exceptions drive the entire error-handling strategy:
- `TransientSdpException` → eligible for `@Retryable` (429, 5xx, connection errors). After all retries are exhausted, Spring Retry rethrows it and it falls through to the DLQ path in the consumer.
- `PermanentSdpException` → skips retry, goes straight to DLQ (most 4xx, deserialization failures, missing required fields). **Exception:** SDP error code 4015 (rate limit) arrives as HTTP 400 but is treated as transient — `ServiceDeskPlusClientImpl` inspects the response body and throws `TransientSdpException` in that case.

**Auth selection via `@ConditionalOnProperty`** — Only one `AuthStrategy` bean is registered at startup:
- `sdp.auth.type=API_KEY` (default) → `ApiKeyAuthStrategy` (sets `AUTHTOKEN` header)
- `sdp.auth.type=OAUTH2` → `OAuthAuthStrategy` (client-credentials flow, thread-safe token cache with double-checked locking)

**SDP API wire format** — All calls to SDP use `Content-Type: application/x-www-form-urlencoded` with a single field `input_data=<URL-encoded JSON>`. The JSON is wrapped in `{"request": <payload>}`. This is a ManageEngine SDP v3 API requirement. The `buildInputData()` method in `ServiceDeskPlusClientImpl` handles this wrapping and encoding. URL pattern: `{base-url}/app/{portal}/api/v3/{resource}`. The `portal` value is set via `sdp.portal` (`SDP_PORTAL` env var) and injected into `buildUrl()` in `ServiceDeskPlusClientImpl`.

**SDP payload POJOs** — The four `model/sdp/Sdp*Payload` classes use `@JsonInclude(NON_NULL)` so absent optional fields are not sent to SDP. These POJOs have snake_case field names (e.g., `content_type`, `email_id`) because SDP's API uses snake_case — do not rename them to camelCase.

**No Spring Kafka Connect** — This is a plain `@KafkaListener` application. There is no Kafka Connect runtime, no connector configuration JSON, and no worker process. `KafkaConsumerConfig` and `KafkaProducerConfig` are manual `@Bean` configurations.

**Java 25 + Lombok** — Lombok annotation processing silently fails on Java 25 unless explicitly registered via `annotationProcessorPaths` in `maven-compiler-plugin`. The `pom.xml` already contains this configuration. If you ever see "cannot find symbol" errors for `log`, getters, builders, or constructors, check that this block is present in `<build><plugins>`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

### Testing approach

Tests are pure unit tests — no Spring context is loaded, no embedded Kafka broker.

- `MessageToSdpTransformerTest` — instantiates the transformer directly (`new MessageToSdpTransformer()`)
- `MessageProcessingServiceTest` — constructs the service with `new ObjectMapper()` + `new MessageToSdpTransformer()` + a Mockito mock for `ServiceDeskPlusClient`
- `ServiceDeskPlusClientImplTest` — starts a `WireMockServer` on a dynamic port and constructs `ServiceDeskPlusClientImpl` directly with the WireMock base URL and portal `"test-portal"` (30 tests total)

Note: `@Retryable` on `ServiceDeskPlusClientImpl` methods is **not active** in the client tests because the bean is instantiated directly (not through a Spring proxy). Tests for retry behavior should be added as a Spring integration test if needed.

### Config reference

All runtime config is in `application.yml` and driven by environment variables. The `local` Spring profile activates human-readable console logging instead of JSON. Activate it with `-Dspring-boot.run.profiles=local` or `SPRING_PROFILES_ACTIVE=local`.

Key env vars: `SDP_BASE_URL`, `SDP_PORTAL` (portal name in API URL path), `SDP_AUTH_TYPE`, `SDP_API_KEY`, `SDP_OAUTH_*`, `SDP_RETRY_*`, `KAFKA_*`.
