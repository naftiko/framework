# Markdown Output Format Support Proposal
## Native Markdown-to-JSON Conversion for the HTTP Consumes Adapter

**Status**: Proposal  
**Date**: March 26, 2026  
**Key Concept**: Add `markdown` as a native `outputRawFormat` in the HTTP consumes adapter, enabling capability authors to extract structured data (tables, front matter, headings) from Markdown content using the existing JSONPath mediation layer — no Java code required.  
**Related**: [HTML Output Format Proposal](html-output-format.md) — sister proposal, shares architectural rationale and pipeline integration approach.

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Motivation: Where Markdown Data Lives](#motivation-where-markdown-data-lives)
3. [Design: Structured Markdown-to-JSON Conversion](#design-structured-markdown-to-json-conversion)
4. [Conversion Rules](#conversion-rules)
5. [Capability YAML Example](#capability-yaml-example)
6. [Implementation Changes](#implementation-changes)
7. [Converter Method Sketch](#converter-method-sketch)
8. [Relationship to the HTML Proposal](#relationship-to-the-html-proposal)
9. [Tradeoffs vs. External Proxy](#tradeoffs-vs-external-proxy)
10. [Limitations & Future Extensions](#limitations--future-extensions)
11. [Open Questions for Discussion](#open-questions-for-discussion)

---

## Executive Summary

### Problem

Markdown is increasingly used as a machine-readable content format beyond documentation: GitHub APIs return Markdown bodies, CMS platforms serve Markdown files, static-site generators expose Markdown with YAML front matter, and AI/LLM tool outputs are frequently Markdown-formatted. These sources contain structured data — tables, front matter metadata, sectioned content — but the framework has no way to consume Markdown responses directly.

### Proposal

Add `"markdown"` to the `outputRawFormat` enum, backed by a lightweight Markdown parser (e.g., [commonmark-java](https://github.com/commonmark/commonmark-java) or [flexmark-java](https://github.com/vsch/flexmark-java)). The converter extracts structured elements — tables, YAML front matter, and heading-based sections — into a predictable JSON tree. From there, the existing JSONPath mediation layer handles extraction and mapping unchanged.

### Key Benefit

A capability author writes `outputRawFormat: "markdown"` and JSONPath mappings — same developer experience as `csv`, `xml`, or the proposed `html` format.

---

## Motivation: Where Markdown Data Lives

| Source | What It Returns | Structured Data |
|--------|----------------|-----------------|
| GitHub API (`/repos/.../readme`) | Markdown body | Front matter, tables, headings |
| CMS APIs (Strapi, Contentful) | Markdown content fields | Embedded tables, metadata |
| Static-site repos (raw files) | `.md` files | YAML front matter + content |
| LLM/AI tool outputs | Markdown-formatted responses | Tables, code blocks, lists |
| Wiki APIs (Notion export, Confluence) | Markdown exports | Tables, heading structure |

In all cases, the structured data is trapped inside Markdown syntax. Today, extracting it requires custom code outside the framework.

---

## Design: Structured Markdown-to-JSON Conversion

The converter parses Markdown into a JSON tree with three top-level sections:

### Output JSON Structure

```json
{
  "frontMatter": {
    "title": "Release Notes",
    "version": "2.1.0",
    "date": "2026-03-15"
  },
  "tables": [
    [
      { "Feature": "HTML support", "Status": "In Progress", "Owner": "Alice" },
      { "Feature": "Markdown support", "Status": "Proposed", "Owner": "Bob" }
    ]
  ],
  "sections": [
    {
      "heading": "Overview",
      "level": 2,
      "content": "This release introduces two new output formats..."
    },
    {
      "heading": "Breaking Changes",
      "level": 2,
      "content": "The outputSchema field now supports CSS selectors..."
    }
  ]
}
```

This gives capability authors three extraction paths via JSONPath:
- `$.frontMatter.version` — metadata from YAML front matter
- `$.tables[0]` — first Markdown table as array of objects
- `$.sections[?(@.heading=='Breaking Changes')].content` — content by heading

---

## Conversion Rules

### 1. YAML Front Matter → `frontMatter` object

Standard `---`-delimited front matter is parsed using Jackson's existing YAML support:

```markdown
---
title: Release Notes
version: 2.1.0
tags: [core, breaking]
---
```

```json
{
  "frontMatter": {
    "title": "Release Notes",
    "version": "2.1.0",
    "tags": ["core", "breaking"]
  }
}
```

If no front matter is present, `frontMatter` is an empty object `{}`.

### 2. Markdown Tables → `tables` array

Pipe-delimited Markdown tables follow the same conversion pattern as the HTML and CSV proposals — header row becomes keys, data rows become objects:

```markdown
| Name    | Price | Stock |
|---------|-------|-------|
| Widget  | $42   | 150   |
| Gadget  | $99   | 30    |
```

```json
{
  "tables": [
    [
      { "Name": "Widget", "Price": "$42", "Stock": "150" },
      { "Name": "Gadget", "Price": "$99", "Stock": "30" }
    ]
  ]
}
```

Multiple tables in the document produce multiple entries in the `tables` array.

### 3. Headings & Content → `sections` array

Each heading creates a section entry containing its text, level, and the body text until the next heading of equal or higher level:

```markdown
## Overview
This release introduces new formats.

## Changes
### Added
- HTML support
### Fixed
- CSV edge case
```

```json
{
  "sections": [
    { "heading": "Overview", "level": 2, "content": "This release introduces new formats." },
    { "heading": "Changes", "level": 2, "content": "" },
    { "heading": "Added", "level": 3, "content": "- HTML support" },
    { "heading": "Fixed", "level": 3, "content": "- CSV edge case" }
  ]
}
```

Section content is **plain text** (Markdown syntax stripped), preserving line breaks for lists and paragraphs.

---

## Capability YAML Example

A capability that consumes a GitHub repo's README and extracts metadata and a feature table:

```yaml
# yaml-language-server: $schema=../../main/resources/schemas/naftiko-schema.json
---
naftiko: "1.0.0-alpha1"
info:
  label: "GitHub README Reader"
  description: "Extracts structured data from a GitHub repo's README"

capability:
  exposes:
    - type: "rest"
      address: "localhost"
      port: 9090
      namespace: "readme"
      resources:
        - path: "/features"
          name: "features"
          operations:
            - method: "GET"
              call: "github.get-readme"
              outputParameters:
                - type: "array"
                  mapping: "$.tables[0]"
                  items:
                    - type: "object"
                      properties:
                        feature:
                          type: "string"
                          mapping: "$.Feature"
                        status:
                          type: "string"
                          mapping: "$.Status"
                        owner:
                          type: "string"
                          mapping: "$.Owner"

        - path: "/metadata"
          name: "metadata"
          operations:
            - method: "GET"
              call: "github.get-readme"
              outputParameters:
                - name: "title"
                  type: "string"
                  mapping: "$.frontMatter.title"
                - name: "version"
                  type: "string"
                  mapping: "$.frontMatter.version"

  consumes:
    - type: "http"
      namespace: "github"
      baseUri: "https://raw.githubusercontent.com"
      resources:
        - path: "/naftiko/framework/main/README.md"
          name: "readme"
          operations:
            - method: "GET"
              name: "get-readme"
              outputRawFormat: "markdown"
```

---

## Implementation Changes

| Component | Change |
|---|---|
| `pom.xml` | Add `org.commonmark:commonmark:0.24.x` and `org.commonmark:commonmark-ext-gfm-tables:0.24.x` dependencies |
| `Converter.java` | Add `convertMarkdownToJson(Reader)` method + `"MARKDOWN"` branch in `convertToJson()` |
| `naftiko-schema.json` | Add `"markdown"` to the `outputRawFormat` enum |
| `ConverterTest.java` | Unit tests for table parsing, front matter, sections, edge cases |
| `MarkdownIntegrationTest.java` | End-to-end test with mock Markdown endpoint |
| `markdown-capability.yaml` | Test fixture |
| `sample-readme.md` | Test Markdown fixture |

### Dependency Choice: commonmark-java

[commonmark-java](https://github.com/commonmark/commonmark-java) is preferred over flexmark-java because:
- Smaller footprint (~150 KB vs ~1.5 MB)
- Spec-compliant (CommonMark + GFM tables extension)
- Active maintenance, used by major projects (VS Code Java, IntelliJ)
- Clean visitor/walker API for extracting nodes by type

---

## Converter Method Sketch

```java
public static JsonNode convertMarkdownToJson(Reader markdownReader) throws IOException {
    String markdown = readFully(markdownReader);
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode result = mapper.createObjectNode();

    // 1. Extract and parse YAML front matter
    ObjectNode frontMatter = mapper.createObjectNode();
    String body = markdown;
    if (markdown.startsWith("---")) {
        int endIndex = markdown.indexOf("---", 3);
        if (endIndex > 0) {
            String yamlBlock = markdown.substring(3, endIndex).trim();
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            frontMatter = (ObjectNode) yamlMapper.readTree(yamlBlock);
            body = markdown.substring(endIndex + 3).trim();
        }
    }
    result.set("frontMatter", frontMatter);

    // 2. Parse Markdown AST with GFM tables extension
    List<Extension> extensions = List.of(TablesExtension.create());
    Parser parser = Parser.builder().extensions(extensions).build();
    Node document = parser.parse(body);

    // 3. Extract tables
    ArrayNode tablesArray = mapper.createArrayNode();
    // Walk AST, find TableBlock nodes, convert header row → keys, body rows → objects
    document.accept(new AbstractVisitor() {
        @Override
        public void visit(CustomBlock customBlock) {
            // TableBlock handling: extract TableHead for headers, TableBody for rows
            // Each row → ObjectNode with header-keyed values
        }
    });
    result.set("tables", tablesArray);

    // 4. Extract heading-based sections
    ArrayNode sectionsArray = mapper.createArrayNode();
    // Walk AST, split by Heading nodes, collect text content between headings
    result.set("sections", sectionsArray);

    return result;
}
```

Integration into `convertToJson()`:

```java
} else if ("MARKDOWN".equalsIgnoreCase(format)) {
    root = Converter.convertMarkdownToJson(entity.getReader());
}
```

---

## Relationship to the HTML Proposal

The Markdown and HTML proposals are complementary and share a common pattern:

| Aspect | HTML | Markdown |
|--------|------|----------|
| Parser library | JSoup | commonmark-java |
| Table output | `$.tables[N]` array of objects | `$.tables[N]` array of objects |
| Scoping mechanism | CSS selector via `outputSchema` | N/A (structured by headings) |
| Extra structure | — | `$.frontMatter`, `$.sections` |
| Schema required | No | No |
| Dependency size | ~400 KB | ~150 KB |

Both produce the same `tables` JSON shape, so JSONPath expressions for table extraction are interchangeable. If both proposals are accepted, a capability author can switch between HTML and Markdown sources by changing only `outputRawFormat` — the output mappings remain identical.

### Implementation Order

Either can be implemented independently. If done together, the shared `tables` array convention ensures consistency. The front matter parsing reuses Jackson's existing YAML support (already a dependency).

---

## Tradeoffs vs. External Proxy

| Dimension | Native `markdown` format | Proxy service |
|---|---|---|
| **Setup** | Zero — `outputRawFormat: markdown` | Deploy & maintain a service |
| **Developer experience** | Same as `csv`/`xml` — pure YAML | Write + host custom code |
| **Flexibility** | Tables, front matter, sections | Unlimited (custom parsing) |
| **New dependency** | commonmark-java (~150 KB) | External runtime |
| **Infrastructure** | None | Network hop + monitoring |
| **Complex Markdown** | Structured elements only | Full AST access |

---

## Limitations & Future Extensions

### Current Scope (This Proposal)

- **Tables**: Pipe-delimited GFM tables converted to JSON arrays of objects
- **Front matter**: YAML front matter parsed to JSON object
- **Sections**: Heading-based document structure with plain text content
- **Plain text content**: Markdown formatting stripped — bold, italic, links rendered as text

### Not In Scope

- **Code blocks**: Not extracted (could be added as named code block objects)
- **Images/links**: References not extracted (could be added to sections as metadata)
- **Nested lists**: Rendered as flat text within section content
- **Column alignment**: GFM table alignment indicators (`---:`, `:---:`) are ignored

### Potential Future Extensions

- **Code block extraction**: `$.codeBlocks[N]` with `language` and `content` fields
- **Link extraction**: `$.links[N]` with `text`, `url`, and `title` fields
- **List extraction**: `$.lists[N]` as JSON arrays (matching the HTML proposal's future extension)
- **Task list support**: GFM checkboxes (`- [x]`, `- [ ]`) as boolean fields

---

## Open Questions for Discussion

1. **Section content format**: Should section content preserve raw Markdown (useful for re-rendering) or strip it to plain text (simpler for data extraction)?

2. **Front matter without body**: If the Markdown is purely front matter (common in CMS metadata files), should `tables` and `sections` be omitted or present as empty arrays?

3. **Table column types**: Should the converter attempt to detect numeric columns (like `"Stock": 150` instead of `"Stock": "150"`), or keep all values as strings for consistency with CSS and HTML converters?

4. **Heading hierarchy**: Should sections nest hierarchically (h3 inside h2) or stay flat? Flat is simpler for JSONPath; nested mirrors document structure.

5. **GraalVM compatibility**: Does commonmark-java work cleanly with GraalVM native-image compilation (the `native` Maven profile)?

6. **Joint implementation with HTML**: Should both proposals be bundled in a single PR to ensure the shared `tables` convention is consistent, or implemented independently?
