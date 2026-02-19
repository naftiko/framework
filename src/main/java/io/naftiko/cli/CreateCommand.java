package io.naftiko.cli;

import picocli.CommandLine.Command;

@Command(
    name = "create",
    mixinStandardHelpOptions = true,
    aliases = {"c", "cr"},
    description = "Create a naftiko resource. Look at the subcommands to know what kind of resource.",
    subcommands = {CreateCapabilityCommand.class}
)
public class CreateCommand implements Runnable {
    
    @Override
    public void run() {
        System.out.println("Use 'naftiko create --help' for available options");
    }
}
