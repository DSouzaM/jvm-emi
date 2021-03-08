package com.mattdsouza.emi.heaps;


import edu.tufts.eaftan.hprofparser.parser.HprofParser;
import heapdl.hprof.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class RootSnapshot {
    private static final String DUMP_CLASS = "com.mattdsouza.emi.heaps.HeapDumper";
    private static final String DUMP_METHOD = "dump";

    public Map<StackFrame, List<Long>> roots;
    public Snapshot snapshot;
    public List<StackTrace> stackTraces;
    RootSnapshot(Map<StackFrame, List<Long>> roots, Snapshot snapshot, List<StackTrace> stackTraces) {
        this.roots = roots;
        this.stackTraces = stackTraces;
        this.snapshot = snapshot;
    }

    public JavaThing getObj(long id) {
        return snapshot.getObj(id);
    }

    public static RootSnapshot fromFile(String hprofFile) throws IOException {
        Map<StackFrame, List<Long>> roots = new HashMap<>();
        Snapshot snapshot = new Snapshot();
        RootSnapshotHandler rootHandler = new RootSnapshotHandler(roots, snapshot, new StackTraces(), false);
        HprofParser parser = new HprofParser(rootHandler);
        parser.parse(new File(hprofFile));
        return new RootSnapshot(roots, snapshot, rootHandler.getAllStackTraces());
    }

    public StackTrace getMainStackTrace() {
        for (StackTrace trace : stackTraces) {
            if (Arrays.stream(trace.getFrames()).anyMatch(
                    (frame) -> frame.getClassName().equals(DUMP_CLASS) && frame.getMethodName().equals(DUMP_METHOD)
            )) {
                return trace;
            }
        }
        throw new RuntimeException("Could not find a main stack trace in the heap snapshot.");
    }

    public LinkedHashMap<StackFrame, List<Long>> filterRoots(StackTrace trace) {
        LinkedHashMap<StackFrame, List<Long>> result = new LinkedHashMap<>();
        boolean foundDumpMethod = false;
        for (StackFrame frame : trace.getFrames()) {
            // Include all frames after the dump call, as long as they exist in the roots mapping.
            if (foundDumpMethod) {
                if (roots.containsKey(frame)) {
                    result.put(frame, roots.get(frame));
                }
            } else {
                if (frame.getClassName().equals(DUMP_CLASS) && frame.getMethodName().equals(DUMP_METHOD)) {
                    foundDumpMethod = true;
                }
            }
        }
        return result;
    }
}

