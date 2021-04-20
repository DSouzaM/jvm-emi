package com.mattdsouza.emi;

import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.tagkit.BytecodeOffsetTag;
import soot.tagkit.Tag;

import java.util.*;
import java.util.stream.Collectors;

class EMIMutator extends BodyTransformer {
    BytecodeCoverage coverage;

    static Random rand = new Random();

    enum Mutation {
//        DELETE,
        ALLOC,
        TRUE_GUARD;

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


        float mutationFrequency = 0.01f;
        if (rand.nextFloat() > mutationFrequency) {
            return;
        }

        Mutation mutation = Mutation.randomChoice();
        switch(mutation) {
//            case DELETE:
//                runDelete(b);
//                break;
            case ALLOC:
                runAlloc(b);
                break;
            case TRUE_GUARD:
                runTrueGuard(b);
                break;
            default:
                throw new RuntimeException("Unknown mutation " + mutation.toString());
        }
        System.out.printf("Mutated %s with strategy %s.\n", b.getMethod().getSignature(), mutation.toString());
    }

    private String getMethodWithDescriptor(SootMethod method) {
        return method.getName() + AbstractJasminClass.jasminDescriptorOf(method.makeRef());
    }

    private BytecodeCoverage.Level coverageOf(Body b, Unit unit) {
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

    private Local makeLocal(Body b, Type t) {
        String prefix = "emiVariable";
        long numExisting = b.getLocals().stream().map(Local::getName).filter(name -> name.contains(prefix)).count();
        String newLocal = prefix + (numExisting+1);
        return Jimple.v().newLocal(newLocal, t);
    }

    private Value generateValue(Type t) {
        if (t instanceof RefLikeType) {
            return NullConstant.v();
        } else if (t instanceof LongType) {
            return LongConstant.v(rand.nextLong());
        } else if (t instanceof IntType) {
            return IntConstant.v(rand.nextInt());
        } else {
            return IntConstant.v(0);
        }
    }

    private void runTrueGuard(Body b) {
        Local newLocal = makeLocal(b, IntType.v());
        b.getLocals().add(newLocal);

        UnitPatchingChain units = b.getUnits();

        // Insert an assignment at the beginning of the method.
        Optional<Unit> firstUnit = units.stream().filter(u -> !(u instanceof JIdentityStmt)).findFirst();
        if (!firstUnit.isPresent()) {
            throw new RuntimeException("Attempted to mutate body of " + b.getMethod().getSignature() + " but it has no appropriate units.");
        }
        Unit assignLocation = firstUnit.get();
        int value = rand.nextInt();
        Unit initStmt = Jimple.v().newAssignStmt(newLocal, IntConstant.v(value));
        units.insertBefore(initStmt, assignLocation);
        // If assignment happens at the beginning of a try block, the variable might not be definitely assigned afterward.
        // Start the trap range after the assignment.
        b.getTraps().forEach(trap -> {
            if (trap.getBeginUnit() == initStmt) {
                trap.setBeginUnit(assignLocation);
            }
        });

        // Now, pick a unit to get "wrapped".
        List<Unit> candidates = units.stream()
                .filter(u -> !(
                        u instanceof JIdentityStmt || u instanceof MonitorStmt ||
                        u instanceof ThrowStmt || u instanceof GotoStmt ||
                        // Don't wrap <init> calls!
                        u instanceof InvokeStmt && ((InvokeStmt) u).getInvokeExpr() instanceof SpecialInvokeExpr ||
                        // Don't wrap new assignments (verifier doesn't seem to understand if both branches are "uninit")
                        u instanceof AssignStmt && ((AssignStmt) u).getRightOp() instanceof AnyNewExpr
                ))
                .filter(u -> coverageOf(b, u) == BytecodeCoverage.Level.LIVE)
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            return;
        }
        Unit choice = candidates.get(rand.nextInt(candidates.size()));

        // Next, generate a valid replacement in the else branch. Sometimes this can be empty, but some statements (e.g.,
        // assignments) need something in this dead else branch to pass bytecode validation.
        NopStmt endIf = Jimple.v().newNopStmt();
        Stmt elses;
        if (choice instanceof AssignStmt) {
            AssignStmt assignStmt = (AssignStmt) choice;
            Type variableType = assignStmt.getLeftOp().getType();
            if (assignStmt.getLeftOp() instanceof Local) {
                // Locals are checked for definite assignment
                elses = Jimple.v().newAssignStmt(assignStmt.getLeftOp(), generateValue(variableType));
            } else {
                elses = Jimple.v().newNopStmt();
            }
        } else if (choice instanceof ReturnVoidStmt) {
            elses = Jimple.v().newReturnVoidStmt();
        } else if (choice instanceof ReturnStmt) {
            ReturnStmt returnStmt = (ReturnStmt) choice;
            elses = Jimple.v().newReturnStmt(generateValue(returnStmt.getOp().getType()));
        } else if (choice instanceof IfStmt || choice instanceof InvokeStmt || choice instanceof SwitchStmt) {
            elses = Jimple.v().newNopStmt();
        } else {
            throw new RuntimeException("Unsupported unit type " + choice.getClass().getName());
        }

        // Finally, insert the if-guard before and the else case afterwards.
        IfStmt ifStmt = Jimple.v().newIfStmt(Jimple.v().newNeExpr(newLocal, IntConstant.v(value)), elses);
        units.insertBefore(ifStmt, choice);

        List<Unit> toInsert = new ArrayList<>();
        toInsert.add(Jimple.v().newGotoStmt(endIf)); // skip elses
        toInsert.add(elses);
        toInsert.add(endIf);
        units.insertAfter(toInsert, choice);
    }

    /* NOTE: This doesn't work very well, because it changes the roots set when performing a heap dump.
     * Maybe there's a better way to do this kind of transformation.
     */
    private void runAlloc(Body b) {
        UnitPatchingChain units = b.getUnits();

        // Find somewhere to insert a new call.
        List<Unit> candidates = units.stream()
                .filter(u -> !(u instanceof JIdentityStmt))
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            return;
        }
        Unit choice = candidates.get(rand.nextInt(candidates.size()));

        Local l = makeLocal(b, RefType.v("java.lang.Object"));
        b.getLocals().add(l);

        List<Unit> toInsert = new ArrayList<>();
        toInsert.add(Jimple.v().newAssignStmt(l, Jimple.v().newNewExpr(RefType.v("java.lang.Object"))));

        units.insertBefore(toInsert, choice);
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
