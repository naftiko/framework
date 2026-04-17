# Control Port
## A Dedicated Management Adapter for Development, Operations, and Governance

**Status**: Proposal  
**Date**: April 16, 2026  
**Spec Version**: `1.0.0-alpha2`  
**Key Concept**: Introduce a new `type: "control"` exposed adapter that provides a single management surface for every capability — health checks, Prometheus metrics, runtime configuration, diagnostic endpoints, and governance hooks — isolated from business traffic on a dedicated port.

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Goals and Non-Goals](#goals-and-non-goals)
3. [Terminology](#terminology)
4. [Design Analogy](#design-analogy)
5. [Architecture Overview](#architecture-overview)
6. [Core Concepts](#core-concepts)
7. [Specification and Schema Changes](#specification-and-schema-changes)
8. [Capability YAML Examples](#capability-yaml-examples)
9. [Endpoint Catalog](#endpoint-catalog)
10. [Runtime Design](#runtime-design)
11. [Relationship with OpenTelemetry Observability Blueprint](#relationship-with-opentelemetry-observability-blueprint)
12. [Security Considerations](#security-considerations)
13. [Testing Strategy](#testing-strategy)
14. [Implementation Roadmap](#implementation-roadmap)
15. [Risks and Mitigations](#risks-and-mitigations)
16. [Acceptance Criteria](#acceptance-criteria)

---

## Executive Summary

### What This Proposes

Add a new `type: "control"` server adapter in `capability.exposes` that provides a **management plane** for every Naftiko capability. The control port is not a business adapter — it does not expose tools, operations, or skills. Instead, it exposes a fixed set of **engine-provided endpoints** grouped into three domains:

| Domain | Purpose | Example Endpoints |
|---|---|---|
| **Development** | Runtime introspection and configuration for capability authors | Configuration reload, spec introspection, remote debugging hooks |
| **Operations** | Production readiness signals for infrastructure and SRE teams | Health/readiness/liveness probes, Prometheus `/metrics`, runtime info |
| **Governance** | Policy enforcement and audit for platform teams | Capability metadata, dependency inventory, compliance labels |

The control port runs on a **dedicated port**, isolated from business traffic, and is managed by the engine — capability authors declare it but do not define its endpoints.

### Why This Fits Naftiko

Naftiko capabilities are declarative YAML documents that wire consumed APIs to exposed adapters. Today, there is no standard way to:

- Check if a running capability is healthy
- Scrape Prometheus metrics without conflicting with business ports
- Reload configuration without restarting the process
- Inspect the resolved capability spec at runtime
- Expose governance metadata for platform cataloging

Every production-grade framework solves this with a management port (Spring Boot Actuator on `:8081`, Quarkus management interface, Kubernetes sidecar patterns). The control port brings this to Naftiko while preserving its declarative philosophy: declare `type: "control"` in YAML, get a full management surface with zero code.

### Why a Separate Adapter (Not Embedded in Business Adapters)

1. **Port isolation** — Infrastructure tools (Prometheus, Kubernetes probes, service mesh sidecars) expect management endpoints on a dedicated port. Mixing them with business traffic creates routing complexity, security risks, and port conflicts.
2. **Independent lifecycle** — The control port must respond to health probes even when business adapters are overloaded, restarting, or misconfigured.
3. **Unified surface** — A capability may expose multiple business adapters (REST on `:8080`, MCP on `:3000`). The control port provides a single address for all management concerns, regardless of how many business adapters are running.
4. **Security boundary** — Management endpoints (config reload, debug info) should never be accidentally exposed on a public-facing business port. A separate adapter makes this explicit.

### Value

| Benefit | Impact |
|---|---|
| **Kubernetes-native** | Standard `/health/live` and `/health/ready` endpoints for probe configuration |
| **Prometheus integration** | `/metrics` on a dedicated port — no conflict with business adapters |
| **Zero-downtime config** | Reload capability configuration without process restart |
| **Spec introspection** | Query the resolved capability spec at runtime for debugging and cataloging |
| **Governance metadata** | Expose dependency inventory and compliance labels for platform teams |
| **Consistent across adapters** | Same management surface whether the capability exposes REST, MCP, Skill, or all three |

### Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Config reload causes runtime inconsistency | Medium | High | Atomic swap with validation; reject invalid specs |
| Management port exposed to public internet | Medium | High | Bind to `localhost` by default; document network policy |
| Debug endpoints leak sensitive data | Low | High | Redact secrets from introspection; auth required for debug |
| Port conflict with business adapters | Low | Low | Schema validation rejects duplicate ports |
| Feature creep — control port becomes a second REST API | Low | Medium | Endpoints are engine-provided, not user-defined |

---

## Goals and Non-Goals

### Goals

1. Introduce `control` as a new exposed adapter type, joining `rest`, `mcp`, `skill`.
2. Provide built-in health, readiness, and liveness endpoints for Kubernetes probe integration.
3. Host the Prometheus `/metrics` scrape endpoint — absorbing Phase 2 of the [OpenTelemetry Observability](opentelemetry-observability.md) blueprint.
4. Support runtime configuration reload (hot reload of capability YAML without process restart).
5. Expose a read-only spec introspection endpoint for debugging and catalog integration.
6. Expose governance metadata (dependency inventory, adapter summary, compliance labels).
7. Keep the control port engine-managed — capability authors declare it, the engine provides the endpoints.
8. Bind to `localhost` by default for security; allow override via `address`.

### Non-Goals (This Proposal)

1. User-defined endpoints on the control port — it is not a second REST API.
2. Full APM or profiling — those belong in the [OpenTelemetry Observability](opentelemetry-observability.md) blueprint.
3. Cluster-wide orchestration or service discovery — the control port is per-capability.
4. Authentication/authorization for the control port in Phase 1 — added in a later phase.
5. WebSocket or streaming management connections.
6. Write operations on governance metadata — the control port is read-only for governance; mutations go through the spec.

---

## Terminology

| Term | Definition |
|---|---|
| **Control port** | A dedicated HTTP port for management traffic, isolated from business adapters |
| **Health probe** | An HTTP endpoint that reports the operational status of the capability (`UP`, `DOWN`, `DEGRADED`) |
| **Liveness probe** | Kubernetes concept — indicates the process is alive and not deadlocked; failure triggers a restart |
| **Readiness probe** | Kubernetes concept — indicates the capability is ready to serve traffic; failure removes it from the load balancer |
| **Hot reload** | Replacing the active capability spec at runtime without stopping the process |
| **Spec introspection** | Querying the resolved (post-merge, post-bind) capability specification at runtime |
| **Governance metadata** | Structured information about a capability's dependencies, adapters, and compliance posture — consumed by platform catalogs |
| **RED metrics** | Rate, Errors, Duration — the standard SRE metric set, exposed via Prometheus |

---

## Design Analogy

### How the control port relates to existing adapters

```
REST Adapter           MCP Adapter            Skill Adapter          Control Adapter (proposed)
────────────           ───────────            ─────────────          ─────────────────────────
ExposesRest            ExposesMcp             ExposesSkill           ExposesControl
├─ namespace           ├─ namespace           ├─ namespace           ├─ (no namespace — singleton)
├─ port                ├─ port                ├─ port                ├─ port
├─ address             ├─ address             ├─ address             ├─ address (default: localhost)
├─ authentication      ├─ transport           ├─ authentication      ├─ (no authentication in phase 1)
│                      │                      │                      │
├─ resources[]         ├─ tools[]             ├─ skills[]            ├─ (no user-defined constructs)
│  └─ operations[]     │  └─ call/steps       │  └─ tools[]          │
│     └─ call/steps    │                      │     └─ call/steps    ├─ Engine-provided endpoints:
│                      ├─ resources[]         │                      │  ├─ /health/live
│                      │                      │                      │  ├─ /health/ready
│                      ├─ prompts[]           │                      │  ├─ /metrics
│                      │                      │                      │  ├─ /info
│                      │                      │                      │  ├─ /spec
│                      │                      │                      │  ├─ /config/reload
│                      │                      │                      │  └─ /governance
```

### Key difference from business adapters

Business adapters expose **user-defined constructs** (tools, operations, skills) backed by orchestration steps. The control adapter exposes **engine-defined endpoints** backed by the engine's own internal state. Capability authors control *whether* the control port is enabled and *where* it listens — not *what* it serves.

### Analogy to established frameworks

| Framework | Management Surface | Default Port |
|---|---|---|
| Spring Boot | Actuator (`/actuator/*`) | `management.server.port` (separate from app) |
| Quarkus | Management interface (`/q/*`) | `quarkus.management.port` (9000) |
| Envoy | Admin interface (`/`) | `admin.address` (typically 15000) |
| **Naftiko** | **Control adapter (`/`)** | **`exposes[type=control].port`** |

---

## Architecture Overview

### Current State

```
Capability
├─ exposes:
│  ├─ type: rest   (port 8080)   ← business traffic
│  ├─ type: mcp    (port 3000)   ← business traffic
│  └─ type: skill  (port 4000)   ← business traffic
│
├─ No health endpoints
├─ No metrics endpoint (Prometheus has nothing to scrape)
├─ No config reload (must restart)
├─ No spec introspection
└─ No governance metadata
```

### Proposed State

```
Capability
├─ exposes:
│  ├─ type: rest    (port 8080)  ← business traffic
│  ├─ type: mcp     (port 3000)  ← business traffic
│  ├─ type: skill   (port 4000)  ← business traffic
│  └─ type: control (port 9090)  ← management traffic
│     ├─ GET /health/live         → { "status": "UP" }
│     ├─ GET /health/ready        → { "status": "UP" } or 503
│     ├─ GET /metrics             → Prometheus exposition format
│     ├─ GET /info                → capability name, version, uptime, adapters
│     ├─ GET /spec                → resolved capability spec (redacted)
│     ├─ POST /config/reload      → trigger hot reload
│     └─ GET /governance          → dependency inventory, compliance labels
│
├─ Kubernetes probes point to :9090/health/*
├─ Prometheus scrapes :9090/metrics
└─ Platform catalog reads :9090/governance
```

### Traffic Isolation

```
                    ┌─────────────────┐
  Business traffic  │                 │  Management traffic
  ─────────────────►│   Capability    │◄───────────────────
   :8080 (REST)     │                 │   :9090 (Control)
   :3000 (MCP)      │  ┌───────────┐  │
   :4000 (Skill)    │  │  Engine    │  │   Kubernetes probes
                    │  │  State     │  │   Prometheus scraper
                    │  └───────────┘  │   Platform catalog
                    └─────────────────┘   Config reload API
```

---

## Core Concepts

### 1. Engine-Provided Endpoints

Unlike business adapters where the user declares tools/operations/resources, the control port's endpoints are **provided by the engine**. The set of available endpoints is determined by:

- **Always present**: `/health/live`, `/health/ready`, `/info`
- **When OTel is active**: `/metrics` (Prometheus scrape)
- **When enabled in spec**: `/config/reload`, `/spec`, `/governance`

Capability authors do not define routes, parameters, or output mappings on the control port.

### 2. Health Model

The health model distinguishes between three states:

| State | Liveness | Readiness | Meaning |
|---|---|---|---|
| `UP` | 200 | 200 | All adapters started, all consumed APIs reachable |
| `DEGRADED` | 200 | 503 | Process is alive but one or more adapters failed to start or a consumed API is unreachable |
| `DOWN` | 503 | 503 | Critical failure — process should be restarted |

**Liveness** answers: "Is the process alive?" — returns 200 unless the JVM is deadlocked or the control port itself is broken (which would mean no response at all).

**Readiness** answers: "Is the capability ready to serve business traffic?" — checks that all declared business adapters are started and listening.

### 3. Hot Reload

`POST /config/reload` triggers a re-read of the capability YAML file and an atomic swap of the internal spec. The reload follows these rules:

1. **Parse** the YAML file from disk (same path as the original load).
2. **Validate** the new spec against the JSON schema and Naftiko rules.
3. **Diff** the new spec against the current spec to determine what changed.
4. **Reject** if structural changes require a restart (e.g., adding/removing adapters, changing ports).
5. **Apply** safe changes atomically (e.g., updated operation parameters, new steps, modified output mappings).
6. **Report** the result in the response: `{ "status": "applied", "changes": [...] }` or `{ "status": "rejected", "reason": "..." }`.

Changes that always require a restart (rejected by reload):
- Adding or removing an `exposes` adapter
- Changing an adapter's `port` or `address`
- Adding or removing a `consumes` adapter
- Changing `capability.info.name`

Changes that are safe to hot-reload:
- Modified `steps` in an operation/tool/procedure
- Updated `outputParameters` mappings
- Changed `inputParameters` (name, type, description, required)
- Updated `aggregates` function definitions
- Modified `authentication` credentials (re-bind)

### 4. Spec Introspection

`GET /spec` returns the **resolved** capability specification — after aggregate ref resolution, bind substitution, and import merging. This is the spec as the engine sees it at runtime.

**Security**: Secrets referenced via `binds` are **redacted** in the response (replaced with `"***"`). The introspection endpoint never exposes raw credentials.

### 5. Governance Metadata

`GET /governance` returns a structured summary for platform catalog integration:

```json
{
  "capability": {
    "name": "weather-service",
    "version": "1.0.0",
    "specVersion": "1.0.0-alpha2"
  },
  "adapters": [
    { "type": "rest", "port": 8080 },
    { "type": "mcp", "port": 3000, "transport": "http" },
    { "type": "control", "port": 9090 }
  ],
  "dependencies": [
    {
      "namespace": "weather-api",
      "baseUri": "https://api.weather.gov",
      "authentication": "none"
    }
  ],
  "aggregates": [
    {
      "namespace": "forecast",
      "functions": ["get-forecast", "get-alerts"],
      "semantics": { "safe": true, "cacheable": true }
    }
  ],
  "labels": {}
}
```

The `labels` field is a free-form key-value map that capability authors can populate for compliance tagging (e.g., `"data-classification": "public"`, `"team": "platform"`).

---

## Specification and Schema Changes

### New `ExposesControl` Definition

```json
"ExposesControl": {
  "type": "object",
  "properties": {
    "type": {
      "const": "control"
    },
    "address": {
      "type": "string",
      "default": "localhost",
      "description": "Bind address for the control port. Defaults to localhost for security."
    },
    "port": {
      "type": "integer",
      "minimum": 1,
      "maximum": 65535,
      "description": "TCP port for the control adapter."
    },
    "endpoints": {
      "$ref": "#/$defs/ControlEndpointsSpec"
    },
    "labels": {
      "type": "object",
      "additionalProperties": { "type": "string" },
      "description": "Free-form key-value labels for governance and catalog integration."
    }
  },
  "required": ["type", "port"],
  "unevaluatedProperties": false
}
```

### `ControlEndpointsSpec` — Granular Endpoint Toggle

```json
"ControlEndpointsSpec": {
  "type": "object",
  "description": "Toggle individual control port endpoint groups. All default to true when the control adapter is declared.",
  "properties": {
    "health": {
      "type": "boolean",
      "default": true,
      "description": "Enable /health/live and /health/ready endpoints."
    },
    "metrics": {
      "type": "boolean",
      "default": true,
      "description": "Enable /metrics (Prometheus scrape). Requires OTel observability to be active."
    },
    "info": {
      "type": "boolean",
      "default": true,
      "description": "Enable /info endpoint."
    },
    "spec": {
      "type": "boolean",
      "default": false,
      "description": "Enable /spec introspection endpoint. Disabled by default — contains resolved spec details."
    },
    "reload": {
      "type": "boolean",
      "default": false,
      "description": "Enable POST /config/reload. Disabled by default — mutates runtime state."
    },
    "governance": {
      "type": "boolean",
      "default": true,
      "description": "Enable /governance metadata endpoint."
    }
  },
  "unevaluatedProperties": false
}
```

### Update `ServerSpec` Discriminator

Add `control` to the `oneOf` in the `exposes` array:

```json
"exposes": {
  "type": "array",
  "items": {
    "oneOf": [
      { "$ref": "#/$defs/ExposesRest" },
      { "$ref": "#/$defs/ExposesMcp" },
      { "$ref": "#/$defs/ExposesSkill" },
      { "$ref": "#/$defs/ExposesControl" }
    ]
  }
}
```

### Validation Rules

Add to `naftiko-rules.yml`:

| Rule | Description |
|---|---|
| `control-port-singleton` | At most one `type: "control"` adapter per capability |
| `control-port-unique` | Control port must not conflict with any business adapter port |
| `control-address-localhost-warning` | Warn if `address` is not `localhost` or `127.0.0.1` (security) |

---

## Capability YAML Examples

### Minimal Control Port

```yaml
capability:
  info:
    name: weather-service
    specVersion: 1.0.0-alpha2
  consumes:
    - type: http
      namespace: weather-api
      baseUri: https://api.weather.gov
      operations:
        - id: get-forecast
          method: GET
          path: /points/{lat},{lon}/forecast
  exposes:
    - type: mcp
      namespace: weather
      port: 3000
      tools:
        - name: get-forecast
          description: Get weather forecast for a location.
          inputParameters:
            - name: lat
              type: number
              description: Latitude
              required: true
            - name: lon
              type: number
              description: Longitude
              required: true
          call: weather-api.get-forecast
          with:
            lat: "{{lat}}"
            lon: "{{lon}}"
    - type: control
      port: 9090
```

This gives you `/health/live`, `/health/ready`, `/metrics`, `/info`, and `/governance` on `:9090` with zero additional configuration.

### Full Control Port with All Options

```yaml
capability:
  info:
    name: payment-gateway
    specVersion: 1.0.0-alpha2
  exposes:
    - type: rest
      namespace: payments
      port: 8080
      resources:
        - path: /payments
          name: payments
          operations:
            - id: create-payment
              method: POST
              call: stripe-api.create-charge
              with:
                amount: "{{amount}}"
    - type: control
      port: 9090
      address: 0.0.0.0
      endpoints:
        health: true
        metrics: true
        info: true
        spec: true
        reload: true
        governance: true
      labels:
        team: payments
        data-classification: pci
        environment: production
```

### Control Port with Governance Labels Only

```yaml
  exposes:
    - type: control
      port: 9090
      endpoints:
        health: true
        metrics: false
        info: true
        spec: false
        reload: false
        governance: true
      labels:
        cost-center: engineering
        owner: platform-team
```

---

## Endpoint Catalog

### Development Endpoints

| Method | Path | Default | Description |
|---|---|---|---|
| `GET` | `/spec` | Disabled | Returns the resolved capability spec with secrets redacted. Useful for debugging ref resolution, bind substitution, and import merging. |
| `POST` | `/config/reload` | Disabled | Triggers a hot reload of the capability YAML. Returns a diff of applied changes or a rejection reason. |

**Future development endpoints** (not in this proposal):

| Method | Path | Description |
|---|---|---|
| `GET` | `/debug/threads` | Thread dump for deadlock diagnosis |
| `GET` | `/debug/steps/{operation-id}` | Step execution trace for a specific operation (dry run) |
| `POST` | `/debug/evaluate` | Evaluate a Mustache template against sample input (template playground) |
| `GET` | `/debug/binds` | List all `binds` references and their resolution status (values redacted) |

### Operations Endpoints

| Method | Path | Default | Description |
|---|---|---|---|
| `GET` | `/health/live` | Enabled | Liveness probe. Returns `200 {"status":"UP"}` if the process is alive. |
| `GET` | `/health/ready` | Enabled | Readiness probe. Returns `200` when all business adapters are started, `503` otherwise. |
| `GET` | `/metrics` | Enabled | Prometheus exposition format. Serves OTel-collected metrics (see [OpenTelemetry Observability](opentelemetry-observability.md) Phase 2). |
| `GET` | `/info` | Enabled | Returns capability name, spec version, engine version, uptime, and adapter summary. |

**Future operations endpoints**:

| Method | Path | Description |
|---|---|---|
| `GET` | `/health/dependencies` | Per-dependency health status (consumed API reachability) |
| `POST` | `/drain` | Graceful shutdown — stop accepting new requests, finish in-flight, then stop |
| `GET` | `/metrics/json` | Metrics in JSON format (for non-Prometheus consumers) |

### Governance Endpoints

| Method | Path | Default | Description |
|---|---|---|---|
| `GET` | `/governance` | Enabled | Returns structured metadata: adapters, dependencies, aggregates, labels. |

**Future governance endpoints**:

| Method | Path | Description |
|---|---|---|
| `GET` | `/governance/sbom` | Software Bill of Materials (dependency tree, license info) |
| `GET` | `/governance/policy` | Policy compliance status (evaluated against external policy engine) |
| `POST` | `/governance/audit` | Emit an audit event to the configured audit sink |

---

## Runtime Design

### Java Class Hierarchy

```
ServerAdapter (existing abstract base)
  ├── RestServerAdapter
  ├── McpServerAdapter
  ├── SkillServerAdapter
  └── ControlServerAdapter (new)
        ├── HealthRestlet          → /health/live, /health/ready
        ├── MetricsRestlet         → /metrics
        ├── InfoRestlet            → /info
        ├── SpecIntrospectionRestlet → /spec
        ├── ConfigReloadRestlet    → /config/reload
        └── GovernanceRestlet      → /governance
```

### `ControlServerAdapter`

```java
class ControlServerAdapter extends ServerAdapter {

    ControlServerAdapter(Capability capability, ControlServerSpec spec) {
        super(capability, spec);

        Router router = new Router(getContext());

        if (spec.getEndpoints().isHealth()) {
            router.attach("/health/live", new HealthLiveRestlet(capability));
            router.attach("/health/ready", new HealthReadyRestlet(capability));
        }
        if (spec.getEndpoints().isMetrics()) {
            router.attach("/metrics", new MetricsRestlet(capability));
        }
        if (spec.getEndpoints().isInfo()) {
            router.attach("/info", new InfoRestlet(capability));
        }
        if (spec.getEndpoints().isSpec()) {
            router.attach("/spec", new SpecIntrospectionRestlet(capability));
        }
        if (spec.getEndpoints().isReload()) {
            router.attach("/config/reload", new ConfigReloadRestlet(capability));
        }
        if (spec.getEndpoints().isGovernance()) {
            router.attach("/governance", new GovernanceRestlet(capability));
        }

        initServer(spec.getAddress(), spec.getPort(), router);
    }
}
```

### Health Check Implementation

```java
class HealthReadyRestlet extends Restlet {

    private final Capability capability;

    @Override
    public void handle(Request request, Response response) {
        boolean allAdaptersReady = capability.getServerAdapters().stream()
            .filter(a -> !(a instanceof ControlServerAdapter))
            .allMatch(ServerAdapter::isStarted);

        if (allAdaptersReady) {
            response.setStatus(Status.SUCCESS_OK);
            response.setEntity("{\"status\":\"UP\"}", MediaType.APPLICATION_JSON);
        } else {
            response.setStatus(Status.SERVER_ERROR_SERVICE_UNAVAILABLE);
            response.setEntity("{\"status\":\"DEGRADED\"}", MediaType.APPLICATION_JSON);
        }
    }
}
```

### Prometheus Metrics Integration

The `/metrics` endpoint on the control port **replaces** the standalone Prometheus exporter server proposed in [OpenTelemetry Observability Phase 2](opentelemetry-observability.md#phase-2--metrics). Instead of OTel spinning up its own HTTP server on a separate port, the control adapter hosts the Prometheus scrape endpoint directly:

```java
class MetricsRestlet extends Restlet {

    private final PrometheusHttpServer prometheusServer; // OTel Prometheus bridge

    @Override
    public void handle(Request request, Response response) {
        // Delegate to OTel's Prometheus exposition format writer
        String metrics = prometheusServer.collectMetrics();
        response.setStatus(Status.SUCCESS_OK);
        response.setEntity(metrics, MediaType.TEXT_PLAIN);
    }
}
```

This consolidation means:
- **One management port** instead of two (no separate `:9464` for Prometheus)
- Prometheus scrape config points to `<host>:<control-port>/metrics`
- The OTel SDK still records metrics via `Meter`; the control port simply serves them

### Info Endpoint Response

```json
{
  "capability": {
    "name": "weather-service",
    "specVersion": "1.0.0-alpha2"
  },
  "engine": {
    "version": "1.0.0-alpha2",
    "java": "21.0.3",
    "nativeImage": false
  },
  "uptime": "PT2H34M12S",
  "startedAt": "2026-04-16T10:00:00Z",
  "adapters": [
    { "type": "mcp", "port": 3000, "status": "started" },
    { "type": "control", "port": 9090, "status": "started" }
  ]
}
```

### Config Reload Flow

```
POST /config/reload
       │
       ▼
┌─────────────────┐     ┌──────────────┐     ┌─────────────┐
│ Read YAML from  │────►│ Validate vs  │────►│ Diff against │
│ original path   │     │ JSON Schema  │     │ current spec │
└─────────────────┘     │ + Rules      │     └──────┬───────┘
                        └──────────────┘            │
                                              ┌─────┴──────┐
                                              │ Structural │
                                         ┌────┤ change?    ├────┐
                                         │ No └────────────┘Yes │
                                         ▼                      ▼
                                ┌────────────────┐    ┌──────────────┐
                                │ Atomic swap    │    │ Reject with  │
                                │ spec + rebuild │    │ reason       │
                                │ step executors │    └──────────────┘
                                └────────────────┘
                                         │
                                         ▼
                              { "status": "applied",
                                "changes": [...] }
```

---

## Relationship with OpenTelemetry Observability Blueprint

The control port absorbs and refines specific proposals from the [OpenTelemetry Observability](opentelemetry-observability.md) blueprint:

| OTel Blueprint Item | Control Port Approach |
|---|---|
| **Phase 2 — Prometheus `/metrics` endpoint** (standalone OTel HTTP server on `:9464`) | **Absorbed** — Prometheus metrics are served by the control adapter on `/metrics`. No standalone OTel metrics server needed. |
| **Phase 3 — `observability.metrics.port`** (schema field for Prometheus port) | **Superseded** — The control adapter's `port` determines where `/metrics` is served. No separate metrics port config. |
| **Phase 1 — Tracing, Phase 0 — Logging** | **Unchanged** — These phases are independent of the control port. The OTel SDK records spans and logs regardless of whether a control adapter is declared. |
| **Phase 3 — `observability` YAML block** | **Complementary** — The `observability` block configures OTel SDK behavior (sampling, exporters). The control port configures *where* the engine-side endpoints are served. |

### When No Control Port Is Declared

If a capability does not declare `type: "control"`:
- Health, info, and governance endpoints are **not available** (no management surface).
- Prometheus metrics fall back to OTel's default standalone exporter (if configured via `OTEL_EXPORTER_PROMETHEUS_PORT`).
- Config reload is not available (restart required).

This preserves backward compatibility — existing capabilities work unchanged.

---

## Security Considerations

### Default-Secure Posture

1. **`address` defaults to `localhost`** — the control port is not network-accessible unless explicitly configured with `0.0.0.0` or a specific interface.
2. **Sensitive endpoints disabled by default** — `/spec` and `/config/reload` are opt-in. Authors must explicitly enable them.
3. **Secret redaction** — `/spec` replaces all `binds`-referenced values with `"***"`. No raw credentials are ever returned.
4. **No write operations on governance** — `/governance` is read-only. Labels are set in YAML, not via the API.

### Phase 2: Authentication (Future)

A later phase adds optional authentication to the control port:

```yaml
  - type: control
    port: 9090
    authentication:
      type: bearer
      token:
        externalRef: CONTROL_TOKEN
```

This reuses the existing `authentication` model from REST adapters.

### Network Policy Recommendations

For Kubernetes deployments:

```yaml
# NetworkPolicy: only allow Prometheus and kubelet to reach the control port
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: control-port-access
spec:
  podSelector:
    matchLabels:
      app: weather-service
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              name: monitoring
      ports:
        - port: 9090
```

---

## Testing Strategy

### Unit Tests

| Test Class | Package | Purpose |
|---|---|---|
| `HealthLiveRestletTest` | `io.naftiko.engine.exposes.control` | Returns 200 when capability is running |
| `HealthReadyRestletTest` | `io.naftiko.engine.exposes.control` | Returns 200 when all adapters started, 503 when degraded |
| `InfoRestletTest` | `io.naftiko.engine.exposes.control` | Returns correct capability metadata and uptime |
| `GovernanceRestletTest` | `io.naftiko.engine.exposes.control` | Returns dependency inventory, adapter list, labels |
| `SpecIntrospectionRestletTest` | `io.naftiko.engine.exposes.control` | Returns resolved spec with secrets redacted |
| `ConfigReloadRestletTest` | `io.naftiko.engine.exposes.control` | Accepts safe changes, rejects structural changes |
| `ControlServerSpecTest` | `io.naftiko.spec.exposes` | Deserialization of `ExposesControl` from YAML |

### Integration Tests

| Test Class | Package | Purpose |
|---|---|---|
| `ControlPortIntegrationTest` | `io.naftiko.engine.exposes.control` | Full lifecycle: start capability with control port, hit all endpoints, verify responses |
| `ControlPortMetricsIntegrationTest` | `io.naftiko.engine.exposes.control` | Prometheus `/metrics` returns OTel-recorded metrics |
| `ControlPortReloadIntegrationTest` | `io.naftiko.engine.exposes.control` | Hot reload: modify YAML, call `/config/reload`, verify new spec is active |
| `ControlPortSingletonRuleTest` | `io.naftiko.engine.exposes.control` | Reject capabilities declaring more than one control adapter |

### Test Fixtures

| Fixture | Location | Description |
|---|---|---|
| `control-port-capability.yaml` | `src/test/resources/` | Capability with all control endpoints enabled |
| `control-port-minimal-capability.yaml` | `src/test/resources/` | Capability with default control port (no `endpoints` block) |
| `control-port-reload-before.yaml` | `src/test/resources/` | Original spec for hot reload test |
| `control-port-reload-after.yaml` | `src/test/resources/` | Modified spec for hot reload test (safe changes) |
| `control-port-reload-structural.yaml` | `src/test/resources/` | Modified spec with structural changes (should be rejected) |

---

## Implementation Roadmap

### Phase 1 — Health and Info (Foundation)

| Task | Component | Description |
|---|---|---|
| 1.1 | Schema | Add `ExposesControl` and `ControlEndpointsSpec` to `naftiko-schema.json` |
| 1.2 | Spec classes | Create `ControlServerSpec`, `ControlEndpointsSpec` in `io.naftiko.spec.exposes` |
| 1.3 | Discriminator | Add `control` to `ServerSpec` Jackson subtypes and schema `oneOf` |
| 1.4 | Adapter | Implement `ControlServerAdapter` extending `ServerAdapter` |
| 1.5 | Health | Implement `HealthLiveRestlet` and `HealthReadyRestlet` |
| 1.6 | Info | Implement `InfoRestlet` with capability metadata and uptime |
| 1.7 | Rules | Add `control-port-singleton` and `control-port-unique` validation rules |
| 1.8 | Tests | Unit + integration tests for health and info endpoints |

### Phase 2 — Metrics and Governance

| Task | Component | Description |
|---|---|---|
| 2.1 | Metrics | Implement `MetricsRestlet` bridging OTel Prometheus exporter |
| 2.2 | Governance | Implement `GovernanceRestlet` with adapter/dependency/label summary |
| 2.3 | Labels | Wire `labels` from spec into governance response |
| 2.4 | OTel integration | Update [OpenTelemetry Observability](opentelemetry-observability.md) Phase 2 to use control port instead of standalone Prometheus server |
| 2.5 | Tests | Unit + integration tests for metrics and governance endpoints |

### Phase 3 — Development Endpoints

| Task | Component | Description |
|---|---|---|
| 3.1 | Spec introspection | Implement `SpecIntrospectionRestlet` with secret redaction |
| 3.2 | Config reload | Implement `ConfigReloadRestlet` with validation, diffing, and atomic swap |
| 3.3 | Reload safety | Implement structural change detection and rejection logic |
| 3.4 | Tests | Unit + integration tests for introspection and reload |

### Phase 4 — Authentication and Advanced Endpoints

| Task | Component | Description |
|---|---|---|
| 4.1 | Authentication | Add optional `authentication` support to `ExposesControl` |
| 4.2 | Dependency health | Implement `/health/dependencies` with per-consumed-API status |
| 4.3 | Graceful drain | Implement `POST /drain` for graceful shutdown |
| 4.4 | Debug endpoints | Implement `/debug/threads`, `/debug/binds` |
| 4.5 | Tests | Full test coverage for authenticated and advanced endpoints |

### Implementation Order and Rationale

| Order | Phase | Effort | Value | Rationale |
|---|---|---|---|---|
| 1st | **Phase 1** (Health + Info) | Low | **Highest** | Kubernetes probe integration is the most immediate operational need; foundation for all other phases |
| 2nd | **Phase 2** (Metrics + Governance) | Medium | High | Prometheus scrape consolidation + platform catalog integration |
| 3rd | **Phase 3** (Dev endpoints) | Medium | Medium | Config reload and spec introspection improve the development loop |
| 4th | **Phase 4** (Auth + Advanced) | Medium | Medium | Hardening for production environments |

---

## Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **Config reload causes inconsistent state** | Medium | High | Atomic swap with full validation before apply; reject structural changes |
| **Control port exposed to internet** | Medium | High | Default `address: localhost`; warn when `0.0.0.0`; auth in Phase 4 |
| **Spec introspection leaks secrets** | Low | High | All `binds`-referenced values redacted; endpoint disabled by default |
| **Port conflict with business adapters** | Low | Low | Schema validation rule rejects duplicate ports |
| **Metrics endpoint unavailable without OTel** | Low | Low | Return `503` with message when OTel SDK is not initialized |
| **Health probe returns false positive** | Low | Medium | Readiness checks actual adapter `isStarted()` state, not just port binding |
| **Hot reload complexity grows with engine features** | Medium | Medium | Clearly define safe vs structural changes; reject unknown diffs |

---

## Acceptance Criteria

### Phase 1 — Health and Info

1. `ExposesControl` is accepted in capability YAML and deserialized correctly.
2. At most one `type: "control"` adapter is allowed per capability (validated by rule).
3. Control port binds to `localhost` by default; explicit `address` overrides it.
4. `GET /health/live` returns `200 {"status":"UP"}` when the capability is running.
5. `GET /health/ready` returns `200` when all business adapters are started, `503` when any is not.
6. `GET /info` returns capability name, spec version, engine version, uptime, and adapter summary.
7. All existing tests pass — zero regressions.

### Phase 2 — Metrics and Governance

1. `GET /metrics` returns Prometheus exposition format with OTel-recorded metrics.
2. `GET /governance` returns adapter list, dependency inventory, aggregate summary, and labels.
3. Labels declared in YAML appear in the governance response.
4. When OTel is not active, `/metrics` returns `503` with an explanatory message.

### Phase 3 — Development Endpoints

1. `GET /spec` returns the resolved capability spec with all `binds` values replaced by `"***"`.
2. `POST /config/reload` re-reads, validates, diffs, and applies safe changes.
3. Structural changes (add/remove adapter, change port) are rejected with a descriptive reason.
4. After a successful reload, subsequent requests use the new spec.
5. Both endpoints are disabled by default and require explicit `endpoints.spec: true` / `endpoints.reload: true`.

### Phase 4 — Authentication and Advanced

1. Optional `authentication` on the control port restricts access to authorized callers.
2. `GET /health/dependencies` returns per-consumed-API reachability status.
3. `POST /drain` stops accepting new requests and shuts down after in-flight requests complete.
4. Debug endpoints return diagnostic information with secrets redacted.
