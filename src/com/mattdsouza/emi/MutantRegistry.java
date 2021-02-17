package com.mattdsouza.emi;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Abstraction over a directory which persists files from the EMI process.
// Contains:
//  - seed/ folder with the base (unmodified) program
//  - inputs/ folder containing sets of command line inputs
//  - outputs/ folder containing the corresponding output for each input
//  - mutants/ folder containing generated mutants (if any)
//  - any other data necessary for execution
public class MutantRegistry {
    public static class MutantRegistryException extends Exception {
        MutantRegistryException(String message) {
            super(message);
        }
    }

    private final Path root;
    private final Path mutants;
    private final Path seed;
    private List<Path> inputs;
    private final Map<String, Path> expectedOutputs;


    public MutantRegistry(String root) throws MutantRegistryException, IOException {
        this.root = checkDirectory(Paths.get(root));
        this.mutants = checkDirectory(this.root.resolve("mutants"));
        this.seed = checkDirectory(this.root.resolve("seed"));
        this.inputs = Collections.unmodifiableList(
                Files.list(checkDirectory(this.seed.resolve("inputs"))).collect(Collectors.toList()));
        Path outputDirectory = checkDirectory(this.seed.resolve("outputs"));

        // Validate that each input has an output
        Map<String, Path> expectedOutputs = new HashMap<>();
        for (Path input : inputs) {
            Path output = checkFile(outputDirectory.resolve(input.getFileName()));
            expectedOutputs.put(input.getFileName().toString(), output);
        }
        this.expectedOutputs = Collections.unmodifiableMap(expectedOutputs);

    }

    public Path getSeed() {
        return seed;
    }

    public List<Path> getInputs() {
        return inputs;
    }

    public Map<String, Path> getExpectedOutputs() {
        return expectedOutputs;
    }

    public Path getMutant(String mutant) throws MutantRegistryException {
        return mutant.equals("seed") ? getSeed() : checkDirectory(mutants.resolve(mutant));
    }

    public Path createMutant(String mutant) throws MutantRegistryException {
        Path newPath = mutants.resolve(mutant);
        if (Files.exists(newPath)) {
            throw new MutantRegistryException("Path " + newPath.toString() + " already exists.");
        }
        return newPath;
    }

    public Path getMutantOutputPath(String mutant) throws MutantRegistryException, IOException {
        Path result = getMutant(mutant).resolve("outputs");
        Files.createDirectories(result);
        return checkDirectory(result);
    }

    public Map<String, Path> getMutantOutputs(String mutant) throws MutantRegistryException, IOException {
        Map<String, Path> result = new HashMap<>();
        Path outputDirectory = getMutantOutputPath(mutant);
        for (String input : expectedOutputs.keySet()) {
            Path output = checkFile(outputDirectory.resolve(input));
            result.put(input, output);
        }
        return result;
    }

    private static Path checkFile(Path p) throws MutantRegistryException {
        if (!Files.exists(p) || !Files.isRegularFile(p)) {
            throw new MutantRegistryException("Path " + p.toString() + " does not exist or is not a file.");
        }
        return p;
    }

    private static Path checkDirectory(Path p) throws MutantRegistryException {
        if (!Files.exists(p) || !Files.isDirectory(p)) {
            throw new MutantRegistryException("Path " + p.toString() + " does not exist or is not a directory.");
        }
        return p;
    }


}
