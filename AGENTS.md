# Naftiko Framework — Agent Guidelines

## Project Context

**Naftiko Framework** is the engine for [Spec-Driven Integration](https://github.com/naftiko/framework/wiki/Spec%E2%80%90Driven-Integration). Capabilities are declared entirely in YAML — no Java required. The framework parses them and exposes them via MCP, SKILL, or REST servers.

- **Language**: Java 21, Maven build system
- **Specification**: `src/main/resources/schemas/naftiko-schema.json` — keep this as first-class citizen in your context
- **Wiki**: https://github.com/naftiko/framework/wiki (Specification, Tutorial, Use Cases, FAQ)

## Key Files

| Path | Purpose |
|---|---|
| `src/main/resources/schemas/naftiko-schema.json` | Naftiko JSON Schema (source of truth) |
| `src/main/resources/schemas/examples/` | Capability examples (`cir.yml`, `notion.yml`, `skill-adapter.yml`, ...) |
| `src/main/resources/tutorial/` | Shipyard Track tutorial (`step-1-shipyard-` to `step-10-shipyard-`) |
| `src/test/resources/` | Test fixtures (not examples) |
| `src/main/resources/scripts/pr-check-wind.ps1` | Local pre-PR validation (Windows) |
| `src/main/resources/scripts/pr-check-mac-linux.sh` | Local pre-PR validation (Unix/macOS) |
| `CONTRIBUTING.md` | Full contribution workflow |

## Build & Test

All commands must be run from the repository root (`framework/`).

```bash
# Run unit tests (standard local workflow — requires JDK 21)
mvn clean test --no-transfer-progress

# Build Docker image (Maven runs inside Docker — no local Maven needed)
docker build -f src/main/resources/deployment/Dockerfile -t naftiko .

# Build native CLI binary (requires GraalVM 21 — triggered by version tags in CI)
mvn -B clean package -Pnative

# Pre-PR validation (Windows)
.\src\main\resources\scripts\pr-check-wind.ps1

# Pre-PR validation (Unix)
bash ./src/main/resources/scripts/pr-check-mac-linux.sh
```

## Local Bootstrap

Before contributing, ensure your local environment has at least JDK 21 and Maven.

**Required:** JDK 21, Maven 3.9+

```bash
java -version    # must be 21+
mvn -version     # must be 3.9+
```

Trivy and Gitleaks are **not required locally** — they run automatically in CI. The `pr-check` scripts use them if installed, but `mvn clean test` is enough to validate your changes before a PR.

If you still want to run the full pre-PR checks locally, install [Trivy](https://github.com/aquasecurity/trivy#installation) and [Gitleaks](https://github.com/zricethezav/gitleaks#installation).

## Code Style

**Java** — follows Google Style. Configure VS Code with `Language Support for Java by Red Hat` and apply settings from [naftiko/code-standards — java](https://github.com/naftiko/code-standards/tree/main/java).

**Method visibility** — prefer package-private (no modifier) over `private` for methods that implement non-trivial logic. This allows direct unit testing from the same package without reflection. Reserve `private` for truly internal helpers that are trivially covered by public API tests (e.g. one-liner formatters, simple getters).

Never modify CI/CD workflows (`.github/workflows/`), security configs, or branch protection rules.

## Test Writing Rules

When writing or generating tests, follow these rules:

**Do:**
- Test behavior through the public API — assert observable outcomes, not implementation details
- When a method is not accessible from a test, make it package-private in the production code (remove `private`) rather than using reflection — this is the correct fix
- Write one focused assertion per test, or group only closely related assertions in a single test
- Name tests in the form `methodShouldDoSomethingWhenCondition`

**Don't:**
- Use `getDeclaredMethod` / `setAccessible(true)` to access non-public methods
- Write tests whose only purpose is to reach a coverage threshold — every test must document a real behavior or guard against a real regression
- Name tests `shouldCoverXxxBranches` or similar — names must describe behavior, not implementation structure
- Group unrelated scenarios in a single test method — split them into separate `@Test` methods

## Capability Design Rules

When designing or modifying a Capability:

**Do:**
- Keep the [Naftiko Specification](src/main/resources/schemas/naftiko-schema.json) and the [Naftiko Rules](src/main/resources/rules/naftiko-rules.yml) as first-class citizens — the schema enforces structure, the rules enforce cross-object consistency, quality, and security
- Look at `src/main/resources/schemas/examples/` for patterns before writing new capabilities
- When renaming a consumed field for a lookup `match`, also add a `ConsumedOutputParameter` on the consumed operation to map the raw field name to a kebab-case name — otherwise the lookup has nothing to match against
- Use `aggregates` to define reusable domain functions when the same operation is exposed through multiple adapters (REST and MCP) — this follows the DDD Aggregate pattern: one definition, multiple projections
- Declare `semantics` (safe, idempotent, cacheable) on aggregate functions to describe domain behavior — the engine derives MCP `hints` automatically
- Override only adapter-specific fields when using `ref` (e.g., `method` for REST, `hints` for MCP) — let the rest be inherited from the function

**Don't:**
- Expose an `inputParameter` that is not used in any step
- Declare consumed `outputParameters` that are not used in the exposed part
- Prefix variables with the capability/namespace/resource name — they are already scoped, unless disambiguation is strictly needed
- Set a type property for `inputParameter` in a rest consumes bloc
- Use an `integer` type instead of a `number` type for `outputParameters` in a mcp exposes bloc
- Bind two `exposes` adapters (e.g. `skill` and `rest`) to the same port
- Use `items:` or nested `type:` on `McpToolInputParameter` for array-typed parameters — only `name`, `type`, `description`, and `required` are allowed
- Use YAML list syntax (`- type: object`) for `items` in `MappedOutputParameterArray` — `items` is a single `MappedOutputParameter` object, not an array
- Use snake_case identifiers where the schema expects `IdentifierKebab` (e.g. `match`, `name`, `namespace`) — use kebab-case
- Use `operation` instead of `call` in steps — `operation` is not a valid property in `OperationStepCall`, only `call` is
- Use `MappedOutputParameter` (with `mapping`, no `name`) when the tool/operation uses `steps` — use `OrchestratedOutputParameter` (with `name`, no `mapping`) instead
- Use typed objects for lookup step `outputParameters` — they are plain string arrays of field names to extract (e.g. `- "fullName"`)
- Put a `path` property on an `ExposedOperation` — extract multi-step operations with a different path into their own `ExposedResource`
- Duplicate a full function definition inline on both MCP tools and REST operations — use `aggregates` + `ref` instead
- Chain `ref` through multiple levels of aggregates — `ref` resolves to a function in a single aggregate, not transitively

## Contribution Workflow

See [CONTRIBUTING.md](CONTRIBUTING.md) for the full workflow. Key rules:

- Open an Issue before starting work
- Branch from `main`: `feat/`, `fix/`, or `chore/` prefix
- Use [Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `chore:` — no scopes for now
- Rebase on `main` before PR — linear history, no merge commits
- One logical change per PR — keep it atomic
- CI must be green (build, tests, schema validation, Trivy, Gitleaks)
- Do **not** use `git push --force` — use `--force-with-lease`

## Bug Workflow (mandatory)

When you identify a bug — whether discovered during development, debugging, or user-reported — follow these steps **in order** before writing any fix:

### 1. Open an Issue

Create a GitHub issue using the **Bug Report** template (`.github/ISSUE_TEMPLATE/bug_report.yml`).
Fill in all required fields: component, description (actual vs expected), steps to reproduce, root cause if known, proposed fix.
If the PR was created or assisted by an AI agent, fill in the **Agent Context** block.

If you cannot create the issue directly (e.g. no `gh` CLI available, no API token), provide the user with all the elements needed to create it manually: suggested title, label, filled-in template body ready to paste. Do not proceed to step 2 until the user confirms the issue number.

### 2. Create a dedicated branch from up-to-date `main`

If there is any work in progress on the current branch (modified files, untracked files), save it first so nothing is lost and the user can return to it after the fix:

```bash
git stash push -m "wip: <description>" -- <only the relevant files>
# or, if everything on the branch belongs to the in-progress work:
git stash push -m "wip: <description>"
```

Note the stash ref or branch name so you can restore it later with `git stash pop` or `git checkout <branch>`.

Then create the fix branch from up-to-date `main`:

```bash
git checkout main
git pull origin main
git checkout -b fix/<short-description>
```

Never start a fix branch from a feature branch or a stale local `main`.
When the fix is merged, remind the user to switch back to their original branch and restore the stash if needed.

### 3. Write non-regression tests before committing the fix

For every bug fix, two tests are required:

**Unit test** — targets the smallest unit of code that contains the bug (method or class level). Place it in the test class corresponding to the fixed class (e.g. `ConverterTest`, `ResolverTest`). If the class has no test file yet, create one. If a test already covers the scenario but is wrong, fix the test first and explain why in a comment.

**Integration test** — validates the fix end-to-end, typically loading a YAML capability fixture and exercising the full chain (deserialization → engine → output). Place the fixture in `src/test/resources/` and the test class in the package closest to the integration point (e.g. `io.naftiko.engine.exposes.mcp`).

Run the full test suite before committing:

```bash
mvn test
```

All existing tests must stay green. If a pre-existing test fails, investigate before touching it.

## Self-Improvement

When a user corrects agent-generated code, evaluate whether the correction should become a new rule in this file. Suggest an AGENTS.md update **only** when all three conditions are met:

1. The corrected code was **generated by the agent** (not pre-existing code being refactored)
2. The correction is **structural** — it targets a convention, pattern, or style choice (e.g. visibility, naming, test design) — not a one-off logic bug or domain-specific mistake
3. The correction is **generalizable** — the same mistake could plausibly recur in a different file or context

When all three conditions are met, propose the specific Do/Don't entry and the section it belongs to. Do not apply it — let the user decide.

For reference, the Test Writing Rules and Method Visibility sections in this file were both added through this process.
