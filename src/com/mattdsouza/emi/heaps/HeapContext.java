package com.mattdsouza.emi.heaps;

import heapdl.hprof.JavaThing;
import heapdl.hprof.StackFrame;

public class HeapContext {
    private static class Edge {
        JavaThing value;
        String pathName;
        Edge (JavaThing value, String pathName) {
            this.value = value;
            this.pathName = pathName;
        }
    }

    private static class Cons<V> {
        V value;
        Cons<V> next;
        Cons(V value, Cons<V> next) {
            this.value = value;
            this.next = next;
        }
    }

    StackFrame base;
    Cons<Edge> path;

    private HeapContext(StackFrame base, Cons<Edge> path) {
        this.base = base;
        this.path = path;
    }

    public HeapContext push(JavaThing value, String pathName) {
        return new HeapContext(this.base, new Cons<>(new Edge(value, pathName), this.path));
    }

    public HeapContext pop() {
        assert this.path != null;
        return new HeapContext(this.base, this.path.next);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        sb.append(frameName(base));
        sb.append(" -> ");

        Cons<Edge> currentPath = path;
        while (currentPath != null) {
            Edge edge = currentPath.value;
            sb.append(edge.value.getClassName());
            sb.append("@");
            sb.append(edge.value.getId());
            sb.append(" -");
            sb.append(edge.pathName);
            sb.append("-> ");
            currentPath = currentPath.next;
        }
        sb.append("]");
        return sb.toString();
    }

    static HeapContext empty(StackFrame base) {
        return new HeapContext(base, null);
    }

    private static String frameName(StackFrame frame) {
        return String.format("\"%s.%s%s\"", frame.getClassName(), frame.getMethodName(), frame.getMethodSignature());
    }


}
