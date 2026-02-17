package io.naftiko.cli;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

import io.naftiko.cli.enums.CapabilityType;
import io.naftiko.cli.enums.FileFormat;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class FileGenerator {
    public static void generateCapabilityFile(String capabilityName, CapabilityType template, FileFormat format, String targetUri, String port) throws IOException {
        String templatePath = "templates/capability." + template.pathName + "." + format.pathName + ".mustache";
        String outputFileName = capabilityName + ".capability." + format.pathName;
        
        // Load template from resources.
        InputStream templateStream = FileGenerator.class
            .getClassLoader()
            .getResourceAsStream(templatePath);
        if (templateStream == null) {
            throw new FileNotFoundException("Template not found: " + templatePath);
        }
        
        // Render template and write file.
        Template mustache = Mustache.compiler().compile(new InputStreamReader(templateStream));
        Map<String, Object> scope = new HashMap<>();
        scope.put("capabilityName", capabilityName);
        scope.put("port", port);
        scope.put("targetUri", targetUri);
        scope.put("username", "{{username}}"); // Let this keyword as is.
        Path outputPath = Paths.get(outputFileName);
        try (Writer writer = Files.newBufferedWriter(outputPath)) {
            mustache.execute(scope, writer);
            writer.flush();
        }
        
        System.out.println("✓ File created successfully: " + outputPath.toAbsolutePath());
    }
}