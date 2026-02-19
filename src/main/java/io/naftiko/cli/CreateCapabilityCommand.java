package io.naftiko.cli;

import picocli.CommandLine.Command;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import io.naftiko.cli.enums.CapabilityType;
import io.naftiko.cli.enums.FileFormat;

@Command(
    name = "capability",
    mixinStandardHelpOptions = true,
    aliases = {"cap"},
    description = "Create a new capability configuration file"
)
public class CreateCapabilityCommand implements Runnable {
    
    @Override
    public void run() {
        try {
            Scanner scanner = new Scanner(System.in);
            
            // Capability name.
            System.out.print("Type your capability name: ");
            String capabilityName = scanner.nextLine().trim();
            if (capabilityName.isEmpty()) {
                System.err.println("Error: capability name cannot be empty");
                System.exit(1);
            }

            // Capability template.
            List<String> templates = Arrays.asList(CapabilityType.PASS_THRU.label, CapabilityType.REST_ADAPTER.label);
            String template = InteractiveMenu.showMenu("Choose the capability template:", templates);

            // File format.
            List<String> formats = Arrays.asList(FileFormat.YAML.label, FileFormat.JSON.label);
            String format = InteractiveMenu.showMenu("Choose file format:", formats);

            // Target URI.
            System.out.print("Type the targted URI: ");
            String targetUri = scanner.nextLine().trim();
            if (targetUri.isEmpty()) {
                System.err.println("Error: targetUri cannot be empty");
                System.exit(1);
            }

            // Port.
            System.out.print("Type your capability exposition port: ");
            String port = scanner.nextLine().trim();
            if (port.isEmpty()) {
                System.err.println("Error: port cannot be empty");
                System.exit(1);
            }
            
            System.out.println("Creating capability: " + capabilityName + " " + template + " " + format + " " + targetUri + " " + port);
            FileGenerator.generateCapabilityFile(capabilityName, CapabilityType.valueOfLabel(template), FileFormat.valueOfLabel(format), targetUri, port);
            
            scanner.close();
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

}
