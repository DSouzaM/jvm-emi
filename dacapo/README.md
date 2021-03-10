# Setting up DaCapo

DaCapo uses custom class loaders to load its harness classes and benchmark classes from directories/jars within the main DaCapo jar.
These make it difficult to use EMI mutants without updating the jar (which takes an inordinate amount of time).

Fortunately, each test harness can override the class loader used to load benchmark classes. MutatedClassLoader.java contains a
simple class loader which will first try to load a class from a specific path (provided by `-Dmutated_path`) before delegating
to its parent. By modifying a test harness (e.g., Batik.java) to use this loader, re-compiling both classes, and adding them to
the proper location in the DaCapo jar, we can make changes to benchmark classes and run DaCapo with them without having to
update the jar for each EMI mutant.
