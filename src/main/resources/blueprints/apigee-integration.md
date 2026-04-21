# Apigee Integration
## Making Apigee-Managed APIs Available to AI Agents

**Status**: Proposed

**Version**: 1.0.0-alpha3

**Date**: April 20, 2026

**Derived from**: [API Gateway Integration](api-gateway-integration.md) — the vendor-neutral parent blueprint

**Context**: [You Have Apigee. Now Your AI Agents Need to Use It.](https://naftiko.io/blog/you-have-apigee-now-your-ai-agents-need-to-use-it/)

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Architecture: Apigee + Naftiko + AI Agents](#2-architecture-apigee--naftiko--ai-agents)
3. [Consumes: Calling Apigee-Managed APIs](#3-consumes-calling-apigee-managed-apis)
4. [Exposes: Sitting Behind Apigee](#4-exposes-sitting-behind-apigee)
5. [Dual Exposure via Aggregates](#5-dual-exposure-via-aggregates)
6. [OpenAPI Round-Trip with Apigee](#6-openapi-round-trip-with-apigee)
7. [Wrapping the Apigee Management API as AI Tools](#7-wrapping-the-apigee-management-api-as-ai-tools)
8. [MCP Behind Apigee — Known Gotchas](#8-mcp-behind-apigee--known-gotchas)
9. [Migration Paths](#9-migration-paths)
10. [Framework Status and Roadmap](#10-framework-status-and-roadmap)
11. [Acceptance Criteria](#11-acceptance-criteria)

---

## 1. Executive Summary

### The Problem

Organizations running Apigee have governed, production-grade API infrastructure — proxies, products, developer portals, analytics. But the primary consumer of those APIs is shifting from developers writing mobile apps to **AI agents** that need to reason about what APIs can do, call them deterministically, and return results that map to business outcomes.

Apigee manages proxies, enforces auth (Verify API Key, OAuth2), rate-limits traffic, and packages APIs as products. It does not produce the structured, machine-readable **capability definitions** that AI agents require. The result: teams build bespoke integration layers between AI tools and Apigee-managed APIs — fragmented, ungoverned, and invisible to the platform engineering team.

### What This Proposes

A unified integration pattern where Naftiko capabilities sit between Apigee and AI agents:

1. **Consumes** — Naftiko calls Apigee-managed APIs using Apigee-native auth (Verify API Key, OAuth2 client credentials) with secrets injected via `binds`
2. **Exposes** — Naftiko sits behind Apigee as a REST API product and/or MCP server, with health probes that Apigee target servers expect
3. **Dual exposure via aggregates** — Define domain logic once, expose as both REST (for the Apigee developer portal) and MCP (for AI agents) from the same YAML
4. **OpenAPI round-trip** — Import Apigee proxy specs to scaffold `consumes`, export REST `exposes` back to Apigee for auto-registration as API products
5. **Apigee Management API as tools** — Wrap Apigee's own admin API as MCP tools so AI agents can manage proxies, products, and developers

### What This Does NOT Do

- **No Apigee replacement** — Naftiko does not replace Apigee. It extends Apigee's value into the AI consumption surface.
- **No Apigee proxy bundles** — Naftiko does not produce Apigee proxy bundles, shared flows, or policy XML. It produces standard OpenAPI specs and HTTP endpoints that Apigee consumes natively.
- **No built-in rate limiting** — Rate limiting stays in Apigee. Naftiko adds client-side resilience (retry, backoff) to handle Apigee-enforced limits gracefully.

### Business Value

| Benefit | Impact | Audience |
|---------|--------|----------|
| **Apigee ROI extension** | Existing Apigee investment becomes AI-ready without changing what Apigee does | Platform Engineering |
| **Governed AI integration** | Capabilities replace bespoke integration layers with auditable, spec-driven definitions | Architecture, InfoSec |
| **Zero-code bridge** | Declarative YAML — no custom middleware between Apigee and AI agents | Developers |
| **Portal + agents from one definition** | Same domain logic serves Apigee developer portal consumers (REST) and AI agents (MCP) | Product Teams |
| **Environment portability** | Same capability YAML across dev/staging/prod — only `binds` change | DevOps, SRE |

---

## 2. Architecture: Apigee + Naftiko + AI Agents

### Deployment Topologies

Where Naftiko runs relative to Apigee affects auth, networking, and probe configuration:

| Topology | Trust boundary | Auth between Apigee and Naftiko | Health probe access |
|---|---|---|---|
| **Same GCP project (GKE / Cloud Run)** | Private network | Optional mTLS; API key acceptable | Private DNS / service mesh |
| **Cloud Run sidecar** | Trusted localhost | None required | `localhost:9090/health/ready` |
| **Different GCP project / on-prem** | Untrusted | mTLS or Apigee-to-Naftiko token | Probe over TLS; keep control port internal |
| **Cloud Run (serverless)** | Platform-managed | Cloud Run invoker IAM | Cold starts may delay `/health/ready` — tune probe `initialDelaySeconds` |

For any topology that crosses a network boundary, keep the control port (9090) on an internal address and route only REST/MCP business ports through Apigee.

### Positioning

```
┌──────────────────────────────────────────────────────────────────┐
│                          Apigee                                   │
│                                                                   │
│   ┌─────────────┐  ┌─────────────┐  ┌────────────────────────┐    │
│   │ API Proxy A │  │ API Proxy B │  │ Naftiko REST (proxy)   │    │
│   └──────┬──────┘  └──────┬──────┘  └───────────┬────────────┘    │
│          │                │                      │                │
│   Verify API Key, OAuth2, Rate Limiting, Analytics, Dev Portal   │
└──────────┼────────────────┼──────────────────────┼────────────────┘
           │                │                      │
           ▼                ▼                      ▼
      ┌─────────┐     ┌─────────┐      ┌──────────────────────┐
      │ Backend │     │ Backend │      │   Naftiko Engine      │
      │  API A  │     │  API B  │      │                       │
      └─────────┘     └─────────┘      │   ┌──────────────┐   │
                                       │   │  Aggregates   │   │
          ┌────────────────────────────│   └──────┬───────┘   │
          │       consumes             │          │            │
          ▼                            │   ┌──────┴───────┐   │
     ┌─────────┐                       │   │ REST exposes │   │
     │ Apigee  │◄── API key / OAuth2 ──│   └──────────────┘   │
     │ (inbound│                       │   ┌──────────────┐   │
     │  to     │                       │   │ MCP exposes  │───────► AI Agents
     │  backend│                       │   └──────────────┘   │
     │  APIs)  │                       │   ┌──────────────┐   │
     └─────────┘       Health probe ───│──►│ Control port │   │
                       /metrics ───────│──►│ (9090)       │   │
                                       │   └──────────────┘   │
                                       └──────────────────────┘
```

### Four Integration Surfaces

| Surface | Direction | Apigee role | Naftiko role |
|---------|-----------|-------------|--------------|
| **Consumes** | Naftiko → Apigee → Backend | Verify API Key / OAuth2, rate limiting, routing | HTTP client with Apigee-native auth |
| **REST exposes** | Apigee → Naftiko | Reverse proxy, developer portal, analytics | REST server registered as Apigee API product |
| **MCP exposes** | AI Agent → Naftiko (direct or via Apigee) | Optional pass-through proxy | MCP server with structured tool definitions |
| **Control port** | Apigee → Naftiko (management) | Target server health monitor | Dedicated port for `/health/live`, `/health/ready`, `/metrics` |

---

## 3. Consumes: Calling Apigee-Managed APIs

### Authentication Patterns

| Apigee policy | Naftiko auth type | Header / placement | Notes |
|---------------|------------------|-------------------|-------|
| **Verify API Key** | `apikey` | `header` / `x-api-key` (or custom) | Most common for internal service-to-service |
| **OAuth2 (client credentials)** | `oauth2` | `header` / `Authorization: Bearer` | Machine-to-machine; see [Token Refresh blueprint](token-refresh-authentication.md) |
| **OAuth2 (access token verification)** | `bearer` | `header` / `Authorization: Bearer` | Pre-fetched token from external IdP |
| **mTLS (Apigee hybrid / Edge Microgateway)** | `mtls` *(planned)* | TLS layer — no header | For B2B and zero-trust environments |

### Quick Start: Calling an Apigee-Managed API

The minimal capability for consuming an API behind Apigee and exposing it to AI agents:

```yaml
naftiko: "1.0.0-alpha3"

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

  exposes:
    - type: "mcp"
      port: 3000
      namespace: "crm"
      tools:
        - ref: "crm-api.contacts.list-contacts"
```

### OAuth2 Client Credentials (Apigee Token Endpoint)

For APIs protected by Apigee's OAuth2 policy, use the `oauth2` auth type with Apigee's token endpoint:

```yaml
binds:
  - namespace: "apigee-oauth"
    keys:
      APIGEE_CLIENT_ID: "client-id"
      APIGEE_CLIENT_SECRET: "client-secret"

capability:
  consumes:
    - type: "http"
      namespace: "payments-api"
      baseUri: "https://api.example.com/payments/v1"
      authentication:
        type: "oauth2"
        grantType: "client_credentials"
        authorizationServerUri: "https://api.example.com/oauth/token"
        clientId: "{{APIGEE_CLIENT_ID}}"
        clientSecret: "{{APIGEE_CLIENT_SECRET}}"
        scopes:
          - "payments.read"
          - "payments.write"
```

The framework automatically acquires, caches, and refreshes tokens. No external token management scripts needed.

### Secret Injection via `binds`

The same capability YAML works across environments by changing only the binding provider:

| Environment | `location` value | Secret source |
|-------------|-----------------|---------------|
| Local dev | `file://secrets/apigee.env` | `.env` file (gitignored) |
| CI/CD | `github-secrets://` | GitHub Actions secrets |
| Staging | `vault://apigee/staging` | HashiCorp Vault |
| GKE Production | *(omitted)* | Kubernetes Secrets / Workload Identity |
| Cloud Run | *(omitted)* | Secret Manager via runtime injection |

---

## 4. Exposes: Sitting Behind Apigee

### Control Port for Apigee Health Probes

Apigee target servers support health monitoring. Naftiko provides this through a dedicated **Control Port** adapter on a separate port from business traffic:

```yaml
capability:
  exposes:
    - type: "control"
      port: 9090
      management:
        health: true       # /health/live + /health/ready
      observability:
        metrics:
          local:
            enabled: true   # /metrics — Prometheus scrape endpoint
```

**Apigee target server health monitor configuration:**

| Setting | Value |
|---------|-------|
| **Protocol** | HTTP |
| **Host** | `naftiko-host` (internal address) |
| **Port** | `9090` |
| **Path** | `/health/ready` |
| **Expected response** | `200 OK` with `{"status":"UP"}` |
| **Interval** | 30s (recommended) |
| **Failure threshold** | 3 consecutive failures before marking target unhealthy |

For **GKE deployments**, also configure Kubernetes probes:

```yaml
# Kubernetes deployment excerpt
livenessProbe:
  httpGet:
    path: /health/live
    port: 9090
  initialDelaySeconds: 5
  periodSeconds: 10
readinessProbe:
  httpGet:
    path: /health/ready
    port: 9090
  initialDelaySeconds: 10
  periodSeconds: 15
```

The control port address defaults to `localhost`. In GKE or Cloud Run, set `address: "0.0.0.0"` to allow external probe access — but keep the control port on an internal network, not exposed through Apigee's public endpoint.

### REST Adapter as an Apigee Backend

When Naftiko sits behind Apigee as a target server, it behaves like any HTTP backend:

```yaml
capability:
  exposes:
    # Control port — health probes for Apigee target server
    - type: "control"
      port: 9090

    # REST — business traffic routed through Apigee
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

**Registering in Apigee:**

1. Export the OpenAPI spec: `naftiko export-openapi --input capability.yml`
2. In Apigee console → **Develop** → **API Proxies** → **Create Proxy** → **Import OpenAPI Spec**
3. Upload the exported OAS file — Apigee scaffolds the proxy with routes matching Naftiko's REST exposes
4. Configure the **Target Server** to point to Naftiko's internal address and port 8080
5. Add the health monitor pointing to port 9090 `/health/ready`
6. Publish the proxy as an **API Product** in the developer portal

### Apigee Developer Portal Integration

The exported OpenAPI spec includes:
- All REST operations with schemas, descriptions, and examples
- `securitySchemes` matching the exposed adapter's auth config (API key, bearer, OAuth2)
- `servers` block with the Naftiko base URL (replace with the Apigee proxy URL post-import)

This means the Apigee developer portal automatically generates:
- Interactive API explorer (Try It Out)
- Client SDK generation
- API documentation pages

### MCP Adapter — Direct or Via Apigee

MCP uses Streamable HTTP (single endpoint, JSON-RPC payloads). When routing MCP through Apigee:

| Concern | Apigee Configuration |
|---------|---------------------|
| **Proxy type** | Use a **pass-through target endpoint** — minimal policy enforcement |
| **Request body** | Apigee proxies parse JSON request bodies for policy enforcement — avoid payload-dependent policies on MCP routes |
| **Response buffering** | Disable response buffering (for SSE streaming support) |
| **Schema validation** | Do not apply OAS validation policies — JSON-RPC payloads don't match REST patterns |
| **Timeout** | Increase target timeout beyond default 55s — tool execution may involve multi-step orchestration |
| **Recommended approach** | Run MCP on a separate port, accessible directly by AI agents (not through Apigee), while REST goes through Apigee |

---

## 5. Dual Exposure via Aggregates

The most powerful pattern for Apigee teams: define domain logic once, expose via both REST (for the Apigee developer portal) and MCP (for AI agents):

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
    # Control port — Apigee health probes and Prometheus metrics
    - type: "control"
      port: 9090
      management:
        health: true
      observability:
        metrics:
          local:
            enabled: true

    # REST — register in Apigee as API product, serve developer portal
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

**The result:**

| Surface | Consumer | Governed by |
|---------|----------|-------------|
| REST (port 8080) | Apigee developer portal users, mobile/web apps | Apigee: rate limiting, analytics, API products |
| MCP (port 3000) | AI agents (Claude, GPT, Gemini, custom) | Naftiko: structured tool definitions, orchestration |
| Control (port 9090) | Apigee target server health monitor, Prometheus | Naftiko: health, metrics, traces |

Both REST and MCP share the same aggregate function — same domain logic, same contract, same orchestration. No duplication.

---

## 6. OpenAPI Round-Trip with Apigee

### Import: Apigee Proxy Spec → Naftiko Consumes

```
┌──────────────────┐     import-openapi      ┌────────────────────┐
│ Apigee Proxy     │ ──────────────────────► │ Naftiko            │
│ OpenAPI Spec     │     (scaffolds          │ consumes block     │
│ (from console    │      consumes)          │                    │
│  or apigeecli)   │                         │ + author exposes   │
└──────────────────┘                         │ + aggregates       │
                                             └────────────────────┘
```

**Steps:**

1. Export the proxy's OpenAPI spec from the Apigee console or via `apigeecli`:
   ```bash
   apigeecli apis get --name my-proxy --revision 1 --org $ORG --token $TOKEN > proxy-spec.yaml
   ```
2. Import into Naftiko:
   ```bash
   naftiko import-openapi --input proxy-spec.yaml
   ```
   This scaffolds a `consumes` block with all operations, parameters, and paths.
3. Author `aggregates`, `exposes` (REST + MCP), and `binds` around the scaffolded consumes.

### Export: Naftiko REST Exposes → Apigee API Product

```
┌────────────────────┐     export-openapi     ┌──────────────────┐
│ Naftiko            │ ─────────────────────► │ Apigee           │
│ REST exposes       │     (produces OAS)     │ Import as Proxy  │
│ adapter            │                        │ + API Product    │
└────────────────────┘                        └──────────────────┘
```

**Steps:**

1. Export from Naftiko:
   ```bash
   naftiko export-openapi --input capability.yml --output naftiko-api.yaml
   ```
2. In Apigee console: **Develop** → **API Proxies** → **Create** → **Upload Spec** → select `naftiko-api.yaml`
3. Configure the proxy's **Target Server** to point to Naftiko's internal address
4. Publish as an **API Product** and assign to the developer portal

The exported OAS includes `components/securitySchemes` matching the REST adapter's auth config, so Apigee's developer portal renders the correct auth UI (API key input, OAuth2 flow, etc.).

---

## 7. Wrapping the Apigee Management API as AI Tools

The [Apigee Management API](https://cloud.google.com/apigee/docs/reference/apis/apigee/rest) exposes proxy CRUD, product management, developer enrollment, analytics, and more. Wrapping it as a Naftiko capability lets AI agents manage Apigee programmatically.

### 7.1 API Lifecycle Management

**What it wraps**: `organizations.apis.*`, `organizations.environments.apis.deployments.*`, `organizations.apiproducts.*`

**AI agent value**: An agent calls `list-api-proxies`, `deploy-revision`, `create-api-product` as MCP tools instead of navigating the Apigee console.

**Scaffolding:**
```bash
# Download the Apigee Management API OAS
# Import into Naftiko to scaffold consumes
naftiko import-openapi --input apigee-management-api.yaml
```

**Naftiko pattern**: Multi-resource capability consuming the Apigee Management API with OAuth2 auth (Google Cloud service account), exposing via MCP for agents and REST for automation pipelines.

### 7.2 API Governance and Observability

**What it wraps**: API catalog search, shadow API discovery (Apigee API hub / observation), spec compliance tracking.

**AI agent value**: An agent runs a governance workflow — find shadow APIs, identify owning teams, compare against catalog, flag gaps — without human coordination.

**Naftiko pattern**: Multi-step orchestration (`steps` + `mappings`) to chain discovery → catalog lookup → comparison. Exposes as MCP tools for AI governance workflows.

### 7.3 Developer Portal and App Management

**What it wraps**: `organizations.developers.*`, `organizations.developers.apps.*`, `organizations.developers.subscriptions.*`

**AI agent value**: An AI assistant answers "which teams have access to the payments API?" or "provision a new app for team X" in real time.

**Naftiko pattern**: CRUD operations exposed as both REST (for self-service portals) and MCP tools (for AI assistants).

### 7.4 Analytics and Traffic Observability

**What it wraps**: `organizations.environments.stats.*`, `organizations.environments.optimizedStats.*`

**AI agent value**: An AI business intelligence agent correlates traffic patterns with business outcomes — which products drive revenue, where errors spike, which developer apps generate the most value.

**Naftiko pattern**: Read-only capabilities with `semantics.safe` and `semantics.cacheable`. Output shaping to transform raw Apigee analytics into AI-friendly structured context.

### 7.5 Spec Management

**What it wraps**: `organizations.apis.revisions.*`, spec retrieval and comparison.

**AI agent value**: An AI assistant retrieves the latest spec for an API proxy, compares revisions, checks against organizational standards, and flags non-compliant changes.

**Naftiko pattern**: Capability consuming the Apigee spec management endpoints. Output shaping via `outputParameters` to return structured spec metadata.

---

## 8. MCP Behind Apigee — Known Gotchas

| Issue | Details | Workaround |
|-------|---------|------------|
| **JSON body parsing** | Apigee proxies parse JSON request bodies for policy enforcement (ExtractVariables, JSONThreatProtection) | Use a **pass-through target endpoint** with minimal policies on the MCP route |
| **SSE response buffering** | Apigee may buffer streaming responses, breaking Server-Sent Events | Disable response buffering; or keep MCP on a direct port (not through Apigee) |
| **Payload validation** | OAS validation policy will reject JSON-RPC payloads (not REST-shaped) | Do not apply OAS validation policies on MCP routes |
| **Target timeout** | Default Apigee target timeout (55s) may be too short for multi-step orchestration | Increase `<HTTPTargetConnection><Properties><Property name="io.timeout.millis">` on the target endpoint |
| **CORS for browser-based MCP clients** | If MCP is accessed from browser-based AI tools through Apigee | Add CORS headers via Apigee AssignMessage policy, or use Naftiko's CORS config (when available) |

**Recommended approach**: Route REST traffic through Apigee (full policy enforcement, analytics, developer portal). Keep MCP on a separate direct port for AI agents — Apigee governs the REST surface, Naftiko governs the MCP surface.

---

## 9. Migration Paths

| Starting from | Migration pattern |
|---|---|
| **Hand-written Python/Node MCP server calling Apigee APIs** | Replace with YAML capability: `naftiko import-openapi` from the Apigee proxy OAS, then author `aggregates` + `mcp exposes`. Delete the custom server. |
| **Apigee proxy with custom Node.js target** | Use forward proxy to preserve behavior, then iteratively replace resources with shaped operations and `aggregates`. |
| **AI agents calling Apigee APIs directly** | Introduce Naftiko between agent and Apigee. Start with MCP exposes over `aggregates` that `ref` the consumed operations — no behavior change, full audit/observability via control port. |
| **Existing REST BFF for portal users** | Add MCP exposes to the existing REST shape via `aggregates` + `ref` — dual exposure without duplicating logic. The REST side stays behind Apigee; MCP serves agents directly. |

---

## 10. Framework Status and Roadmap

### What Works Today

| Feature | Status | Apigee relevance |
|---------|--------|-----------------|
| **API key auth on consumes** | ✅ Implemented | Verify API Key policy → `apikey` with `x-api-key` header |
| **Bearer auth on consumes** | ✅ Implemented | Pre-fetched OAuth2 token → `bearer` |
| **`binds` for secret injection** | ✅ Implemented | `file://` for dev, K8s Secrets / Secret Manager for prod |
| **OpenAPI export** | ✅ Implemented | `naftiko export-openapi` → import into Apigee as API proxy |
| **OpenAPI export security schemes** | ✅ Implemented | Auth config → `components/securitySchemes` for developer portal |
| **OpenAPI import** | ✅ Implemented | Apigee proxy OAS → scaffold `consumes` block |
| **Aggregates + ref** | ✅ Implemented | Single definition, dual REST (Apigee portal) + MCP (agents) |
| **Semantics → MCP hints** | ✅ Implemented | `safe`/`idempotent` auto-derive MCP tool hints |
| **Control port** | ✅ Implemented | `/health/live` + `/health/ready` for Apigee target server health monitor |
| **Prometheus metrics** | ✅ Implemented | `/metrics` on control port for monitoring |

### What's Coming

| Feature | Priority | Apigee relevance |
|---------|----------|-----------------|
| **OAuth2 `client_credentials`** | High | Apigee OAuth2 token endpoint — automatic acquisition, caching, refresh |
| **CORS configuration** | High | Apigee developer portal "Try It Out" needs CORS headers |
| **Client-side resilience** | High | Handle Apigee 429 rate-limit responses with retry + exponential backoff |
| **OpenTelemetry trace propagation** | Medium | Propagate `X-Cloud-Trace-Context` from Apigee through Naftiko to backends — connect spans in Cloud Trace |
| **Cache-Control headers** | Medium | Enable Apigee response caching on `semantics.cacheable` operations |
| **Correlation labels** | Medium | Extract `X-Apigee-Developer-Id` and `X-Apigee-Api-Product` as metric labels for correlated analytics |
| **mTLS on consumes** | Medium | Apigee hybrid / Edge Microgateway mTLS enforcement |

### Apigee-Specific Trace Propagation

Apigee injects `X-Cloud-Trace-Context` (Cloud Trace format) and standard `traceparent` (W3C format). Without extracting and propagating these, Naftiko capabilities appear as black boxes in **Apigee Analytics** and **Cloud Trace** dashboards.

Once OpenTelemetry trace propagation is implemented, the end-to-end trace looks like:

```
Apigee Proxy → [traceparent/X-Cloud-Trace-Context] → Naftiko → consumed backend API
     │                                                  │
     └── Apigee Analytics dashboard                     └── Cloud Trace / /traces endpoint
```

### Apigee Correlation Labels

Apigee tags requests with consumer and product identifiers. Mapping these to Naftiko metrics enables cross-platform analytics:

| Apigee header | Naftiko metric label | Use case |
|---------------|---------------------|----------|
| `X-Apigee-Developer-Id` | `apigee.developer_id` | Per-developer traffic attribution (spans only — high cardinality) |
| `X-Apigee-Api-Product` | `apigee.api_product` | Per-product RED metrics correlation |

---

## 11. Acceptance Criteria

### Phase 1 — Apigee Production Readiness

- [x] Control port exposes `/health/live` and `/health/ready` on a dedicated management port
- [x] `/health/ready` aggregates readiness across all business adapters and returns `503` until all are started
- [x] Control port runs on a separate port, defaults to `localhost`
- [ ] OAuth2 `client_credentials` acquires, caches, and refreshes tokens from Apigee's OAuth2 endpoint
- [ ] `naftiko export-openapi` emits `securitySchemes` matching the exposed adapter's authentication config
- [ ] Exported OpenAPI spec imports successfully into Apigee and generates a working proxy (manual verification)

### Phase 2 — Apigee Developer Experience

- [ ] CORS preflight requests return correct headers (Apigee developer portal "Try It Out")
- [ ] Retry logic handles Apigee 429 responses with exponential backoff and `Retry-After` respect
- [ ] Full OpenAPI round-trip: Apigee proxy OAS → `import-openapi` → author capability → `export-openapi` → Apigee import as product

### Phase 3 — Apigee Operational Maturity

- [ ] `X-Cloud-Trace-Context` and `traceparent` headers from Apigee are extracted and propagated through Naftiko
- [ ] Naftiko capabilities appear as connected spans in Cloud Trace alongside Apigee spans
- [ ] REST responses include `Cache-Control` headers when `semantics.cacheable: true`

---

## Changelog

| Version | Date | Changes |
|---|---|---|
| `1.0.0-alpha3` | April 20, 2026 | Initial Apigee-specific variant derived from [API Gateway Integration](api-gateway-integration.md) v1.0.0-alpha3 |
