# HTML Output Format Support Proposal
## Native HTML-to-JSON Conversion for the HTTP Consumes Adapter

**Status**: Proposal  
**Date**: March 26, 2026  
**Key Concept**: Add `html` as a native `outputRawFormat` in the HTTP consumes adapter, enabling capability authors to extract structured data (tables, lists) from HTML pages using the existing JSONPath mediation layer — no Java code required.

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Current Pipeline](#current-pipeline)
3. [Why HTML Fits the Existing Architecture](#why-html-fits-the-existing-architecture)
4. [Design: Table-Aware Conversion](#design-table-aware-conversion)
5. [CSS Selector Scoping via `outputSchema`](#css-selector-scoping-via-outputschema)
6. [Capability YAML Example](#capability-yaml-example)
7. [Implementation Changes](#implementation-changes)
8. [Converter Method Sketch](#converter-method-sketch)
9. [Tradeoffs vs. External Proxy](#tradeoffs-vs-external-proxy)
10. [Limitations & Future Extensions](#limitations--future-extensions)
11. [Open Questions for Discussion](#open-questions-for-discussion)

---

## Executive Summary

### Problem

Many useful data sources serve HTML pages (e.g. status dashboards, public directories, pricing tables) rather than structured APIs. Today, consuming these sources requires deploying an intermediary proxy service that scrapes HTML and re-exposes it as JSON — adding operational overhead outside the framework.

### Proposal

Add `"html"` to the `outputRawFormat` enum, backed by [JSoup](https://jsoup.org/) for lenient HTML parsing. The converter produces a predictable JSON tree from HTML tables, which then flows through the existing JSONPath mediation and output mapping layer unchanged.

### Key Benefit

A capability author writes `outputRawFormat: "html"` and JSONPath mappings — identical developer experience to `xml` or `csv`. No Java code, no proxy service, no extra infrastructure.

---

## Current Pipeline

Every format in Naftiko goes through the same three-stage mediation chain:

```
HTTP response (raw bytes)
  → Converter.convertToJson(format, schema, entity)        // Stage 1: normalize to JsonNode
    → Resolver.resolveOutputMappings(spec, root, mapper)   // Stage 2: JSONPath extraction
      → Converter.jsonPathExtract(root, "$.some.path")     // Stage 3: shape output
        → REST / MCP JSON response
```

Currently supported formats:

| Format | `outputRawFormat` | Schema Required | Parser |
|--------|-------------------|-----------------|--------|
| JSON | `json` (default) | No | Jackson ObjectMapper |
| XML | `xml` | No | Jackson XmlMapper |
| CSV | `csv` | No | Jackson CsvMapper |
| YAML | `yaml` | No | Jackson YAMLFactory |
| Protobuf | `protobuf` | Yes (`outputSchema`) | Jackson ProtobufMapper |
| Avro | `avro` | Yes (`outputSchema`) | Apache Avro + AvroMapper |

The key insight: **Stages 2 and 3 are format-agnostic.** They operate on `JsonNode` regardless of source format. Adding a new format only requires a new Stage 1 converter — the mediation layer works unchanged.

---

## Why HTML Fits the Existing Architecture

### Why not just use `outputRawFormat: xml`?

Real-world HTML is **not well-formed XML**. The existing `XmlMapper` will fail on:
- Unclosed tags (`<br>`, `<img>`, `<input>`)
- Missing attribute quotes (`<td class=price>`)
- Implicit closing (`<p>text<p>more text`)
- HTML entities (`&nbsp;`, `&mdash;`)

HTML requires a **lenient parser** that normalizes messy markup into a clean DOM before conversion.

### Why HTML tables specifically?

HTML tables are the most common structured data pattern on the web — they have an inherent schema (header row defines keys, data rows provide values). This maps directly to the JSON array-of-objects structure that the CSV converter already produces, making it a natural fit.

---

## Design: Table-Aware Conversion

The converter parses HTML, extracts `<table>` elements, and converts each into a JSON array of objects where column headers become keys:

### Input HTML

```html
<html>
<body>
  <table>
    <thead>
      <tr><th>Name</th><th>Price</th><th>Stock</th></tr>
    </thead>
    <tbody>
      <tr><td>Widget</td><td>$42</td><td>150</td></tr>
      <tr><td>Gadget</td><td>$99</td><td>30</td></tr>
    </tbody>
  </table>
</body>
</html>
```

### Output JSON (normalized `JsonNode`)

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

Each `<table>` becomes an entry in the `tables` array. Each entry is an array of objects (one per row), with keys derived from `<th>` cells in the header row. This mirrors the CSV converter's output shape.

---

## CSS Selector Scoping via `outputSchema`

The `outputSchema` field (already used by Avro and Protobuf for schema file paths) can be repurposed for HTML to carry a **CSS selector** that scopes the conversion to specific elements:

```yaml
outputRawFormat: "html"
outputSchema: "table.results"    # Only convert <table class="results">
```

This tells JSoup to `doc.select("table.results")` before converting, filtering out navigation, headers, footers, and other noise. Without `outputSchema`, all `<table>` elements are converted.

---

## Capability YAML Example

A complete capability that scrapes a product listing page:

```yaml
# yaml-language-server: $schema=../../main/resources/schemas/naftiko-schema.json
---
naftiko: "1.0.0-alpha1"
info:
  label: "Product Catalog Scraper"
  description: "Extracts product data from HTML catalog page"

capability:
  exposes:
    - type: "rest"
      address: "localhost"
      port: 9090
      namespace: "catalog"
      resources:
        - path: "/products"
          name: "products"
          operations:
            - method: "GET"
              call: "vendor.get-catalog"
              outputParameters:
                - type: "array"
                  mapping: "$.tables[0]"          # first table on the page
                  items:
                    - type: "object"
                      properties:
                        name:
                          type: "string"
                          mapping: "$.Name"        # header-derived key
                        price:
                          type: "string"
                          mapping: "$.Price"
                        stock:
                          type: "string"
                          mapping: "$.Stock"

  consumes:
    - type: "http"
      namespace: "vendor"
      baseUri: "https://vendor.example.com"
      resources:
        - path: "/catalog"
          name: "catalog"
          operations:
            - method: "GET"
              name: "get-catalog"
              outputRawFormat: "html"              # ← new format
              outputSchema: "table.products"       # ← CSS selector (optional)
```

The developer experience is identical to the XML or CSV patterns — declare the format, write JSONPath mappings, done.

---

## Implementation Changes

| Component | Change |
|---|---|
| `pom.xml` | Add `org.jsoup:jsoup` dependency |
| `Converter.java` | Add `convertHtmlToJson(Reader, String)` method + `"HTML"` branch in `convertToJson()` |
| `naftiko-schema.json` | Add `"html"` to the `outputRawFormat` enum |
| `ConverterTest.java` | Unit tests for table conversion, CSS selector scoping, edge cases |
| `HtmlIntegrationTest.java` | End-to-end test with mock HTML server |
| `html-capability.yaml` | Test fixture |
| `sample-products.html` | Test HTML fixture |

---

## Converter Method Sketch

```java
public static JsonNode convertHtmlToJson(Reader htmlReader, String cssSelector)
        throws IOException {
    String html = readFully(htmlReader);
    Document doc = Jsoup.parse(html);

    Elements tables;
    if (cssSelector != null && !cssSelector.isEmpty()) {
        tables = doc.select(cssSelector);
    } else {
        tables = doc.select("table");
    }

    ObjectMapper mapper = new ObjectMapper();
    ArrayNode tablesArray = mapper.createArrayNode();

    for (Element table : tables) {
        // Extract headers from <th> cells
        Elements headerCells = table.select("thead th");
        if (headerCells.isEmpty()) {
            headerCells = table.select("tr:first-child th");
        }
        List<String> headers = headerCells.stream()
                .map(Element::text)
                .collect(Collectors.toList());

        // Extract data rows
        Elements rows = table.select("tbody tr");
        if (rows.isEmpty()) {
            rows = table.select("tr");
            if (!headers.isEmpty()) {
                rows = rows.subList(1, rows.size()); // skip header row
            }
        }

        ArrayNode rowsArray = mapper.createArrayNode();
        for (Element row : rows) {
            Elements cells = row.select("td");
            ObjectNode obj = mapper.createObjectNode();
            for (int i = 0; i < Math.min(headers.size(), cells.size()); i++) {
                obj.put(headers.get(i), cells.get(i).text());
            }
            rowsArray.add(obj);
        }
        tablesArray.add(rowsArray);
    }

    ObjectNode result = mapper.createObjectNode();
    result.set("tables", tablesArray);
    return result;
}
```

Integration into `convertToJson()`:

```java
} else if ("HTML".equalsIgnoreCase(format)) {
    root = Converter.convertHtmlToJson(entity.getReader(), schema);
}
```

Here `schema` carries the CSS selector string (or `null` if unspecified).

---

## Tradeoffs vs. External Proxy

| Dimension | Native `html` format | Proxy service |
|---|---|---|
| **Setup** | Zero — `outputRawFormat: html` | Deploy & maintain a service |
| **Developer experience** | Same as `xml`/`csv` — pure YAML | Write + host custom code |
| **Flexibility** | Tables + CSS selectors | Unlimited (custom DOM logic) |
| **New dependency** | JSoup (~400 KB) | External runtime |
| **Infrastructure** | None | Network hop + monitoring |
| **Non-table HTML** | Not supported (see below) | Full control |

For the common case — extracting structured data from HTML tables — the native approach is simpler and keeps everything declarative. For arbitrary DOM scraping or complex page interactions (JavaScript rendering, pagination), an external proxy remains more appropriate.

---

## Limitations & Future Extensions

### Current Scope (This Proposal)

- **Tables only**: Converts `<table>` elements to JSON arrays of objects
- **Static HTML**: No JavaScript rendering (server-side HTML only)
- **Text content**: Extracts `Element.text()` — no nested HTML or attributes

### Potential Future Extensions

- **List extraction**: Convert `<ul>`/`<ol>` elements to JSON arrays
- **Definition lists**: Convert `<dl>` elements to JSON objects (`<dt>` → key, `<dd>` → value)
- **Attribute access**: Include element attributes (e.g., `href` from `<a>` tags) via a configurable option
- **Nested tables**: Recursive table-in-table conversion

These could be added incrementally without schema changes — the `outputSchema` CSS selector mechanism is already flexible enough to target any element type.

---

## Open Questions for Discussion

1. **CSS selector field**: Is reusing `outputSchema` for CSS selectors acceptable, or should a new field (e.g., `outputSelector`) be introduced?

2. **Tables without headers**: Should tables lacking `<th>` rows fall back to index-based keys (`"0"`, `"1"`, `"2"`) or be skipped?

3. **Multiple tables**: Is the `$.tables[N]` indexing approach intuitive, or would named extraction (via CSS selector per table) be preferred?

4. **Non-table data**: Should the first version include basic list/definition-list support, or is table-only sufficient for initial release?

5. **GraalVM native image**: Does JSoup work cleanly with GraalVM native-image compilation (the `native` Maven profile), or does it require reflection configuration?
