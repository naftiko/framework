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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.restlet.data.MediaType;
import org.restlet.representation.StringRepresentation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import io.naftiko.spec.OutputParameterSpec;

public class ConverterTest {

    @TempDir
    Path tempDir;

    @Test
    public void convertToJsonShouldRejectUnsupportedFormat() {
        StringRepresentation entity =
                new StringRepresentation("{}", MediaType.APPLICATION_JSON);

        IOException error = assertThrows(IOException.class,
                () -> Converter.convertToJson("INI", null, entity));

        assertEquals("Unsupported \"INI\" format specified", error.getMessage());
    }

    @Test
    public void convertToJsonShouldRequireSchemaForProtobuf() {
        StringRepresentation entity =
                new StringRepresentation("{}", MediaType.APPLICATION_OCTET_STREAM);

        IOException error = assertThrows(IOException.class,
                () -> Converter.convertToJson("Protobuf", null, entity));

        assertEquals(
                "Protobuf format requires outputSchema to be specified in operation specification",
                error.getMessage());
    }

    @Test
    public void convertToJsonShouldRequireSchemaForProtobufWhenSchemaIsEmpty() {
        StringRepresentation entity =
                new StringRepresentation("{}", MediaType.APPLICATION_OCTET_STREAM);

        IOException error = assertThrows(IOException.class,
                () -> Converter.convertToJson("Protobuf", "", entity));

        assertEquals(
                "Protobuf format requires outputSchema to be specified in operation specification",
                error.getMessage());
    }

    @Test
    public void convertToJsonShouldRequireSchemaForAvro() {
        StringRepresentation entity =
                new StringRepresentation("{}", MediaType.APPLICATION_OCTET_STREAM);

        IOException error = assertThrows(IOException.class,
                () -> Converter.convertToJson("Avro", "", entity));

        assertEquals("Avro format requires outputSchema to be specified in operation specification",
                error.getMessage());
    }

    @Test
    public void loadSchemaFileShouldPreferLocalFileAndSupportClasspathFallback() throws Exception {
        Path localSchema = tempDir.resolve("local-schema.avsc");
        Files.writeString(localSchema, "{\"type\":\"record\",\"name\":\"Local\",\"fields\":[]}");

        try (InputStream local = Converter.loadSchemaFile(localSchema.toString())) {
            assertNotNull(local);
            assertEquals('{', new String(local.readAllBytes(), StandardCharsets.UTF_8).charAt(0));
        }

                try (InputStream classpath = Converter.loadSchemaFile("schemas/test-records.avsc")) {
            assertNotNull(classpath);
            String content = new String(classpath.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(true, content.contains("record"));
        }
    }

    @Test
    public void applyMaxLengthIfNeededShouldTruncateAndIgnoreInvalidLengths() throws Exception {
        OutputParameterSpec truncatedSpec = new OutputParameterSpec();
        truncatedSpec.setMaxLength("5");

        JsonNode truncated = Converter.applyMaxLengthIfNeeded(truncatedSpec,
                new com.fasterxml.jackson.databind.ObjectMapper().readTree("\"abcdefgh\""));
        assertEquals("abcde", truncated.asText());

        OutputParameterSpec invalidSpec = new OutputParameterSpec();
        invalidSpec.setMaxLength("abc");

        JsonNode untouched = Converter.applyMaxLengthIfNeeded(invalidSpec,
                new com.fasterxml.jackson.databind.ObjectMapper().readTree("\"abcdefgh\""));
        assertEquals("abcdefgh", untouched.asText());
    }

    @Test
    public void jsonPathExtractShouldSupportMissingPathsAndPropertiesWithSpaces() throws Exception {
        JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                {
                  "user details": {
                    "contact email": "alice@example.com"
                  }
                }
                """);

        JsonNode value = Converter.jsonPathExtract(root,
                "$['user details']['contact email']");
        JsonNode missing = Converter.jsonPathExtract(root, "$.missing.field");

        assertEquals("alice@example.com", value.asText());
        assertEquals(true, missing.isNull());
        assertEquals("$.['user details'].email",
                Converter.fixJsonPathWithSpaces("$.user details.email"));
    }

        @Test
        public void convertToJsonShouldSupportJsonWhenFormatIsNull() throws Exception {
                StringRepresentation entity =
                                new StringRepresentation("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON);

                JsonNode root = Converter.convertToJson(null, null, entity);

                assertEquals("ok", root.get("status").asText());
        }

        @Test
        public void convertToJsonShouldSupportExplicitJsonFormat() throws Exception {
                StringRepresentation entity =
                                new StringRepresentation("{\"status\":\"ok\",\"count\":2}",
                                                MediaType.APPLICATION_JSON);

                JsonNode root = Converter.convertToJson("JSON", null, entity);

                assertEquals("ok", root.get("status").asText());
                assertEquals(2, root.get("count").asInt());
        }

        @Test
        public void convertToJsonShouldSupportXmlYamlAndCsvFormats() throws Exception {
                StringRepresentation xmlEntity =
                                new StringRepresentation("<root><name>Alice</name></root>", MediaType.APPLICATION_XML);
                JsonNode xml = Converter.convertToJson("XML", null, xmlEntity);
                assertEquals("Alice", xml.get("name").asText());

                StringRepresentation yamlEntity = new StringRepresentation("name: Alice\nage: 42\n",
                                MediaType.valueOf("application/yaml"));
                JsonNode yaml = Converter.convertToJson("YAML", null, yamlEntity);
                assertEquals("Alice", yaml.get("name").asText());
                assertEquals(42, yaml.get("age").asInt());

                StringRepresentation csvEntity =
                                new StringRepresentation("name,age\nAlice,42\nBob,39\n", MediaType.TEXT_CSV);
                JsonNode csv = Converter.convertToJson("CSV", null, csvEntity);
                assertTrue(csv.isArray());
                assertEquals("Alice", csv.get(0).get("name").asText());
                assertEquals("39", csv.get(1).get("age").asText());
        }

        @Test
        public void convertProtobufToJsonShouldWrapMissingSchemaAsIOException() {
                ByteArrayInputStream payload = new ByteArrayInputStream(new byte[] {1, 2, 3});

                IOException error = assertThrows(IOException.class,
                                () -> Converter.convertProtobufToJson(payload, "schemas/does-not-exist.proto"));

                assertTrue(error.getMessage().contains("Failed to deserialize Protocol Buffer"));
                assertTrue(error.getMessage().contains("Proto schema file not found"));
        }

        @Test
        public void convertAvroToJsonShouldWrapMissingSchemaAsIOException() {
                ByteArrayInputStream payload = new ByteArrayInputStream(new byte[] {1, 2, 3});

                IOException error = assertThrows(IOException.class,
                                () -> Converter.convertAvroToJson(payload, "schemas/does-not-exist.avsc"));

                assertTrue(error.getMessage().contains("Failed to deserialize Avro data"));
                assertTrue(error.getMessage().contains("Avro schema file not found"));
        }

        @Test
        public void loadSchemaFileShouldReturnNullWhenSchemaCannotBeFound() throws Exception {
                InputStream stream = Converter.loadSchemaFile("schemas/does-not-exist.any");
                assertEquals(null, stream);
        }

        @Test
        public void jsonPathExtractShouldHandleRootSelectorsAndEmptyMapping() throws Exception {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree("{\"a\":1}");

                assertEquals(root, Converter.jsonPathExtract(root, "$"));
                assertEquals(root, Converter.jsonPathExtract(root, "$."));
                assertEquals(NullNode.instance, Converter.jsonPathExtract(root, ""));
                assertEquals(NullNode.instance, Converter.jsonPathExtract(root, null));
                assertEquals(NullNode.instance, Converter.jsonPathExtract(null, "$"));
        }

        @Test
        public void applyMaxLengthIfNeededShouldHandleNullSpecAndNonTextualValues() throws Exception {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode numberNode = mapper.readTree("123");
                JsonNode textNode = mapper.readTree("\"hello\"");

                assertEquals(numberNode, Converter.applyMaxLengthIfNeeded(new OutputParameterSpec(),
                                numberNode));
                assertEquals(textNode, Converter.applyMaxLengthIfNeeded(null, textNode));
                assertEquals(NullNode.instance,
                                Converter.applyMaxLengthIfNeeded(new OutputParameterSpec(), NullNode.instance));
        }

        @Test
        public void fixJsonPathWithSpacesShouldLeaveMappingsWithoutSpacesUntouched() {
                assertEquals(null, Converter.fixJsonPathWithSpaces(null));
                assertEquals("$.user.email", Converter.fixJsonPathWithSpaces("$.user.email"));
        }
}