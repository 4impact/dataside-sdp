# ServiceDesk Plus Kafka Sink Connector

A Spring Boot 3.5 application that consumes JSON messages from a Kafka topic and writes them to **ServiceDesk Plus (SDP)** via its REST API. No Kafka Connect framework is used — the application uses the Kafka Consumer API directly for maximum control.

---

## Features

- **Four SDP operations**: `CREATE`, `UPDATE`, `ADD_NOTE`, `CLOSE`
- **Configurable auth**: API Key or OAuth 2.0 client-credentials (auto-cached token)
- **Retry with backoff**: Transient failures (5xx, 429, timeout, and SDP rate-limiting error 4015) are retried up to N times with exponential backoff
- **Dead Letter Queue**: Permanent failures (4xx, schema errors) and exhausted retries are published to a configurable DLQ topic
- **Manual offset commit**: Messages are only acknowledged after successful processing or DLQ dispatch
- **Structured JSON logging**: MDC fields (`messageId`, `operation`, `sdpRequestId`) appear in every log line

---

## Kafka Message Schema

```json
{
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "operation": "CREATE",
  "sdpRequestId": null,
  "subject": "VPN access not working",
  "description": "User cannot connect to corporate VPN since this morning.",
  "priority": "High",
  "category": "Network",
  "subCategory": "VPN",
  "requester": {
    "name": "Jane Doe",
    "email": "jane.doe@example.com"
  },
  "note": null,
  "status": null,
  "customFields": {},
  "timestamp": "2026-03-04T10:15:30Z"
}
```

| Field | Required for |
|-------|-------------|
| `operation` | All |
| `subject` | `CREATE` |
| `sdpRequestId` | `UPDATE`, `ADD_NOTE`, `CLOSE` |
| `note` | `ADD_NOTE` |

---

## Configuration

All settings are driven by environment variables. See `src/main/resources/application.yml` for defaults.

| Environment Variable | Default | Description |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker(s) |
| `KAFKA_GROUP_ID` | `sdp-sink-connector` | Consumer group |
| `KAFKA_INPUT_TOPIC` | `sdp-requests` | Input topic |
| `KAFKA_DLQ_TOPIC` | `sdp-requests-dlq` | Dead Letter Queue topic |
| `SDP_BASE_URL` | `https://helpdesk.example.com` | SDP instance base URL |
| `SDP_PORTAL` | _(empty)_ | SDP portal name (appears in API URL path as `/app/{portal}/`) |
| `SDP_AUTH_TYPE` | `API_KEY` | `API_KEY` or `OAUTH2` |
| `SDP_API_KEY` | _(empty)_ | API Key value |
| `SDP_OAUTH_TOKEN_URL` | _(empty)_ | OAuth2 token endpoint |
| `SDP_OAUTH_CLIENT_ID` | _(empty)_ | OAuth2 client ID |
| `SDP_OAUTH_CLIENT_SECRET` | _(empty)_ | OAuth2 client secret |
| `SDP_RETRY_MAX_ATTEMPTS` | `3` | Max retry attempts |
| `SDP_RETRY_INITIAL_MS` | `1000` | Initial backoff (ms) |
| `SDP_RETRY_MULTIPLIER` | `2.0` | Backoff multiplier |
| `SDP_RETRY_MAX_MS` | `15000` | Max backoff (ms) |

---

## Running

### Prerequisites

- Java 25+
- Maven 3.8+
- Running Kafka broker

### Local development

```bash
# Start Kafka (requires Docker)
docker run -d --name kafka -p 9092:9092 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  confluentinc/cp-kafka:7.5.0

# Run application
export SDP_BASE_URL=https://your-sdp-instance.com
export SDP_PORTAL=your-portal-name
export SDP_API_KEY=your-api-key
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Running tests

```bash
mvn test
```

### Docker

```bash
docker build -t sdp-sink-connector .

docker run -d \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e SDP_BASE_URL=https://your-sdp.example.com \
  -e SDP_PORTAL=your-portal-name \
  -e SDP_AUTH_TYPE=API_KEY \
  -e SDP_API_KEY=your-api-key \
  -e KAFKA_INPUT_TOPIC=sdp-requests \
  -e KAFKA_DLQ_TOPIC=sdp-requests-dlq \
  sdp-sink-connector
```

---

## Architecture

```
Kafka Topic
    │
    ▼
SdpKafkaConsumer          (@KafkaListener, manual ack)
    │
    ▼
MessageProcessingService  (deserialize → validate → route)
    │
    ├── MessageToSdpTransformer  (KafkaMessage → SDP payload)
    │
    └── ServiceDeskPlusClientImpl  (@Retryable, RestClient)
              │
              ├── AuthStrategy (ApiKey or OAuth2)
              │
              └── SDP REST API

On failure:
    └── DeadLetterService  (KafkaTemplate → DLQ topic)
```

---

## Project Structure

```
src/main/java/com/fourimpact/sdpsinkconnector/
├── SdpSinkConnectorApplication.java
├── config/          # Kafka consumer/producer config, retry template
├── consumer/        # KafkaListener
├── model/           # KafkaMessage, OperationType, SDP payload POJOs
├── transformer/     # KafkaMessage → SDP payload mapping
├── service/         # Orchestration, DLQ dispatch
├── client/          # RestClient-based SDP API client + auth strategies
└── exception/       # TransientSdpException, PermanentSdpException
```
