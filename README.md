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
  "urgency": "Urgent",
  "impact": "Affects Business",
  "category": "Network",
  "subCategory": "VPN",
  "group": "Network Team",
  "technician": "john.smith@example.com",
  "mode": "E-Mail",
  "requestType": "Incident",
  "site": "Head Office",
  "customer": "123456789",
  "template": "Default Template",
  "emailIdsToNotify": ["manager@example.com"],
  "requester": {
    "name": "Jane Doe",
    "email": "jane.doe@example.com"
  },
  "note": null,
  "status": null,
  "updateReason": null,
  "closureComments": null,
  "resolution": null,
  "impactDetails": null,
  "sla": null,
  "level": null,
  "item": null,
  "isFcr": null,
  "statusChangeComments": null,
  "customFields": {},
  "timestamp": "2026-03-04T10:15:30Z"
}
```

| Field | Required for | Notes |
|-------|-------------|-------|
| `operation` | All | `CREATE`, `UPDATE`, `ADD_NOTE`, `CLOSE` |
| `subject` | `CREATE` | |
| `sdpRequestId` | `UPDATE`, `ADD_NOTE`, `CLOSE` | |
| `note` | `ADD_NOTE` | |
| `status` | `CLOSE` | Used as closure code name (e.g. `"Resolved"`) |
| `priority`, `urgency`, `impact` | `CREATE`, `UPDATE` | Named by value (e.g. `"High"`) |
| `category`, `subCategory` | `CREATE`, `UPDATE` | Named by value |
| `group`, `mode`, `requestType`, `site`, `template` | `CREATE`, `UPDATE` | Named by value |
| `technician` | `CREATE`, `UPDATE` | Email address |
| `customer` | `CREATE`, `UPDATE` | SDP customer ID |
| `emailIdsToNotify` | `CREATE`, `UPDATE` | Array of email addresses |
| `updateReason` | `UPDATE` | Audit trail reason for the change |
| `closureComments` | `CLOSE` | Comment recorded when closing |
| `resolution` | `CREATE`, `UPDATE` | Resolution text (mapped to `resolution.content`) |
| `impactDetails` | `CREATE`, `UPDATE` | Free-text impact description |
| `sla` | `CREATE`, `UPDATE` | SLA name |
| `level` | `CREATE`, `UPDATE` | Request level name |
| `item` | `CREATE`, `UPDATE` | Item name |
| `isFcr` | `CREATE`, `UPDATE` | First contact resolution flag |
| `statusChangeComments` | `CREATE`, `UPDATE` | Comment attached to a status change |
| `customFields` | `CREATE`, `UPDATE` | Map of UDF field keys to values |

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

## SDP API — curl Reference

All commands below target the **Zoho SDP MSP Cloud (AU region)**.
Replace `<access_token>`, `<client_id>`, `<client_secret>`, `<auth_code>`, and `<portal>` with your actual values.

---

### 1. Obtain an OAuth Token

#### Step 1 — Generate an authorisation code

Open this URL in a browser. After granting consent, Zoho redirects to
`http://localhost?code=<auth_code>` — copy the `code` value.

**For request operations only (`CREATE`, `UPDATE`, `ADD_NOTE`, `CLOSE`):**
```
https://accounts.zoho.com.au/oauth/v2/auth
  ?scope=SDPOnDemand.requests.ALL
  &client_id=<client_id>
  &response_type=code
  &redirect_uri=http://localhost
  &access_type=offline
```

**For request operations + reading templates (`getRequestTemplate`):**
```
https://accounts.zoho.com.au/oauth/v2/auth
  ?scope=SDPOnDemand.requests.ALL,SDPOnDemand.setup.READ
  &client_id=<client_id>
  &response_type=code
  &redirect_uri=http://localhost
  &access_type=offline
```

> `SDPOnDemand.setup.READ` is required for the `request_templates` endpoint.
> Using only `SDPOnDemand.requests.ALL` returns HTTP 401 on that endpoint.

#### Step 2 — Exchange the code for a token

```bash
curl -X POST "https://accounts.zoho.com.au/oauth/v2/token" \
  -d "code=<auth_code>" \
  -d "grant_type=authorization_code" \
  -d "client_id=<client_id>" \
  -d "client_secret=<client_secret>" \
  -d "redirect_uri=http://localhost"
```

**Successful response:**
```json
{
  "access_token": "1000.xxxx...xxxx",
  "refresh_token": "1000.xxxx...xxxx",
  "scope": "SDPOnDemand.requests.ALL SDPOnDemand.setup.READ",
  "api_domain": "https://www.zohoapis.com.au",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

> The `access_token` expires in 1 hour. Store the `refresh_token` to obtain new tokens without re-authorising.

---

### 2. List Requests

```bash
curl --location \
  'https://servicedeskplus.net.au/app/<portal>/api/v3/requests' \
  --header 'Authorization: Zoho-oauthtoken <access_token>' \
  --header 'Accept: application/vnd.manageengine.sdp.v3+json' \
  --header 'Content-Type: application/x-www-form-urlencoded'
```

**Successful response (truncated):**
```json
{
  "response_status": [{"status_code": 2000, "status": "success"}],
  "list_info": {"has_more_rows": true, "row_count": 10},
  "requests": [
    {
      "id": "7564000000307383",
      "display_id": "1",
      "subject": "URGENT - VPN Connection Issue",
      "status": {"name": "Closed"},
      "requester": {"name": "Bruce Williams"},
      "technician": {"name": "Heather Graham"},
      "template": {"id": "7564000000278687", "name": "Default Request"}
    }
  ]
}
```

> **Common mistake:** The endpoint is `/requests` (plural). Using `/request` (singular) returns
> `status_code: 4007 — Invalid URL`.

---

### 3. Get a Request Template

```bash
curl --location \
  'https://servicedeskplus.net.au/app/<portal>/api/v3/request_templates/<templateId>' \
  --header 'Authorization: Zoho-oauthtoken <access_token>' \
  --header 'Accept: application/vnd.manageengine.sdp.v3+json' \
  --header 'Content-Type: application/x-www-form-urlencoded'
```

**Successful response (truncated):**
```json
{
  "response_status": {"status_code": 2000, "status": "success"},
  "request_template": {
    "id": "7564000000278687",
    "name": "Default Request",
    "is_default": true,
    "is_service_template": false,
    "inactive": false,
    "comments": "Default template used for new request creation.",
    "request": {
      "status": {"name": "Open"}
    }
  }
}
```

> **Common mistakes:**
> - Missing portal segment: `/api/v3/request_templates/...` → `4007 Invalid URL`. Must be `/app/<portal>/api/v3/request_templates/...`
> - Colon prefix on ID: `:7564000000278687` is API doc placeholder notation — use `7564000000278687` directly.
> - Wrong scope: token must include `SDPOnDemand.setup.READ`; `SDPOnDemand.requests.ALL` alone returns HTTP 401.

---

### Common Errors

| HTTP | SDP `status_code` | Cause | Fix |
|---|---|---|---|
| 404 | 4007 | Invalid URL (wrong path, missing portal, or stray `:` on ID) | Check URL matches `/app/<portal>/api/v3/<resource>` |
| 401 | — | OAuth token missing required scope | Re-authorise with the correct scope (see above) |
| 400 | 4015 | SDP rate limit hit | Handled automatically via `TransientSdpException` retry |

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
