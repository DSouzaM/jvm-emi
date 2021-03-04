package com.mattdsouza.emi.heaps;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mattdsouza.emi.MutantGenerator;
import heapdl.hprof.*;
import org.apache.commons.cli.*;

import java.util.*;

public class HeapDiffer {
    RootSnapshot firstSnapshot;
    RootSnapshot secondSnapshot;
    String prefix;
    boolean computeFullDiff;
    List<String> errors;

    Map<StackFrame, List<Long>> firstRoots;
    Map<StackFrame, List<Long>> secondRoots;

    BiMap<Long, Long> identity;


    private static class HeapDifferException extends Exception {
        public HeapDifferException(String message) {
            super(message);
        }
    }

    private HeapDiffer(String firstPath, String secondPath, String prefix, boolean computeFullDiff) throws Exception {
        firstSnapshot = RootSnapshot.fromFile(firstPath);
        secondSnapshot = RootSnapshot.fromFile(secondPath);
        this.prefix = prefix;
        this.computeFullDiff = computeFullDiff;
        this.errors = new ArrayList<>();
        firstRoots = firstSnapshot.filterRoots(prefix);
        secondRoots = secondSnapshot.filterRoots(prefix);
        identity = HashBiMap.create();
    }

    private boolean computeDiff() {
        List<StackTrace> firstStackTraces = firstSnapshot.filterStackTraces(prefix);
        List<StackTrace> secondStackTraces = secondSnapshot.filterStackTraces(prefix);

        try {
            if (firstStackTraces.size() != 1 || secondStackTraces.size() != 1) {
                return error("Currently, only diffing single stack traces is supported. " +
                        "Found %d traces in the first dump and %d in the second.",
                        firstStackTraces.size(), secondStackTraces.size());
            }

            StackFrame[] firstFrames = firstStackTraces.get(0).getFrames();
            StackFrame[] secondFrames = secondStackTraces.get(0).getFrames();
            if (firstFrames.length != secondFrames.length) {
                return error("Traces incomparable: first has %d frames, second has %d.",
                        firstFrames.length, secondFrames.length);
            }
            for (int i = 0; i < firstFrames.length; i++) {
                StackFrame firstFrame = firstFrames[i];
                StackFrame secondFrame = secondFrames[i];
                String firstFrameName = frameName(firstFrame);
                String secondFrameName = frameName(secondFrame);
                if (!firstFrameName.equals(secondFrameName)) {
                    return error("Frames at index %d are for different methods: first is %s, second is %s.",
                            firstFrameName, secondFrameName);
                }
            }

            for (int i = 0; i < firstFrames.length; i++) {
                diffFrame(i, firstFrames[i], secondFrames[i]);
            }
        } catch (HeapDifferException ex) {
            return false;
        }
        return this.errors.isEmpty();
    }

    private boolean diffFrame(int frameIndex, StackFrame firstFrame, StackFrame secondFrame) throws HeapDifferException {
        List<Long> firstFrameRoots = firstRoots.get(firstFrame);
        List<Long> secondFrameRoots = secondRoots.get(secondFrame);

        if (firstFrameRoots == null && secondFrameRoots == null) {
            // Native frames don't get generated. As long as both traces are consistent here, it's OK.
            return true;
        }
        if (firstFrameRoots == null || secondFrameRoots == null) {
            return error("Frame at index %d not found for the %s trace.",
                    firstFrameRoots == null ? "first" : "second");
        }

        if (firstFrameRoots.size() != secondFrameRoots.size()){
            return error("Frames %s at index %d contain a different number of roots: first has %d, second has %d.",
                    frameName(firstFrame), frameIndex, firstFrameRoots.size(), secondFrameRoots.size());
        }

        // Assumption: roots are ordered
        HeapContext firstContext = HeapContext.empty(firstFrame);
        HeapContext secondContext = HeapContext.empty(secondFrame);
        for (int i = 0; i < firstFrameRoots.size(); i++) {
            diffObject(firstFrameRoots.get(i), secondFrameRoots.get(i), firstContext, secondContext);
        }
        return true;
    }

    private boolean diffObject(long firstObjId, long secondObjId, HeapContext firstContext, HeapContext secondContext) throws HeapDifferException {
        if (firstObjId == 0 && secondObjId == 0) {
            return true;
        } else if (firstObjId == 0) {
            return error("Path is null in first dump, but not null in second dump: %s vs. %s",
                    firstContext, secondContext);
        } else if (secondObjId == 0) {
            return error("Path is not null in first dump, but null in second dump: %s vs. %s",
                    firstContext, secondContext);
        }

        if (identity.containsKey(firstObjId)) {
            long match = identity.get(firstObjId);
            if (match == secondObjId) {
                return true;
            } else {
                return error("Object %d in first dump is equivalent to object %d in second dump, but was compared to object %d",
                        firstObjId, match, secondObjId);
            }
        } else if (identity.containsValue(secondObjId)) {
            return error("Object %d in second dump is equivalent to object %d in first dump, but was compared to object %d",
                    secondObjId, identity.inverse().get(secondObjId), firstObjId);
        }

        JavaThing firstThing = firstSnapshot.getObj(firstObjId);
        JavaThing secondThing = secondSnapshot.getObj(secondObjId);

        if (!firstThing.getClassName().equals(secondThing.getClassName())) {
            return error("Path between dumps point to differently-typed objects: %s points to a $s, %s points to a %s",
                    firstContext, firstThing.getClassName(), secondContext, secondThing.getClassName());
        }

        // To handle pointer loops, assume these objects are equivalent until proven otherwise.
        identity.put(firstObjId, secondObjId);

        try {
            if (firstThing instanceof JavaObject) {
                JavaObject firstObject = (JavaObject) firstThing;
                JavaObject secondObject = (JavaObject) secondThing;

                List<JavaField> firstFields = sorted(firstObject.getFields());
                List<JavaField> secondFields = sorted(secondObject.getFields());

                for (int i = 0; i < firstFields.size(); i++) {
                    JavaField firstField = firstFields.get(i);
                    JavaField secondField = secondFields.get(i);
                    assert firstField.getName().equals(secondField.getName());

                    if (firstField.getType().equals("Object")) {
                        return diffObject(
                                Long.parseLong(firstField.getValue()),
                                Long.parseLong(secondField.getValue()),
                                firstContext.push(firstObject, firstField.getName()),
                                secondContext.push(secondObject, secondField.getName())
                        );
                    } else {
                        // todo: compare primitives in a better way than string value comparison
                        if (!firstField.getValue().equals(secondField.getValue())) {
                            return error("Field value %s differs between objects on paths %s and %s: first dump has %s, second has %s",
                                    firstField.getName(), firstContext, secondContext,
                                    firstField.getValue(), secondField.getValue());
                        }
                    }
                }
            }
        } catch (HeapDifferException ex) {
            // Remove assumption and rethrow.
            identity.remove(firstObjId, secondObjId);
            throw ex;
        }
        return true;
    }

    private List<JavaField> sorted(List<JavaField> lst) {
        List<JavaField> copy = new ArrayList<>(lst);
        copy.sort(Comparator.comparing(JavaField::getName));
        return copy;
    }

    public static void main(String[] args) throws Exception {
        CommandLine options = parseOptions(args);
        String firstPath = options.getOptionValue("first");
        String secondPath = options.getOptionValue("second");
        String prefix = options.getOptionValue("prefix");
        boolean computeFullDiff = options.hasOption("full");

        HeapDiffer differ = new HeapDiffer(firstPath, secondPath, prefix, computeFullDiff);
        boolean success = differ.computeDiff();
        if (success) {
            System.out.println("No differences detected.");
            System.exit(0);
        } else {
            if (computeFullDiff) {
                System.out.printf("%d difference(s) detected:\n", differ.errors.size());
                for (int i = 0; i < differ.errors.size(); i++) {
                    System.out.printf("%d: %s\n", i+1, differ.errors.get(i));
                }
            } else {
                assert differ.errors.size() == 1;
                System.out.println("First difference detected:");
                System.out.println(differ.errors.get(0));
            }
            System.exit(1);
        }
    }

    public static boolean diff(String firstPath, String secondPath, String prefix) throws Exception {
        HeapDiffer differ = new HeapDiffer(firstPath, secondPath, prefix, false);
        return differ.computeDiff();
    }

    public static List<String> fullDiff(String firstPath, String secondPath, String prefix) throws Exception {
        HeapDiffer differ = new HeapDiffer(firstPath, secondPath, prefix, true);
        differ.computeDiff();
        return differ.errors;
    }

    // Helper to log errors (if full diff requested) or throw errors.
    // Returns false so that you can write `return error(...)` inside a diff function
    private boolean error(String fmt, Object ... args) throws HeapDifferException {
        String error = String.format(fmt, args);
        errors.add(error);

        if (!computeFullDiff) {
            throw new HeapDifferException(error);
        }
        return false;
    }

    private static String frameName(StackFrame frame) {
        return String.format("\"%s.%s%s\"", frame.getClassName(), frame.getMethodName(), frame.getMethodSignature());
    }

    static CommandLine parseOptions(String[] args) {
        Options options = new Options();

        Option first = new Option("f", "first", true, "Path to first heap dump");
        first.setRequired(true);
        options.addOption(first);

        Option second = new Option("s", "second", true, "Path to second heap dump");
        second.setRequired(true);
        options.addOption(second);

        Option coverage = new Option("p", "prefix", true, "Package prefix to look for");
        coverage.setRequired(true);
        options.addOption(coverage);

        Option computeFullDiff = new Option("full", false, "Whether to generate a full report (or abort on first failure)");
        computeFullDiff.setRequired(false);
        options.addOption(computeFullDiff);

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
