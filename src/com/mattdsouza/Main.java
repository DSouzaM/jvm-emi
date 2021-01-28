package com.mattdsouza;

import soot.*;

import java.util.Map;

public class Main {
    public static void main(String[] args) {
        PackManager.v().getPack("jtp").add(new Transform("jtp.mytransform", new MyTransformer()));
        soot.Main.main(args);
    }
}


class MyTransformer extends BodyTransformer {
    @Override
    protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
        System.out.println(b.getMethod().getClass().getName() + "::" + b.getMethod().getName());
        for (Unit u : b.getUnits()) {
            System.out.println(u.getTag("BytecodeOffsetTag") + ": " + u.toString());
        }
    }
}
