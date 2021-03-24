package com.mattdsouza.emi;

import soot.*;
import soot.jimple.Expr;
import soot.jimple.Jimple;
import soot.jimple.NullConstant;
import soot.jimple.internal.AbstractOpStmt;
import soot.jimple.internal.JIdentityStmt;
import soot.jimple.internal.JimpleLocal;
import soot.tagkit.BytecodeOffsetTag;
import soot.tagkit.Tag;

import java.util.*;
import java.util.stream.Collectors;

class EMIMutator extends BodyTransformer {
    BytecodeCoverage coverage;

    static Random rand = new Random();

    enum Mutation {
        DELETE,
        ALLOC;

        static Mutation randomChoice() {
            Mutation[] all = Mutation.values();
            return all[rand.nextInt(all.length)];
        }
    }


    EMIMutator(BytecodeCoverage coverage) {
        this.coverage = coverage;
    }

    @Override
    protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
        String clazz = b.getMethod().getDeclaringClass().getName();
        String methodWithDescriptor = getMethodWithDescriptor(b.getMethod());

        // Mutating constructors is asking for problems.
        if (b.getMethod().isConstructor()) {
            return;
        }

        // Don't mutate unreached methods. We need a liveness trace in order to produce a valid control flow graph.
        if (!coverage.methodReached(clazz, methodWithDescriptor)) {
            return;
        }


        Mutation mutation = Mutation.randomChoice();
        switch(mutation) {
            case DELETE:
//                runDelete(b);
                break;
            case ALLOC:
//                runAlloc(b);
                break;
            default:
                throw new RuntimeException("Unknown mutation " + mutation.toString());
        }
    }

    private String getMethodWithDescriptor(SootMethod method) {
        return method.getName() + AbstractJasminClass.jasminDescriptorOf(method.makeRef());
    }

    private BytecodeCoverage.Level coverageOf(Body b, Unit unit) {
        BytecodeCoverage.Level result;

        Tag offsetTag = unit.getTag("BytecodeOffsetTag");
        if (offsetTag == null) {
            return BytecodeCoverage.Level.NON_INSTRUCTION;
        } else {
            String clazz = b.getMethod().getDeclaringClass().getName();
            String methodWithDescriptor = getMethodWithDescriptor(b.getMethod());
            int offset = ((BytecodeOffsetTag) offsetTag).getBytecodeOffset();
            return coverage.coverageOf(clazz, methodWithDescriptor, offset);
        }
    }

    /* NOTE: This doesn't work very well, because it changes the roots set when performing a heap dump.
     * Maybe there's a better way to do this kind of transformation.
     */
    private void runAlloc(Body b) {
        UnitPatchingChain units = b.getUnits();

        // Insert at beginning of method, after parameter initializations.
        Optional<Unit> liveUnits = units.stream().filter(u -> u instanceof JIdentityStmt).findFirst();
        if (!liveUnits.isPresent()) {
            throw new RuntimeException("Attempted to mutate body of " + b.getMethod().getSignature() + " but it has no appropriate units.");
        }
        Unit toMutate = liveUnits.get();

        Local l = Jimple.v().newLocal("emi_variable", RefType.v("java.lang.Object"));
        b.getLocals().add(l);

        List<Unit> toInsert = new ArrayList<>();
        toInsert.add(Jimple.v().newAssignStmt(l, Jimple.v().newNewExpr(RefType.v("java.lang.Object"))));
        toInsert.add(Jimple.v().newAssignStmt(l, NullConstant.v()));

        units.insertBefore(toInsert, toMutate);
    }


    /* NOTE: This is broken right now, because it produces invalid results sometimes, and ASM fails when trying to
     *  compute the stack frame map. I think it's related to control flow and exception handlers, but it's not clear.
     *  Before using this technique we need a better understanding of when it is OK to delete instructions.
     */
    private void runDelete(Body b) {
        String clazz = b.getMethod().getDeclaringClass().getName();
        String methodWithDescriptor = getMethodWithDescriptor(b.getMethod());

        UnitPatchingChain units = b.getUnits();
        Iterator<Unit> unitIt = units.snapshotIterator();

        while (unitIt.hasNext()) {
            Unit unit = unitIt.next();
            BytecodeCoverage.Level coverageLevel = coverageOf(b, unit);
            if (coverageLevel == BytecodeCoverage.Level.DEAD && deletable(unit)) {
                units.remove(unit);
            }
        }

    }

    private boolean deletable(Unit unit) {
        return !(unit instanceof AbstractOpStmt);
    }
}
