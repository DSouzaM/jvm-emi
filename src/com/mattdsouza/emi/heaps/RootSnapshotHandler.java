package com.mattdsouza.emi.heaps;

import heapdl.hprof.*;

import java.util.*;

public class RootSnapshotHandler extends SnapshotHandler {
    public Map<StackFrame, Set<Long>> roots;
    // Maintain this mapping so we can obtain the stack trace for a given thread serial num.
    private Map<Integer, StackTrace> threadToStackTrace;

    public RootSnapshotHandler(Map<StackFrame, Set<Long>> roots, Snapshot snapshot, StackTraces stackTraces, boolean extractStringConstants) {
        super(snapshot, stackTraces, extractStringConstants);
        this.roots = roots;
        this.threadToStackTrace = new HashMap<>();
    }

    private void addRoot(StackFrame frame, long objId) {
        roots.computeIfAbsent(frame, k -> new HashSet<>()).add(objId);
    }

    @Override
    public void stackTrace(int stackTraceSerialNum, int threadSerialNum, int numFrames, long[] stackFrameIds) {
        super.stackTrace(stackTraceSerialNum, threadSerialNum, numFrames, stackFrameIds);
        threadToStackTrace.put(threadSerialNum, localStackTraces.get(stackTraceSerialNum));
    }

    @Override
    public void rootJavaFrame(long objId, int threadSerialNum, int frameNum) {
        StackTrace trace = threadToStackTrace.get(threadSerialNum);
        if (trace == null) {
            return;
        }

        StackFrame frame = trace.getFrames()[frameNum];
        addRoot(frame, objId);
    }

    public List<StackTrace> getAllStackTraces() {
        return new ArrayList<>(localStackTraces.values());
    }


}
