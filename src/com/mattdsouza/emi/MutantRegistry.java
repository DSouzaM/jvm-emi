package com.mattdsouza.emi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
    private final Path seed;
    private final Map<Path, Path> expectedOutputs;


    public MutantRegistry(String root) throws MutantRegistryException, IOException {
        this.root = checkDirectory(Paths.get(root));
        this.seed = checkDirectory(this.root.resolve("seed"));
        Path[] inputs = Files.list(checkDirectory(this.root.resolve("inputs"))).toArray(Path[]::new);
        Path outputDirectory = checkDirectory(this.root.resolve("outputs"));

        Map<Path, Path> expectedOutputs = new HashMap<>();
        for (Path input : inputs) {
            Path output = checkFile(outputDirectory.resolve(input.getFileName()));
            expectedOutputs.put(input, output);
        }
        this.expectedOutputs = Collections.unmodifiableMap(expectedOutputs);

    }

    public Path getSeed() {
        return seed;
    }

    public Map<Path, Path> getExpectedOutputs() {
        return expectedOutputs;
    }

    public Path getVariant(String variant) throws MutantRegistryException {
        return checkDirectory(root.resolve(variant));
    }

    public Path createVariant(String variant) throws MutantRegistryException {
        Path newPath = root.resolve(variant);
        if (Files.exists(newPath)) {
            throw new MutantRegistryException("Path " + newPath.toString() + " already exists.");
        }
        return newPath;
    }

    private static Path checkFile(Path p) throws MutantRegistryException {
        if (!Files.exists(p) || !Files.isRegularFile(p)) {
            throw new MutantRegistryException("Path "  + p.toString() + " does not exist or is not a file.");
        }
        return p;
    }

    private static Path checkDirectory(Path p) throws MutantRegistryException {
        if (!Files.exists(p) || !Files.isDirectory(p)) {
            throw new MutantRegistryException("Path "  + p.toString() + " does not exist or is not a directory.");
        }
        return p;
    }


}
