package com.mattdsouza.emi;

import soot.Body;
import soot.BodyTransformer;
import soot.Unit;
import soot.UnitPatchingChain;
import soot.tagkit.BytecodeOffsetTag;
import soot.tagkit.Tag;

import java.util.Iterator;
import java.util.Map;

class EMIMutator extends BodyTransformer {
    BytecodeCoverage coverage;

    EMIMutator(BytecodeCoverage coverage) {
        this.coverage = coverage;
    }

    @Override
    protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
        String clazz = b.getMethod().getDeclaringClass().getName();
        String method = b.getMethod().getName();

        // Don't mutate unreached methods. We need a liveness trace in order to produce a valid control flow graph.
        if (!coverage.methodReached(clazz, method)) {
            return;
        }

        UnitPatchingChain units = b.getUnits();
        Iterator<Unit> unitIt = units.snapshotIterator();
        while (unitIt.hasNext()) {
            Unit unit = unitIt.next();
            Tag offsetTag = unit.getTag("BytecodeOffsetTag");

            BytecodeCoverage.Level coverageLevel;
            if (offsetTag == null) {
                coverageLevel = BytecodeCoverage.Level.LIVE;
            } else {
                int offset = ((BytecodeOffsetTag) offsetTag).getBytecodeOffset();
                coverageLevel = coverage.coverageOf(clazz, method, offset);
            }

            // TODO: add coin flip, and other mutation strategies
            if (coverageLevel == BytecodeCoverage.Level.DEAD) {
                units.remove(unit);
            }
        }
    }
}
