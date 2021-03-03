package com.mattdsouza.emi.heaps;


import edu.tufts.eaftan.hprofparser.parser.HprofParser;
import heapdl.hprof.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RootSnapshot {
    public Map<StackFrame, Set<Long>> roots;
    public Snapshot snapshot;
    public List<StackTrace> stackTraces;
    RootSnapshot(Map<StackFrame, Set<Long>> roots, Snapshot snapshot, List<StackTrace> stackTraces) {
        this.roots = roots;
        this.stackTraces = stackTraces;
        this.snapshot = snapshot;
    }

    public JavaThing getObj(long id) {
        return snapshot.getObj(id);
    }

    public static RootSnapshot fromFile(String hprofFile) throws IOException {
        Map<StackFrame, Set<Long>> roots = new HashMap<>();
        Snapshot snapshot = new Snapshot();
        RootSnapshotHandler rootHandler = new RootSnapshotHandler(roots, snapshot, new StackTraces(), false);
        HprofParser parser = new HprofParser(rootHandler);
        parser.parse(new File(hprofFile));
        return new RootSnapshot(roots, snapshot, rootHandler.getAllStackTraces());
    }
}

