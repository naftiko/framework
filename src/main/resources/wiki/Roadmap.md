## Version 1.0 - Second Alpha - End of April :deciduous_tree:

The goal of this version is to solidify the MVP to enable common AI integration use cases and grow our community.

### Rightsize AI context
- [x] Add mocking feature to MCP server adapter similar to REST server adapter
- [x] Add tool annotations (readOnly, destructive, idempotent, openWorld)
- [x] Add support for authentication in the MCP server adapter

### Enable API reusability
- [x] Add HTML and Markdown data format support for HTTP consumption
- [x] Add interoperability with OpenAPI Specification
  - [x] Import OAS into an HTTP "consumes" adapter
  - [x] Export OAS from a REST "exposes" adapter

### Core developer experience
- [x] Factorize capability core with functions initially, entities and events later
- [x] Enhance traceability and debuggability of engine (support Open Telemetry)
- [x] Provide Control port usable via REST clients, Naftiko CLI
- [ ] Use named objects for input and output parameters, like for properties, matching the JSON Structure syntax
- [ ] Support inline scripting steps (Python, JavaScript, Groovy initially)

### Packaging
- [ ] Publish Naftiko JSON Structure
- [ ] Publish Naftiko Skill based on Naftiko CLI
- [ ] Publish Naftiko Ruleset based on Spectral
- [ ] Publish Maven Artifacts to [Maven Central](https://central.sonatype.com/)
- [ ] Publish Javadocs to [Javadoc.io](https://javadoc.io)
- [ ] Publish Docker Image to [Docker Hub](https://hub.docker.com/)

## Version 1.0 - Third Alpha - End of May :deciduous_tree:

### Rightsize AI context
- [ ] Facilitate integration with MCP and AI gateways
  - [ ] MCP trust propagation

### Enable API reusability
- [ ] Facilitate integration with API gateways
  - [ ] CORS configuration for API developer portals
  - [ ] Enable token refresh flows for consumed APIs
  - [ ] Gateway context propagation via Open Telemetry
  - [ ] mTLS client certificates on consumes
- [ ] Support HTTP cache control directives
- [ ] Enable pagination at consumes and exposes level

### Enable agent orchestration
- [ ] Support A2A server adapter with tool discovery and execution

### Core developer experience
- [ ] Externalize individual "exposes" objects into separate files, similar to "consumes" objects
- [ ] Allow reuse of "binds" blocks across capabilities
- [ ] Add conditional steps, for-each steps, parallel-join
- [ ] Native integration with [Langchain4j](https://docs.langchain4j.dev/), see [issue #293](https://github.com/naftiko/framework/issues/293)

## Version 1.0 - First Beta - End of June :blossom:

The goal of this version is to deliver a stable MVP, including a stable Naftiko Specification.

### Rightsize AI context
- [ ] Evolve MCP server adapter to support [server-side code mode like CloudFlare](https://www.reddit.com/r/mcp/comments/1o1wdfh/do_you_think_code_mode_will_supercede_mcp/)
  - [ ] Facilitate skills publication in skills marketplaces

### Enable API reusability
- [ ] Increase HTTP client resiliency (retry, circuit breaker, rate limiter, time limiter, bulkhead, cache, fallback)
- [ ] Add client SDKs generation to Naftiko CLI for top languages (TypeScript, Python, Java, Go)

### Core developer experience
- [ ] Expand support for "tags" and "labels" in Naftiko Spec
- [ ] Facilitate authorization management (scope declarations and enforcement)
- [ ] Publish starter capability templates (golden path skeletons with all required fields pre-filled)
- [ ] Incorporate community feedback
- [ ] Complete test coverage and overall quality

## Version 1.0 - General Availability - September :apple:

The goal of this version is to release our first version ready for production.

- [ ] Incorporate community feedback
- [ ] Publish reference bridge capabilities (RSS/Atom XML feeds, XML/SOAP, CSV, etc.)
- [ ] Solidify the existing beta version scope
- [ ] Increase test coverage and overall quality
- [ ] Publish JSON Schema to [JSON Schema Store](https://www.schemastore.org/)
- [ ] Profile and optimize engine for best latency and throughput

## Version 1.1 - December :snowflake:

The goal of this version is to broaden the platform surface area based on production learnings.

### Rightsize AI context
- [ ] Add AI framework wrappers (Langchain4j, LangChain, LlamaIndex)
- [ ] Support AI Catalog spec (join MCP, A2A initiative)

### Enhance API reusability
- [ ] Support Webhook server adapter for workflow automation
- [ ] Add support for gRPC (incl. proto generation) and oRPC as server adapters
- [ ] Add full resiliency patterns (rate limiter, time limiter, bulkhead, cache)

### Enable Data reusability
- [ ] Add support for Singer, Airbyte and SQL as server adapters
- [ ] Add support for FILE and SQL as client adapters
- [ ] Support templatized SQL request with proper security

### Core developer experience
- [ ] Facilitate integration with Keycloak, OpenFGA
- [ ] Provide Control webapp (per Capability)
- [ ] Publish Docker Desktop Extension to Docker Hub
