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
    private final Path support;


    public MutantRegistry(String root) throws MutantRegistryException, IOException {
        this.root = checkDirectory(Paths.get(root));
        this.mutants = checkDirectory(this.root.resolve("mutants"));
        this.seed = checkDirectory(this.root.resolve("seed"));
        this.support = checkDirectory(this.root.resolve("support"));
    }

    public Path getSeed() {
        return seed;
    }

    public List<Path> getSupportingJars() throws IOException, MutantRegistryException{
        return Files.list(checkDirectory(support.resolve("jar")))
                .filter((path) -> path.toString().endsWith(".jar"))
                .collect(Collectors.toList());
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
