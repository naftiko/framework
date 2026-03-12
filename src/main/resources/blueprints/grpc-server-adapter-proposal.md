# gRPC Procedures Jetty Embedded Proposal
## Adding a gRPC Server Adapter with Procedure Semantics

**Status**: Proposal  
**Date**: March 11, 2026  
**Key Concept**: Add a new `grpc` exposed server adapter implemented on embedded Jetty only (no Servlet dependency), where gRPC exposes **procedures** that keep the same declarative structure and execution model as MCP **tools**.

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Goals and Non-Goals](#goals-and-non-goals)
3. [Terminology](#terminology)
4. [Design Analogy](#design-analogy)
5. [Architecture Overview](#architecture-overview)
6. [Specification and Schema Changes](#specification-and-schema-changes)
7. [Capability YAML Example](#capability-yaml-example)
8. [Runtime Design](#runtime-design)
9. [Jetty-Only Transport Design](#jetty-only-transport-design)
10. [Dependency Constraints](#dependency-constraints)
11. [gRPC Status Code Mapping](#grpc-status-code-mapping)
12. [Testing Strategy](#testing-strategy)
13. [Implementation Roadmap](#implementation-roadmap)
14. [Risks and Mitigations](#risks-and-mitigations)
15. [Acceptance Criteria](#acceptance-criteria)

---

## Executive Summary

### What This Proposes

Add a new `type: grpc` server adapter in `capability.exposes` with these principles:

1. **Jetty embedded only** for runtime hosting.
2. **Zero Servlet dependency** in compile/runtime classpath.
3. gRPC surface is modeled as **procedures**.
4. Procedure shape and orchestration are **identical to MCP tools** (`call`, `with`, `steps`, `outputParameters`).

### Why This Fits Naftiko

Naftiko already separates:

- Transport/adapter concerns (API, MCP)
- Declarative orchestration (`call` or `steps`)
- Output mapping (`outputParameters`)

This proposal extends that model without introducing a second orchestration paradigm.

### Value

- Adds first-class gRPC exposure for typed service ecosystems.
- Preserves existing capability authoring mental model from MCP tools.
- Keeps runtime footprint coherent with current Jetty usage.

---

## Goals and Non-Goals

### Goals

1. Introduce `grpc` as a new exposed adapter type.
2. Define `procedures` as the gRPC equivalent of MCP `tools`.
3. Reuse existing execution engine (`OperationStepExecutor`) for procedure execution.
4. Support unary RPC in first iteration.
5. Keep runtime strictly Jetty embedded, with no Servlet artifacts.

### Non-Goals (Phase 1)

1. Bidirectional/client/server streaming RPC.
2. Proto code generation from capability files.
3. TLS/mTLS configuration — phase 1 uses h2c (cleartext HTTP/2); TLS is phase 2.
4. Replacing MCP tool model; this is additive.
5. gRPC server reflection API — dynamic procedures with `application/grpc+json` codec make standard reflection impossible to implement. Clients must know the service contract.

---

## Design Analogy

### How adapters map to their primary constructs

```
API Adapter                  MCP Adapter                   gRPC Adapter (proposed)
───────────                  ───────────                   ───────────────────────
ExposesApi                   ExposesMcp                    ExposesGrpc
├─ namespace                 ├─ namespace                  ├─ namespace
├─ port                      ├─ port (http transport)      ├─ port
├─ address                   ├─ address                    ├─ address
│                            ├─ description                ├─ package
│                            │                             ├─ service
├─ resources[]               ├─ tools[]                    └─ procedures[]
│  ├─ path                   │  ├─ name                        ├─ name
│  └─ operations[]           │  ├─ label                       ├─ label
│     ├─ method              │  ├─ description                 ├─ description
│     ├─ name                │  ├─ inputParameters[]           ├─ inputParameters[]
│     ├─ label               │  ├─ call / steps                ├─ call / steps
│     ├─ description         │  ├─ with                        ├─ with
│     ├─ inputParameters[]   │  └─ outputParameters[]          └─ outputParameters[]
│     ├─ call / steps
│     └─ outputParameters[]
```

### Concept-level mapping across adapter types

| Concept | API Adapter | MCP Adapter | gRPC Adapter |
|---|---|---|---|
| **Entry point** | resource path | tool name | procedure name |
| **Method grouping** | `resources[]` → `operations[]` | `tools[]` | `procedures[]` |
| **Input** | HTTP query / path / header / body | JSON arguments map | gRPC request payload (JSON codec) |
| **Output** | HTTP response body | `CallToolResult` | gRPC response payload (JSON codec) |
| **Simple dispatch** | `call: namespace.op` | `call: namespace.op` | `call: namespace.op` |
| **Orchestration** | `steps[]` | `steps[]` | `steps[]` |
| **Static injection** | `with` | `with` | `with` |
| **Output mapping** | `outputParameters` | `outputParameters` | `outputParameters` |
| **Transport** | Restlet/Jetty HTTP/1.1 | Jetty HTTP/1.1 (MCP JSON-RPC) | Jetty HTTP/2 h2c (gRPC unary) |
| **Execution engine** | `OperationStepExecutor` | `OperationStepExecutor` | `OperationStepExecutor` |

---

## Terminology

- **MCP**: exposes `tools`
- **gRPC**: exposes `procedures`
- **Structure parity**: a gRPC procedure has the same declarative fields and execution rules as an MCP tool.

Field-level parity:

- `name`
- `label`
- `description`
- `inputParameters`
- `call`
- `with`
- `steps`
- `outputParameters`

---

## Architecture Overview

### Current

- `ServerSpec` supports `api` and `mcp` via `@JsonSubTypes`.
- `Capability` creates `ApiServerAdapter` or `McpServerAdapter` by type string.
- MCP tools are executed through transport-agnostic logic in `McpToolHandler` using `OperationStepExecutor`.
- Jetty runs HTTP/1.1 only (pom has `jetty-http2-common` but no `jetty-http2-server`).

### Proposed

- Extend `ServerSpec` polymorphism with `GrpcServerSpec` (`type: grpc`).
- Extend `Capability` adapter dispatch with `GrpcServerAdapter`.
- Add `GrpcProcedureHandler` that mirrors `McpToolHandler` behavior, delegating to same `OperationStepExecutor`.
- Add `JettyGrpcUnaryHandler extends Handler.Abstract` for gRPC transport — no Servlet.
- Extend Jetty setup with `HTTP2CServerConnectionFactory` for h2c (cleartext HTTP/2).

### Execution Flow

```
Jetty (HTTP/2 h2c)
  └── JettyGrpcUnaryHandler
        1. Verify Content-Type: application/grpc+json
        2. Decode gRPC frame (1-byte flag + 4-byte length + body)
        3. Deserialize JSON body → Map<String, Object>
        4. Route /{package}.{service}/{procedure} → GrpcProcedureHandler
              └── OperationStepExecutor
                    ├── call mode  → HttpClientAdapter
                    └── steps mode → HttpClientAdapter (repeated)
        5. Apply outputParameters mappings
        6. Serialize result → JSON → gRPC frame
        7. Write response + grpc-status trailer
```

---

## Specification and Schema Changes

### New Exposes Type

Add `ExposesGrpc` to the main schema `capability.exposes` oneOf list.

### ExposesGrpc Fields

| Field | Required | Description |
|---|---|---|
| `type` | yes | Constant `"grpc"` |
| `address` | no | Host/address (default `localhost`) |
| `port` | yes | TCP port |
| `namespace` | yes | Unique identifier for this adapter |
| `package` | no | gRPC proto package name. When set, endpoint is `/{package}.{service}/{procedure}`; when absent, endpoint is `/{service}/{procedure}` |
| `service` | yes | gRPC service name |
| `procedures` | yes | Array of procedure definitions (min 1) |

### GrpcProcedure Definition

Define `GrpcProcedure` with the same structure and validation behavior as `McpTool`:

- Requires `name` and `description`
- Supports either:
  - simple mode: `call` (+ optional `with` and mapped `outputParameters`)
  - orchestration mode: `steps` (+ optional `with` and orchestrated `outputParameters`)

### Documentation

Update the Naftiko specification docs with:

1. New gRPC Expose section.
2. Terminology distinction: MCP tools vs gRPC procedures.
3. Explicit runtime rule: embedded Jetty only, zero Servlet dependency.

---

## Capability YAML Example

```yaml
# yaml-language-server: $schema=../../main/resources/schemas/capability-schema.json
---
naftiko: "0.5"
info:
  label: "Order Service gRPC Capability"
  description: "Exposes order management operations as a gRPC service backed by a REST API."
  tags:
    - orders
    - grpc
  created: "2026-03-11"
  modified: "2026-03-11"

capability:
  exposes:
    - type: "grpc"
      address: "localhost"
      port: 50051
      namespace: "orders-grpc"
      package: "io.example.orders"
      service: "OrderService"

      procedures:
        # Simple call mode
        - name: "GetOrder"
          description: "Retrieve a single order by its identifier."
          inputParameters:
            - name: "order_id"
              type: "string"
              description: "Unique identifier of the order."
          call: "orders-api.get-order"
          with:
            id: "{{order_id}}"
          outputParameters:
            - name: "id"
              type: "string"
              mapping: "$.id"
            - name: "status"
              type: "string"
              mapping: "$.status"
            - name: "total"
              type: "number"
              mapping: "$.amount.total"

        # Orchestrated steps mode
        - name: "GetOrderWithCustomer"
          description: "Retrieve an order together with its associated customer details."
          inputParameters:
            - name: "order_id"
              type: "string"
              description: "Unique identifier of the order."
          steps:
            - type: "call"
              name: "fetch-order"
              call: "orders-api.get-order"
              with:
                id: "{{order_id}}"
            - type: "call"
              name: "fetch-customer"
              call: "customers-api.get-customer"
              with:
                customer_id: "{{$.fetch-order.customerId}}"
          outputParameters:
            - name: "order_id"
              type: "string"
              mapping: "{{$.fetch-order.id}}"
            - name: "customer_name"
              type: "string"
              mapping: "{{$.fetch-customer.name}}"

  consumes:
    - type: "http"
      namespace: "orders-api"
      baseUri: "https://api.example.com/v1/"
      resources:
        - path: "orders/{{id}}"
          name: "order"
          label: "Single Order"
          operations:
            - method: "GET"
              name: "get-order"
              label: "Get Order"
              inputParameters:
                - name: "id"
                  in: "path"

    - type: "http"
      namespace: "customers-api"
      baseUri: "https://api.example.com/v1/"
      resources:
        - path: "customers/{{customer_id}}"
          name: "customer"
          label: "Single Customer"
          operations:
            - method: "GET"
              name: "get-customer"
              label: "Get Customer"
              inputParameters:
                - name: "customer_id"
                  in: "path"
```

The gRPC endpoint for `GetOrder` resolves to `POST /io.example.orders.OrderService/GetOrder` over HTTP/2 h2c.

---

## Runtime Design

### New Spec Classes (`io.naftiko.spec.exposes`)

1. `GrpcServerSpec extends ServerSpec` — `type: grpc`, adds `package`, `service`, `procedures`
2. `GrpcServerProcedureSpec` — mirrors `McpServerToolSpec` field-for-field

### New Engine Classes (`io.naftiko.engine.exposes`)

1. `GrpcServerAdapter extends ServerAdapter` — owns Jetty lifecycle, mirrors `McpServerAdapter`
2. `GrpcProcedureHandler` — mirrors `McpToolHandler`; resolves input map → `OperationStepExecutor` → output map
3. `JettyGrpcUnaryHandler extends Handler.Abstract` — gRPC framing, routing, codec

### Input Parameter Mapping

gRPC request body is a flat or nested JSON object decoded from the gRPC frame. It is converted to `Map<String, Object>` and merged with the procedure's `with` map, exactly as MCP tool arguments are merged.

Procedure `inputParameters` provide the names and types for documentation and schema validation, not for extraction — matching the MCP tools convention.

---

## Jetty-Only Transport Design

### Hard Constraint

Transport implementation must use only embedded Jetty APIs, following the `Handler.Abstract` pattern established by `JettyMcpStreamableHandler`. No Servlet container APIs.

### Protocol Scope (Phase 1)

Unary gRPC over HTTP/2 **h2c** (cleartext HTTP/2) only. This means:
- No TLS required for phase 1 (suitable for container-to-container, local, and intra-cluster use).
- `HTTP2CServerConnectionFactory` upgrades plain TCP connections to HTTP/2.
- TLS (`h2`) and ALPN negotiation are explicitly deferred to phase 2.

### Endpoint Pattern

- With `package` set: `POST /{package}.{service}/{procedure}`
- Without `package`: `POST /{service}/{procedure}`

### Codec Strategy

Use `application/grpc+json` as the content type for all phase 1 communication. This:
- Avoids all protobuf schema generation and `Struct` encoding complexity.
- Maps naturally to Naftiko's JSON-native parameter model and Jackson-based output mappings.
- Is a valid gRPC codec per the gRPC HTTP/2 specification.

The `protobuf-java` dependency remains for gRPC message framing utilities only, not for typed message schema.

### gRPC Framing Responsibilities

`JettyGrpcUnaryHandler` handles:

1. Validate `Content-Type: application/grpc+json`.
2. Read body — decode gRPC frame: 1-byte compression flag (must be `0x00`) + 4-byte big-endian length + payload bytes.
3. Deserialize JSON payload bytes → `Map<String, Object>` via Jackson.
4. Route to target `GrpcProcedureHandler` by path.
5. Execute procedure and collect result.
6. Serialize result map → JSON bytes → gRPC frame (compression flag `0x00` + length + bytes).
7. Write HTTP/2 response with `Content-Type: application/grpc+json`.
8. Write HTTP/2 trailers: `grpc-status: {code}` (and `grpc-message` if error).

---

## Dependency Constraints

### Current State (from `pom.xml`)

- `jetty-http2-common` — already present (shared HTTP/2 data structures).
- `jetty-server` — already present.

### Required Additions

| Artifact | Group | Reason |
|---|---|---|
| `jetty-http2-server` | `org.eclipse.jetty.http2` | HTTP/2 server connection factory (h2c). Required — `jetty-http2-common` alone does NOT provide server-side HTTP/2. |

No other new dependencies are required for phase 1 (h2c with JSON codec).

### Forbidden

- Any Servlet API — `javax.servlet`, `jakarta.servlet`.
- `grpc-servlet` artifacts.
- `grpc-netty` or `grpc-netty-shaded` — Netty is not in this runtime.
- Proto codegen plugins (`protoc`, `protobuf-maven-plugin`) — phase 1 does not require generated types.

### Build Guardrail

Add a CI step using `mvn dependency:tree` assertion or `mvn enforcer:enforce` with `bannedDependencies` to fail the build if any forbidden artifact is detected in the compile/runtime dependency tree.

---

## gRPC Status Code Mapping

A deterministic mapping from internal execution states to gRPC status codes:

| Internal Condition | gRPC Status Code | Notes |
|---|---|---|
| Procedure not found (unknown name) | `NOT_FOUND (5)` | Unknown procedure in path |
| Input validation failure | `INVALID_ARGUMENT (3)` | Missing required input parameters |
| Upstream HTTP 4xx | `FAILED_PRECONDITION (9)` | Upstream returned a client error |
| Upstream HTTP 401/403 | `PERMISSION_DENIED (7)` | Auth failure on consumed endpoint |
| Upstream HTTP 404 | `NOT_FOUND (5)` | Consumed resource not found |
| Upstream HTTP 5xx | `UNAVAILABLE (14)` | Upstream server error |
| Upstream connection refused / timeout | `UNAVAILABLE (14)` | Network or availability issue |
| Output mapping failure | `INTERNAL (13)` | JSONPath or mapping error |
| Unknown internal error | `INTERNAL (13)` | Catch-all for unexpected exceptions |
| Success | `OK (0)` | |

All non-OK responses populate the `grpc-message` trailer with the exception message.

---

## Testing Strategy

### Contract and Wiring Tests

1. YAML deserialization of `type: grpc` expose.
2. `ServerSpec` subtype dispatch correctness.
3. `Capability` creates `GrpcServerAdapter`.

### Runtime Tests

1. Adapter start/stop lifecycle on configured port.
2. Unary procedure invocation in `call` mode.
3. Unary procedure invocation in `steps` mode.
4. Output mapping parity with MCP tool behavior.
5. Error-to-gRPC-status mapping.

### Schema Tests

1. Valid `ExposesGrpc` sample passes.
2. Invalid structures fail (missing procedures, invalid call/steps combinations, etc.).

---

## Implementation Roadmap

### Milestone 1: Contract Layer

**Goal**: A capability with `type: grpc` deserializes, instantiates the correct adapter, and passes schema validation.

| # | Task | File(s) |
|---|---|---|
| 1.1 | Add `GrpcServerSpec extends ServerSpec` with `package`, `service`, `procedures` fields | `spec/exposes/GrpcServerSpec.java` |
| 1.2 | Add `GrpcServerProcedureSpec` mirroring `McpServerToolSpec` | `spec/exposes/GrpcServerProcedureSpec.java` |
| 1.3 | Register `@JsonSubTypes.Type(value = GrpcServerSpec.class, name = "grpc")` | `spec/exposes/ServerSpec.java` |
| 1.4 | Add `else if ("grpc"...)` branch in adapter dispatch | `Capability.java` |
| 1.5 | Add skeleton `GrpcServerAdapter` with no-op `start()`/`stop()` | `engine/exposes/GrpcServerAdapter.java` |
| 1.6 | Add `grpc-capability.yaml` test fixture | `src/test/resources/grpc-capability.yaml` |
| 1.7 | Add deserialization + wiring integration tests | `CapabilityGrpcIntegrationTest.java` |

**Acceptance**: `Capability` loads the fixture, `serverAdapters.get(0)` is `GrpcServerAdapter`, spec fields are correct.

### Milestone 2: Schema and Docs

**Goal**: Valid `type: grpc` capability passes schema; invalid shapes are rejected; spec docs are updated.

| # | Task | File(s) |
|---|---|---|
| 2.1 | Add `ExposesGrpc` definition to `exposes` oneOf | `schemas/naftiko-schema.json` |
| 2.2 | Add `GrpcProcedure` definition (mirrors `McpTool` rules) | `schemas/naftiko-schema.json` |
| 2.3 | Add `GrpcProcedureInputParameter` definition | `schemas/naftiko-schema.json` |
| 2.4 | Add gRPC Expose section to specification docs | `wiki/Specification.md` |

**Acceptance**: Schema validator accepts the sample YAML; missing `procedures` or wrong `call`/`steps` patterns are rejected.

### Milestone 3: Procedure Execution

**Goal**: Procedures execute with identical semantics to MCP tools (call mode and steps mode).

| # | Task | File(s) |
|---|---|---|
| 3.1 | Implement `GrpcProcedureHandler` delegating to `OperationStepExecutor` | `engine/exposes/GrpcProcedureHandler.java` |
| 3.2 | Wire handler into `GrpcServerAdapter` constructor | `engine/exposes/GrpcServerAdapter.java` |
| 3.3 | Add unit tests: call mode, steps mode, output mapping, error path | `CapabilityGrpcIntegrationTest.java` |

**Acceptance**: Procedure handler test results match equivalent MCP tool handler test results for same inputs/outputs.

### Milestone 4: Jetty Unary gRPC Transport

**Goal**: Unary gRPC calls over h2c succeed end-to-end with correct framing, routing, and status/trailer handling.

| # | Task | File(s) |
|---|---|---|
| 4.1 | Add `jetty-http2-server` dependency | `pom.xml` |
| 4.2 | Implement `JettyGrpcUnaryHandler extends Handler.Abstract` — framing, JSON codec, routing | `engine/exposes/JettyGrpcUnaryHandler.java` |
| 4.3 | Configure `HTTP2CServerConnectionFactory` in `GrpcServerAdapter.initHttpTransport()` | `engine/exposes/GrpcServerAdapter.java` |
| 4.4 | Implement gRPC status/trailer mapping from `GrpcProcedureHandler` result | `engine/exposes/JettyGrpcUnaryHandler.java` |
| 4.5 | Add lifecycle integration test: start, invoke, stop | `CapabilityGrpcIntegrationTest.java` |

**Acceptance**: `grpcurl` (or equivalent test client with h2c + JSON) can call a procedure and receive a valid response with `grpc-status: 0`.

### Milestone 5: Hardening

**Goal**: Production-appropriate error handling, CI guardrails, and full test coverage.

| # | Task | File(s) |
|---|---|---|
| 5.1 | Add Maven Enforcer `bannedDependencies` rule for Servlet/grpc-servlet | `pom.xml` |
| 5.2 | Implement full gRPC status code mapping table | `JettyGrpcUnaryHandler.java` |
| 5.3 | Add idle timeout configuration (aligned with MCP adapter pattern) | `GrpcServerAdapter.java` |
| 5.4 | Schema test coverage for `ExposesGrpc` | existing schema test suite |

---

## Risks and Mitigations

1. **`jetty-http2-server` introduces new HTTP/2 framing complexity**
   - The current codebase has `jetty-http2-common` but no server-side HTTP/2 setup (`HTTP2CServerConnectionFactory`). This is new ground.
   - Mitigation: isolate HTTP/2 connector setup in `GrpcServerAdapter.initHttpTransport()`, keep h2c only in phase 1 to avoid ALPN/TLS complexity.

2. **gRPC framing is not abstracted by Jetty**
   - Jetty handles HTTP/2 frames, but gRPC message framing (compression flag + 4-byte length) is application-level and must be implemented manually.
   - Mitigation: isolate framing in a single utility class; cover with byte-level unit tests before wiring to Jetty.

3. **Semantic drift from MCP tools**
   - Mitigation: add cross-path parity tests comparing `McpToolHandler` and `GrpcProcedureHandler` results for identical call/steps/output configurations.

4. **Unintended Servlet transitive dependency**
   - Some Jetty companion modules pull in Servlet APIs as transitive dependencies.
   - Mitigation: explicit `<exclusion>` blocks in pom.xml for Servlet artifacts; `mvn enforcer` rule to fail fast.

5. **Client tooling for `application/grpc+json`**
   - Standard `grpcurl` defaults to `application/grpc+proto`. JSON codec mode requires explicit flags (`-format json`).
   - Mitigation: document this in setup guide; provide a minimal test script using `grpcurl --format json`.

6. **`package` field being optional introduces path ambiguity**
   - If two services share the same name in different packages, and package is omitted, routing collides.
   - Mitigation: enforce namespace uniqueness in schema validation; recommend always setting `package` in production.

---

## Acceptance Criteria

1. Capability spec supports `type: grpc` with `procedures`.
2. Procedures use the same declarative structure and execution semantics as MCP tools.
3. Runtime uses embedded Jetty only.
4. No Servlet dependency is present in compile or runtime dependencies.
5. Unary gRPC calls execute successfully for both `call` and `steps` procedure modes.
6. Schema, docs, and tests are updated and aligned.
