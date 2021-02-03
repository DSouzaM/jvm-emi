package com.mattdsouza;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import soot.*;
import soot.tagkit.BytecodeOffsetTag;
import soot.tagkit.Tag;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        final String COVERAGE_REPORT = "jacoco.xml";
        BytecodeCoverage coverage = null;
        try {
            coverage = BytecodeCoverage.fromFile(COVERAGE_REPORT);
        } catch (ParserConfigurationException | IOException | SAXException e) {
            e.printStackTrace();
        }

        PackManager.v().getPack("jtp").add(new Transform("jtp.mytransform", new MyTransformer(coverage)));
        soot.Main.main(args);
    }
}


class BytecodeCoverage {
    enum Level {
        UNKNOWN,
        LIVE,
        DEAD
    }

    private final Map<String, Map<String, Set<Integer>>> coverage;

    private BytecodeCoverage(Map<String, Map<String, Set<Integer>>> coverage) {
        this.coverage = coverage;
    }

    public Level coverageOf(String className, String methodName, int offset) {
        Map<String, Set<Integer>> classCoverage = coverage.get(className);
        if (classCoverage == null) return Level.UNKNOWN;
        Set<Integer> methodCoverage = classCoverage.get(methodName);
        if (methodCoverage == null) return Level.UNKNOWN;
        return (methodCoverage.contains(offset)) ? Level.LIVE : Level.DEAD;
    }

    public static BytecodeCoverage fromFile(String coverageFile) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringElementContentWhitespace(true);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        File file = new File(coverageFile);
        Document doc = builder.parse(file);

        Map<String, Map<String, Set<Integer>>> coverage = new HashMap<>();
        NodeList classes = doc.getElementsByTagName("class");
        for (int i = 0; i < classes.getLength(); i++) {
            Element clazz = (Element) classes.item(i);
            String className = clazz.getAttribute("name").replace("/", ".");

            Map<String, Set<Integer>> classCoverage = new HashMap<>();
            NodeList methods = clazz.getElementsByTagName("method");
            for (int j = 0; j < methods.getLength(); j++) {
                Element method = (Element) methods.item(j);
                String methodName = method.getAttribute("name");

                NodeList bytecodes = method.getElementsByTagName("bytecode");
                assert bytecodes.getLength() == 1;
                Element bytecode = (Element) bytecodes.item(0);
                Set<Integer> methodCoverage = Arrays.stream(bytecode.getAttribute("offsets")
                        .split(","))
                        .map(Integer::parseInt)
                        .collect(Collectors.toSet());
                classCoverage.put(methodName, methodCoverage);
            }
            coverage.put(className, classCoverage);
        }
        return new BytecodeCoverage(coverage);
    }
}

class MyTransformer extends BodyTransformer {
    BytecodeCoverage coverage;

    MyTransformer(BytecodeCoverage coverage) {
        this.coverage = coverage;
    }

    @Override
    protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
        String clazz = b.getMethod().getDeclaringClass().getName();
        String method = b.getMethod().getName();
        System.out.println(clazz + "::" + method);
        UnitPatchingChain units = b.getUnits();
        Iterator<Unit> unitIt = units.snapshotIterator();
        while (unitIt.hasNext()) {
            Unit unit = unitIt.next();
            Tag offsetTag = unit.getTag("BytecodeOffsetTag");

            int offset;
            BytecodeCoverage.Level coverageLevel;
            if (offsetTag == null) {
                offset = -1;
                coverageLevel = BytecodeCoverage.Level.LIVE;
            } else {
                offset = ((BytecodeOffsetTag) offsetTag).getBytecodeOffset();
                coverageLevel = coverage.coverageOf(clazz, method, offset);
            }

            char cov = (coverageLevel == BytecodeCoverage.Level.LIVE) ? ' ' : (coverageLevel == BytecodeCoverage.Level.DEAD) ? '!' : '?';
            System.out.printf("\t%d\t%c\t%s\n", offset, cov, unit.toString());

            if (coverageLevel == BytecodeCoverage.Level.DEAD) {
                units.remove(unit);
            }
        }
    }
}
