package io.naftiko.cli;

import picocli.CommandLine.Command;

@Command(
    name = "create",
    aliases = {"c", "cr"},
    description = "Create resources",
    subcommands = {CreateCapabilityCommand.class}
)
public class CreateCommand implements Runnable {
    
    @Override
    public void run() {
        System.out.println("Use 'naftiko create --help' for available options");
    }
}
