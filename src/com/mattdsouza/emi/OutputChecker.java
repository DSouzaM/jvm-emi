package com.mattdsouza.emi;

import org.apache.commons.cli.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;


public class OutputChecker {
    public static void main(String[] args) throws Exception {
        CommandLine options = parseOptions(args);
        String registryPath = options.getOptionValue("registry");
        String variant = options.getOptionValue("variant");
        System.exit(checkOutput(registryPath, variant) ? 0 : 1);
    }


    public static boolean checkOutput(String registryPath, String variant) throws Exception {
        MutantRegistry registry = new MutantRegistry(registryPath);

        Map<String, Path> expected = registry.getExpectedOutputs();
        Map<String, Path> actual = registry.getMutantOutputs(variant);
        for (String input : expected.keySet()) {
            Path expectedOutput = expected.get(input);
            Path actualOutput = actual.get(input);
            if (!checkOneOutput(expectedOutput, actualOutput)) {
                System.out.printf("Detected a difference on input %s for variant %s. Exiting.\n", input, variant);
                return false;
            }
        }
        System.out.println("No differences detected.");
        return true;
    }

    private static boolean checkOneOutput(Path expected, Path actual) throws Exception {
        // TODO: expand to more interesting outputs
        String expectedContents = readFile(expected);
        String actualContents = readFile(actual);
        return expectedContents.equals(actualContents);
    }

    private static String readFile(Path path) throws IOException {
        byte[] encoded = Files.readAllBytes(path);
        return new String(encoded);
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
