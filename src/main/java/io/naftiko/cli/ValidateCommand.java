package io.naftiko.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@Command(
    name = "validate",
    aliases = {"v", "val"},
    description = "Validate a YAML or JSON file against a JSON Schema"
)
public class ValidateCommand implements Runnable {
    
    @Parameters(index = "0", description = "Path to the YAML or JSON file to validate")
    private String filePath;
    
    @Parameters(index = "1", description = "Path to the JSON Schema file")
    private String schemaPath;
    
    @Override
    public void run() {
        try {
            // Check files exist.
            Path fileToValidate = Paths.get(filePath);
            Path schemaFile = Paths.get(schemaPath);
            if (!Files.exists(fileToValidate)) {
                System.err.println("Error: File not found: " + filePath);
                System.exit(1);
            }
            if (!Files.exists(schemaFile)) {
                System.err.println("Error: Schema file not found: " + schemaPath);
                System.exit(1);
            }
            
            // Load file to validate and schema file.
            JsonNode dataNode = loadFile(fileToValidate.toFile());
            JsonNode schemaNode = loadFile(schemaFile.toFile());
            
            // Create JSON Schema validator
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            JsonSchema schema = factory.getSchema(schemaNode);
            
            // Validate.
            Set<ValidationMessage> errors = schema.validate(dataNode);
            
            // Display validation result.
            if (errors.isEmpty()) {
                System.out.println("✓ Validation successful!");
                System.out.println("  File: " + filePath);
                System.out.println("  Schema: " + schemaPath);
                System.out.println("  Status: OK");
            } else {
                System.err.println("✗ Validation failed!");
                System.err.println("  File: " + filePath);
                System.err.println("  Schema: " + schemaPath);
                System.err.println("\nErrors found:");
                
                int errorCount = 1;
                for (ValidationMessage error : errors) {
                    System.err.println("\n  [" + errorCount + "] " + error.getMessage());
                    System.err.println("      Path: " + error.getPath());
                    errorCount++;
                }
                System.exit(1);
            }
            
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Validation error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private JsonNode loadFile(File file) throws IOException {
        String fileName = file.getName().toLowerCase();
        
        if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
            // Parser YAML
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            return yamlMapper.readTree(file);
        } else if (fileName.endsWith(".json")) {
            // Parser JSON
            ObjectMapper jsonMapper = new ObjectMapper();
            return jsonMapper.readTree(file);
        } else {
            throw new IllegalArgumentException(
                "Unsupported file format. Only .yaml, .yml, and .json are supported."
            );
        }
    }
}