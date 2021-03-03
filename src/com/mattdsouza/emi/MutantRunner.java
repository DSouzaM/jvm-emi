package com.mattdsouza.emi;

import org.apache.commons.cli.*;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class MutantRunner {
    public static void main(String[] args) throws Exception {
        CommandLine options = parseOptions(args);
        String registryPath = options.getOptionValue("registry");
        String variant = options.getOptionValue("variant");

        if (options.hasOption("profile")) {
            profileMutant(registryPath, variant);
        } else {
            runMutant(registryPath, variant);
        }
    }

    private static void runJavaClass(String clazz, Path classPath, List<String> jvmArgs, List<String> programArgs, Path input, Path output) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("java");

        if (classPath != null) {
            command.add("-cp");
            command.add(classPath.toString());
        }

        if (jvmArgs != null) {
            command.addAll(jvmArgs);
        }

        command.add(clazz);

        if (programArgs != null) {
            command.addAll(programArgs);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        if (input != null) {
            pb = pb.redirectInput(input.toFile());
        }
        if (output != null) {
            pb = pb.redirectOutput(output.toFile());
        }
        Process p = pb.start();
        int result = p.waitFor();
        if (result != 0) {
            String error = String.format("Java program %s failed with exit code %d.", clazz, result);
            System.err.println("STDOUT:");
            System.err.println(error);
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ( (line = br.readLine()) != null)
                System.err.println(line);
            System.err.println("STDERR:");
            System.err.println(error);
            br = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ( (line = br.readLine()) != null)
                System.err.println(line);

            throw new Exception(error);
        }
    }

    public static void runMutant(String registryPath, String variant) throws Exception {
        MutantRegistry registry = new MutantRegistry(registryPath);
        Path mutantPath = registry.getMutant(variant);
        Path outputDirectory = registry.getMutantOutputPath(variant);
        for (Path input : registry.getInputs()) {
            String inputName = input.getFileName().toString();
            // TODO: make class configurable
            runJavaClass("example.Main", mutantPath, null, null, input, outputDirectory.resolve(inputName));
        }
    }

    public static void profileMutant(String registryPath, String variant) throws Exception {
        MutantRegistry registry = new MutantRegistry(registryPath);
        Path mutantPath = registry.getMutant(variant);

        Path mutantBinaryProfilePath = mutantPath.resolve("jacoco.exec");
        // Run mutant with agent
        for (Path input : registry.getInputs()) {
            String javaAgentArg = String.format("-javaagent:lib/jacocoagent.jar=destfile=%s", mutantBinaryProfilePath.toString());
            List<String> jvmArgs = new ArrayList<>();
            jvmArgs.add(javaAgentArg);
            // TODO: make class configurable
            runJavaClass("example.Main", mutantPath, jvmArgs, null, input, null);
        }

        // Generate XML report from result
        List<String> reportArgs = new ArrayList<>();
        reportArgs.add("report");
        reportArgs.add(mutantBinaryProfilePath.toString());
        reportArgs.add("--classfiles");
        reportArgs.add(mutantPath.toString());
        reportArgs.add("--xml");
        reportArgs.add(registry.getMutantProfilePath(variant).toString());

        runJavaClass(
                "org.jacoco.cli.internal.Main",
                Paths.get("lib/jacococli.jar"),
                null,
                reportArgs,
                null,
                null
        );
    }


    static CommandLine parseOptions(String[] args) {
        Options options = new Options();

        Option registryDirectory = new Option("r", "registry", true, "Path to registry folder");
        registryDirectory.setRequired(true);
        options.addOption(registryDirectory);

        Option variant = new Option("v", "variant", true, "Variant for which to check output");
        variant.setRequired(true);
        options.addOption(variant);

        Option profile = new Option("p", "profile", false,"Whether or not to profile the run");
        options.addOption(profile);

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
