package com.mattdsouza.emi.heaps;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.mattdsouza.emi.MutantGenerator;
import heapdl.hprof.*;
import org.apache.commons.cli.*;

import java.util.*;

public class HeapDiffer {
    RootSnapshot firstSnapshot;
    RootSnapshot secondSnapshot;
    String prefix;
    Map<StackFrame, List<Long>> firstRoots;
    Map<StackFrame, List<Long>> secondRoots;

    BiMap<Long, Long> identity;

    private static class HeapDifferException extends Exception {
        public HeapDifferException(String message) {
            super(message);
        }
    }

    private HeapDiffer(String firstPath, String secondPath, String prefix) throws Exception {
        firstSnapshot = RootSnapshot.fromFile(firstPath);
        secondSnapshot = RootSnapshot.fromFile(secondPath);
        this.prefix = prefix;
        firstRoots = firstSnapshot.filterRoots(prefix);
        secondRoots = secondSnapshot.filterRoots(prefix);
        identity = HashBiMap.create();
    }

    private boolean checkDiff() {
        List<StackTrace> firstStackTraces = firstSnapshot.filterStackTraces(prefix);
        List<StackTrace> secondStackTraces = secondSnapshot.filterStackTraces(prefix);

        try {
            if (firstStackTraces.size() != 1 || secondStackTraces.size() != 1) {
                exit("Currently, only diffing single stack traces is supported. " +
                        "Found %d traces in the first dump and %d in the second.",
                        firstStackTraces.size(), secondStackTraces.size());
            }

            StackFrame[] firstFrames = firstStackTraces.get(0).getFrames();
            StackFrame[] secondFrames = secondStackTraces.get(0).getFrames();
            if (firstFrames.length != secondFrames.length) {
                exit("Traces incomparable: first has %d frames, second has %d.",
                        firstFrames.length, secondFrames.length);
            }
            for (int i = 0; i < firstFrames.length; i++) {
                StackFrame firstFrame = firstFrames[i];
                StackFrame secondFrame = secondFrames[i];
                String firstFrameName = frameName(firstFrame);
                String secondFrameName = frameName(secondFrame);
                if (!firstFrameName.equals(secondFrameName)) {
                    exit("Frames at index %d are for different methods: first is %s, second is %s.",
                            firstFrameName, secondFrameName);
                }
            }

            for (int i = 0; i < firstFrames.length; i++) {
                diffFrame(i, firstFrames[i], secondFrames[i]);
            }
        } catch (HeapDifferException ex) {
            System.err.println(ex.getMessage());
            return false;
        }
        return true;
    }

    private void diffFrame(int frameIndex, StackFrame firstFrame, StackFrame secondFrame) throws HeapDifferException {
        List<Long> firstFrameRoots = firstRoots.get(firstFrame);
        List<Long> secondFrameRoots = secondRoots.get(secondFrame);

        if (firstFrameRoots == null && secondFrameRoots == null) {
            // Native frames don't get generated. As long as both traces are consistent here, it's OK.
            return;
        }
        if (firstFrameRoots == null || secondFrameRoots == null) {
            exit("Frame at index %d not found for the %s trace.",
                    firstFrameRoots == null ? "first" : "second");
        }

        if (firstFrameRoots.size() != secondFrameRoots.size()){
            exit("Frames %s at index %d contain a different number of roots: first has %d, second has %d.",
                    frameName(firstFrame), frameIndex, firstFrameRoots.size(), secondFrameRoots.size());
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
            exit("Path is null in first dump, but not null in second dump: %s vs. %s",
                    firstContext, secondContext);
        } else if (secondObjId == 0) {
            exit("Path is not null in first dump, but null in second dump: %s vs. %s",
                    firstContext, secondContext);
        }

        if (identity.containsKey(firstObjId)) {
            long match = identity.get(firstObjId);
            if (match == secondObjId) {
                return;  // success
            } else {
                exit("Object %d in first dump is equivalent to object %d in second dump, but was compared to object %d",
                        firstObjId, match, secondObjId);
            }
        } else if (identity.containsValue(secondObjId)) {
            exit("Object %d in second dump is equivalent to object %d in first dump, but was compared to object %d",
                    secondObjId, identity.inverse().get(secondObjId), firstObjId);
        }

        JavaThing firstThing = firstSnapshot.getObj(firstObjId);
        JavaThing secondThing = secondSnapshot.getObj(secondObjId);

        if (!firstThing.getClassName().equals(secondThing.getClassName())) {
            exit("Path between dumps point to differently-typed objects: %s points to a $s, %s points to a %s",
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
                        diffObject(
                                Long.parseLong(firstField.getValue()),
                                Long.parseLong(secondField.getValue()),
                                firstContext.push(firstObject, firstField.getName()),
                                secondContext.push(secondObject, secondField.getName())
                        );
                    } else {
                        // todo: compare primitives in a better way than string value comparison
                        if (!firstField.getValue().equals(secondField.getValue())) {
                            exit("Field value %s differs between objects on paths %s and %s: first dump has %s, second has %s",
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
        System.exit(diff(firstPath, secondPath, prefix) ? 0 : 1);
    }

    public static boolean diff(String firstPath, String secondPath, String prefix) throws Exception {
        HeapDiffer differ = new HeapDiffer(firstPath, secondPath, prefix);
        return differ.checkDiff();
    }

    static void exit(String fmt, Object ... args) throws HeapDifferException {
        String error = String.format(fmt, args);
        throw new HeapDifferException(error);
    }

    static String frameName(StackFrame frame) {
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
