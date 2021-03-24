package com.mattdsouza.emi.heaps;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import heapdl.hprof.*;
import org.apache.commons.cli.*;

import java.util.*;

public class HeapDiffer {
    RootSnapshot firstSnapshot;
    RootSnapshot secondSnapshot;
    boolean computeFullDiff;
    List<String> errors;

    List<StackFrame> firstFrames;
    List<StackFrame> secondFrames;
    Map<StackFrame, List<Long>> firstRoots;
    Map<StackFrame, List<Long>> secondRoots;

    BiMap<Long, Long> identity;


    private static class HeapDifferException extends Exception {
        public HeapDifferException(String message) {
            super(message);
        }
    }

    private HeapDiffer(String firstPath, String secondPath, boolean computeFullDiff) throws Exception {
        firstSnapshot = RootSnapshot.fromFile(firstPath);
        secondSnapshot = RootSnapshot.fromFile(secondPath);
        this.computeFullDiff = computeFullDiff;
        this.errors = new ArrayList<>();

        firstFrames = firstSnapshot.getMainStackFrames();
        secondFrames = secondSnapshot.getMainStackFrames();
        firstRoots = firstSnapshot.filterRoots(firstFrames);
        secondRoots = secondSnapshot.filterRoots(secondFrames);
        identity = HashBiMap.create();
    }

    private void computeDiff() {
        try {
            if (firstFrames.size() != secondFrames.size()) {
                error("Traces incomparable: first has %d frames, second has %d.",
                        firstFrames.size(), secondFrames.size());
                return;
            }
            for (int i = 0; i < firstFrames.size(); i++) {
                StackFrame firstFrame = firstFrames.get(i);
                StackFrame secondFrame = secondFrames.get(i);
                String firstFrameName = frameName(firstFrame);
                String secondFrameName = frameName(secondFrame);
                if (!firstFrameName.equals(secondFrameName)) {
                    error("Frames at index %d are for different methods: first is %s, second is %s.",
                            firstFrameName, secondFrameName);
                    return;
                }
            }

            for (int i = 0; i < firstFrames.size(); i++) {
                diffFrame(i, firstFrames.get(i), secondFrames.get(i));
            }
        } catch (HeapDifferException ex) {}  // Abort main diff loop on exception
    }

    private void diffFrame(int frameIndex, StackFrame firstFrame, StackFrame secondFrame) throws HeapDifferException {
        List<Long> firstFrameRoots = firstRoots.get(firstFrame);
        List<Long> secondFrameRoots = secondRoots.get(secondFrame);

        if (firstFrameRoots == null && secondFrameRoots == null) {
            // Native frames don't get generated. As long as both traces are consistent here, it's OK.
            return;
        }
        if (firstFrameRoots == null || secondFrameRoots == null) {
            error("Frame at index %d not found for the %s trace.",
                    firstFrameRoots == null ? "first" : "second");
            return;
        }

        if (firstFrameRoots.size() != secondFrameRoots.size()){
            error("Frames %s at index %d contain a different number of roots: first has %d, second has %d.",
                    frameName(firstFrame), frameIndex, firstFrameRoots.size(), secondFrameRoots.size());
            return;
        }

        // Assumption: roots are ordered
        HeapContext firstContext = HeapContext.empty(firstFrame);
        HeapContext secondContext = HeapContext.empty(secondFrame);
        for (int i = 0; i < firstFrameRoots.size(); i++) {
            diffObject(firstFrameRoots.get(i), secondFrameRoots.get(i), firstContext, secondContext);
        }
    }

    private void diffObject(long firstObjId, long secondObjId, HeapContext firstContext, HeapContext secondContext) throws HeapDifferException {
        if (firstObjId == 0 && secondObjId == 0) {
            return;
        } else if (firstObjId == 0) {
            error("Path is null in first dump, but not null in second dump: %s vs. %s",
                    firstContext, secondContext);
            return;
        } else if (secondObjId == 0) {
            error("Path is not null in first dump, but null in second dump: %s vs. %s",
                    firstContext, secondContext);
            return;
        }

        if (identity.containsKey(firstObjId)) {
            long match = identity.get(firstObjId);
            if (match != secondObjId) {
                error("Object %d in first dump is (potentially) equivalent to object %d in second dump, but was compared to object %d",
                        firstObjId, match, secondObjId);
            }
            return;
        } else if (identity.containsValue(secondObjId)) {
            error("Object %d in second dump is (potentially) equivalent to object %d in first dump, but was compared to object %d",
                    secondObjId, identity.inverse().get(secondObjId), firstObjId);
            return;
        }

        JavaThing firstThing = firstSnapshot.getObj(firstObjId);
        JavaThing secondThing = secondSnapshot.getObj(secondObjId);

        if (!firstThing.getClassName().equals(secondThing.getClassName())) {
            error("Path between dumps point to differently-typed objects: %s points to a $s, %s points to a %s",
                    firstContext, firstThing.getClassName(), secondContext, secondThing.getClassName());
            return;
        } else if (shouldIgnore(firstThing.getClassName())) {
            return;
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

                    HeapContext newFirstContext = firstContext.push(firstObject, firstField.getName());
                    HeapContext newSecondContext = secondContext.push(secondObject, secondField.getName());
                    if (firstField.getType().equals("Object")) {
                        diffObject(
                                Long.parseLong(firstField.getValue()),
                                Long.parseLong(secondField.getValue()),
                                newFirstContext,
                                newSecondContext
                        );
                    } else {
                        // todo: compare primitives in a better way than string value comparison
                        if (!firstField.getValue().equals(secondField.getValue())) {
                            error("Field value %s differs between objects on paths %s and %s: first dump has %s, second has %s",
                                    firstField.getName(), newFirstContext, newSecondContext,
                                    firstField.getValue(), secondField.getValue());
                        }
                    }
                }
            } else if (firstThing instanceof JavaObjectArray) {
                JavaObjectArray firstArray = (JavaObjectArray) firstThing;
                JavaObjectArray secondArray = (JavaObjectArray) secondThing;

                long[] firstElements = firstArray.getElements();
                long[] secondElements = secondArray.getElements();
                for (int i = 0; i < firstElements.length; i++) {
                    diffObject(
                            firstElements[i],
                            secondElements[i],
                            firstContext.push(firstArray, Integer.toString(i)),
                            secondContext.push(secondArray, Integer.toString(i))
                    );
                }
            }
        } catch (HeapDifferException ex) {
            // Remove assumption and rethrow.
            identity.remove(firstObjId, secondObjId);
            throw ex;
        }
    }

    private List<JavaField> sorted(List<JavaField> lst) {
        List<JavaField> copy = new ArrayList<>(lst);
        copy.sort(Comparator.comparing(JavaField::getName));
        return copy;
    }

    private boolean shouldIgnore(String className) {
        List<String> classNames = Arrays.asList(
            "HashMap", "ClassLoader", "ThreadPoolExecutor", "java.io.FileDescriptor", "java.lang.Thread",
            "PmdThread" // pmd
        );
        List<String> packagePrefixes = Arrays.asList("java.lang.ref", "sun");
        return classNames.stream().anyMatch(className::contains) || packagePrefixes.stream().anyMatch(className::startsWith);
    }

    public static void main(String[] args) throws Exception {
        CommandLine options = parseOptions(args);
        String firstPath = options.getOptionValue("first");
        String secondPath = options.getOptionValue("second");
        boolean computeFullDiff = options.hasOption("full");

        HeapDiffer differ = new HeapDiffer(firstPath, secondPath, computeFullDiff);
        differ.computeDiff();
        if (differ.errors.isEmpty()) {
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
        HeapDiffer differ = new HeapDiffer(firstPath, secondPath, false);
        differ.computeDiff();
        return differ.errors.isEmpty();
    }

    public static List<String> fullDiff(String firstPath, String secondPath, String prefix) throws Exception {
        HeapDiffer differ = new HeapDiffer(firstPath, secondPath, true);
        differ.computeDiff();
        return differ.errors;
    }

    // Helper to log errors (if full diff requested) or throw errors.
    private void error(String fmt, Object ... args) throws HeapDifferException {
        String error = String.format(fmt, args);
        errors.add(error);

        if (!computeFullDiff) {
            throw new HeapDifferException(error);
        }
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

        Option computeFullDiff = new Option("full", false, "Whether to generate a full report (or abort on first failure)");
        computeFullDiff.setRequired(false);
        options.addOption(computeFullDiff);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        try {
            return parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp(HeapDiffer.class.getName(), options);
            System.exit(1);
        }
        return null;
    }
}
