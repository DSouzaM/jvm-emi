package com.mattdsouza.emi;

import com.mattdsouza.emi.heaps.RootSnapshot;

import heapdl.hprof.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class HeapPrinter {
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Heap printer expects exactly two arguments: the hprof file name, and a package prefix.");
        }
        String hprofFile = args[0];
        String prefix = args[1];

        RootSnapshot snapshot = RootSnapshot.fromFile(hprofFile);

        List<StackTrace> relevantStackTraces = snapshot.stackTraces.stream()
                .filter((entry) -> Arrays.stream(entry.getFrames()).anyMatch(
                        (frame) -> frame.getClassName().startsWith(prefix)
                ))
                .collect(Collectors.toList());

        Map<StackFrame, Set<Long>> relevantRoots = relevantStackTraces.stream()
                .flatMap((trace) -> Arrays.stream(trace.getFrames())
                        .filter(snapshot.roots::containsKey)
                )
                .collect(Collectors.toMap((frame) -> frame, snapshot.roots::get));

        StringBuilder buf = new StringBuilder();
        buf.append("digraph heap {\n");

        /// Generate nodes and edges for stack frames.
        for (StackTrace trace : relevantStackTraces) {
            String prev = null;
            StringBuilder sameRankBuf = new StringBuilder();
            sameRankBuf.append("{rank = same; ");

            for (StackFrame frame : trace.getFrames()) {
                String name = frameName(frame);
                buf.append(name);
                buf.append(" [shape=box];\n");
                if (prev != null) {
                    buf.append(prev);
                    buf.append(" -> ");
                    buf.append(name);
                    buf.append(";\n");
                }

                sameRankBuf.append(name);
                sameRankBuf.append(";");

                prev = name;
            }
            sameRankBuf.append("}\n");
            sameRankBuf.append("rankdir = LR;\n");
            buf.append(sameRankBuf.toString());
        }

        Set<Long> seen = new HashSet<>();
        Queue<Long> toVisit = new ArrayDeque<>();

        /// Perform BFS to generate pointer graph.
        // Initialize BFS queue with roots
        for (Map.Entry<StackFrame, Set<Long>> entry : relevantRoots.entrySet()) {
            String name = frameName(entry.getKey());

            for (Long objId : entry.getValue()) {
                toVisit.add(objId);
                buf.append(name);
                buf.append(" -> ");
                buf.append(objId);
                buf.append(";\n");
            }
        }

        // Perform BFS
        while (!toVisit.isEmpty()) {
            long objId = toVisit.poll();
            if (!seen.add(objId) || objId == 0) {
                continue;
            }
            JavaThing thing = snapshot.getObj(objId);

            buf.append(objId);
            buf.append(" [label = \"");

            if (thing.getClassName().equals("java.lang.String")) {
                // don't print out fields of String
                buf.append("string");
                buf.append("\"];\n");
                continue;
            }

            buf.append(thing.getClassName());
            buf.append("@");
            buf.append(thing.getId());
            buf.append("\"];\n");

            if (thing instanceof JavaObject) {
                JavaObject javaObject = (JavaObject) thing;

                for (JavaField field : javaObject.getFields()) {
                    if (field.getType().equals("Object") && !"0".equals(field.getValue())) {
                        long fieldObjId = Long.parseLong(field.getValue());
                        toVisit.add(fieldObjId);
                        buf.append(objId);
                        buf.append(" -> ");
                        buf.append(fieldObjId);
                        buf.append(" [label = ");
                        buf.append(field.getName());
                        buf.append("];\n");
                    }
                }
            } else if (thing instanceof JavaObjectArray) {
                JavaObjectArray javaObjectArray = (JavaObjectArray) thing;
                long[] elements = javaObjectArray.getElements();

                for (int i = 0; i < elements.length; i++) {
                    long elementObjId = elements[i];
                    toVisit.add(elementObjId);
                    buf.append(objId);
                    buf.append(" -> ");
                    buf.append(elementObjId);
                    buf.append(" [label = ");
                    buf.append(i);
                    buf.append("];\n");
                }
            }
        }

        buf.append("}");

        System.out.println(buf.toString());
    }
    static String frameName(StackFrame frame) {
        return String.format("\"%s.%s%s\"", frame.getClassName(), frame.getMethodName(), frame.getMethodSignature());
    }
}
