package com.mattdsouza.emi;

import org.apache.commons.cli.*;
import org.xml.sax.SAXException;
import soot.*;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

// Entrypoint to generate a new EMI variant from an existing variant.
public class MutantGenerator {
    public static void main(String[] args) throws Exception {
        CommandLine options = parseOptions(args);
        String registryPath = options.getOptionValue("registry");
        String variant = options.getOptionValue("variant");
        String newVariant = options.getOptionValue("new-variant");
        String coverageFile = options.getOptionValue("coverage");
        generateMutant(registryPath, variant, newVariant, coverageFile);
    }

    public static void generateMutant(String registryPath, String variant, String newVariant, String coverageFile) throws Exception {
        MutantRegistry registry = new MutantRegistry(registryPath);
        String variantPath = registry.getMutant(variant).toString();

        String classPath = registry.getSupportingJars().stream()
                .map(Path::toString)
                .collect(Collectors.joining(":")) + ":" + variantPath;
        System.out.println("Classpath is " + classPath);
        String outputPath = registry.createMutant(newVariant).toString();

        List<String> sootOptions = new ArrayList<>();
        // Add classes to Soot classpath
        sootOptions.add("-cp");
        sootOptions.add(classPath);
        // Direct Soot to transform classes in directory
        sootOptions.add("-process-dir");
        sootOptions.add(variantPath);
        // Prepend Soot classpath to default classpath
        sootOptions.add("-pp");
        // Direct Soot to remember bytecode offsets
        sootOptions.add("-keep-bytecode-offset");
        // Output transformed results as classfiles
        sootOptions.add("-f");
        sootOptions.add("c");
        // Indicate output location for classfiles
        sootOptions.add("-d");
        sootOptions.add(outputPath);
        // Direct Soot to validate the output
//        sootOptions.add("-validate");

        // Parse coverage and add our transformer to the Soot pipeline
        BytecodeCoverage coverage = null;
        try {
            coverage = BytecodeCoverage.fromFile(coverageFile);
        } catch (ParserConfigurationException | IOException | SAXException e) {
            e.printStackTrace();
        }
        PackManager.v().getPack("jtp").add(new Transform("jtp.mytransform", new EMIMutator(coverage)));

        // Run Soot
        String[] sootArgs = new String[sootOptions.size()];
        soot.Main.main(sootOptions.toArray(sootArgs));
    }

    static CommandLine parseOptions(String[] args) {
        Options options = new Options();

        Option registryDirectory = new Option("r", "registry", true, "Path to registry folder");
        registryDirectory.setRequired(true);
        options.addOption(registryDirectory);

        Option variant = new Option("v", "variant", true, "Variant to mutate");
        variant.setRequired(true);
        options.addOption(variant);

        Option newVariant = new Option("n", "new-variant", true, "New variant name");
        newVariant.setRequired(true);
        options.addOption(newVariant);

        Option coverage = new Option("c", "coverage", true, "JaCoCo coverage report file (XML)");
        coverage.setRequired(true);
        options.addOption(coverage);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        try {
            return parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp(MutantGenerator.class.getName(), options);
            System.exit(1);
        }
        return null;
    }
}


