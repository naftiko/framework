package io.naftiko.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "naftiko",
    mixinStandardHelpOptions = true, 
    version = "1.0",
    description = "Naftiko CLI",
    subcommands = {CreateCommand.class, ValidateCommand.class}
)
public class Cli implements Runnable {    
    @Override
    public void run() {
        System.out.println("Use 'naftiko --help' for usage information");
    }
    
    public static void main(String[] args) {
        CommandLine command = new CommandLine(new Cli());
        int exitCode = command.execute(args);
        System.exit(exitCode);
    }
}
