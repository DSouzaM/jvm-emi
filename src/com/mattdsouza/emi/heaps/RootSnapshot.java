package com.mattdsouza.emi.heaps;


import edu.tufts.eaftan.hprofparser.parser.HprofParser;
import heapdl.hprof.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class RootSnapshot {
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

    public List<StackTrace> filterStackTraces(String prefix) {
        return stackTraces.stream()
            .filter((entry) -> Arrays.stream(entry.getFrames()).anyMatch(
                    (frame) -> frame.getClassName().startsWith(prefix)
            ))
            .collect(Collectors.toList());
    }

    public Map<StackFrame, List<Long>> filterRoots(String prefix) {
        return filterStackTraces(prefix).stream()
            .flatMap((trace) -> Arrays.stream(trace.getFrames())
                    .filter(roots::containsKey)
            )
            .collect(Collectors.toMap((frame) -> frame, roots::get));
    }
}

