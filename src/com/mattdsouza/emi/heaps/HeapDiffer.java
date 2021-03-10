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
        firstRoots = firstSnapshot.filterRoots(firstSnapshot.getMainStackTrace());
        secondRoots = secondSnapshot.filterRoots(secondSnapshot.getMainStackTrace());
        identity = HashBiMap.create();
    }

    private void computeDiff() {
        StackTrace firstStackTrace = firstSnapshot.getMainStackTrace();
        StackTrace secondStackTrace = secondSnapshot.getMainStackTrace();

        try {
            StackFrame[] firstFrames = firstStackTrace.getFrames();
            StackFrame[] secondFrames = secondStackTrace.getFrames();
            if (firstFrames.length != secondFrames.length) {
                error("Traces incomparable: first has %d frames, second has %d.",
                        firstFrames.length, secondFrames.length);
                return;
            }
            for (int i = 0; i < firstFrames.length; i++) {
                StackFrame firstFrame = firstFrames[i];
                StackFrame secondFrame = secondFrames[i];
                String firstFrameName = frameName(firstFrame);
                String secondFrameName = frameName(secondFrame);
                if (!firstFrameName.equals(secondFrameName)) {
                    error("Frames at index %d are for different methods: first is %s, second is %s.",
                            firstFrameName, secondFrameName);
                    return;
                }
            }

            for (int i = 0; i < firstFrames.length; i++) {
                diffFrame(i, firstFrames[i], secondFrames[i]);
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
                        diffObject(
                                Long.parseLong(firstField.getValue()),
                                Long.parseLong(secondField.getValue()),
                                firstContext.push(firstObject, firstField.getName()),
                                secondContext.push(secondObject, secondField.getName())
                        );
                    } else {
                        // todo: compare primitives in a better way than string value comparison
                        if (!firstField.getValue().equals(secondField.getValue())) {
                            error("Field value %s differs between objects on paths %s and %s: first dump has %s, second has %s",
                                    firstField.getName(), firstContext, secondContext,
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
