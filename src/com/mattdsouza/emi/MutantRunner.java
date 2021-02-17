package com.mattdsouza.emi;

import org.apache.commons.cli.*;

import java.io.BufferedOutputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MutantRunner {
    public static void main(String[] args) throws Exception {
        CommandLine options = parseOptions(args);
        String registryPath = options.getOptionValue("registry");
        String variant = options.getOptionValue("variant");
        runMutant(registryPath, variant);
    }


    public static void runMutant(String registryPath, String variant) throws Exception {
        MutantRegistry registry = new MutantRegistry(registryPath);
        Path mutantPath = registry.getMutant(variant);
        Path outputDirectory = registry.getMutantOutputPath(variant);
        for (Path input : registry.getInputs()) {
            String inputName = input.getFileName().toString();
            List<String> command = new ArrayList<>();
            command.add("java");
            command.add("-cp");
            command.add(mutantPath.toString());
            command.add("example.Main"); // TODO: make configurable

            ProcessBuilder pb = new ProcessBuilder(command);
            // TODO: output other than stdout
            pb = pb.redirectOutput(outputDirectory.resolve(inputName).toFile());
            pb = pb.redirectInput(input.toFile());
            pb.start().waitFor();
        }
    }


    static CommandLine parseOptions(String[] args) {
        Options options = new Options();

        Option registryDirectory = new Option("r", "registry", true, "Path to registry folder");
        registryDirectory.setRequired(true);
        options.addOption(registryDirectory);

        Option variant = new Option("v", "variant", true, "Variant for which to check output");
        variant.setRequired(true);
        options.addOption(variant);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        try {
            return parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp(OutputChecker.class.getName(), options);
            System.exit(1);
        }
        return null;
    }
}
