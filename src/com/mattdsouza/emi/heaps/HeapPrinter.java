package com.mattdsouza.emi.heaps;

import com.mattdsouza.emi.MutantGenerator;
import heapdl.hprof.*;
import org.apache.commons.cli.*;

import java.io.IOException;
import java.util.*;

public class HeapPrinter {
    public static void main(String[] args) throws IOException {
        CommandLine options = parseOptions(args);
        String hprofFile = options.getOptionValue("dump");
        String prefix = options.getOptionValue("prefix");

        RootSnapshot snapshot = RootSnapshot.fromFile(hprofFile);

        List<StackTrace> relevantStackTraces = snapshot.filterStackTraces(prefix);
        Map<StackFrame, List<Long>> relevantRoots = snapshot.filterRoots(prefix);

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
        for (Map.Entry<StackFrame, List<Long>> entry : relevantRoots.entrySet()) {
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

    static CommandLine parseOptions(String[] args) {
        Options options = new Options();

        Option dump = new Option("d", "dump", true, "Path to heap dump");
        dump.setRequired(true);
        options.addOption(dump);

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
