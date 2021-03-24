package com.mattdsouza.emi.heaps;

import org.apache.commons.cli.*;
import soot.*;
import soot.jimple.Expr;
import soot.jimple.Jimple;
import soot.jimple.Stmt;
import soot.tagkit.BytecodeOffsetTag;
import soot.tagkit.Tag;

import java.util.*;

// Helper program add a heap dump instruction to a program.
// This is a utility program to run before starting an EMI campaign on some test program.
public class HeapDumpInstrumentor {
    public static void main(String[] args) {
        CommandLine options = parseOptions(args);
        String classPath = options.getOptionValue("classpath");
        String clazz = options.getOptionValue("class");
        String method = options.getOptionValue("method");
        int offset = Integer.parseInt(options.getOptionValue("offset"));

        String dumpLibrary = System.getProperty("heap_dump_library");
        if (dumpLibrary == null) {
            System.out.println("Expected -Dheap_dump_library to be set to the location of the heap dump jar.");
            System.exit(1);
        }

        System.out.printf("Adding heap dump instruction to %s.%s:%d.\n", clazz, method, offset);

        List<String> sootOptions = new ArrayList<>();
        // Add classes to Soot classpath (including current classpath, so that we can resolve HeapDumper
        sootOptions.add("-cp");
        // TODO: Using the current classpath is a hack, since HeapDumper will be reachable in this program.
        //  We should really use dumpLibrary, but Bazel passes a non-portable relative path.
        sootOptions.add(String.format("%s:%s", classPath, System.getProperty("java.class.path")));
        // Prepend Soot classpath to default classpath
        sootOptions.add("-pp");
        // Direct Soot to remember bytecode offsets
        sootOptions.add("-keep-bytecode-offset");
        // Output transformed results as classfiles
        sootOptions.add("-f");
        sootOptions.add("c");

        // Direct Soot to transform just the given class
        sootOptions.add(clazz);
        HeapDumpTransformer transformer = new HeapDumpTransformer(method, offset);
        PackManager.v().getPack("jtp").add(new Transform("jtp.mytransform", transformer));

        // Pre-load HeapDumper class so it's accessible in the transformer.
        Scene.v().addBasicClass("com.mattdsouza.emi.heaps.HeapDumper", SootClass.SIGNATURES);

        // Run Soot
        String[] sootArgs = new String[sootOptions.size()];
        soot.Main.main(sootOptions.toArray(sootArgs));

        if (!transformer.success) {
            System.err.printf("Instrumentation failed. Did not encounter a method \"%s\".\n", method);
        }
    }

    static CommandLine parseOptions(String[] args) {
        Options options = new Options();

        Option classPath = new Option("cp", "classpath", true, "Classpath containing relevant classes");
        classPath.setRequired(true);
        options.addOption(classPath);

        Option clazz = new Option("c", "class", true, "Class to instrument");
        clazz.setRequired(true);
        options.addOption(clazz);

        Option method = new Option("m", "method", true,
                "Method to instrument, specified as a method name (e.g. \"foo\") or signature (e.g. \"void foo(int,java.lang.Object)\")");
        method.setRequired(true);
        options.addOption(method);

        Option offset = new Option("o", "offset", true, "Offset at which to insert instruction");
        offset.setRequired(true);
        options.addOption(offset);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        try {
            return parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp(HeapDumpInstrumentor.class.getName(), options);
            System.exit(1);
        }
        return null;
    }
}

class HeapDumpTransformer extends BodyTransformer {
    String method;
    int offset;
    boolean success;

    HeapDumpTransformer(String method, int offset) {
        this.method = method;
        this.offset = offset;
        this.success = false;
    }

    @Override
    protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
        if (!b.getMethod().getSubSignature().contains(this.method)) {
            return;
        }

        long numMatchingMethods = b.getMethod().getDeclaringClass().getMethods().stream()
                .filter((meth) -> meth.getSubSignature().contains(this.method))
                .count();
        if (numMatchingMethods > 1) {
            throw new RuntimeException(String.format(
                    "Found %d overloads containing method string \"%s\". Try providing a more specific method string.",
                    numMatchingMethods, this.method
            ));
        }

        UnitPatchingChain units = b.getUnits();
        Iterator<Unit> unitIt = units.snapshotIterator();
        while (unitIt.hasNext()) {
            Unit unit = unitIt.next();
            Tag offsetTag = unit.getTag("BytecodeOffsetTag");

            if (offsetTag == null) {
                continue;
            }

            int offset = ((BytecodeOffsetTag) offsetTag).getBytecodeOffset();

            if (offset < this.offset) {
                continue;
            } else if (offset > this.offset) {
                // A shortcoming of Jimple is that not every bytecode instruction maps to a Jimple instruction.
                // Sometimes multiple bytecode instructions are combined into one Jimple instruction.
                // We could use ASM directly to get the exact instruction we want, but this is good enough for a one-off
                // invokestatic insertion.
                System.err.printf("Failed to insert heap dump. No Soot instruction corresponding to offset %d.\n", this.offset);
                Scanner scan = new Scanner(System.in);
                String answer;
                do {
                    System.err.printf("Is %d a suitable alternative? (y/n) ", offset);
                    answer = scan.next();
                } while (!answer.equals("y") && !answer.equals("n"));

                if (answer.equals("n")) {
                    throw new RuntimeException("Failed to insert heap dump at the given offset.");
                }
            }


            System.out.println("Found instruction at specified offset: " + unit.toString() +
                    ". Inserting heap dump immediately before it.");

            SootClass dumpClass = Scene.v().getSootClass("com.mattdsouza.emi.heaps.HeapDumper");
            SootMethod dumpMethod = dumpClass.getMethod("void dump()");
            Expr dumpCall = Jimple.v().newStaticInvokeExpr(dumpMethod.makeRef());
            Stmt dumpStmt = Jimple.v().newInvokeStmt(dumpCall);
            units.insertBefore(dumpStmt, unit);
            this.success = true;
            return;
        }
    }
}
