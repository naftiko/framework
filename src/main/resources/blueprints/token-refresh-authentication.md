# Token Refresh Authentication
## Automatic Token Lifecycle Management for Consumed HTTP Adapters

**Status**: Proposed (revised)

**Version**: 1.0.0-alpha2

**Date**: April 11, 2026

**Parent proposal**: [Aggregates & Ref](https://github.com/naftiko/framework/pull/240)

**Revision note**: Updated for consistency with the OAuth 2.1 resource server authentication implemented in [MCP Server Authentication](https://github.com/naftiko/framework/pull/243). Key changes: renamed client-side type to `oauth2-client` (avoids collision with the existing `type: "oauth2"` used on the exposes side), aligned HTTP client and thread-safety patterns with `OAuth2AuthenticationRestlet`, confirmed no new dependencies are needed, and updated Spectral rule prefixes.

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Landscape Analysis — OAuth2 and Proprietary Flows](#2-landscape-analysis--oauth2-and-proprietary-flows)
3. [Current State — Static Tokens](#3-current-state--static-tokens)
4. [Design Analogy](#4-design-analogy)
5. [Core Concepts](#5-core-concepts)
6. [Schema Changes — Authentication](#6-schema-changes--authentication)
7. [Engine Changes — Token Lifecycle](#7-engine-changes--token-lifecycle)
8. [Implementation Examples](#8-implementation-examples)
9. [Security Considerations](#9-security-considerations)
10. [Validation Rules](#10-validation-rules)
11. [Existing Infrastructure (Server-Side OAuth2)](#11-existing-infrastructure-server-side-oauth2)
12. [Design Decisions & Rationale](#12-design-decisions--rationale)
13. [Implementation Roadmap](#13-implementation-roadmap)
14. [Backward Compatibility](#14-backward-compatibility)

---

## 1. Executive Summary

### What This Proposes

Extend the `Authentication` union on consumed HTTP adapters with two new auth types that handle automatic token acquisition, caching, and refresh — eliminating the need for external token management scripts or sidecar processes:

1. **`type: "oauth2-client"` — OAuth 2.0 Client Credentials & Refresh Token flows** — The engine exchanges credentials at a token endpoint for an `access_token`, caches it, tracks `expires_in`, and transparently refreshes before expiry or on `401` response. Supports the two most common OAuth2 grant types for machine-to-machine and delegated-user scenarios.

2. **`type: "custom-token"` — Proprietary token endpoint flows** — For APIs that use non-standard authentication (custom login endpoints, session tokens, JWT exchange) the engine calls a user-defined consumed operation to obtain a token, extracts the token and optional TTL from the response, and applies the same cache-and-refresh lifecycle.

Both types integrate with the existing `binds` system for secret injection and the existing `Resolver.resolveMustacheTemplate()` pipeline for value resolution. No changes to the orchestration engine (`steps`/`call`/`with`) are needed — token management is handled internally by `HttpClientAdapter` before any request is dispatched.

> **Naming note**: The type is `oauth2-client` (not `oauth2`) because `type: "oauth2"` already exists in the shared `Authentication` union — it is used on the **exposes** side for OAuth 2.1 resource server authentication (JWT validation via JWKS). The `-client` suffix makes the directionality explicit: this type *acquires* tokens as a client, while the existing `oauth2` type *validates* tokens as a server.

### What This Does NOT Do

- **No Authorization Code flow with user interaction** — interactive browser-based consent is out of scope. Naftiko runs headless; user-delegated access requires a pre-obtained refresh token.
- **No PKCE** — Proof Key for Code Exchange is relevant only for public clients with interactive flows.
- **No Device Authorization Grant** — device code flows require user interaction on a secondary device.
- **No token persistence across restarts** — tokens are cached in memory only. On restart, the engine re-acquires tokens. Persistent token storage (encrypted file, external vault) is a future extension.
- **No changes to exposed adapter authentication** — OAuth 2.1 resource server auth (`type: "oauth2"`) is already implemented on the exposes side (`OAuth2AuthenticationRestlet`, `McpOAuth2Restlet`). This proposal targets the consumes (outbound) side only.
- **No changes to CI/CD workflows** or branch protection rules.

### Business Value

| Benefit | Impact | Users |
|---------|--------|-------|
| **Zero-glue token management** | Eliminate cron jobs, Lambda rotators, and sidecar token refreshers | DevOps, SREs |
| **Long-running capability uptime** | Capabilities consuming OAuth2 APIs no longer fail after token expiry | Operators |
| **Broader API coverage** | Unlock Salesforce, Microsoft Graph, Google APIs, and enterprise OAuth2 providers | Integrators |
| **Security by default** | Tokens are never written to YAML; credentials resolve from `binds` at runtime | InfoSec Teams |
| **Declarative intent** | Capability authors declare "how to authenticate" — the engine handles when and how often | Capability Authors |

### Key Design Decisions

1. **Authentication, not orchestration**: Token refresh is an adapter-internal concern, not a user-visible step. Capability authors should not need to write `steps` to obtain tokens — the engine handles it transparently.

2. **Proactive + reactive refresh**: The engine refreshes tokens *before* expiry (proactive, using `expires_in` minus a safety margin) *and* retries on `401 Unauthorized` (reactive, as a fallback for clock skew or early revocation).

3. **One token per consumed adapter**: Each `consumes` entry with an `oauth2` or `custom-token` authentication block manages its own independent token lifecycle. No sharing across namespaces.

4. **Thread-safe, lazy acquisition**: The first request triggers token acquisition. Concurrent requests wait for the same acquisition (no thundering herd). Subsequent requests reuse the cached token until refresh is needed. This mirrors the lazy-initialization pattern already established by `OAuth2AuthenticationRestlet.ensureInitialized()` on the server side.

5. **`custom-token` reuses consumed operations**: Instead of inventing a new HTTP call mechanism for proprietary auth endpoints, `custom-token` references an existing consumed operation via `call`. This is consistent with how webhooks reuse consumed operations for registration.

6. **Consistent HTTP client**: Token endpoint calls use the Restlet `Client` — the same HTTP client already used by `HttpClientAdapter` for all consumed API calls. This keeps the consumes side on a single HTTP stack and avoids mixing Restlet with `java.net.http`.

### Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| **Credential leak in logs** | Medium | High | Never log token values; mask in debug output |
| **Token endpoint downtime** | Medium | Medium | Retry with exponential backoff; fail-open is NOT an option |
| **Clock skew causes premature/late refresh** | Low | Medium | Configurable safety margin (default 60s) |
| **Refresh token rotation** | Low | Medium | Update stored refresh token when endpoint returns a new one |
| **Race condition on concurrent refresh** | Low | High | Lock-based single-flight refresh pattern |
| **Schema complexity** | Low | Low | Additive — new auth types, existing types unchanged |

**Overall Risk**: **LOW-MEDIUM** — OAuth2 flows are well-standardized. The engine complexity is concentrated in `HttpClientAdapter`; no changes to the orchestration engine.

---

## 2. Landscape Analysis — OAuth2 and Proprietary Flows

### 2.1 OAuth 2.0 Grant Types Relevant to M2M

| Grant Type | RFC | Use Case | Interactive? | Naftiko Relevance |
|-----------|-----|----------|-------------|-------------------|
| **Client Credentials** | RFC 6749 §4.4 | Server-to-server, no user context | No | **Primary target** — most common M2M flow |
| **Refresh Token** | RFC 6749 §6 | Extend access using a long-lived refresh token | No (after initial auth) | **Secondary target** — user-delegated access with pre-obtained refresh token |
| **Authorization Code** | RFC 6749 §4.1 | User-delegated access with browser consent | Yes | Out of scope — Naftiko is headless |
| **Device Code** | RFC 8628 | IoT/CLI device authorization | Yes (secondary device) | Out of scope |
| **JWT Bearer Assertion** | RFC 7523 | Service account (Google, Azure) | No | **Future extension** — requires JWT signing |

### 2.2 Token Endpoint Response (RFC 6749 §5.1)

All OAuth2 token endpoints return the same response structure:

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "dGhpcyBpcyBhIHJlZnJlc2g...",
  "scope": "read write"
}
```

| Field | Required | Purpose |
|-------|----------|---------|
| `access_token` | Yes | The token to use in `Authorization: Bearer` headers |
| `token_type` | Yes | Always `"Bearer"` for our use cases |
| `expires_in` | Recommended | Token lifetime in seconds. If absent, assume no expiry (but still handle `401`) |
| `refresh_token` | Optional | Long-lived token to obtain new access tokens without re-authenticating |
| `scope` | Optional | Granted scopes (informational — Naftiko does not enforce scopes) |

### 2.3 Real-World API Survey

| API Provider | Auth Flow | Token Endpoint | Token Lifetime | Refresh? |
|-------------|-----------|---------------|----------------|----------|
| **Salesforce** | Client Credentials / Refresh Token | `https://login.salesforce.com/services/oauth2/token` | 2 hours | Yes |
| **Microsoft Graph** | Client Credentials | `https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token` | 1 hour | No (re-acquire) |
| **Google APIs** | JWT Bearer / Refresh Token | `https://oauth2.googleapis.com/token` | 1 hour | Yes (service accounts use JWT) |
| **HubSpot** | Client Credentials / Refresh Token | `https://api.hubapi.com/oauth/v1/token` | 30 minutes | Yes |
| **Shopify** | Custom (exchange code for token) | `https://{shop}.myshopify.com/admin/oauth/access_token` | No expiry | No |
| **Stripe** | Static API Key | N/A | No expiry | No |
| **Notion** | Static Bearer Token | N/A | No expiry | No |

**Key insight**: APIs with short-lived tokens (Salesforce, Microsoft, Google, HubSpot) are the primary beneficiaries. APIs with static tokens (Stripe, Notion) already work with the existing `bearer` auth type.

### 2.4 Proprietary Token Flows

Some APIs do not follow OAuth2 but still require token acquisition via an endpoint:

| Pattern | Example | Flow |
|---------|---------|------|
| **Login endpoint** | `POST /api/auth/login` with `{ username, password }` → `{ token, expiresAt }` | Custom JSON-based auth |
| **API key exchange** | `POST /api/token` with API key in header → `{ sessionToken }` | Legacy enterprise APIs |
| **JWT self-signed** | Sign a JWT locally, `POST /auth/token` with assertion → `{ access_token }` | Google service accounts |
| **SAML assertion** | Exchange SAML token at STS endpoint → `{ access_token }` | Enterprise SSO (rare for APIs) |

The `custom-token` auth type covers all of these by delegating the token acquisition call to a user-defined consumed operation.

---

## 3. Current State — Static Tokens

### What Works Today

```yaml
consumes:
  - type: "http"
    namespace: "notion"
    baseUri: "https://api.notion.com/v1"
    authentication:
      type: "bearer"
      token: "{{NOTION_TOKEN}}"       # Static — set at startup, never changes
```

The `bearer` token is resolved once via Mustache template substitution from `binds` and applied to every outgoing request by `HttpClientAdapter.setChallengeResponse()`.

### Where It Breaks

```
                             ┌──────────────────┐
                             │  Naftiko Engine   │
                             │  (bearer token)   │
                             └────────┬─────────┘
                                      │
              t=0: token valid        │  t=3600: token expired
              ──────────────────────► │ ──────────────────────►
                                      │
                    200 OK            │         401 Unauthorized
              ◄─────────────          │  ◄──────────────────────
                                      │
                                      │  ✗ No retry, no refresh
                                      │  ✗ Capability fails until restart
                                      │  ✗ Operator must rotate externally
```

**Problem**: For APIs with expiring tokens (Salesforce, Microsoft Graph, Google), the capability stops working after the token's TTL. Today, operators must:

1. Run an external cron job / Lambda to refresh the token
2. Inject the new token via environment variable
3. Restart the Naftiko container

This defeats the "zero-glue" promise of Naftiko.

### Proposed State

```
                             ┌──────────────────────────────┐
                             │  Naftiko Engine               │
                             │  (oauth2-client / custom-token) │
                             │                                │
                             │  ┌──────────────────────────┐ │
                             │  │  TokenManager             │ │
                             │  │  • acquire on first use   │ │
                             │  │  • cache access_token     │ │
                             │  │  • track expires_in       │ │
                             │  │  • proactive refresh      │ │
                             │  │  • reactive 401 retry     │ │
                             │  └──────────────────────────┘ │
                             └────────┬─────────────────────┘
                                      │
              t=0: acquire token      │  t=3540: proactive refresh
              ──────────────────────► │ ──────────────────────►
                                      │
                    200 OK            │         200 OK (new token)
              ◄─────────────          │  ◄──────────────────────
                                      │
                                      │  ✓ Automatic, invisible to capability
                                      │  ✓ No restart, no external tooling
```

---

## 4. Design Analogy

### Bearer is a key. OAuth2 is a key card system.

The existing `type: "bearer"` authentication is like a physical key: you cut it once, it works until the lock is changed, and if it breaks you need a locksmith (manual token rotation).

The proposed `type: "oauth2-client"` authentication is like a hotel key card system: you check in (client credentials), get a card (access token) that works for a limited time (expires_in), and when it expires you go back to the front desk (token endpoint) to get a new one — automatically, without checking out and back in.

The `type: "custom-token"` authentication is like a building's custom badge system: the protocol is specific to the building, but the pattern is the same — present credentials, receive a time-limited badge, renew when it expires.

### In Naftiko Terms

| Concept | Bearer (current) | OAuth2-Client / Custom-Token (proposed) |
|---------|------------------|----------------------------------|
| **Token source** | Static value from `binds` | Dynamically acquired from token endpoint |
| **Token lifetime** | Unlimited (until external rotation) | Tracked via `expires_in` or TTL |
| **Refresh trigger** | Manual restart | Automatic: proactive (before expiry) or reactive (on `401`) |
| **Credential storage** | Token in `binds` | Client ID/secret in `binds` (never the access token) |
| **Failure mode** | Silent `401` errors | Retry with fresh token, then fail with clear error |

---

## 5. Core Concepts

### 5.1 Token Manager

A new internal component in `HttpClientAdapter` responsible for the token lifecycle. One `TokenManager` per consumed adapter with `oauth2-client` or `custom-token` authentication.

**Responsibilities**:
- **Acquire**: Call the token endpoint on first use (lazy initialization)
- **Cache**: Store the `access_token` and its expiry timestamp in memory
- **Refresh (proactive)**: Re-acquire the token when `now + safetyMargin >= expiryTime`
- **Refresh (reactive)**: Re-acquire on `401 Unauthorized` response, then retry the original request exactly once
- **Concurrency**: Use a lock to prevent multiple threads from refreshing simultaneously (single-flight pattern)

**Not a separate class** (yet): In the initial implementation, `TokenManager` can be an inner concern of `HttpClientAdapter`. If complexity grows (JWT signing, persistent storage), it can be extracted.

### 5.2 OAuth2 Client Credentials Flow

```
  Naftiko Engine                          Token Endpoint
       │                                       │
       │  POST /token                           │
       │  grant_type=client_credentials         │
       │  client_id={{CLIENT_ID}}               │
       │  client_secret={{CLIENT_SECRET}}        │
       │  scope=read+write (optional)           │
       │ ──────────────────────────────────────► │
       │                                       │
       │  200 OK                               │
       │  { access_token, expires_in }         │
       │ ◄────────────────────────────────────── │
       │                                       │
       │  [cache token, schedule refresh]       │
       │                                       │
       │  GET /api/data                        │
       │  Authorization: Bearer <access_token>  │
       │ ──────────────────────────────────────► │  Target API
```

### 5.3 OAuth2 Refresh Token Flow

```
  Naftiko Engine                          Token Endpoint
       │                                       │
       │  [initial: use pre-obtained             │
       │   refresh_token from binds]             │
       │                                       │
       │  POST /token                           │
       │  grant_type=refresh_token              │
       │  refresh_token={{REFRESH_TOKEN}}        │
       │  client_id={{CLIENT_ID}}               │
       │ ──────────────────────────────────────► │
       │                                       │
       │  200 OK                               │
       │  { access_token, expires_in,           │
       │    refresh_token (maybe rotated) }     │
       │ ◄────────────────────────────────────── │
       │                                       │
       │  [cache token, update refresh_token    │
       │   if rotated, schedule next refresh]   │
```

### 5.4 Custom Token Flow

```
  Naftiko Engine                     Consumed Operation
       │                                ("auth.login")
       │                                       │
       │  [call consumed operation with `with`  │
       │   injectors from binds]                │
       │ ──────────────────────────────────────► │
       │                                       │
       │  200 OK                               │
       │  { <tokenField>: "abc123",             │
       │    <expiresField>: 3600 }              │
       │ ◄────────────────────────────────────── │
       │                                       │
       │  [extract token via JsonPath,          │
       │   cache, schedule refresh]             │
```

### 5.5 Reactive 401 Retry

Regardless of auth type, when the engine receives a `401 Unauthorized` response:

1. Discard the cached token
2. Re-acquire a fresh token (same flow as initial acquisition)
3. Retry the original request with the new token
4. If the retry also returns `401`, fail with a clear error — do not loop

This handles edge cases: token revoked server-side, clock skew causing early expiry, or `expires_in` missing from the token response.

---

## 6. Schema Changes — Authentication

### 6.1 Updated `Authentication` Union

Add two new variants to the existing `oneOf` (which already contains `AuthOAuth2` for server-side resource server auth):

```json
"Authentication": {
  "description": "Authentication",
  "oneOf": [
    { "$ref": "#/$defs/AuthBasic" },
    { "$ref": "#/$defs/AuthApiKey" },
    { "$ref": "#/$defs/AuthBearer" },
    { "$ref": "#/$defs/AuthDigest" },
    { "$ref": "#/$defs/AuthOAuth2" },
    { "$ref": "#/$defs/AuthOAuth2Client" },
    { "$ref": "#/$defs/AuthCustomToken" }
  ]
}
```

> **Design note**: `AuthOAuth2` (server-side, for exposes) and `AuthOAuth2Client` (client-side, for consumes) are both in the shared union. The engine dispatches based on `type` — `"oauth2"` routes to `OAuth2AuthenticationRestlet` on the server side; `"oauth2-client"` routes to `TokenManager` on the client side. A Spectral rule ensures `oauth2` is not used on `consumes` and `oauth2-client` is not used on `exposes`.

### 6.2 `AuthOAuth2Client`

```json
"AuthOAuth2Client": {
  "type": "object",
  "description": "OAuth 2.0 client authentication with automatic token acquisition and refresh. The engine exchanges credentials at a token endpoint, caches the access token, and refreshes it proactively before expiry or reactively on 401.",
  "properties": {
    "type": {
      "type": "string",
      "const": "oauth2-client"
    },
    "grantType": {
      "type": "string",
      "enum": ["client-credentials", "refresh-token"],
      "description": "OAuth2 grant type. 'client-credentials' for M2M server-to-server. 'refresh-token' for user-delegated access with a pre-obtained refresh token."
    },
    "tokenEndpoint": {
      "type": "string",
      "format": "uri",
      "description": "Absolute URL of the OAuth2 token endpoint (e.g., 'https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token')."
    },
    "clientId": {
      "type": "string",
      "description": "OAuth2 client identifier. Supports Mustache templates (e.g., '{{CLIENT_ID}}')."
    },
    "clientSecret": {
      "type": "string",
      "description": "OAuth2 client secret. Supports Mustache templates (e.g., '{{CLIENT_SECRET}}'). Sent in the request body by default."
    },
    "refreshToken": {
      "type": "string",
      "description": "Pre-obtained refresh token (required when grantType is 'refresh-token'). Supports Mustache templates."
    },
    "scope": {
      "type": "string",
      "description": "Space-separated OAuth2 scopes to request (e.g., 'read write'). Optional — omit if the token endpoint does not require scopes."
    },
    "safetyMargin": {
      "type": "integer",
      "minimum": 0,
      "default": 60,
      "description": "Seconds before token expiry to proactively refresh. Default: 60. Set to 0 to disable proactive refresh (rely on reactive 401 retry only)."
    }
  },
  "required": ["type", "grantType", "tokenEndpoint", "clientId"],
  "if": {
    "properties": { "grantType": { "const": "refresh-token" } }
  },
  "then": {
    "required": ["refreshToken"]
  },
  "additionalProperties": false
}
```

### 6.3 Existing `AuthOAuth2` (Server-Side — Unchanged)

For reference, the existing `AuthOAuth2` definition (used on the **exposes** side for resource server JWT validation) remains unchanged:

```json
"AuthOAuth2": {
  "type": "object",
  "description": "OAuth 2.1 resource server authentication. The server validates bearer tokens issued by an external authorization server.",
  "properties": {
    "type": { "const": "oauth2" },
    "authorizationServerUrl": { "type": "string", "format": "uri" },
    "resource": { "type": "string", "format": "uri" },
    "scopes": { "type": "array", "items": { "type": "string" } },
    "audience": { "type": "string" },
    "tokenValidation": { "enum": ["jwks", "introspection"], "default": "jwks" }
  },
  "required": ["type", "authorizationServerUrl", "resource"],
  "additionalProperties": false
}
```

The two definitions have no overlapping fields (except `type`) and serve opposite directions:
- `AuthOAuth2` (exposes): "validate incoming tokens from an authorization server"
- `AuthOAuth2Client` (consumes): "acquire outgoing tokens from an authorization server"

### 6.4 `AuthCustomToken`

```json
"AuthCustomToken": {
  "type": "object",
  "description": "Custom token endpoint authentication. Calls a consumed operation to acquire a token, extracts it from the response, and manages its lifecycle automatically.",
  "properties": {
    "type": {
      "type": "string",
      "const": "custom-token"
    },
    "acquire": {
      "type": "object",
      "description": "How to obtain the token.",
      "properties": {
        "call": {
          "type": "string",
          "pattern": "^[a-zA-Z0-9-]+\\.[a-zA-Z0-9-]+$",
          "description": "Reference to a consumed operation (namespace.operation) that returns the token. The referenced operation must be on a *different* consumed adapter (to avoid circular auth)."
        },
        "with": {
          "$ref": "#/$defs/WithInjector",
          "description": "Parameter injection for the token acquisition call."
        },
        "tokenField": {
          "type": "string",
          "description": "JsonPath expression to extract the token from the response body (e.g., '$.access_token', '$.data.token')."
        },
        "expiresInField": {
          "type": "string",
          "description": "JsonPath expression to extract the TTL in seconds from the response body (e.g., '$.expires_in'). Optional — if omitted, the token is assumed to never expire (but 401 reactive refresh still applies)."
        }
      },
      "required": ["call", "tokenField"],
      "additionalProperties": false
    },
    "placement": {
      "type": "string",
      "enum": ["bearer", "header", "query"],
      "default": "bearer",
      "description": "How to apply the token to outgoing requests. 'bearer' sets 'Authorization: Bearer <token>'. 'header' uses a custom header (requires 'headerName'). 'query' appends as a query parameter (requires 'paramName')."
    },
    "headerName": {
      "type": "string",
      "description": "Custom header name (required when placement is 'header')."
    },
    "paramName": {
      "type": "string",
      "description": "Query parameter name (required when placement is 'query')."
    },
    "safetyMargin": {
      "type": "integer",
      "minimum": 0,
      "default": 60,
      "description": "Seconds before token expiry to proactively refresh. Default: 60."
    }
  },
  "required": ["type", "acquire"],
  "additionalProperties": false
}
```

### 6.5 Schema Design Notes

**Why `oauth2-client` and not just `oauth2`**: The `Authentication` union (`#/$defs/Authentication`) is shared between `exposes` and `consumes` in the Naftiko schema. The type `"oauth2"` is already claimed by `AuthOAuth2` for server-side resource server authentication (JWT validation via JWKS, implemented in `OAuth2AuthenticationRestlet`). Using the same type name with different fields would create ambiguity in the schema `oneOf` discriminator and in Jackson's `@JsonSubTypes` mapping. The `-client` suffix makes the directionality explicit and avoids all collision.

**Why `grantType` uses kebab-case (`client-credentials`) instead of the OAuth2 spec's snake_case (`client_credentials`)**: Consistency with the Naftiko schema convention (`IdentifierKebab`). The engine maps internally to the wire format (`grant_type=client_credentials`) when constructing the token request.

**Why `tokenEndpoint` is a standalone URL (not a consumed operation)**: OAuth2 token endpoints follow a standardized protocol (RFC 6749 §5). The engine knows exactly how to call them — there is no value in forcing the user to declare a consumed resource/operation for a well-defined endpoint. This also avoids circular authentication (the token endpoint itself would need auth to call).

**Why `custom-token.acquire.call` references a different consumed adapter**: If the token acquisition operation lived on the same consumed adapter that requires the token, the engine would need to call an authenticated endpoint to obtain the authentication — a circular dependency. The `call` reference must target an operation on a separate, independently-authenticated (or unauthenticated) consumed adapter. A Spectral rule enforces this.

---

## 7. Engine Changes — Token Lifecycle

### 7.1 `HttpClientAdapter` Extensions

The core change is in `HttpClientAdapter.setChallengeResponse()`. Today, it reads the static token from the spec and applies it. With `oauth2-client` and `custom-token`, it delegates to the token manager:

```
setChallengeResponse()
  ├── "basic"         → (unchanged) static credentials
  ├── "digest"        → (unchanged) static credentials
  ├── "bearer"        → (unchanged) static token from spec
  ├── "apikey"        → (unchanged) static key/value from spec
  ├── "oauth2-client" → tokenManager.getValidToken() → apply as Bearer
  └── "custom-token"  → tokenManager.getValidToken() → apply per placement
```

The new `case "oauth2-client"` branch follows the same structure as the existing `case "bearer"` — it builds a `ChallengeResponse(ChallengeScheme.HTTP_OAUTH_BEARER)` and sets the raw value. The only difference is that the token comes from `TokenManager` rather than a static spec field.

### 7.2 Token Manager Lifecycle

```
                     ┌──────────────────────────────────┐
                     │          TokenManager              │
                     │                                    │
                     │  State:                            │
                     │    accessToken: String              │
                     │    expiresAt: Instant               │
                     │    refreshToken: String (mutable)   │
                     │    lock: ReentrantLock              │
                     │                                    │
                     │  Methods:                          │
                     │    getValidToken(): String          │
                     │    acquireToken(): void             │
                     │    isExpired(): boolean             │
                     │    handleUnauthorized(): String     │
                     └──────────┬───────────────────────┘
                                │
          ┌─────────────────────┼─────────────────────┐
          │                     │                     │
     First request         Before expiry          On 401
          │                     │                     │
     acquireToken()        acquireToken()        handleUnauthorized()
          │                     │                     │
     POST /token           POST /token          discard + acquireToken()
          │                     │                + retry original request
     cache result          cache result               │
          │                     │              return new token
     return token          return token         (or throw on 2nd 401)
```

### 7.3 Reactive 401 Retry — Integration Point

The `401` retry must happen at the call site, not inside `setChallengeResponse()`. The natural integration point is `OperationStepExecutor.execute()` (or the individual `handle()` calls in `findClientRequestFor()`):

```
execute(call, steps, parameters, entityLabel)
  │
  ├── found = findClientRequestFor(call, parameters)
  │     └── setChallengeResponse()  ← applies current (possibly stale) token
  │
  ├── found.handle()
  │     └── response = HTTP call
  │
  ├── if response.status == 401 AND auth is oauth2-client|custom-token:
  │     ├── tokenManager.handleUnauthorized()  ← re-acquire token
  │     ├── rebuild request with new token
  │     ├── retry handle()
  │     └── if 401 again → throw AuthenticationException
  │
  └── return found
```

This is a single retry (not a loop). The retry flag is tracked per-request to prevent infinite loops.

### 7.4 Thread Safety

The server-side `OAuth2AuthenticationRestlet` already establishes the thread-safety pattern for the framework: `synchronized` blocks for initialization and refresh, `volatile` fields for cached state, and a double-check idiom to avoid redundant work. The client-side `TokenManager` follows the same pattern for consistency:

```java
class TokenManager {
    private final Object tokenLock = new Object();
    private volatile String accessToken;
    private volatile Instant expiresAt;
    private volatile String refreshToken;  // may be rotated by the server

    String getValidToken(Map<String, Object> parameters) {
        if (accessToken != null && !isExpiringSoon()) {
            return accessToken;
        }
        synchronized (tokenLock) {
            // Double-check after acquiring lock
            if (accessToken != null && !isExpiringSoon()) {
                return accessToken;
            }
            acquireToken(parameters);
            return accessToken;
        }
    }
}
```

This is the same double-check pattern used by `OAuth2AuthenticationRestlet.ensureInitialized()` (with `initLock`) and `refreshJwkSet()` (with `jwkRefreshLock`). Using `synchronized` + `volatile` rather than `ReentrantLock` keeps the concurrency model uniform across the codebase.

### 7.5 Token Acquisition — OAuth2

For `type: "oauth2-client"`, the engine constructs a standard form-encoded POST to the `tokenEndpoint` using the Restlet `Client` — the same HTTP client already used by `HttpClientAdapter` for all consumed API calls. The `TokenManager` reuses the adapter's existing `Client` instance (`httpClient`) rather than creating a separate HTTP stack. This keeps the consumes side on a single HTTP library and benefits from any proxy, TLS, or connection-pool configuration already applied to the adapter.

**Client Credentials:**
```
POST {tokenEndpoint}
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
&client_id={resolved clientId}
&client_secret={resolved clientSecret}
&scope={scope}                          // if specified
```

**Refresh Token:**
```
POST {tokenEndpoint}
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token
&refresh_token={resolved refreshToken}
&client_id={resolved clientId}
&client_secret={resolved clientSecret}  // if specified
```

The response is parsed as JSON. The engine extracts `access_token`, `expires_in` (if present), and `refresh_token` (if present and newer than the current one — token rotation).

### 7.6 Token Acquisition — Custom Token

For `type: "custom-token"`, the engine delegates to the consumed operation referenced by `acquire.call`:

1. Resolve the `call` reference to a `HttpClientAdapter` + `HttpClientOperationSpec`
2. Build the request using `with` injectors (same as `OperationStepExecutor.findClientRequestFor()`)
3. Execute the HTTP call
4. Parse the response body as JSON
5. Extract the token using `acquire.tokenField` (JsonPath)
6. Extract the TTL using `acquire.expiresInField` (JsonPath, optional)
7. Cache and apply

**Circular dependency guard**: The consumed adapter referenced by `acquire.call` must NOT itself use `oauth2-client` or `custom-token` authentication. If it does, the engine throws a startup error. This is validated at capability load time, not at runtime.

---

## 8. Implementation Examples

### 8.1 Salesforce — OAuth2 Client Credentials

```yaml
naftiko: "1.0.0-alpha2"

info:
  label: "Salesforce Integration"
  description: "Query Salesforce objects via OAuth2 Client Credentials."
  tags:
    - Salesforce
    - OAuth2

binds:
  - namespace: "salesforce-creds"
    keys:
      SF_CLIENT_ID: "SF_CLIENT_ID"
      SF_CLIENT_SECRET: "SF_CLIENT_SECRET"
      SF_INSTANCE_URL: "SF_INSTANCE_URL"

capability:
  exposes:
    - type: "mcp"
      address: "localhost"
      port: 3000
      namespace: "salesforce-mcp"
      tools:
        - ref: "salesforce-domain.query-accounts"

  aggregates:
    - label: "Salesforce Domain"
      namespace: "salesforce-domain"
      functions:
        - name: "query-accounts"
          description: "Query Salesforce Account records."
          semantics:
            safe: true
          inputParameters:
            - name: "query"
              type: "string"
              description: "SOQL query string."
              required: true
          call: "salesforce.query"
          with:
            q: "query"
          outputParameters:
            - name: "records"
              type: "array"

  consumes:
    - type: "http"
      namespace: "salesforce"
      baseUri: "{{SF_INSTANCE_URL}}/services/data/v59.0"
      authentication:
        type: "oauth2-client"
        grantType: "client-credentials"
        tokenEndpoint: "{{SF_INSTANCE_URL}}/services/oauth2/token"
        clientId: "{{SF_CLIENT_ID}}"
        clientSecret: "{{SF_CLIENT_SECRET}}"
      resources:
        - name: "soql"
          path: "/query"
          operations:
            - name: "query"
              method: "GET"
              inputParameters:
                - name: "q"
                  in: "query"
                  type: "string"
              outputParameters:
                - name: "records"
                  type: "array"
                  mapping: "$.records"
```

### 8.2 Microsoft Graph — OAuth2 with Scopes

```yaml
naftiko: "1.0.0-alpha2"

info:
  label: "Microsoft Graph Users"
  description: "List Azure AD users via Microsoft Graph API."
  tags:
    - Microsoft
    - Azure
    - OAuth2

binds:
  - namespace: "azure-creds"
    keys:
      AZURE_TENANT_ID: "AZURE_TENANT_ID"
      AZURE_CLIENT_ID: "AZURE_CLIENT_ID"
      AZURE_CLIENT_SECRET: "AZURE_CLIENT_SECRET"

capability:
  exposes:
    - type: "mcp"
      address: "localhost"
      port: 3000
      namespace: "graph-mcp"
      tools:
        - ref: "graph-domain.list-users"

  aggregates:
    - label: "Graph Domain"
      namespace: "graph-domain"
      functions:
        - name: "list-users"
          description: "List Azure AD users."
          semantics:
            safe: true
            cacheable: true
          call: "ms-graph.get-users"
          outputParameters:
            - name: "users"
              type: "array"

  consumes:
    - type: "http"
      namespace: "ms-graph"
      baseUri: "https://graph.microsoft.com/v1.0"
      authentication:
        type: "oauth2-client"
        grantType: "client-credentials"
        tokenEndpoint: "https://login.microsoftonline.com/{{AZURE_TENANT_ID}}/oauth2/v2.0/token"
        clientId: "{{AZURE_CLIENT_ID}}"
        clientSecret: "{{AZURE_CLIENT_SECRET}}"
        scope: "https://graph.microsoft.com/.default"
      resources:
        - name: "users"
          path: "/users"
          operations:
            - name: "get-users"
              method: "GET"
              outputParameters:
                - name: "users"
                  type: "array"
                  mapping: "$.value"
```

### 8.3 HubSpot — OAuth2 Refresh Token

```yaml
naftiko: "1.0.0-alpha2"

info:
  label: "HubSpot CRM Integration"
  description: "Access HubSpot contacts using a pre-obtained refresh token."
  tags:
    - HubSpot
    - CRM
    - OAuth2

binds:
  - namespace: "hubspot-creds"
    keys:
      HUBSPOT_CLIENT_ID: "HUBSPOT_CLIENT_ID"
      HUBSPOT_CLIENT_SECRET: "HUBSPOT_CLIENT_SECRET"
      HUBSPOT_REFRESH_TOKEN: "HUBSPOT_REFRESH_TOKEN"

capability:
  exposes:
    - type: "mcp"
      address: "localhost"
      port: 3000
      namespace: "hubspot-mcp"
      tools:
        - ref: "hubspot-domain.list-contacts"

  aggregates:
    - label: "HubSpot Domain"
      namespace: "hubspot-domain"
      functions:
        - name: "list-contacts"
          description: "List HubSpot contacts."
          semantics:
            safe: true
          call: "hubspot.get-contacts"
          outputParameters:
            - name: "contacts"
              type: "array"

  consumes:
    - type: "http"
      namespace: "hubspot"
      baseUri: "https://api.hubapi.com/crm/v3"
      authentication:
        type: "oauth2-client"
        grantType: "refresh-token"
        tokenEndpoint: "https://api.hubapi.com/oauth/v1/token"
        clientId: "{{HUBSPOT_CLIENT_ID}}"
        clientSecret: "{{HUBSPOT_CLIENT_SECRET}}"
        refreshToken: "{{HUBSPOT_REFRESH_TOKEN}}"
      resources:
        - name: "contacts"
          path: "/objects/contacts"
          operations:
            - name: "get-contacts"
              method: "GET"
              outputParameters:
                - name: "contacts"
                  type: "array"
                  mapping: "$.results"
```

### 8.4 Enterprise API — Custom Token Endpoint

```yaml
naftiko: "1.0.0-alpha2"

info:
  label: "Enterprise Inventory API"
  description: "Access an internal inventory API that uses a custom login endpoint."
  tags:
    - Enterprise
    - Custom Auth

binds:
  - namespace: "inventory-creds"
    keys:
      API_USERNAME: "API_USERNAME"
      API_PASSWORD: "API_PASSWORD"

capability:
  exposes:
    - type: "mcp"
      address: "localhost"
      port: 3000
      namespace: "inventory-mcp"
      tools:
        - ref: "inventory-domain.list-products"

  aggregates:
    - label: "Inventory Domain"
      namespace: "inventory-domain"
      functions:
        - name: "list-products"
          description: "List products from the inventory system."
          semantics:
            safe: true
          call: "inventory.get-products"
          outputParameters:
            - name: "products"
              type: "array"

  consumes:
    # Auth adapter — no authentication required (it IS the auth endpoint)
    - type: "http"
      namespace: "inventory-auth"
      baseUri: "https://inventory.internal.example.com"
      resources:
        - name: "auth"
          path: "/api/auth"
          operations:
            - name: "login"
              method: "POST"
              body:
                type: "json"
                data:
                  username: "{{username}}"
                  password: "{{password}}"

    # Data adapter — uses custom-token auth via the auth adapter
    - type: "http"
      namespace: "inventory"
      baseUri: "https://inventory.internal.example.com"
      authentication:
        type: "custom-token"
        acquire:
          call: "inventory-auth.login"
          with:
            username: "{{API_USERNAME}}"
            password: "{{API_PASSWORD}}"
          tokenField: "$.data.sessionToken"
          expiresInField: "$.data.expiresIn"
        placement: "header"
        headerName: "X-Session-Token"
      resources:
        - name: "products"
          path: "/api/v2/products"
          operations:
            - name: "get-products"
              method: "GET"
              outputParameters:
                - name: "products"
                  type: "array"
                  mapping: "$.items"
```

### 8.5 Custom Token with Bearer Placement (Simpler Case)

```yaml
# A simpler custom-token example where the token is applied as a standard
# Bearer header (the default placement).
authentication:
  type: "custom-token"
  acquire:
    call: "auth-service.get-token"
    with:
      api-key: "{{API_KEY}}"
    tokenField: "$.access_token"
    expiresInField: "$.expires_in"
  # placement defaults to "bearer" — no extra config needed
```

---

## 9. Security Considerations

### 9.1 Credential Storage

| Secret | Storage | Exposure Risk |
|--------|---------|---------------|
| Client ID | `binds` → environment variable | Low (not a secret by itself) |
| Client Secret | `binds` → environment variable | High — must never be logged or serialized |
| Refresh Token | `binds` → environment variable | High — equivalent to long-term access |
| Access Token | In-memory only (TokenManager) | Medium — short-lived, but grants access while valid |

**Rules**:
- Access tokens MUST NOT be written to logs, even at DEBUG level
- Client secrets MUST NOT appear in error messages
- Refresh tokens, if rotated by the server, MUST be updated in memory only (not written back to environment or files)

### 9.2 Transport Security

- Token endpoint calls MUST use HTTPS. The engine rejects `http://` token endpoints at startup (schema `format: "uri"` + Spectral rule)
- The same HTTPS requirement already applies to `baseUri` by convention but is not enforced by the current schema. This proposal does not change that.

### 9.3 Token Scope Minimization

The `scope` field allows requesting only the permissions needed. Capability authors SHOULD declare the narrowest scope possible. The engine does not enforce this — it is a design guideline.

### 9.4 Refresh Token Rotation

Some providers (e.g., HubSpot, Salesforce) return a new `refresh_token` alongside the new `access_token`. The engine MUST:
1. Detect the new refresh token in the response
2. Update the in-memory `refreshToken` field
3. Use the new refresh token for subsequent refreshes

If rotation fails (old refresh token rejected, new one not received), the engine logs an error and fails the request. Recovery requires operator intervention (provide a new refresh token via `binds`).

### 9.5 Circular Authentication Prevention

If `custom-token.acquire.call` references an operation on a consumed adapter that itself uses `oauth2-client` or `custom-token`, the engine would enter an infinite loop trying to authenticate. This MUST be detected at startup:

```
Load capability
  → For each consumed adapter with custom-token auth:
    → Resolve acquire.call to target consumed adapter
    → If target.authentication.type ∈ {oauth2-client, custom-token}:
      → FAIL: "Circular authentication dependency: {source} → {target}"
```

---

## 10. Validation Rules

### 10.1 Spectral Rules

Rules for client-side token refresh use the `naftiko-oauth2-client-*` prefix to distinguish them from the existing server-side `naftiko-oauth2-*` rules (e.g. `naftiko-oauth2-https-authserver`, `naftiko-oauth2-resource-https`, `naftiko-oauth2-scopes-defined`).

| Rule ID | Severity | Description |
|---------|----------|-------------|
| `naftiko-oauth2-client-token-endpoint-https` | error | `tokenEndpoint` must start with `https://` |
| `naftiko-oauth2-client-refresh-token-required` | error | `refreshToken` required when `grantType` is `refresh-token` |
| `naftiko-oauth2-client-secret-template` | warn | `clientSecret` should use a Mustache template (not a literal value) |
| `naftiko-oauth2-client-not-on-exposes` | error | `type: "oauth2-client"` must not be used on an exposes adapter |
| `naftiko-oauth2-server-not-on-consumes` | error | `type: "oauth2"` must not be used on a consumes adapter |
| `naftiko-custom-token-no-self-ref` | error | `acquire.call` must not reference an operation on the same consumed adapter |
| `naftiko-custom-token-no-circular-auth` | error | `acquire.call` target must not itself use `oauth2-client` or `custom-token` auth |
| `naftiko-custom-token-placement-header` | error | `headerName` required when `placement` is `header` |
| `naftiko-custom-token-placement-query` | error | `paramName` required when `placement` is `query` |

### 10.2 Engine Startup Validation

Beyond Spectral (which validates YAML structure), the engine performs runtime validation at capability load:

1. **Circular auth check**: Traverse `custom-token.acquire.call` references and verify no cycles
2. **Token endpoint reachability**: Optionally (configurable) perform a lightweight check that the token endpoint responds (HEAD or OPTIONS request). Disabled by default to avoid startup delays.

---

## 11. Existing Infrastructure (Server-Side OAuth2)

The OAuth 2.1 resource server authentication — already implemented on the exposes side — establishes patterns and provides reusable infrastructure that this proposal builds on directly. No new dependencies are needed.

### 11.1 Classes Already Implemented

| Class | Package | Relevance to This Proposal |
|-------|---------|---------------------------|
| `OAuth2AuthenticationRestlet` | `io.naftiko.engine.exposes` | Establishes JWKS caching pattern, `synchronized` + `volatile` thread safety, lazy initialization (uses `java.net.http.HttpClient` for metadata — the consumes side uses Restlet `Client` instead for consistency with `HttpClientAdapter`) |
| `McpOAuth2Restlet` | `io.naftiko.engine.exposes.mcp` | MCP-specific OAuth2 extension — no direct reuse, but validates the adapter-specific override pattern |
| `OAuth2AuthenticationSpec` | `io.naftiko.spec.consumes` | Server-side spec class. The new `OAuth2ClientAuthenticationSpec` follows the same conventions (package, naming, `volatile` fields) |
| `AuthenticationSpec` | `io.naftiko.spec.consumes` | Polymorphic base with `@JsonSubTypes` — new entries for `oauth2-client` and `custom-token` are added here |
| `ServerAdapter.buildServerChain()` | `io.naftiko.engine.exposes` | Routes `type: "oauth2"` to `OAuth2AuthenticationRestlet`. The analogous routing for `type: "oauth2-client"` happens in `HttpClientAdapter.setChallengeResponse()` |

### 11.2 Dependencies Already Available

| Dependency | Version | Used By (Server Side) | Reused By (Client Side) |
|-----------|---------|----------------------|------------------------|
| `org.restlet.Client` | 2.7.0-m2 | `HttpClientAdapter` (all consumed API calls) | `TokenManager.acquireToken()` (token endpoint POST) — reuses the adapter's existing `Client` instance |
| `com.nimbusds:nimbus-jose-jwt` | 9.37.3 | JWT parsing, signature verification | Future: JWT Bearer Assertion signing (Phase 4) |
| `com.fasterxml.jackson.core:jackson-databind` | 2.20.2 | `@JsonSubTypes` deserialization | Token endpoint JSON response parsing, `@JsonSubTypes` for new spec class |
| `com.jayway.jsonpath:json-path` | 2.9.0 | Spectral rule evaluation | `custom-token` response field extraction (`tokenField`, `expiresInField`) |
| `org.restlet` | 2.7.0-m2 | Server-side HTTP framework, `ChallengeResponse` | `HttpClientAdapter`: `ChallengeResponse(ChallengeScheme.HTTP_OAUTH_BEARER)` for applying acquired tokens |

### 11.3 Patterns Established by Server-Side

| Pattern | Server-Side Implementation | Client-Side Equivalent |
|---------|---------------------------|----------------------|
| Lazy initialization | `ensureInitialized()` with `initLock` + `volatile initialized` | `getValidToken()` with `tokenLock` + `volatile accessToken` |
| Double-check locking | `synchronized (initLock) { if (initialized) return; ... }` | `synchronized (tokenLock) { if (!isExpiringSoon()) return; ... }` |
| Cache with TTL | `JWKS_CACHE_TTL_MS` (5 min), `JWKS_MIN_REFRESH_MS` (30s min between refreshes) | `expiresAt` (from `expires_in`), `safetyMargin` (60s default) |
| HTTP fetching | `fetchUrl(String url)` — `java.net.http.HttpClient`, 10s timeout, returns null on non-200 | Restlet `Client` POST with form-encoded body — same client instance as business API calls |
| Error handling | Log at WARNING, return null/false to caller | Log at WARNING, throw to prevent request with stale token |

---

## 12. Design Decisions & Rationale

### 12.1 Why Two Auth Types Instead of One?

**Decision**: Separate `oauth2-client` and `custom-token` types.

**Alternative considered**: A single `token-refresh` type that covers both OAuth2 and custom endpoints.

**Rationale**: OAuth2 has a well-defined protocol (RFC 6749) — the engine knows exactly how to construct the token request (form-encoded POST, specific parameters). Merging it with custom endpoints would either force OAuth2 users to redundantly declare things the engine already knows, or make the schema so generic that it loses the guardrails OAuth2 provides. Two types give precision where it matters and flexibility where it's needed.

### 12.2 Why `oauth2-client` Instead of `oauth2`?

**Decision**: Use `type: "oauth2-client"` for client-side token acquisition, keeping `type: "oauth2"` for the existing server-side resource server auth.

**Alternatives considered**: (a) Reuse `type: "oauth2"` with context-dependent fields. (b) Split the `Authentication` union into separate consumes/exposes unions.

**Rationale**: Option (a) fails because the `Authentication` union is a single `oneOf` — JSON Schema cannot discriminate the same `const` value to two different definitions based on context (the schema validator doesn't know whether the parent is an `exposes` or `consumes` block). It would also confuse Jackson's `@JsonSubTypes` mapping: `name = "oauth2"` already maps to `OAuth2AuthenticationSpec` (server-side). Option (b) would be a breaking schema change, splitting a shared definition into two. The `-client` suffix is the simplest path: no breaking changes, clear semantics, and consistent with the convention that auth type names describe what they *do* (a `bearer` token is *passed*, an `oauth2` server *validates*, an `oauth2-client` *acquires*).

### 12.3 Why Not Use Steps for Token Acquisition?

**Decision**: Token refresh is engine-internal, not user-visible orchestration.

**Alternative considered**: Let users write a `pre-auth` step in their orchestration chain that calls the token endpoint.

**Rationale**: Authentication is a cross-cutting concern. Every operation on the consumed adapter needs the token — not just specific orchestrated flows. If the user had to add a token step to every operation, it would violate DRY and make capabilities fragile (forget one step → runtime failure). The engine should handle it transparently, just like it handles Basic auth today.

### 12.4 Why `tokenEndpoint` Is Standalone (Not a Consumed Operation)?

**Decision**: OAuth2's `tokenEndpoint` is a raw URL, not a `call` reference.

**Alternative considered**: Require users to declare the token endpoint as a consumed resource/operation.

**Rationale**: (1) The token endpoint is not a business API — it's infrastructure. Polluting the `consumes` block with auth plumbing defeats the clarity of "consumes = the APIs I integrate with." (2) The engine knows how to call an OAuth2 token endpoint — no user configuration needed for request body, headers, or response parsing. (3) Avoiding circular auth — the consumed adapter itself needs this token, so having the token endpoint as an operation on the same adapter creates a chicken-and-egg problem.

The `custom-token` type uses `call` because the engine does NOT know how to call a proprietary auth endpoint — the user must define it. But `call` references a *different* consumed adapter, avoiding the circular dependency.

### 12.5 Why Proactive + Reactive Refresh (Not Just One)?

**Decision**: Refresh proactively (before `expires_in`) AND reactively (on `401`).

**Alternative considered**: Reactive only (simpler), or proactive only (cleaner).

**Rationale**: Proactive-only fails when the token is revoked server-side before `expires_in` (early revocation, scope changes). Reactive-only causes one failed business request per refresh cycle (the request that triggers the `401`), which may be observable to the end user. Both together give zero-downtime token management: proactive handles the normal case, reactive handles the edge cases.

### 12.6 Why No Token Persistence?

**Decision**: Tokens are cached in memory only; lost on restart.

**Alternative considered**: Persist tokens to an encrypted file or external store.

**Rationale**: OAuth2 tokens are cheap to re-acquire (a single HTTP POST). Re-acquisition on restart is simpler and more secure than managing encrypted storage, file permissions, and stale token cleanup. If a future use case requires persistence (e.g., refresh tokens that are single-use after rotation), it can be added as an opt-in `persistence` block without changing the core design.

### 12.7 Why `safetyMargin` Has a Default (60s)?

**Decision**: Default safety margin is 60 seconds.

**Alternative considered**: No default (require explicit configuration) or adaptive margin.

**Rationale**: 60 seconds is a pragmatic default — short enough to avoid wasting token validity, long enough to cover clock skew and network latency. Most token lifetimes are 30–120 minutes; 60 seconds is <3% of the shortest. Making it configurable (including `0` for reactive-only) covers edge cases without burdening the common case.

---

## 13. Implementation Roadmap

### Phase 1 — OAuth2 Client Credentials (MVP)

| Item | Scope |
|------|-------|
| Schema: `AuthOAuth2Client` definition | `naftiko-schema.json` |
| Spec class: `OAuth2ClientAuthenticationSpec` | `io.naftiko.spec.consumes` |
| `@JsonSubTypes` entry: `name = "oauth2-client"` | `AuthenticationSpec` |
| Token manager: acquisition + caching (using Restlet `Client`) | `io.naftiko.engine.consumes.http` |
| Proactive refresh (timer-based) | `TokenManager` |
| `setChallengeResponse()` extension: `case "oauth2-client"` | `HttpClientAdapter` |
| Spectral rules: `naftiko-oauth2-client-*` (HTTPS, template warnings, exposes/consumes guards) | `naftiko-rules.yml` |
| Unit tests: token acquisition, expiry, refresh | `TokenManagerTest` |
| Integration test: mock OAuth2 server | `OAuth2ClientIntegrationTest` |
| Example: Salesforce capability | `schemas/examples/` |

### Phase 2 — OAuth2 Refresh Token + Reactive 401

| Item | Scope |
|------|-------|
| `grantType: "refresh-token"` support | `TokenManager` |
| Refresh token rotation handling | `TokenManager` |
| Reactive 401 retry | `OperationStepExecutor` / `HttpClientAdapter` |
| Spectral rule: `naftiko-oauth2-client-refresh-token-required` | `naftiko-rules.yml` |
| Integration test: refresh token flow | `OAuth2ClientRefreshIntegrationTest` |
| Example: HubSpot capability | `schemas/examples/` |

### Phase 3 — Custom Token Endpoint

| Item | Scope |
|------|-------|
| Schema: `AuthCustomToken` definition | `naftiko-schema.json` |
| Spec class: `CustomTokenAuthenticationSpec` | `io.naftiko.spec.consumes` |
| `@JsonSubTypes` entry: `name = "custom-token"` | `AuthenticationSpec` |
| Token manager: custom call delegation | `TokenManager` |
| Circular auth detection at startup | `Capability` loader |
| Spectral rules: no-self-ref, no-circular, placement | `naftiko-rules.yml` |
| Unit tests: custom acquisition, circular detection | `CustomTokenAuthTest` |
| Integration test: mock custom auth endpoint | `CustomTokenIntegrationTest` |
| Example: enterprise inventory capability | `schemas/examples/` |

### Phase 4 — Future Extensions (Not in This Proposal)

| Item | Description |
|------|-------------|
| JWT Bearer Assertion | `grantType: "jwt-bearer"` — sign a JWT locally, exchange at token endpoint (Google service accounts). The `nimbus-jose-jwt` library (9.37.3) already used by `OAuth2AuthenticationRestlet` for server-side JWT validation supports JWT signing — no new dependency needed |
| Token persistence | Opt-in encrypted file or external vault for refresh tokens that survive restarts |
| Client certificate auth (mTLS) | `type: "mtls"` — mutual TLS with client certificates |
| OIDC Discovery | Auto-discover `tokenEndpoint` from `/.well-known/openid-configuration` |

---

## 14. Backward Compatibility

### Schema

- **Additive**: Two new entries in the `Authentication` union `oneOf`: `AuthOAuth2Client` and `AuthCustomToken`. Existing `basic`, `bearer`, `apikey`, `digest`, and `oauth2` (server-side) types are unchanged.
- **No breaking changes**: Existing capability YAML files validate without modification.
- **Version**: No spec version bump needed — alpha allows additive changes.

### Engine

- **`HttpClientAdapter.setChallengeResponse()`**: New `case "oauth2-client"` and `case "custom-token"` branches added to the existing `switch`. Existing branches unchanged.
- **`OperationStepExecutor`**: Reactive 401 retry wraps existing `handle()` calls — no change to the happy path.
- **`TokenManager`**: New class in `io.naftiko.engine.consumes.http`. No modifications to existing classes (except `HttpClientAdapter` gaining a field).
- **`OAuth2ClientAuthenticationSpec`**: New spec class in `io.naftiko.spec.consumes`. Does not modify the existing `OAuth2AuthenticationSpec` (server-side).
- **`AuthenticationSpec`**: Two new `@JsonSubTypes` entries added (`oauth2-client`, `custom-token`). Existing entries unchanged.

### Dependencies

- **No new dependencies**: Restlet `Client` (already used by `HttpClientAdapter` for all consumed API calls), Jackson (already used throughout), and `json-path` (already used by Spectral rules) cover all needs. `nimbus-jose-jwt` (already present) is available for the future JWT Bearer Assertion extension.

### Tests

- **All existing tests pass**: Static auth types behave exactly as before. No behavioral changes to existing code paths.
- **New tests are additive**: New test classes for token manager, OAuth2 flow, custom token flow.
