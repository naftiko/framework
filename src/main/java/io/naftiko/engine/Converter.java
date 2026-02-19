/**
 * Copyright 2025-2026 Naftiko
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.naftiko.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.databind.MappingIterator;
import org.apache.avro.Schema;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.restlet.representation.Representation;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import io.naftiko.spec.OutputParameterSpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

/**
 * Utility class for converting between different data formats (XML, YAML, CSV, Protocol Buffer,
 * Avro) and JSON, with support for JSONPath extraction.
 */
public class Converter {

    /** Convert various formats to JSON */
    public static JsonNode convertToJson(String format, String schema, Representation entity)
            throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = null;

        // Convert based on outputRawFormat
        if ("XML".equalsIgnoreCase(format)) {
            root = Converter.convertXmlToJson(entity.getReader());
        } else if ("Protobuf".equalsIgnoreCase(format)) {
            if (schema == null || schema.isEmpty()) {
                throw new IOException(
                        "Protobuf format requires outputSchema to be specified in operation specification");
            }
            root = Converter.convertProtobufToJson(entity.getStream(), schema);
        } else if ("Avro".equalsIgnoreCase(format)) {
            if (schema == null || schema.isEmpty()) {
                throw new IOException(
                        "Avro format requires outputSchema to be specified in operation specification");
            }
            root = Converter.convertAvroToJson(entity.getStream(), schema);
        } else if ("YAML".equalsIgnoreCase(format)) {
            // YAML is text-based; use the reader to parse to JsonNode
            root = Converter.convertYamlToJson(entity.getReader());
        } else if ("CSV".equalsIgnoreCase(format)) {
            // CSV is text-based; use the reader to parse to JsonNode
            root = Converter.convertCsvToJson(entity.getReader());
        } else if ("JSON".equalsIgnoreCase(format) || format == null) {
            root = mapper.readTree(entity.getReader());
        } else {
            throw new IOException("Unsupported \"" + format + "\" format specified");
        }

        return root;
    }

    /**
     * Convert XML input stream to JsonNode using Jackson XML support.
     * 
     * @param xmlReader Reader containing XML data
     * @return JsonNode representing the parsed XML structure
     * @throws IOException if XML parsing fails
     */
    public static JsonNode convertXmlToJson(Reader xmlReader) throws IOException {
        XmlMapper xmlMapper = new XmlMapper();
        return xmlMapper.readTree(xmlReader);
    }

    /**
     * Convert YAML input stream to JsonNode using Jackson YAML support. No schema is required for
     * YAML parsing.
     * 
     * @param yamlReader Reader containing YAML data
     * @return JsonNode representing the parsed YAML structure
     * @throws IOException if YAML parsing fails
     */
    public static JsonNode convertYamlToJson(Reader yamlReader) throws IOException {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        return yamlMapper.readTree(yamlReader);
    }

    /**
     * Convert CSV input (reader) to a JsonNode (array of objects) using Jackson CSV support.
     * Assumes first row contains headers. Returns an ArrayNode where each element is an object
     * mapping header->value for that row.
     *
     * @param csvReader Reader containing CSV data
     * @return JsonNode (ArrayNode) representing CSV rows
     * @throws IOException if CSV parsing fails
     */
    public static JsonNode convertCsvToJson(Reader csvReader) throws IOException {
        CsvMapper csvMapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();

        MappingIterator<JsonNode> it =
                csvMapper.readerFor(JsonNode.class).with(schema).readValues(csvReader);

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arr = mapper.createArrayNode();

        while (it.hasNext()) {
            JsonNode row = it.next();
            arr.add(row);
        }

        return arr;
    }

    /**
     * Convert Protocol Buffer input stream to JsonNode using Jackson Protobuf support. Loads the
     * proto schema file from local filesystem or classpath resources and uses it to deserialize the
     * binary data.
     * 
     * Schema resolution order: 1. Local filesystem: {schemaFilename} (path relative to current
     * working directory) 2. Classpath resource: {schemaFilename} (resource path)
     * 
     * Users can include folder names in the outputSchema value (e.g.,
     * "schemas/test-records.proto"). This supports both development (classpath resources) and
     * production (Docker volumes).
     * 
     * @param protoBufInputStream InputStream containing binary Protocol Buffer data
     * @param schemaFilename path to the .proto schema file to load
     * @return JsonNode representing the parsed Protocol Buffer data
     * @throws IOException if Proto parsing fails or schema file not found in any location
     */
    public static JsonNode convertProtobufToJson(InputStream protoBufInputStream,
            String schemaFilename) throws IOException {
        try {
            InputStream schemaInputStream = loadSchemaFile(schemaFilename);

            if (schemaInputStream == null) {
                throw new IOException("Proto schema file not found: " + schemaFilename
                        + " (searched in current directory and classpath)");
            }

            // Parse the schema
            ProtobufSchema schema = ProtobufSchemaLoader.std.load(schemaInputStream);

            // Create a ProtobufMapper with the schema and deserialize the binary data to JsonNode
            ProtobufMapper mapper = new ProtobufMapper();
            return mapper.readerFor(JsonNode.class).with(schema).readValue(protoBufInputStream);
        } catch (IOException e) {
            throw new IOException("Failed to deserialize Protocol Buffer: " + e.getMessage(), e);
        }
    }

    /**
     * Convert Avro binary data to JsonNode using Jackson Avro support. Loads the Avro schema file
     * from local filesystem or classpath resources and uses it to deserialize the binary data.
     * 
     * Schema resolution order: 1. Local filesystem: {schemaFilename} (path relative to current
     * working directory) 2. Classpath resource: {schemaFilename} (resource path)
     * 
     * Users can include folder names in the outputSchema value (e.g., "schemas/records.avsc"). This
     * supports both development (classpath resources) and production (Docker volumes).
     * 
     * @param avroInputStream InputStream containing binary Avro data
     * @param schemaFilename path to the .avsc (Avro schema) file to load
     * @return JsonNode representing the parsed Avro data
     * @throws IOException if Avro parsing fails or schema file not found in any location
     */
    public static JsonNode convertAvroToJson(InputStream avroInputStream, String schemaFilename)
            throws IOException {
        try {
            InputStream schemaInputStream = loadSchemaFile(schemaFilename);

            if (schemaInputStream == null) {
                throw new IOException("Avro schema file not found: " + schemaFilename
                        + " (searched in current directory and classpath)");
            }

            // Parse the Avro schema
            Schema schema = new Schema.Parser().parse(schemaInputStream);

            // Decode the Avro binary data
            Decoder decoder = DecoderFactory.get().binaryDecoder(avroInputStream, null);
            DatumReader<GenericRecord> datumReader = new GenericDatumReader<>(schema);
            GenericRecord record = datumReader.read(null, decoder);

            // Convert GenericRecord to JsonNode using AvroMapper
            AvroMapper mapper = new AvroMapper();
            return mapper.valueToTree(record);
        } catch (IOException e) {
            throw new IOException("Failed to deserialize Avro data: " + e.getMessage(), e);
        }
    }

    /**
     * Load a schema file from local filesystem or classpath resources. Attempts to load from local
     * filesystem first, then falls back to classpath resources.
     * 
     * @param schemaFilename the path to the schema file to load (can include folder names)
     * @return InputStream for the schema file, or null if not found anywhere
     * @throws IOException if there are I/O errors reading the file
     */
    public static InputStream loadSchemaFile(String schemaFilename) throws IOException {
        // Try loading from local filesystem first (for Docker/production environments)
        java.io.File localFile = new java.io.File(schemaFilename);
        if (localFile.exists() && localFile.isFile()) {
            return new java.io.FileInputStream(localFile);
        }

        // Fall back to classpath resources (for development/testing)
        InputStream resourceStream =
                Converter.class.getClassLoader().getResourceAsStream(schemaFilename);

        return resourceStream;
    }

    /**
     * Apply maximum length constraint to a JsonNode string value if specified in the spec.
     * 
     * @param spec The output parameter specification containing maxLength constraint
     * @param node The JsonNode to potentially truncate
     * @return The node truncated to maxLength if applicable, or the original node
     */
    public static JsonNode applyMaxLengthIfNeeded(OutputParameterSpec spec, JsonNode node) {
        if (node == null || node.isNull() || spec == null) {
            return node;
        }

        String maxLength = spec.getMaxLength();
        if (maxLength != null && node.isTextual()) {
            try {
                int max = Integer.parseInt(maxLength);
                String s = node.asText();
                if (s.length() > max) {
                    return new ObjectMapper().getNodeFactory().textNode(s.substring(0, max));
                }
            } catch (NumberFormatException nfe) {
                // ignore invalid maxLength
            }
        }

        return node;
    }

    /**
     * Simple JSON path extractor supporting paths like $.a.b[0].c — where $ refers to the provided
     * root node (not the entire document unless that is the root).
     * 
     * @param root The root JsonNode to extract from
     * @param mapping The JSONPath expression
     * @return The extracted JsonNode, or NullNode if path not found
     */
    public static JsonNode jsonPathExtract(JsonNode root, String mapping) {
        if (mapping == null || mapping.isEmpty()) {
            return NullNode.instance;
        }

        String m = mapping.trim();

        if (m.equals("$") || m.equals("$.")) {
            return root == null ? NullNode.instance : root;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            Configuration config =
                    Configuration.builder().jsonProvider(new JacksonJsonNodeJsonProvider(mapper))
                            .mappingProvider(new JacksonMappingProvider(mapper)).build();
            return JsonPath.using(config).parse(root).read(m, JsonNode.class);
        } catch (PathNotFoundException pnfe) {
            return NullNode.instance;
        } catch (Exception e) {
            // If the path contains properties with spaces, try to fix it by converting to bracket
            // notation
            if (e.getMessage() != null && e.getMessage().contains("bracket notion")) {
                String fixedMapping = fixJsonPathWithSpaces(m);

                if (!fixedMapping.equals(m)) {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        Configuration config = Configuration.builder()
                                .jsonProvider(new JacksonJsonNodeJsonProvider(mapper))
                                .mappingProvider(new JacksonMappingProvider(mapper)).build();
                        return JsonPath.using(config).parse(root).read(fixedMapping,
                                JsonNode.class);
                    } catch (Exception e2) {
                        // If the fix didn't work, return null
                        return NullNode.instance;
                    }
                }
            }
            // For any other exception, return null
            return NullNode.instance;
        }
    }

    /**
     * Convert JSONPath expressions with spaces in property names to bracket notation. For example:
     * $.foo bar.baz becomes $['foo bar'].baz
     * 
     * @param mapping The JSONPath expression to fix
     * @return The fixed JSONPath expression with bracket notation for properties with spaces
     */
    public static String fixJsonPathWithSpaces(String mapping) {
        if (mapping == null || !mapping.contains(" ")) {
            return mapping;
        }

        // Pattern to match dot-notation properties that contain spaces (not already in brackets)
        // Matches: . followed by characters that aren't special chars but contain a space
        String result = mapping;

        // Replace patterns like .foo bar with .['foo bar']
        // Using regex to find dot followed by a property name with spaces
        result = result.replaceAll("\\.([^.\\[\\]]+\\s+[^.\\[\\]]*)(?=[\\].\\[$]|$)", ".['$1']");

        return result;
    }
}
