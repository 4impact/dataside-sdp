# SDP Request Template API

## Endpoint

```
GET {base-url}/app/{portal}/api/v3/request_templates/{templateId}
```

**Example:**
```
GET https://servicedeskplus.net.au/app/itdesk/api/v3/request_templates/7564000000278687
```

---

## Prerequisites

### 1. OAuth Token Scope

This endpoint requires the **`SDPOnDemand.setup.READ`** scope.

The `SDPOnDemand.requests.ALL` scope used for request CRUD is **not sufficient** — SDP
returns HTTP 401 if the token does not include a setup scope.

| Scope | Grants |
|---|---|
| `SDPOnDemand.setup.READ` | Read-only access to templates and other setup resources |
| `SDPOnDemand.setup.ALL` | Full CRUD on setup resources |

### 2. Generate an Authorisation Code

Open the following URL in a browser (replace placeholders):

```
https://accounts.zoho.com.au/oauth/v2/auth
  ?scope=SDPOnDemand.requests.ALL,SDPOnDemand.setup.READ
  &client_id=<client_id>
  &response_type=code
  &redirect_uri=http://localhost
  &access_type=offline
```

After granting access, Zoho redirects to:
```
http://localhost?code=1000.xxxxxxxx...
```

Copy the `code` value from the redirect URL.

### 3. Exchange the Code for a Token

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
  "scope": "SDPOnDemand.setup.READ",
  "api_domain": "https://www.zohoapis.com.au",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

> The `access_token` is valid for 1 hour. Use the `refresh_token` to get new access tokens
> without re-authorising.

---

## Making the Request

```bash
curl --location \
  'https://servicedeskplus.net.au/app/itdesk/api/v3/request_templates/7564000000278687' \
  --header 'Authorization: Zoho-oauthtoken <access_token>' \
  --header 'Accept: application/vnd.manageengine.sdp.v3+json' \
  --header 'Content-Type: application/x-www-form-urlencoded'
```

### Required headers

| Header | Value |
|---|---|
| `Authorization` | `Zoho-oauthtoken <access_token>` |
| `Accept` | `application/vnd.manageengine.sdp.v3+json` |
| `Content-Type` | `application/x-www-form-urlencoded` |

### URL notes

- The portal segment (`/app/{portal}/`) is **required**. Omitting it returns `4007 Invalid URL`.
- The template ID must **not** be prefixed with a colon. `:7564000000278687` is a documentation
  placeholder convention — the real URL uses `7564000000278687` directly.
- To find a template ID, call `GET /app/{portal}/api/v3/requests` and read the
  `template.id` field from any request object.

---

## Response

**HTTP 200 — success**

```json
{
  "response_status": {
    "status_code": 2000,
    "status": "success"
  },
  "request_template": {
    "id": "7564000000278687",
    "name": "Default Request",
    "image": "incident-icon",
    "is_default": true,
    "is_service_template": false,
    "is_customer_segmented": false,
    "inactive": false,
    "comments": "Default template used for new request creation.",
    "request": {
      "status": { "name": "Open", "internal_name": "Open", "color": "#0066ff" },
      "subject": null,
      "description": null,
      "requester": null,
      "priority": null,
      "urgency": null,
      "impact": null,
      "category": null,
      "subcategory": null,
      "group": null,
      "site": null,
      "email_ids_to_notify": []
    },
    "layouts": [ ... ],
    "has_tasks": false,
    "has_checklists": false
  }
}
```

### Key response fields

| Field | Description |
|---|---|
| `id` | SDP internal template ID |
| `name` | Display name in SDP UI |
| `is_default` | `true` = used when no template is specified on a new request |
| `is_service_template` | `false` = incident template; `true` = service catalog template |
| `inactive` | `true` = template is disabled and cannot be selected |
| `request` | Default field values pre-filled when using this template |
| `layouts` | Field layout for technician view and requester view |

---

## Common Errors

| HTTP | SDP status_code | Cause | Fix |
|---|---|---|---|
| 404 | 4007 | Invalid URL (missing portal or stray `:` on ID) | Use `/app/{portal}/api/v3/request_templates/{id}` |
| 401 | — | OAuth token missing `SDPOnDemand.setup.READ` scope | Re-authorise with the setup scope added |

---

## Application Config

When using OAuth2 auth (`SDP_AUTH_TYPE=OAUTH2`), ensure the token obtained via the
client-credentials flow includes the `SDPOnDemand.setup.READ` scope.

In `application.yml`:
```yaml
sdp:
  auth:
    type: OAUTH2
    oauth2:
      scope: SDPOnDemand.requests.ALL SDPOnDemand.setup.READ
```
