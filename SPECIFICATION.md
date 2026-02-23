# Naftiko Specification

**Version 0.3**

**Publication Date:** February 2026

---

## 1. Introduction

The Naftiko Specification defines a standard, language-agnostic interface for describing modular, composable capabilities. In short, a **capability** is a functional unit that consumes external APIs (sources) and exposes adapters that allow other systems to interact with it.

When properly defined via NCS, a capability can be discovered, orchestrated, validated and executed with minimal implementation logic. The specification enables description of:

- **Consumed sources**: External APIs or services that the capability uses
- **Exposed adapters**: Server interfaces that the capability provides (HTTP, REST, etc.)
- **Orchestration**: How calls to consumed sources are combined and mapped to realize exposed functions

### 1.1 Schema Access

The JSON Schema for the Naftiko Capability Specification is available in two forms:

- **Raw file** — The schema source file is hosted on GitHub: [capability-schema.json](https://github.com/naftiko/framework/blob/main/src/main/resources/schemas/capability-schema.json)
- **Interactive viewer** — A human-friendly viewer is available at: [Schema Viewer](https://naftiko.github.io/schema-viewer/)

### 1.2 Core Concepts

**Capability**: The central object that defines a modular functional unit with clear input/output contracts. 

**Consumes**: External sources (APIs, services) that the capability uses to realize its operations.

**Exposes**: Server adapters that provide access to the capability's operations.

**Resources**: API endpoints that group related operations.

**Operations**: Individual HTTP operations (GET, POST, etc.) that can be performed on resources.

**Namespace**: A unique identifier for consumed sources, used for routing and mapping with the expose layer.

---

## 2. Format

A Naftiko Capability document that conforms to the Naftiko Capability Specification is itself a JSON object, which may be represented either in **JSON** or **YAML** format.

All field names in the specification are **case-sensitive**.

NCS Objects expose two types of fields:

- **Fixed fields**: which have a declared name
- **Patterned fields**: which have a declared pattern for the field name

---

## 3. Objects and Fields

### 3.1 Naftiko Object

This is the root object of the Naftiko Capability document.

#### 3.1.1 Fixed Fields

| Field Name | Type | Description |
| --- | --- | --- |
| **naftiko** | `string` | **REQUIRED**. Version of the Naftiko schema. MUST be `"0.3"` for this version. |
| **info** | `Info` | **REQUIRED**. Metadata about the capability. |
| **capability** | `Capability` | **REQUIRED**. Technical configuration of the capability including sources and adapters. |

#### 3.1.2 Rules

- The `naftiko` field MUST be present and MUST have the value `"0.3"` for documents conforming to this version of the specification.
- Both `info` and `capability` objects MUST be present.
- No additional properties are allowed at the root level.

---

### 3.2 Info Object

Provides metadata about the capability.

#### 3.2.1 Fixed Fields

| Field Name | Type | Description |
| --- | --- | --- |
| **label** | `string` | **REQUIRED**. The display name of the capability. |
| **description** | `string` | **REQUIRED**. A description of the capability. The more meaningful it is, the easier for agent discovery. |
| **tags** | `string[]` | List of tags to help categorize the capability for discovery and filtering. |
| **created** | `string` | Date the capability was created (format: `YYYY-MM-DD`). |
| **modified** | `string` | Date the capability was last modified (format: `YYYY-MM-DD`). |
| **stakeholders** | `Person[]` | List of stakeholders related to this capability (for discovery and filtering). |

#### 3.2.2 Rules

- Both `label` and `description` are mandatory.
- No additional properties are allowed.

#### 3.2.3 Info Object Example

```yaml
info:
  label: Notion Page Creator
  description: Creates and manages Notion pages with rich content formatting
  tags:
    - notion
    - automation
  created: "2026-02-17"
  modified: "2026-02-17"
  stakeholders:
    - role: owner
      fullName: "Jane Doe"
      email: "jane.doe@example.
```

---

### 3.3 Person Object

Describes a person related to the capability.

#### 3.3.1 Fixed Fields

| Field Name | Type | Description |
| --- | --- | --- |
| **role** | `string` | **REQUIRED**. The role of the person in relation to the capability. E.g. owner, editor, viewer. |
| **fullName** | `string` | **REQUIRED**. The full name of the person. |
| **email** | `string` | The email address of the person. MUST match pattern `^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$`. |

#### 3.3.2 Rules

- Both `role` and `fullName` are mandatory.
- No additional properties are allowed.

#### 3.3.3 Person Object Example

```yaml
- role: owner
  fullName: "Jane Doe"
  email: "jane.doe@example.com"
```

---

### 3.4 Capability Object

Defines the technical configuration of the capability.

#### 3.4.1 Fixed Fields

| Field Name | Type | Description |
| --- | --- | --- |
| **exposes** | `Exposes[]` | **REQUIRED**. List of exposed server adapters. |
| **consumes** | `Consumes[]`  | **REQUIRED**. List of consumed client adapters. |

#### 3.4.2 Rules

- The `exposes` array MUST contain at least one entry.
- The `consumes` array MUST contain at least one entry.
- If the `consumes` array contains two or more entries, each entry MUST include both `baseUri` and `namespace` fields.
- There are several types of exposed adapters and consumed sources objects, all will be described in following objects.
- No additional properties are allowed.

#### 3.4.3 Namespace Uniqueness Rule

When multiple `consumes` entries are present:

- Each `namespace` value MUST be unique across all consumes entries.
- The `namespace` field is used for routing from the expose layer to the correct consumed source.
- Duplicate namespace values will result in ambiguous routing and are forbidden.

#### 3.4.4 Capability Object Example

```yaml
capability:
  exposes:
    - type: rest
      port: 3000
      resources:
        - path: /tasks
          operations:
            - id: create-task
              name: Create Task
              method: POST
              steps:
                - call: api.create-task
              outputParameters:
                - name: taskId
                  value: $.data.id
  consumes:
    - type: http
      namespace: api
      targetUri: https://api.example.compath
      resources:
        - id: tasks
          name: Tasks API
          path: /tasks
          operations:
            - id: create-task
              name: Create Task
              inputParameters:
                - name: task_id
                  in: path
                  method: POST
              outputParameters:
                - name: taskId
                  value: $.data.id
```

---

### 3.5 Exposes Object

Describes a server adapter that exposes functionality.

> Update (schema v0.3): the exposition adapter is **API** with `type: "api"` (and a required `namespace`). Legacy `httpProxy` / `rest` exposition types are not part of the JSON Schema anymore.
> 

#### 3.5.1 API Expose

API exposition configuration.

**Fixed Fields:**

| Field Name | Type | Description |
| --- | --- | --- |
| **type** | `string` | **REQUIRED**. MUST be `"api"`. |
| **address** | `string` | Server address. Can be a hostname, IPv4, or IPv6 address. |
| **port** | `integer` | **REQUIRED**. Port number. MUST be between 1 and 65535. |
| **authentication** | `Authentication` | Authentication configuration. Defaults to `"inherit"`. |
| **namespace** | `string` | **REQUIRED**. Unique identifier for this exposed API. |
| **resources** | `ExposedResource[]` | **REQUIRED**. List of exposed resources. |

#### 3.5.2 ExposedResource Object

An exposed resource (either **operations** or **forward**).

**Fixed Fields:**

| Field Name | Type | Description |
| --- | --- | --- |
| **address** | `string` | Server address. Can be a hostname, IPv4, or IPv6 address. |
| **port** | `integer` | **REQUIRED**. Port number. MUST be between 1 and 65535. |
| **authentication** | `Authentication` | Authentication configuration. Defaults to `"inherit"`. |
| **path** | `string` | **REQUIRED**. Path of the resource (supports `param` placeholders). |
| **name** | `string` | Technical name for the resource (used for references, pattern `^[a-zA-Z0-9-]+$`). |
| **label** | `string` | Display name for the resource (likely used in UIs). |
| **inputParameters** | `InputParameter[]` | Input parameters attached to the resource. |
| **operations** | `ExposedOperation[]` | Operations available on this resource (mutually exclusive with `forward`). |
| **forward** | `ForwardConfig` | Forwarding configuration (mutually exclusive with `operations`). |

#### 3.5.3 Rules

- The `port` field MUST be an integer between 1 and 65535 (inclusive).
- The `address` field, when present, MUST be a valid hostname, IPv4 address, or IPv6 address.

#### 3.5.4 Address Validation Patterns

- **Hostname**: `^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$`
- **IPv4**: `^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$`
- **IPv6**: `^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$`

#### 3.5.5 Exposes Object Examples

**HTTP Proxy:**

```yaml
type: httpProxy
port: 8080
address: localhost
forward:
  trustedHeaders:
    - Authorization
    - X-API-Key
  targetNamespaces:
    - github
    - slack
```

**REST Adapter:**

```yaml
type: rest
port: 3000
resources:
  - path: /status
    operations:
      - id: get-status
        name: Get Status
        method: GET
        steps:
          - call: api.health-check
        outputParameters:
          - name: status
            value: $.status
```

---

### 3.6 Consumes Object

Describes a client adapter for consuming external APIs.

> Update (schema v0.3): `targetUri` is now `baseUri`.
> 

#### 3.6.1 Fixed Fields

| Field Name | Type | Description |
| --- | --- | --- |
| **type** | `string` | **REQUIRED**. Type of consumer. Valid values: `"http"`. |
| **namespace** | `string` | Path suffix used for routing from exposes. MUST match pattern `^[a-zA-Z0-9-]+$`. |
| **baseUri** | `string` | **REQUIRED**. Base URI for the consumed API. Must be a valid http(s) URL (no `path` placeholder in the schema). |
| **authentication** | Authentication Object | Authentication configuration. Defaults to `"inherit"`. |
| **headers** | [`string`] | Headers to include in requests (format: 'Name: Value'). |
| **resources** | [ConsumedHttpResource Object] | List of API resources. If not specified, all requests will be routed to this consumes. |

#### 3.6.2 Rules

- The `type` field MUST be `"http"`.
- The `baseUri` field is required.
- The `namespace` field is required and MUST be unique across all consumes entries.
- The `namespace` value MUST match the pattern `^[a-zA-Z0-9-]+$` (alphanumeric and hyphens only).
- Each header string in the `headers` array MUST follow the format `HeaderName: HeaderValue`.
- The `resources` array is required and MUST contain at least one entry.

#### 3.6.3 Base URI Format

The `baseUri` field MUST be a valid `http://` or `https://` URL, and may optionally include a base path.

Example: `https://api.github.com` or `https://api.github.com/v3`

#### 3.6.4 Consumes Object Example

```yaml
type: http
namespace: github
baseUri: https://api.github.com
authentication:
  type: bearer
  token: ${GITHUB_TOKEN}
headers:
  - "Accept: application/vnd.github.v3+json"
resources:
  - id: users
    name: Users API
    path: /users/{username}
    operations:
      - id: get-user
        name: Get User
        inputParameters:
          - name: username
            in: path
            method: GET
        outputParameters:
          - name: userId
            value: $.id
  - id: repos
    name: Repositories API
    path: /users/{username}/repos
    operations:
      - id: list-repos
        name: List Repositories
        inputParameters:
          - name: username
            in: path
            method: GET
        outputParameters:
          - name: repos
            value: $
```

---

### 3.7 ConsumedHttpResource Object

Describes an API resource that can be consumed from an external API.

#### 3.7.1 Fixed Fields

| Field Name | Type | Description |
| --- | --- | --- |
| **id** | `string` | **REQUIRED**. Unique identifier of the resource. MUST match pattern `^[a-zA-Z0-9-]+$`. |
| **name** | `string` | **REQUIRED**. Display name of the resource. |
| **path** | `string` | **REQUIRED**. Path of the resource, relative to the consumes baseUri . Supports `path` placeholder. |
| **headers** | [`string`] | Headers to include when accessing this resource. Each entry MUST match pattern `^[^:]+:\\s*.+$` (format: `Name: Value`). |
| **operations** | [ConsumedHttpOperation Object] | **REQUIRED**. List of operations for this resource. |

#### 3.7.2 Rules

- The `id` field MUST be unique within the parent consumes object's resources array.
- The `id` field MUST match the pattern `^[a-zA-Z0-9-]+$` (alphanumeric and hyphens only).
- Each header string in the `headers` array MUST follow the format `HeaderName: HeaderValue`.
- The `path` field will be substituted into the parent consumes object's `baseUri` at the `path` placeholder position.
- The `operations` array MUST contain at least one entry.

#### 3.7.3 ConsumedHttpResource Object Example

```yaml
id: users
name: Users API
path: /users/{username}
headers:
  - "Accept: application/json"
operations:
  - id: get-user
    name: Get User
    inputParameters:
      - name: username
        in: path
        method: GET
    outputParameters:
      - name: userId
        value: $.id
```

---

### 3.8 ConsumedHttpOperation Object

Describes an operation that can be performed on a consumed resource.

#### 3.8.1 Fixed Fields

| Field Name | Type | Description |
| --- | --- | --- |
| **id** | `string` | **REQUIRED**. Unique identifier of the operation. MUST match pattern `^[a-zA-Z0-9-]+$`. |
| **name** | `string` | **REQUIRED**. Display name of the operation. |
| **inputParameters** | [InputParameter Object] | **REQUIRED**. List of input parameters for the operation. |
| **outputParameters** | [OutputParameter Object] | **REQUIRED**. List of output parameters. MUST contain at least one entry. |

#### 3.8.2 Rules

- The `id` field MUST be unique within the parent resource's operations array.
- The `id` field MUST match the pattern `^[a-zA-Z0-9-]+$` (alphanumeric and hyphens only).
- The `inputParameters` array is required but MAY be empty.
- The `outputParameters` array MUST contain at least one entry.

#### 3.8.3 ConsumedHttpOperation Object Example

```yaml
id: get-user
name: Get User Profile
inputParameters:
  - name: username
    in: path
    method: GET
  - name: include
    in: query
    method: GET
outputParameters:
  - name: userId
    value: $.id
  - name: username
    value: $.login
  - name: email
    value: $.email
```

---

### 3.9 ExposedOperation Object

Describes an operation exposed on an exposed resource.

#### 3.9.1 Fixed Fields

| Field Name | Type | Description |
| --- | --- | --- |
| **name** | `string` | **REQUIRED**. Technical name for the operation (pattern `^[a-zA-Z0-9-]+$`). |
| **method** | `string` | **REQUIRED**. HTTP method. One of: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`. |
| **label** | `string` | Display name for the operation (likely used in UIs). |
| **inputParameters** | `InputParameter[]` | Input parameters attached to the operation. |
| **outputParameters** | `OutputParameter[]` | Output parameters extracted/mapped for this operation. |
| **steps** | `OperationStep[]` | **REQUIRED**. Sequence of calls to consumed operations (at least 1 step). |

#### 3.9.2 Rules

- The `steps` array MUST contain at least one entry.
- Each step references a consumed operation using `{namespace}.{operationName}`.

#### 3.9.3 ExposedOperation Object Example

```yaml
name: get-user-profile
label: Get User Profile
method: GET
steps:
  - call: github.get-user
outputParameters:
  - name: userId
    value: $response.id
```

---

### 3.10 RequestBody Object

Describes request body configuration for consumed operations.

#### 3.10.1 Fixed Fields

| Field Name | Type | Description |
| --- | --- | --- |
| **body** | `object` | **REQUIRED**. Body payload and type. |

#### 3.10.2 Shape

`RequestBody` is structured as:

- `body.type`: one of `json`, `text`, `xml`, `sparql`, `formUrlEncoded`, `multipartForm`
- `body.data`: the payload, whose shape depends on `type`

#### 3.10.3 RequestBody Example

```yaml
body:
  type: json
  data:
    hello: "world"
```

---

### 3.11 InputParameter Object

Describes a single input parameter for an operation.

#### 3.11.1 Fixed Fields

| Field Name | Type | Description |
| --- | --- | --- |
| **name** | `string` | **REQUIRED**. Parameter name. MUST match pattern `^[a-zA-Z0-9-*]+$`. |
| **in** | `string` | **REQUIRED**. Parameter location. Valid values: `"query"`, `"header"`, `"path"`, `"cookie"`, `"body"`. |
| **value** | `string` | Value or JSONPath reference. |

#### 3.11.2 Rules

- The `name` field MUST match the pattern `^[a-zA-Z0-9-*]+$`.
- The `in` field MUST be one of: `"query"`, `"header"`, `"path"`, `"cookie"`, `"body"`.
- A unique parameter is defined by the combination of `name` and `in`.

#### 3.11.3 InputParameter Object Example

```yaml
- name: username
  in: path
- name: page
  in: query
- name: Authorization
  in: header
- name: payload
  in: body
  value: $.input
```

---

### 3.12 OutputParameter Object

Describes output parameter mapping using JsonPath expressions.

> Note: The `$` root gives direct access to the raw response payload of the consumed operation.
> 

#### 3.12.1 Fixed Fields

| Field Name | Type | Description |
| --- | --- | --- |
| **name** | `string` | **REQUIRED**. Output parameter name. MUST match pattern `^[a-zA-Z0-9-_]+$`. |
| **value** | `string` | **REQUIRED**. JsonPath expression to extract value from consumed function response. |

#### 3.12.2 Rules

- The `name` field MUST match the pattern `^[a-zA-Z0-9-_*]+$` (alphanumeric, hyphens, underscores, and asterisk).
- The `value` field MUST start with `$`.

#### 3.12.3 JsonPath roots (extensions)

In a consumed resource, **`$`** refers to the *raw response payload* of the consumed operation (after decoding based on `outputRawFormat`). The root `$` gives direct access to the JSON response body.

Example, if you consider the following JSON response :

```json
{
  "id": "154548",
  "titles": [
    {
      "text": {
        "content": "This is title[0].text.content",
        "author": "user1"
      }
    }
  ],
  "created_time": "2024-06-01T12:00:00Z"
}
```

- `$.id` is `154548`
- `$.titles[0].text.content` is `This is title[0].text.content`

#### 3.12.4 Common patterns

- `$.fieldName` — accesses a top-level field
- `$.data.user.id` — accesses nested fields
- `$.items[0]` — accesses array elements
- `$.items[*].id` — accesses all ids in an array

#### 3.12.5 OutputParameter Object Example

```yaml
outputParameters:
  - name: dbName
    value: $.title[0].text.content
  - name: dbId
    value: $.id
```

---

### 3.13 OperationStep Object

Describes a single step in a sequence of operation calls for orchestration.

#### 3.13.1 Fixed Fields

| Field Name | Type | Description |
| --- | --- | --- |
| **call** | `string` | **REQUIRED**. Reference to consumed function. Format: `{namespace}.{requestId}`. MUST match pattern `^[a-zA-Z0-9-]+\\.[a-zA-Z0-9-]+$`. |
| **inputParameters** | `OperationStepParameter[]` | List of parameters to pass to the called operation. Each parameter specifies a name, location, and value. |
| **mappings** | `StepOutputMapping[]` | Defines how to map the output parameters of this step to the input parameters of the next steps or to the output parameters of the exposed operation. Each mapping specifies a `targetName` and a JsonPath `value` reference. |

#### 3.13.2 Rules

- The `call` field MUST follow the format `{namespace}.{operationId}`.
- The `namespace` portion MUST correspond to a namespace defined in one of the capability's consumes entries.
- The `operationId` portion MUST correspond to an operation `id` defined in the consumes entry identified by the namespace.
- The pattern is strictly: alphanumeric-with-hyphens, a literal dot (`.`), then alphanumeric-with-hyphens again.
- Invalid format or non-existent namespace/operationId references will result in runtime resolution errors.

#### 3.13.3 OperationStep Reference Resolution

The `call` value is resolved as follows:

1. Split the value on the `.` character into namespace and operationId
2. Find the consumes entry with matching `namespace` field
3. Within that consumes entry's resources, find the operation with matching `id` field
4. Execute that operation as part of the orchestration sequence

#### 3.13.4 OperationStep Object Example

```yaml
steps:
  - call: "notion.get-database"
    inputParameters:
      - name: "database_id"
        in: "path"
        value: "$this.sample.database_id"
    mappings:
      - targetName: "db_name"
        value: "$.dbName"
  - call: slack.post-message
  - call: database.store-record
```

---

### 3.14 StepOutputMapping Object

Describes how to map the output of an operation step to the input of another step or to the output of the exposed operation.

#### 3.14.1 Fixed Fields

| Field Name | Type | Description |
| --- | --- | --- |
| **targetName** | `string` | **REQUIRED**. The name of the parameter to map to. It can be an input parameter of a next step or an output parameter of the exposed operation. |
| **value** | `string` | **REQUIRED**. A JsonPath reference to the value to map from. E.g. `$.get-database.database_id`. |

#### 3.14.2 Rules

- Both `targetName` and `value` are mandatory.
- No additional properties are allowed.

#### 3.14.3 How mappings wire steps to exposed outputs

A StepOutputMapping connects the **output parameters of a consumed operation** (called by the step) to the **output parameters of the exposed operation** (or to input parameters of subsequent steps).

- **`targetName`** — refers to the `name` of an output parameter declared on the exposed operation, or the `name` of an input parameter of a subsequent step. The target parameter receives its value from the mapping.
- **`value`** — a JsonPath expression where **`$`** is the root of the consumed operation's output parameters. The syntax `$.{outputParameterName}` references a named output parameter of the consumed operation called in this step.

#### 3.14.4 End-to-end example

Consider a consumed operation `notion.get-database` that declares:

```yaml
# In consumes → resources → operations
name: "get-database"
outputParameters:
  - name: "dbName"
    value: "$.title[0].text.content"
```

And the exposed side of the capability:

```yaml
# In exposes
exposes:
  - type: "api"
    address: "localhost"
    port: 9090
    namespace: "sample"
    resources:
      - path: "/databases/{database_id}"
        name: "db"
        label: "Database resource"
        inputParameters:
          - name: "database_id"
            in: "path"
        operations:
          - name: "get-db"
            method: "GET"
            label: "Get Database"
            outputParameters:
              - name: "Api-Version"
                value: "v1"
              - name: "db_name"
            steps:
              - call: "notion.get-database"
                inputParameters:
                  - name: "database_id"
                    value: "$this.sample.database_id"
                mappings:
                  - targetName: "db_name"
                    value: "$.dbName"
```

Here is what happens at orchestration time:

1. The step calls `notion.get-database`, which extracts `dbName` and `dbId` from the raw response via its own output parameters.
2. The mapping `targetName: "db_name"` refers to the exposed operation's output parameter `db_name`.
3. The mapping `value: "$.dbName"` resolves to the value of the consumed operation's output parameter named `dbName`.
4. As a result, the exposed output `db_name` is populated with the value extracted by `$.dbName` (i.e. `title[0].text.content` from the raw Notion API response).
5. The exposed output `Api-Version` has a static value `"v1"` and does not depend on any mapping.

#### 3.14.5 StepOutputMapping Object Example

```yaml
mappings:
  - targetName: "db_name"
    value: "$.dbName"
```

---

### 3.15 OperationStepParameter Object

Describes a single parameter in an operation step.

#### 3.15.1 Fixed Fields

| Field Name | Type | Description |
| --- | --- | --- |
| **name** | `string` | **REQUIRED**. The name of the parameter in the called operation. |
| **in** | `string` | **REQUIRED**. The location of the parameter in the called operation. Valid values: `"query"`, `"header"`, `"path"`, `"cookie"`, `"body"`. |
| **value** | `string` | **REQUIRED**. The value of the parameter. It can be a static value or a reference to a previous step output using JsonPath syntax. E.g. `$this.step1.output.data.id`. |

#### 3.15.2 Rules

- All three fields (`name`, `in`, `value`) are mandatory.
- The `in` field MUST be one of: `"query"`, `"header"`, `"path"`, `"cookie"`, `"body"`.
- No additional properties are allowed.

#### 3.15.3 JsonPath context for `value` — the `$this` root

In an OperationStepParameter, the `value` field can use the **`$this`** root to reference the *current capability execution context* — that is, values already resolved during orchestration.

**`$this`** navigates the expose layer's input parameters using the path `$this.{exposeNamespace}.{inputParameterName}`. This allows a step to receive values that were provided by the caller of the exposed operation.

- **`$this.{exposeNamespace}.{paramName}`** — accesses an input parameter of the exposed resource or operation identified by its namespace.
- The `{exposeNamespace}` corresponds to the `namespace` of the exposed API.
- The `{paramName}` corresponds to the `name` of an input parameter declared on the exposed resource or operation.

**Example:** If the exposed API has namespace `sample` and an input parameter `database_id` declared on its resource, then:

- `$this.sample.database_id` resolves to the value of `database_id` provided by the caller.

#### 3.15.4 OperationStepParameter Object Example

```yaml
- name: "database_id"
  in: "path"
  value: "$this.sample.database_id"
```

---

### 3.16 Authentication Object

Defines authentication configuration. Five types are supported: inherit, basic, apikey, bearer, and digest.

#### 3.16.1 Inherit Authentication

Inherits authentication from parent context.

**Fixed Fields:**

| Field Name | Type | Description |
| --- | --- | --- |
| **type** | `string` | The literal value `"inherit"` (when used as an object). When used as a scalar, just the string `"inherit"`. |

**Example:**

```yaml
authentication: inherit
```

#### 3.16.2 Basic Authentication

HTTP Basic Authentication.

**Fixed Fields:**

| Field Name | Type | Description |
| --- | --- | --- |
| **type** | `string` | **REQUIRED**. MUST be `"basic"`. |
| **username** | `string` | Username for basic auth. |
| **password** | `string` | Password for basic auth. |

**Example:**

```yaml
authentication:
  type: basic
  username: admin
  password: ${SECRET_PASSWORD}
```

#### 3.16.3 API Key Authentication

API Key authentication via header or query parameter.

**Fixed Fields:**

| Field Name | Type | Description |
| --- | --- | --- |
| **type** | `string` | **REQUIRED**. MUST be `"apikey"`. |
| **key** | `string` | API key name (header name or query parameter name). |
| **value** | `string` | API key value. |
| **placement** | `string` | Where to place the key. Valid values: `"header"`, `"query"`. |

**Example:**

```yaml
authentication:
  type: apikey
  key: X-API-Key
  value: ${API_KEY}
  placement: header
```

#### 3.16.4 Bearer Token Authentication

Bearer token authentication.

**Fixed Fields:**

| Field Name | Type | Description |
| --- | --- | --- |
| **type** | `string` | **REQUIRED**. MUST be `"bearer"`. |
| **token** | `string` | Bearer token value. |

**Example:**

```yaml
authentication:
  type: bearer
  token: ${BEARER_TOKEN}
```

#### 3.16.5 Digest Authentication

HTTP Digest Authentication.

**Fixed Fields:**

| Field Name | Type | Description |
| --- | --- | --- |
| **type** | `string` | **REQUIRED**. MUST be `"digest"`. |
| **username** | `string` | Username for digest auth. |
| **password** | `string` | Password for digest auth. |

**Example:**

```yaml
authentication:
  type: digest
  username: admin
  password: ${SECRET_PASSWORD}
```

#### 3.16.6 Rules

- Only one authentication type can be used per authentication object.
- The `type` field determines which additional fields are required or allowed.
- Authentication can be specified at multiple levels (exposes, consumes) with inner levels overriding outer levels.
- The value `"inherit"` (either as a scalar string or as an object with `type: "inherit"`) means authentication will be inherited from the parent context.

---

### 3.17 ForwardHeaders Object

Defines rules for forwarding headers in HTTP proxy mode.

#### 3.17.1 Fixed Fields

| Field Name | Type | Description |
| --- | --- | --- |
| **trustedHeaders** | [`string`] | **REQUIRED**. List of headers allowed to be forwarded. No wildcards supported. |
| **targetNamespaces** | [`string`] | **REQUIRED**. List of consumer namespaces that allow header forwarding. No wildcards supported. |

#### 3.17.2 Rules

- Both `trustedHeaders` and `targetNamespaces` arrays MUST contain at least one entry.
- No wildcard patterns are supported; each header name and namespace must be listed explicitly.
- Header names in `trustedHeaders` are case-insensitive (following HTTP header conventions).
- Only headers listed in `trustedHeaders` will be forwarded to consumed sources.
- Headers will only be forwarded to consumes entries whose `namespace` appears in `targetNamespaces`.
- The combination acts as an allow-list: a header must be in `trustedHeaders` AND the target must be in `targetNamespaces` for forwarding to occur.

#### 3.17.3 ForwardHeaders Object Example

```yaml
forward:
  trustedHeaders:
    - Authorization
    - X-Request-ID
    - X-Correlation-ID
    - X-API-Version
  targetNamespaces:
    - github
    - slack
    - notion
```

---

## 4. Complete Example

Here is a complete Naftiko Capability document demonstrating the main features.

```yaml
---
naftiko: "0.3"
info:
  label: "Sample Capability"
  description: "This is a sample capability specification to demonstrate the features of Naftiko"
  tags:
    - Naftiko
    - Sample
  created: "2026-01-01"
  modified: "2026-02-12"
  stakeholders:
    - role: "editor"
      fullName: "John Doe"
      email: "john.doe@example.com"

capability:
  exposes:
    - type: "api"
      address: "localhost"
      port: 9090
      namespace: "sample"
      resources:
        - path: "/users/username"
          name: "user"
          label: "User resource"
          description: "This is a resource to retrieve user information"
          operations:
            - method: "GET"
              name: "get-user"
              label: "Get User"
              inputParameters:
                - name: "username"
                  in: "path"
              steps:
                - call: "github.get-user"
                  inputParameters:
                    - name: "username"
                      in: "path"
                      value: "$this.sample.username"

        - path: "/databases/database_id"
          name: "db"
          label: "Database resource"
          description: "This is a resource to retrieve and update information about a database"
          inputParameters:
            - name: "database_id"
              in: "path"
          operations:
            - method: "GET"
              name: "get-db"
              label: "Get Database"
              outputParameters:
                - name: "database_id"
                  value: "$this.notion.db.get-database.dbId"
                - name: "Api-Version"
                  value: "v1"
                - name: "name"
                  value: "$this.notion.db.get-database.dbName"
              steps:
                - call: "notion.get-database"

        - path: "/notion/path"
          label: "Notion API Pass-thru Proxy"
          description: "A proxy to forward requests to the Notion API while the capability is being configured"
          forward:
            targetNamespace: "notion"
            trustedHeaders:
              - "Notion-Version"

        - path: "/github/path"
          label: "GitHub API Pass-thru Proxy"
          description: "A proxy to forward requests to the GitHub API while the capability is being configured"
          forward:
            targetNamespace: "github"
            trustedHeaders:
              - "Notion-Version"

  consumes:
    - type: "http"
      namespace: "notion"
      baseUri: "https://api.notion.com/v1/"
      inputParameters:
        - name: "Notion-Version"
          in: "header"
          value: "2022-06-28"
      authentication:
        type: "basic"
        username: "scott"
        password: "tiger"
      resources:
        - path: "databases/database_id"
          name: "db"
          label: "Database resource"
          description: "This is a resource to retrieve and update information about a database"
          inputParameters:
            - name: "database_id"
              in: "path"
              value: "1234"
          operations:
            - method: "GET"
              name: "get-database"
              label: "Get Database"
              outputRawFormat: "XML"
              outputParameters:
                - name: "dbName"
                  value: "$.title[0].text.content"
                - name: "dbId"
                  value: "$.id"

            - method: "PUT"
              name: "update-database"
              label: "Update Database"

            - method: "DELETE"
              name: "delete-database"
              label: "Delete Database"

        - path: "databases/database_id/query"
          name: "query-db"
          label: "Query database resource"
          operations:
            - method: "POST"
              name: "query-database"
              label: "Query Database"

    - type: "http"
      namespace: "github"
      baseUri: "https://api.github.com/v1/"
      resources:
        - path: "/users/username"
          name: "user"
          label: "User resource"
          operations:
            - method: "GET"
              name: "get-user"
              label: "Get User"
```

---

## 5. Versioning

The Naftiko Capability Specification uses semantic versioning. The `naftiko` field in the Naftiko Object specifies the exact version of the specification (e.g., `"0.3"`). 

Tools processing Naftiko Capability documents MUST validate this field to ensure compatibility with the specification version they support.

---

This specification defines how to describe modular, composable capabilities that consume multiple sources and expose unified interfaces, supporting orchestration, authentication, and flexible routing patterns.