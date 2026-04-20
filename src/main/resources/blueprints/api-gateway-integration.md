# API Gateway Integration
## Gateway-Ready Capabilities for AI Agents

**Status**: Proposed

**Version**: 1.0.0-alpha3

**Date**: April 20, 2026

**Related blueprints**:
- [Control Port](control-port.md) — health probes, Prometheus metrics, and trace inspection on a dedicated management port
- [Token Refresh Authentication](token-refresh-authentication.md) — OAuth2 `client_credentials` for consumed APIs behind gateways
- [MCP Server Authentication](mcp-server-authentication.md) — OAuth 2.1 resource server for exposed MCP/REST adapters
- [HTTP Cache Control](http-cache-control.md) — declarative cache semantics for REST responses
- [OpenTelemetry Observability](opentelemetry-observability.md) — distributed tracing across gateway → Naftiko → consumed API
- [OpenAPI Interoperability](openapi-interoperability.md) — bidirectional OAS import/export for gateway catalog integration
- [Agent Skills Support](agent-skills-support.md) — skill adapter for AI agent discovery

**Context**: [You Have Apigee. Now Your AI Agents Need to Use It.](https://naftiko.io/blog/you-have-apigee-now-your-ai-agents-need-to-use-it/) — the same integration pattern applies to AWS API Gateway, Azure APIM, Kong, Tyk, and Axway; vendor-specific blog posts will be linked here as they are published.

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [The Gateway-Capability-Agent Architecture](#2-the-gateway-capability-agent-architecture)
3. [Consumes: Calling APIs Behind a Gateway](#3-consumes-calling-apis-behind-a-gateway)
4. [Exposes: Sitting Behind a Gateway](#4-exposes-sitting-behind-a-gateway)
5. [Framework Features — What Exists Today](#5-framework-features--what-exists-today)
6. [Framework Gaps — What Needs to Be Built](#6-framework-gaps--what-needs-to-be-built)
7. [Agent Skill: `api-gateway-integration`](#7-agent-skill-api-gateway-integration)
8. [Use Cases: Wrapping Gateway Management APIs as AI Tools](#8-use-cases-wrapping-gateway-management-apis-as-ai-tools)
9. [Gateway-Specific Patterns](#9-gateway-specific-patterns)
10. [Implementation Roadmap](#10-implementation-roadmap)
11. [Acceptance Criteria](#11-acceptance-criteria)

---

## 1. Executive Summary

### The Problem

Enterprises that invested in API gateways have governed API infrastructure. But the primary consumer of those APIs is shifting from developers writing mobile apps to AI agents that need to reason about what APIs can do, call them deterministically, and return results that map to business outcomes. Meanwhile, a new category of AI-native gateways is emerging specifically for agent-to-tool and agent-to-LLM traffic — complementing rather than replacing traditional API gateways.

Gateways manage proxies, enforce auth, rate-limit traffic, and package APIs as products. They do not produce the structured, machine-readable capability definitions that AI agents require. The result: teams build bespoke integration layers between AI tools and gateway-managed APIs — fragmented, ungoverned, and invisible to the platform engineering team.

### What This Proposes

A unified integration pattern where Naftiko capabilities sit between API gateways and AI agents:

1. **Consumes** — Naftiko calls gateway-managed APIs using native gateway auth patterns (API keys, subscription keys, OAuth2 client credentials) with secrets injected via `binds`
2. **Exposes** — Naftiko sits behind the same gateway (or alongside it) as a REST API product and/or MCP server, with health checks, CORS, and cache headers that gateways expect
3. **Dual exposure via aggregates** — Define domain logic once, expose as both REST (for gateway/portal import) and MCP (for AI agents) from the same YAML
4. **OpenAPI round-trip** — Import gateway API specs to scaffold `consumes`, export REST `exposes` back to the gateway portal for auto-registration
5. **Agent skill** — A dedicated `api-gateway-integration` skill that guides agents through gateway integration patterns

### What This Does NOT Do

- **No gateway replacement** — Naftiko does not replace Apigee, AWS API Gateway, Azure APIM, Kong, Tyk, or similar. It extends their value into the AI consumption surface.
- **No gateway-specific runtime plugins** — Naftiko does not produce Apigee proxy bundles, AWS Lambda authorizers, Azure APIM policies, or Kong/Tyk plugin configurations. It produces standard OpenAPI specs and HTTP endpoints that gateways consume natively.
- **No built-in rate limiting** — Rate limiting stays in the gateway. Naftiko adds client-side resilience (retry, backoff) to handle gateway-enforced limits gracefully.
- **No changes to CI/CD workflows** or branch protection rules.

### Business Value

| Benefit | Impact | Users |
|---------|--------|-------|
| **Gateway ROI extension** | Existing API gateway investments become AI-ready without changing what the gateway does | Platform Engineering |
| **Governed AI integration** | Capabilities replace bespoke integration layers with auditable, spec-driven definitions | Architecture, InfoSec |
| **Zero-code bridge** | Declarative YAML — no custom middleware between gateway and AI agents | Developers |
| **Dual-surface exposure** | Same domain logic serves portal consumers (REST) and AI agents (MCP) | Product Teams |
| **Environment portability** | Same capability YAML across dev/staging/prod — only `binds` change | DevOps, SRE |

---

## 2. The Gateway-Capability-Agent Architecture

### Where to start

Use this table to jump to the section that matches your starting point:

| Your starting point | Jump to |
|---|---|
| "I have APIs behind a gateway that AI agents need to use" | [§3 Consumes](#3-consumes-calling-apis-behind-a-gateway) + [§9.1 OpenAPI round-trip](#91-openapi-round-trip-workflow) |
| "I want to put my Naftiko capability behind my company's gateway" | [§4 Exposes](#4-exposes-sitting-behind-a-gateway) + registration table |
| "I want the same API for portal consumers and AI agents" | [§4 Dual exposure via aggregates](#dual-exposure-via-aggregates) |
| "I'm already running an AI-native gateway (MCP proxy)" | [§9.4 AI-native gateways](#94-ai-native-gateways--agentgateway-kong-ai-gateway-axway-ai-gateway) |
| "I need to wrap a gateway's management API as AI tools" | [§8 Use Cases](#8-use-cases-wrapping-gateway-management-apis-as-ai-tools) |
| "I'm migrating from a hand-written integration layer" | [§9.5 Migration paths](#95-migration-paths) |

### Deployment topologies

Where Naftiko runs relative to the gateway affects auth, networking, and probe configuration:

| Topology | Trust boundary | Auth between gateway and Naftiko | Health probe access |
|---|---|---|---|
| **Sidecar** (same pod/host) | Trusted localhost | None required | `localhost:9090/health/ready` |
| **Same cluster / VPC** | Private network | Optional mTLS; API key acceptable | Private DNS / service mesh |
| **Different network** (public internet) | Untrusted | mTLS or gateway-to-Naftiko token required | Probe over TLS; keep control port internal via separate listener |
| **Serverless / Cloud Run** | Platform-managed | Platform IAM (e.g., Cloud Run invoker) | Cold starts may delay `/health/ready` — tune probe `initialDelaySeconds` |

For any topology that crosses a network boundary, keep the control port (9090) on an internal address and route only REST/MCP business ports through the gateway.

### Positioning

```
┌────────────────────────────────────────────────────────────────┐
│                        API Gateway                             │
│    (Apigee / AWS API GW / Azure APIM / Kong / Tyk / etc.)      │
│                                                                │
│   ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────────────┐   │
│   │ Proxy A │  │ Proxy B │  │ Proxy C │  │ Naftiko (REST)  │   │
│   └────┬────┘  └────┬────┘  └────┬────┘  └────────┬────────┘   │
│        │            │            │                  │          │
│   Rate limiting, Auth, Analytics, Developer Portal             │
└────────┼────────────┼────────────┼──────────────────┼──────────┘
         │            │            │                  │
         ▼            ▼            ▼                  ▼
    ┌─────────┐  ┌─────────┐  ┌─────────┐   ┌──────────────────┐
    │ Backend │  │ Backend │  │ Backend │   │ Naftiko Engine   │
    │  API A  │  │  API B  │  │  API C  │   │                  │
    └─────────┘  └─────────┘  └─────────┘   │  ┌────────────┐  │
                                            │  │ Aggregates │  │
         ┌──────────────────────────────────│  └─────┬──────┘  │
         │          consumes                │        │         │
         ▼                                  │   ┌────┴─────┐   │
    ┌─────────┐                             │   │ REST     │   │
    │ Gateway │◄─── API key / OAuth2 ───────│   │ exposes  │   │
    │ (inbound│                             │   └──────────┘   │
    │  to     │                             │   ┌──────────┐   │
    │  backend│                             │   │ MCP      │──────► AI Agents
    │  APIs)  │                             │   │ exposes  │   │
    └─────────┘                             │   └──────────┘   │
                                            │   ┌──────────┐   │
                              Health probe ─│──►│ Control  │   │
                              /metrics ─────│──►│ port     │   │
                                            │   └──────────┘   │
                                            └──────────────────┘
```

### Four Integration Surfaces

| Surface | Direction | Gateway role | Naftiko role |
|---------|-----------|-------------|--------------|
| **Consumes** | Naftiko → Gateway → Backend | Auth enforcement, rate limiting, routing | HTTP client with gateway-native auth (apikey, bearer, OAuth2) |
| **REST exposes** | Gateway → Naftiko | Reverse proxy, developer portal, analytics | REST server registered as a gateway API product |
| **MCP exposes** | AI Agent → Naftiko (direct or via gateway) | Optional pass-through proxy | MCP server with structured tool definitions |
| **Control port** | Gateway → Naftiko (management plane) | Health probe target, metrics scrape | Dedicated port for `/health/live`, `/health/ready`, `/metrics` |

---

## 3. Consumes: Calling APIs Behind a Gateway

### Quick start: the smallest gateway-ready capability

The minimal shape for calling a gateway-managed API and exposing it to agents:

```yaml
naftiko: "1.0.0-alpha3"

binds:
  - namespace: "gateway"
    location: "file://secrets/gateway.env"
    keys: { API_KEY: "gateway-api-key" }

capability:
  consumes:
    - type: "http"
      namespace: "backend"
      baseUri: "https://gateway.example.com/api/v1"
      authentication:
        type: "apikey"
        placement: "header"
        key: "x-api-key"
        value: "{{API_KEY}}"
      resources:
        - path: "items"
          name: "items"
          operations:
            - method: "GET"
              name: "list-items"

  exposes:
    - type: "mcp"
      port: 3000
      namespace: "backend"
      tools:
        - ref: "backend.items.list-items"
```

From here, add `aggregates` (for dual REST+MCP exposure), `semantics` (for MCP hints and caching), a `control` adapter (for health probes), and resilience/CORS as needs grow. The rest of this blueprint covers each of those additions.

### Authentication patterns by gateway

| Gateway | Common auth pattern | Naftiko auth type | Header/placement |
|---------|-------------------|------------------|-----------------|
| **Apigee** | Verify API Key policy | `apikey` | `header` / `x-api-key` or custom |
| **Apigee** | OAuth2 (client credentials) | `oauth2` (see [Token Refresh blueprint](token-refresh-authentication.md)) | `header` / `Authorization: Bearer` |
| **AWS API Gateway** | API key (usage plans) | `apikey` | `header` / `x-api-key` |
| **AWS API Gateway** | IAM / Cognito JWT | `bearer` (pre-fetched) or `oauth2` | `header` / `Authorization: Bearer` |
| **Azure APIM** | Subscription key | `apikey` | `header` / `Ocp-Apim-Subscription-Key` |
| **Azure APIM** | OAuth2 (Entra ID) | `oauth2` | `header` / `Authorization: Bearer` |
| **Kong** | Key Authentication plugin | `apikey` | `header` / `apikey` (configurable via `key_names`) |
| **Kong** | OAuth2 / JWT plugin | `oauth2` or `bearer` | `header` / `Authorization: Bearer` |
| **Tyk** | Auth Token (API key) | `apikey` | `header` / `Authorization` |
| **Tyk** | JWT / OAuth2 | `oauth2` or `bearer` | `header` / `Authorization: Bearer` |
| **Axway Amplify** | API key / Pass-through | `apikey` | `header` / `KeyId` or custom |
| **Axway Amplify** | OAuth2 (external IdP) | `oauth2` | `header` / `Authorization: Bearer` |
| **Any (enterprise)** | mTLS client certificates | `mtls` *(see [§6.10](#610-mtls-client-certificates-on-consumes-priority-medium))* | TLS layer — no header |
| **AWS API Gateway** | IAM / SigV4 signing | `sigv4` *(see [§6.11](#611-aws-sigv4-signing-on-consumes-priority-low))* | `header` / `Authorization` + `X-Amz-*` |

### Example: Consuming an Apigee-managed API

```yaml
naftiko: "1.0.0-alpha2"

binds:
  - namespace: "apigee-credentials"
    description: "Apigee API key for the CRM proxy"
    location: "file://secrets/apigee.env"      # dev — omit for prod
    keys:
      APIGEE_API_KEY: "crm-api-key"

capability:
  consumes:
    - type: "http"
      namespace: "crm-api"
      description: "CRM API managed by Apigee"
      baseUri: "https://api.example.com/crm/v2"
      authentication:
        type: "apikey"
        placement: "header"
        key: "x-api-key"
        value: "{{APIGEE_API_KEY}}"
      resources:
        - path: "contacts"
          name: "contacts"
          operations:
            - method: "GET"
              name: "list-contacts"
              inputParameters:
                - name: "status"
                  in: "query"
                  type: "string"
              outputParameters:
                - name: "contacts"
                  type: "array"
                  mapping: "$.contacts"
                  items:
                    type: "object"
                    properties:
                      id:
                        type: "string"
                        mapping: "$.id"
                      name:
                        type: "string"
                        mapping: "$.fullName"
```

### Secret injection via `binds`

The same capability YAML works across environments by changing only the binding provider:

| Environment | `location` value | Secret source |
|-------------|-----------------|---------------|
| Local dev | `file://secrets/apigee.env` | `.env` file (gitignored) |
| CI/CD | `github-secrets://` | GitHub Actions secrets |
| Staging | `vault://apigee/staging` | HashiCorp Vault |
| Production | *(omitted)* | Runtime injection (K8s env, Vault agent) |

### Dependency: Token Refresh blueprint

For OAuth2 `client_credentials` — the most common machine-to-machine gateway auth pattern — the [Token Refresh Authentication](token-refresh-authentication.md) blueprint adds automatic token acquisition, caching, and refresh to the `consumes` authentication union. This eliminates the need for external token management scripts when consuming OAuth2-protected gateway endpoints.

---

## 4. Exposes: Sitting Behind a Gateway

### Control port for gateway health probes and metrics

Every production gateway deployment requires health check endpoints. Naftiko provides these through a dedicated **Control Port** adapter (`type: "control"`) that runs on a separate port from business traffic — isolated, unauthenticated by default, and invisible to API consumers.

```yaml
capability:
  exposes:
    # Control port — gateway health probes and metrics scraping
    - type: "control"
      port: 9090
      management:
        health: true       # /health/live + /health/ready (default: true)
        info: false         # /status + /config (default: false)
      observability:
        metrics:
          local:
            enabled: true   # /metrics — Prometheus scrape endpoint
```

**Health endpoints:**

| Endpoint | Behavior | Use case |
|----------|----------|----------|
| `GET /health/live` | Returns `200 {"status":"UP"}` if the process is running | Kubernetes liveness probe, gateway liveness check |
| `GET /health/ready` | Returns `200 {"status":"UP"}` if all business adapters (REST, MCP, Skill) are started; `503 {"status":"DEGRADED"}` otherwise | Kubernetes readiness probe, gateway health monitoring |

**Gateway probe configuration:**

| Gateway | Health check configuration | Target |
|---------|--------------------------|--------|
| **Apigee** | Target server health monitor → `http://naftiko-host:9090/health/ready` | Readiness probe |
| **AWS API Gateway** | Target group health check → `http://naftiko-host:9090/health/ready` | ALB/NLB health check |
| **Azure APIM** | Backend probe → `http://naftiko-host:9090/health/ready` | Backend health monitoring |
| **Kubernetes** | `livenessProbe` → `:9090/health/live`, `readinessProbe` → `:9090/health/ready` | Pod lifecycle |
| **Kong** | Upstream active health check → `http://naftiko-host:9090/health/ready` | Target health monitoring |
| **Tyk** | Upstream health monitoring → `http://naftiko-host:9090/health/ready` | Service liveness |
| **Axway Amplify** | Backend health probe → `http://naftiko-host:9090/health/ready` | Backend availability |

The control port address defaults to `localhost` for security. In containerized deployments, set `address: "0.0.0.0"` to allow external probe access — but keep the control port on an internal network, not exposed through the gateway's public endpoint.

### REST adapter as a gateway backend

When the Naftiko REST adapter sits behind a gateway, it behaves like any HTTP backend:

```yaml
capability:
  exposes:
    # Control port — health probes for the gateway
    - type: "control"
      port: 9090

    # REST — business traffic routed through the gateway
    - type: "rest"
      address: "0.0.0.0"
      port: 8080
      namespace: "crm-tools"
      resources:
        - path: "/contacts"
          name: "contacts"
          operations:
            - method: "GET"
              name: "list-contacts"
              ref: "crm.list-contacts"
              inputParameters:
                - name: "status"
                  in: "query"
                  type: "string"
                  description: "Filter by contact status."
```

Gateway registration:

| Gateway | Registration method | Naftiko output |
|---------|-------------------|---------------|
| **Apigee** | Import OpenAPI spec as API proxy | `naftiko export-openapi` → import into Apigee |
| **AWS API Gateway** | Import OpenAPI with extensions | `naftiko export-openapi` → import via console/CLI |
| **Azure APIM** | Import OpenAPI spec as API | `naftiko export-openapi` → import into APIM |
| **Kong** | decK declarative config or Admin API (OAS import secondary) | `naftiko export-openapi` → convert to decK config or use Kong OAS import |
| **Tyk** | Import OpenAPI via Tyk Dashboard or Gateway API | `naftiko export-openapi` → import into Tyk Dashboard |
| **Axway Amplify** | Import OpenAPI via Amplify API Management | `naftiko export-openapi` → import into Amplify Engage catalog |

### MCP adapter — direct or behind gateway

MCP uses Streamable HTTP (single endpoint, JSON-RPC payloads). Gateway configuration considerations:

| Concern | Recommendation |
|---------|---------------|
| **Routing** | Route POST to the MCP port — MCP uses a single HTTP endpoint |
| **Content-Type** | Pass `application/json` through without transformation |
| **Response buffering** | Disable response buffering (important for SSE streaming) |
| **Schema validation** | Disable request/response schema validation (JSON-RPC payloads don't match REST patterns) |
| **Timeout** | Set generous timeouts — tool execution may involve multi-step orchestration |

### Dual exposure via aggregates

The most powerful pattern: define domain logic once, expose via both REST (for gateway/portal) and MCP (for AI agents):

```yaml
capability:
  aggregates:
    - namespace: "crm"
      label: "CRM Operations"
      functions:
        - name: "list-contacts"
          description: "List CRM contacts with optional status filter."
          semantics:
            safe: true
            idempotent: true
            cacheable: true
          call: "crm-api.list-contacts"
          with:
            status: "status"
          inputParameters:
            - name: "status"
              type: "string"
              description: "Filter by contact status (active, inactive, all)."
          outputParameters:
            - type: "array"
              mapping: "$.contacts"
              items:
                type: "object"
                properties:
                  id:
                    type: "string"
                    mapping: "$.id"
                  name:
                    type: "string"
                    mapping: "$.fullName"

  exposes:
    # Control port — health probes and Prometheus metrics
    - type: "control"
      port: 9090
      management:
        health: true
      observability:
        metrics:
          local:
            enabled: true

    # REST — register in gateway as API product
    - type: "rest"
      address: "0.0.0.0"
      port: 8080
      namespace: "crm-rest"
      resources:
        - path: "/contacts"
          name: "contacts"
          operations:
            - ref: "crm.list-contacts"
              method: "GET"
              inputParameters:
                - name: "status"
                  in: "query"
                  type: "string"
                  description: "Filter by contact status."

    # MCP — direct access for AI agents
    - type: "mcp"
      address: "0.0.0.0"
      port: 3000
      namespace: "crm-mcp"
      description: "CRM tools for AI agents."
      tools:
        - ref: "crm.list-contacts"
```

The REST adapter becomes a gateway-managed API product (rate limiting, analytics, developer portal). The MCP adapter serves AI agents with structured tool definitions. Both share the same domain function, contract, and orchestration.

---

## 5. Framework Features — What Exists Today

| Feature | Status | Details |
|---------|--------|---------|
| **API key auth on consumes** | ✅ Implemented | `type: apikey`, placement: header/query |
| **Bearer auth on consumes** | ✅ Implemented | `type: bearer` with static token |
| **Basic/digest auth on consumes** | ✅ Implemented | `type: basic`, `type: digest` |
| **Auth on REST/MCP exposes** | ✅ Implemented | Same `Authentication` union on exposed adapters |
| **`binds` for secret injection** | ✅ Implemented | `file://` for dev, runtime for prod |
| **Forward proxy** | ✅ Implemented | `forward.targetNamespace` + `trustedHeaders` |
| **OpenAPI export** | ✅ Implemented | `naftiko export-openapi` (OAS 3.0/3.1, YAML/JSON) |
| **OpenAPI export security schemes** | ✅ Implemented | Bearer, basic, digest, API key, and OAuth2 → `components/securitySchemes` |
| **OpenAPI import** | ✅ Implemented | `naftiko import-openapi` (OAS 3.0/3.1 + Swagger 2.0) |
| **Aggregates + ref** | ✅ Implemented | Single definition, dual REST+MCP exposure |
| **Semantics → MCP hints** | ✅ Implemented | `safe`/`idempotent` auto-derive `readOnly`/`idempotent` hints |
| **Skill adapter** | ✅ Implemented | Agent Skills Spec metadata, derived tools from sibling adapters |
| **Control port** | ✅ Implemented | `type: "control"` adapter with `/health/live`, `/health/ready`, `/metrics`, `/traces` |
| **OpenTelemetry metrics** | ✅ Implemented | RED metrics (Rate, Errors, Duration) exposed via Prometheus on the control port |

---

## 6. Framework Gaps — What Needs to Be Built

### ~~6.1 Health Check Endpoints~~ → ✅ Resolved by Control Port

Health probes are now provided by the **Control Port** adapter (`type: "control"`), implemented on the current branch. See §4 for the full design.

The control port approach is superior to the originally proposed per-adapter health endpoint:

| Aspect | Original proposal (per-adapter) | Control port (implemented) |
|--------|-------------------------------|---------------------------|
| **Port isolation** | Health shared business port | Dedicated management port — gateway probes never hit business traffic |
| **Scope** | Per-adapter health only | Aggregated readiness across all business adapters |
| **Liveness vs readiness** | Single endpoint | Separate `/health/live` (process alive) and `/health/ready` (all adapters started) |
| **Metrics** | Not included | `/metrics` Prometheus endpoint on same port |
| **Trace inspection** | Not included | `/traces` local span buffer for debugging |
| **Auth** | Excluded from business auth | Separate port — no auth concern mixing |

### 6.2 CORS Configuration (Priority: High)

**Why**: Developer portals (Apigee, Azure APIM dev portal) test APIs directly from the browser. Without CORS headers, browser-based API explorers fail. Also needed when Naftiko REST is exposed directly (without a gateway handling CORS).

**Proposal**: Optional `cors` block on `ExposesRest`.

Schema addition:

```yaml
cors:
  allowedOrigins:
    - "https://portal.example.com"
    - "https://apigee-portal.example.com"
  allowedMethods:
    - "GET"
    - "POST"
  allowedHeaders:
    - "Authorization"
    - "Content-Type"
  exposedHeaders:
    - "X-Rate-Limit-Remaining"
    - "X-Request-Id"
  allowCredentials: false   # set true only for authenticated portals with session cookies
  maxAge: 3600           # preflight cache in seconds
```

Engine behavior:
- Adds `Access-Control-Allow-Origin`, `Access-Control-Allow-Methods`, `Access-Control-Allow-Headers` response headers
- Handles `OPTIONS` preflight requests automatically
- `allowedOrigins: ["*"]` permitted but triggers a Spectral warning (overly permissive)

**Dependency**: None — can be implemented independently.

### 6.3 Client-Side Resilience on Consumes (Priority: High)

**Why**: Gateways enforce rate limits (429), and backends behind gateways return transient errors (502, 503). Without retry logic, capabilities fail on the first transient error.

**Proposal**: Optional `resilience` block on `HttpClientAdapter` (consumes level) or per-operation override.

Schema addition on `ConsumesHttp`:

```yaml
consumes:
  - type: "http"
    namespace: "rate-limited-api"
    resilience:
      timeout: 5000              # request timeout in ms
      retries: 3                 # max retry attempts
      retryOn:
        - 429
        - 502
        - 503
      retryOnNetworkErrors: true # retry on connect-refused, DNS failure, socket timeout
      backoff: "exponential"     # none | fixed | exponential
      backoffBase: 1000          # base delay in ms (default: 1000)
      jitterFactor: 0.3          # 0.0-1.0 — randomization to avoid thundering herd
```

Engine behavior:
- Retry on specified HTTP status codes with configurable backoff
- Retry on network-level errors (connect refused, DNS failure, read timeout) when `retryOnNetworkErrors: true`
- Apply jitter (randomized delay ± `jitterFactor × backoff`) to avoid thundering-herd retries across replicas
- Respect `Retry-After` header from gateway (overrides backoff calculation)
- Timeout applies per individual request attempt
- Total timeout = `timeout × (retries + 1)` as upper bound
- Circuit breaker and cross-request retry budgets are deferred to a future iteration (require state tracking across requests)

**Dependency**: None — can be implemented independently.

### ~~6.4 OpenAPI Export: Security Schemes~~ → ✅ Implemented

`OasExportBuilder.buildSecurity()` now maps all supported authentication types to OpenAPI `securitySchemes`:

| Naftiko auth type | OpenAPI security scheme |
|-------------------|----------------------|
| `apikey` | `type: apiKey`, `in: header/query`, `name: <key>` |
| `bearer` | `type: http`, `scheme: bearer` |
| `basic` | `type: http`, `scheme: basic` |
| `digest` | `type: http`, `scheme: digest` |
| `oauth2` | `type: oauth2`, `flows.clientCredentials` with `tokenUrl` and `scopes` |

The exported spec includes `security` at the document level and `components/securitySchemes` with the mapped scheme. OAuth2 uses the `clientCredentials` flow with `authorizationServerUri` as `tokenUrl`.

### 6.5 OAuth2 Client Credentials on Consumes (Priority: High)

**Covered by**: [Token Refresh Authentication](token-refresh-authentication.md) blueprint.

This is the most common machine-to-machine auth pattern across all major gateways (Apigee, AWS Cognito, Azure Entra ID, Kong OAuth2 plugin, Tyk OAuth2 middleware, Axway external IdP). The token-refresh blueprint adds `type: "oauth2"` to the consumes authentication union with automatic token acquisition, caching, and refresh. Implementing that blueprint directly unblocks the gateway consumes story.

### 6.6 OpenTelemetry / Trace Propagation (Priority: Medium)

**Partially covered by**: [OpenTelemetry Observability](opentelemetry-observability.md) blueprint and the current branch.

The control port already exposes RED metrics via `/metrics` (Prometheus scrape) and local trace inspection via `/traces`. What remains is **context propagation**: gateways inject trace context headers (`traceparent`, `X-Cloud-Trace-Context`, Kong's `X-Kong-Request-Id`) and without extracting and propagating these, Naftiko capabilities appear as black boxes in gateway analytics dashboards (Apigee Analytics, AWS X-Ray, Azure Monitor, Kong Vitals, Tyk Analytics).

### 6.7 Cache-Control Headers (Priority: Medium)

**Covered by**: [HTTP Cache Control](http-cache-control.md) blueprint.

Gateways and CDNs placed in front of Naftiko can cache responses automatically when proper `Cache-Control` headers are present. The cache-control blueprint adds declarative cache semantics to REST operations, with `semantics.cacheable` as the aggregate-level trigger.

### 6.8 Gateway correlation labels on metrics/traces (Priority: Medium)

**Why**: Gateways tag requests with consumer/product/route identifiers (Apigee `developer_id` + `api_product`, AWS `api_id` + `stage`, Kong `consumer_id` + `route_id`, Azure APIM `subscription_id`, Tyk `org_id` + `api_id`). Without propagating these as OpenTelemetry resource attributes or metric labels, Naftiko's RED metrics cannot be correlated with gateway-side analytics dashboards.

**Proposal**: Extract configured gateway correlation headers on the REST `exposes` adapter and attach them as span attributes and metric labels. Configuration lives on the `exposes.rest` adapter:

```yaml
exposes:
  - type: "rest"
    port: 8080
    correlation:
      headers:
        - header: "X-Apigee-Developer-Id"
          label: "apigee.developer_id"
        - header: "X-Consumer-ID"
          label: "kong.consumer_id"
        - header: "X-Subscription-Id"
          label: "apim.subscription_id"
```

High-cardinality labels (e.g., per-user IDs) should only be attached to spans, not to metrics, to avoid Prometheus cardinality explosion. The engine should enforce a configurable allowlist or warn via Spectral when a correlation label appears high-cardinality.

**Dependency**: [OpenTelemetry Observability](opentelemetry-observability.md).

### 6.9 MCP trust propagation from fronting gateway (Priority: Medium)

**Why**: When an AI-native gateway (AgentGateway, Kong AI Gateway) fronts Naftiko's MCP port, the gateway typically terminates agent auth (JWT, API key) and forwards the request. Naftiko must either (a) trust the forwarded identity for audit logging, or (b) re-verify — today there is no explicit design for either path on the MCP adapter.

**Proposal**: Add a `trust` block to `ExposesMcp`:

```yaml
exposes:
  - type: "mcp"
    port: 3000
    trust:
      mode: "forwarded"            # forwarded | verify | none
      agentIdentityHeader: "X-Agent-Id"
      trustedNetworks:
        - "10.0.0.0/8"              # accept forwarded identity only from these CIDRs
      claimsHeader: "X-Forwarded-Claims"   # base64-encoded JWT claims from gateway
```

Engine behavior:
- `mode: forwarded` — extract agent identity from trusted headers when source IP is in `trustedNetworks`; attach to spans and audit logs
- `mode: verify` — re-validate the forwarded JWT against the configured issuer (falls back to [MCP Server Authentication](mcp-server-authentication.md))
- `mode: none` — ignore forwarded identity (stateless MCP, no audit trail)

**Dependency**: [MCP Server Authentication](mcp-server-authentication.md).

### 6.10 mTLS client certificates on consumes (Priority: Medium)

**Why**: Many enterprise gateways (Apigee Edge Microgateway, Axway API Gateway, Kong Enterprise) enforce mTLS for backend and B2B integrations. Naftiko must be able to present a client certificate when calling the gateway.

**Proposal**: Add `type: "mtls"` to the consumes authentication union (or `tls:` block alongside `authentication:`):

```yaml
consumes:
  - type: "http"
    namespace: "b2b-partner"
    authentication:
      type: "mtls"
      clientCertificate: "{{PARTNER_CLIENT_CERT}}"   # PEM
      clientKey: "{{PARTNER_CLIENT_KEY}}"            # PEM
      caBundle: "{{PARTNER_CA_BUNDLE}}"              # optional, for self-signed server certs
```

Secrets are injected via `binds` from files or secret stores — never committed inline.

**Dependency**: None — can be implemented independently.

### 6.11 AWS SigV4 signing on consumes (Priority: Low)

**Why**: AWS API Gateway with `AWS_IAM` authorization requires requests to be signed with [Signature Version 4](https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html). This also applies when consuming native AWS services (S3, DynamoDB, Lambda) directly through Naftiko.

**Proposal**: Add `type: "sigv4"` to the consumes authentication union:

```yaml
consumes:
  - type: "http"
    namespace: "aws-api"
    authentication:
      type: "sigv4"
      region: "us-east-1"
      service: "execute-api"
      accessKeyId: "{{AWS_ACCESS_KEY_ID}}"
      secretAccessKey: "{{AWS_SECRET_ACCESS_KEY}}"
      sessionToken: "{{AWS_SESSION_TOKEN}}"   # optional, for STS credentials
```

**Dependency**: None. Lower priority than OAuth2 since most AWS API Gateway deployments use API keys or Cognito JWT (already supported).

---

## 7. Agent Skill: `api-gateway-integration`

### Purpose

A dedicated Naftiko agent skill that guides AI coding agents through gateway integration patterns. Today the `naftiko-capability` skill covers general capability authoring but has no gateway-specific decision paths. An agent asked to "put this behind Apigee" or "consume this AWS API Gateway endpoint" has no reference to follow.

### Skill structure

```
.agents/skills/api-gateway-integration/
├── SKILL.md                          # Skill definition, decision framework, workflows
└── references/
    ├── consume-gateway-api.md        # Pattern: call APIs behind a gateway
    ├── expose-behind-gateway.md      # Pattern: register Naftiko as gateway backend
    ├── dual-exposure.md              # Pattern: REST for gateway + MCP for agents
    ├── gateway-auth-patterns.md      # Auth mapping table per gateway vendor
    ├── openapi-round-trip.md         # Import gateway spec → export back to gateway
    ├── dev-to-prod-secrets.md        # Binds patterns for gateway credentials
    └── ai-native-gateways.md         # Pattern: AgentGateway, Kong AI Gateway, Axway AI Gateway
```

### Decision framework in SKILL.md

| User situation | Reference |
|---------------|-----------|
| "I want to call an API managed by Apigee/AWS/Azure" | `consume-gateway-api.md` |
| "I want to call an API managed by Kong/Tyk/Axway" | `consume-gateway-api.md` |
| "I want to deploy Naftiko behind a gateway" | `expose-behind-gateway.md` |
| "I want the same API for portal users and AI agents" | `dual-exposure.md` |
| "How do I authenticate with my gateway?" | `gateway-auth-patterns.md` |
| "I have an OpenAPI spec from my gateway — build a capability" | `openapi-round-trip.md` |
| "Move from local dev keys to production secrets" | `dev-to-prod-secrets.md` |
| "I want to federate Naftiko MCP servers behind an AI gateway" | `ai-native-gateways.md` |

### Gateway-specific guidance per reference

Each reference includes vendor-specific callouts:

**Apigee**:
- API key via Verify API Key policy → `apikey` auth on consumes
- OAuth2 via Apigee token endpoint → `oauth2` auth on consumes (token-refresh blueprint)
- Apigee target server registration → control port `/health/ready`
- Apigee developer portal → OpenAPI export with security schemes

**AWS API Gateway**:
- Usage plan API key → `apikey` auth with `x-api-key` header
- Cognito JWT → `bearer` or `oauth2` auth on consumes
- Target group health check → control port `/health/ready`
- API Gateway import → OpenAPI export (supports `x-amazon-apigateway-*` extensions as manual additions)

**Azure APIM**:
- Subscription key → `apikey` auth with `Ocp-Apim-Subscription-Key` header
- Entra ID (Azure AD) OAuth2 → `oauth2` auth on consumes
- Backend probe → control port `/health/ready`
- APIM API import → OpenAPI export with security schemes

**Kong**:
- Key Authentication plugin → `apikey` auth with `apikey` header (configurable via `key_names`)
- JWT / OAuth2 plugin → `bearer` or `oauth2` auth on consumes
- Upstream active health check → control port `/health/ready`
- Primary registration via decK declarative YAML or Admin API; secondary via OpenAPI import
- Kong Konnect developer portal → OpenAPI export

**Tyk**:
- Auth Token → `apikey` auth with `Authorization` header (Tyk's default key header)
- JWT / OAuth2 middleware → `bearer` or `oauth2` auth on consumes
- Upstream health monitoring → control port `/health/ready`
- Tyk Dashboard → OpenAPI import/export (OAS 3.x native support)
- Built-in developer portal

**Axway Amplify**:
- API key / Pass-through → `apikey` auth on consumes
- OAuth2 (external IdP) → `oauth2` auth on consumes
- Backend health probe → control port `/health/ready`
- Amplify Engage catalog → OpenAPI import for API discovery and governance
- Amplify AI Gateway → complementary AI/LLM access governance

---

## 8. Use Cases: Wrapping Gateway Management APIs as AI Tools

Inspired by the [Apigee blog post](https://naftiko.io/blog/you-have-apigee-now-your-ai-agents-need-to-use-it/), these five categories represent the most common patterns for exposing a gateway's own management/admin APIs to AI agents. They apply to any gateway, not just Apigee. Each suggests a reference capability filename (e.g. `apigee-admin.yaml`, `kong-admin.yaml`) that can be scaffolded via `naftiko import-openapi` from the gateway's admin OAS.

### 8.1 API Lifecycle Management

**What it wraps**: Proxy CRUD, product management, developer enrollment, environment and deployment operations.

**AI agent value**: Instead of navigating the gateway console, an agent calls `list-api-proxies`, `deploy-revision`, `create-api-product` as MCP tools.

**Naftiko pattern**: Multi-resource capability consuming the gateway's management API, exposing via REST (for automation pipelines) and MCP (for AI agents).

### 8.2 API Governance and Observability

**What it wraps**: API catalog search, shadow API discovery (Apigee APIM observation layer), spec compliance tracking.

**AI agent value**: An agent runs a governance workflow — find shadow APIs, identify owning teams, compare against catalog, flag gaps — without human coordination.

**Naftiko pattern**: Multi-step orchestration (`steps` + `mappings`) to chain discovery → catalog lookup → comparison. Exposes as MCP tools for AI governance workflows.

### 8.3 API Specification Management

**What it wraps**: Spec inventory, contents retrieval, version comparison, compliance linting.

**AI agent value**: An AI assistant retrieves the latest spec for an API, checks it against organizational standards, and flags non-compliant changes.

**Naftiko pattern**: Capability consuming the gateway's spec management API. Output shaping via `outputParameters` to return structured spec metadata.

### 8.4 Developer Portal and App Management

**What it wraps**: Developer registration, app provisioning, subscription management, access revocation.

**AI agent value**: An AI assistant answers "which teams have access to the payments API?" or "provision a new app for team X" in real time.

**Naftiko pattern**: CRUD operations exposed as both REST (for self-service portals) and MCP tools (for AI assistants).

### 8.5 Analytics and Traffic Observability

**What it wraps**: Traffic analytics, latency metrics, error rate tracking, revenue attribution.

**AI agent value**: An AI business intelligence agent correlates traffic patterns with business outcomes — which products drive revenue, where errors spike, which developer apps generate the most value.

**Naftiko pattern**: Read-only capabilities with `semantics.safe` and `semantics.cacheable`. Output shaping to transform raw analytics into AI-friendly structured context.

---

## 9. Gateway-Specific Patterns

### 9.1 OpenAPI round-trip workflow

```
┌──────────────┐     import-openapi      ┌───────────────────┐
│   Gateway    │ ───────────────────────►│  Naftiko          │
│   API Spec   │     (scaffolds          │  consumes block   │
│   (OAS 3.x)  │      consumes)          │                   │
└──────────────┘                         │  + author exposes │
                                         │  + aggregates     │
┌──────────────┐     export-openapi      │                   │
│   Gateway    │ ◄───────────────────────│  REST exposes     │
│   API Import │     (registers as       │  adapter          │
│              │      API product)       └───────────────────┘
└──────────────┘
```

1. Export the gateway API's OpenAPI spec (from Apigee proxy, AWS API, Azure APIM, Kong Admin API, Tyk Dashboard, or Axway catalog)
2. `naftiko import-openapi --input gateway-api.yaml` → scaffolds `consumes` block
3. Author `aggregates`, `exposes` (REST + MCP), and `binds`
4. `naftiko export-openapi --input capability.yml` → produces OAS for the REST exposes surface
5. Import the exported OAS back into the gateway to register Naftiko as an API product

### 9.2 Forward proxy for incremental migration

For teams that want to start with pass-through and customize incrementally:

```yaml
capability:
  exposes:
    - type: "rest"
      port: 8080
      namespace: "gateway-bridge"
      resources:
        # Phase 1: pure proxy
        - path: "/legacy-orders"
          forward:
            targetNamespace: "orders-api"
            trustedHeaders:
              - "Authorization"
              - "X-Request-Id"
              - "X-Correlation-Id"

        # Phase 2+: shaped and enriched
        - path: "/orders"
          name: "orders"
          operations:
            - method: "GET"
              name: "list-orders"
              ref: "orders.list-orders"
```

This follows the [proxy-then-customize](../../../.agents/skills/naftiko-capability/references/proxy-then-customize.md) pattern: ship fast with forwarding, then iteratively add shaping, orchestration, and agent exposure.

### 9.3 MCP behind a gateway — known gotchas

| Gateway | Gotcha | Workaround |
|---------|--------|------------|
| **All** | SSE response buffering cuts off streaming | Disable response buffering on the gateway route |
| **All** | JSON-RPC payloads don't match REST request/response validation schemas | Disable request/response schema validation on the MCP route |
| **Apigee** | Apigee proxies parse JSON request bodies for policy enforcement | Use a pass-through target endpoint with minimal policies |
| **AWS API Gateway** | 30s integration timeout (REST API type) | Use HTTP API type (no hard timeout) or place MCP on a separate ALB |
| **Azure APIM** | Default request body size limit (256 KB) | Increase `max-size` in APIM policy for the MCP route |
| **Kong** | Default `proxy_buffering on` breaks SSE streaming | Set `proxy_buffering: off` on the Route or globally via `nginx_proxy` directives |
| **Kong** | Plugin-based auth may reject JSON-RPC payloads if body inspection is enabled | Use minimal plugins on the MCP route (key-auth in header only, no body parsing) |
| **Tyk** | Default request timeout may be too short for multi-step orchestration | Increase `proxy.listen_path` timeout or use extended timeout middleware |
| **Axway** | API Gateway request/response size limits | Increase payload limits in the Axway API Gateway filter policy |

### 9.4 AI-native gateways — AgentGateway, Kong AI Gateway, Axway AI Gateway

A new category of gateways is emerging specifically for AI agent traffic. Unlike traditional API gateways that manage REST/HTTP traffic, AI-native gateways proxy **MCP, A2A, and LLM inference** traffic. They complement Naftiko rather than replace it.

**Architecture: Naftiko + AI-native gateway**

```
AI Agent → AgentGateway (MCP proxy, RBAC, audit) → Naftiko MCP exposes → Consumed APIs
```

Naftiko produces MCP endpoints with structured tool definitions. The AI-native gateway federates, secures, and governs them.

| AI-native gateway | Key features relevant to Naftiko | Relationship |
|---|---|---|
| **[AgentGateway](https://agentgateway.dev/)** (Linux Foundation) | MCP federation (single endpoint for multiple MCP servers), JWT/API key/OAuth auth, CEL-based RBAC, OpenTelemetry tracing, OpenAPI→MCP translation, A2A agent-to-agent routing | Naftiko MCP exposes → AgentGateway federates and governs |
| **Kong AI Gateway** | LLM routing, prompt engineering plugins, AI traffic analytics, rate limiting per model, built on Kong Gateway infrastructure | Naftiko REST/MCP exposes behind Kong → Kong AI Gateway adds LLM-specific policies |
| **Axway Amplify AI Gateway** | AI/LLM access governance, composable intelligence, integrates with Amplify Engage catalog | Naftiko capabilities registered in Amplify → AI Gateway governs agent access |
| **Tyk AI Studio** | Open-source AI control plane, LLM provider management, prompt management | Complementary — Tyk manages LLM access, Naftiko manages API-to-tool bridging |

**Key considerations when Naftiko sits behind an AI-native gateway:**

| Concern | Guidance |
|---|---|
| **MCP transport** | AgentGateway supports stdio, SSE, and Streamable HTTP — Naftiko’s Streamable HTTP works natively |
| **Tool federation** | AgentGateway can federate tools from multiple Naftiko instances into a single MCP endpoint for agents |
| **Auth layering** | The AI gateway handles agent-facing auth (JWT, API key); Naftiko’s MCP exposes auth can be disabled or set to trust the gateway |
| **Observability** | Both Naftiko (via control port `/metrics`, `/traces`) and AgentGateway emit OpenTelemetry data — traces connect end-to-end when `traceparent` propagation is enabled |
| **OpenAPI→MCP overlap** | AgentGateway can auto-translate OpenAPI specs to MCP tools; Naftiko does this via capability authoring with richer orchestration (steps, mappings, aggregates). Use Naftiko when you need shaping/orchestration, AgentGateway when pass-through suffices |

> **Note**: Kubernetes Gateway API-based gateways (KGateway/Gloo, Istio Gateway, Contour) use standard Kubernetes health probes and `HTTPRoute` CRDs. The existing Kubernetes row in §4 already covers their health check patterns. They do not require separate gateway-specific callouts in this blueprint.

### 9.5 Migration paths

Most teams migrate from an existing integration shape rather than starting from scratch:

| Starting from | Migration pattern |
|---|---|
| Hand-written Python/Node MCP server calling gateway APIs | Replace with YAML capability: `naftiko import-openapi` from the gateway OAS, then author `aggregates` + `mcp exposes`. Delete the custom server. |
| Existing OpenAPI proxy with a custom auth layer | Use forward proxy (§9.2) to preserve behavior, then iteratively replace resources with shaped operations and `aggregates`. |
| Agents calling gateway APIs directly (no broker) | Introduce Naftiko between agent and gateway. Start with MCP exposes over `aggregates` that `ref` the consumed operations — no behavior change, full audit/observability via control port. |
| Bespoke REST BFF (Backend-For-Frontend) for portal users | Add MCP exposes to the existing REST BFF shape via `aggregates` + `ref` — dual exposure without duplicating logic. |
| Multiple MCP servers per team | Federate behind AgentGateway (§9.4); each team keeps their Naftiko capability; agents see a unified endpoint. |

### 9.6 Out of scope for v1

This blueprint focuses on **HTTP/REST gateway integration**. The following are out of scope for v1 and tracked as future work:

- **GraphQL gateways** (Apollo Router, Kong GraphQL plugin, Tyk GraphQL) — consumes and exposes over GraphQL
- **gRPC gateways** (Envoy, Kong gRPC plugin, KGateway gRPC routes) — binary protocol routing and gRPC-Web translation
- **SOAP / XML-RPC** legacy protocols behind API gateways
- **WebSocket** passthrough for real-time APIs

These may be addressed in a future `gateway-integration-protocols` blueprint if demand materializes.

---

## 10. Implementation Roadmap

### Phase 1 — Production Gateway Readiness (Critical)

| Item | Type | Status | Blueprint |
|------|------|--------|-----------|
| ~~Health check endpoints~~ | ~~Framework + Schema~~ | ✅ Done — Control Port | [Control Port](control-port.md) |
| ~~Prometheus metrics endpoint~~ | ~~Framework~~ | ✅ Done — `/metrics` on control port | [OpenTelemetry](opentelemetry-observability.md) |
| OAuth2 `client_credentials` on consumes | Framework + Schema | 🔲 Pending | [Token Refresh](token-refresh-authentication.md) |
| OpenAPI export with security schemes | Framework | 🔲 Pending | [OpenAPI Interop](openapi-interoperability.md) + §6.4 |

### Phase 2 — Developer Experience

| Item | Type | Dependency | Blueprint |
|------|------|-----------|-----------|
| CORS configuration | Framework + Schema | None | This blueprint (§6.2) |
| Client-side resilience (retry/timeout) | Framework + Schema | None | This blueprint (§6.3) |
| `api-gateway-integration` agent skill | Skill | Phase 1 features | This blueprint (§7) |

### Phase 3 — Operational Maturity

| Item | Type | Dependency | Blueprint |
|------|------|-----------|-----------|
| OpenTelemetry trace propagation (`traceparent`) | Framework | None | [OpenTelemetry](opentelemetry-observability.md) |
| Cache-Control header emission | Framework + Schema | None | [HTTP Cache](http-cache-control.md) |
| Gateway-specific OpenAPI export profiles | Framework | Phase 1 OAS export | Future |

### Phase 4 — Documentation and Examples

| Item | Type | Dependency |
|------|------|-----------|
| Gateway integration tutorial (tutorial step 11+) | Docs | Phase 1 |
| Apigee reference capability example | Example | Phase 1 |
| AWS API Gateway reference capability example | Example | Phase 1 |
| Azure APIM reference capability example | Example | Phase 1 |
| Kong reference capability example | Example | Phase 1 |
| Tyk reference capability example | Example | Phase 1 |
| AgentGateway + Naftiko integration guide | Docs | Phase 2 |

---

## 11. Acceptance Criteria

### Phase 1 exit criteria

- [x] Control port exposes `/health/live` and `/health/ready` on a dedicated management port
- [x] `/health/ready` aggregates readiness across all business adapters (REST, MCP, Skill) and returns `503` until all are started
- [x] Control port has no authentication concern mixed with business adapters (separate port, defaults to `localhost`)
- [ ] OAuth2 `client_credentials` acquires, caches, and refreshes tokens automatically on consumes
- [ ] `naftiko export-openapi` emits `securitySchemes` matching the exposed adapter's authentication config
- [ ] Exported OpenAPI spec imports successfully into Apigee, AWS API Gateway, Azure APIM, Kong, and Tyk (manual verification)

### Phase 2 exit criteria

- [ ] CORS preflight requests return correct headers
- [ ] Retry logic respects `Retry-After` header from gateway
- [ ] `api-gateway-integration` agent skill passes smoke test: agent can scaffold a gateway-consuming capability from a user prompt

### Phase 3 exit criteria

- [ ] Gateway-injected `traceparent` headers are extracted and propagated through Naftiko steps to consumed APIs
- [ ] REST responses include `Cache-Control` headers when `semantics.cacheable: true`
- [ ] Naftiko capabilities appear as connected spans in distributed tracing tools (Jaeger, Datadog, Azure Monitor)

---

## Changelog

| Version | Date | Changes |
|---|---|---|
| `1.0.0-alpha1` | (initial) | Initial proposal covering Apigee, AWS API Gateway, Azure APIM |
| `1.0.0-alpha2` | April 2026 | Added Kong, Tyk, Axway coverage; AI-native gateways (§9.4) |
| `1.0.0-alpha3` | May 2026 | Decision tree + deployment topologies (§2); use-case retitling (§8); migration paths (§9.5); out-of-scope scope note (§9.6); mTLS/SigV4 auth rows + gaps (§6.10, §6.11); correlation labels (§6.8); MCP trust propagation (§6.9); CORS/resilience schema refinements; quick-start example (§3); changelog |
