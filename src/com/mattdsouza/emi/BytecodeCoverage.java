package com.mattdsouza.emi;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

// Abstraction over a JaCoCo coverage report modified to include bytecode offsets.
// Includes helper methods to parse coverage reports and determine which offsets are covered.
class BytecodeCoverage {
    enum Level {
        UNKNOWN,
        LIVE,
        DEAD
    }

    private final Map<String, Map<String, Set<Integer>>> coverage;
    private final Set<String> methodsReached;

    private BytecodeCoverage(Map<String, Map<String, Set<Integer>>> coverage, Set<String> methodsReached) {
        this.coverage = coverage;
        this.methodsReached = methodsReached;
    }

    public Level coverageOf(String className, String methodName, int offset) {
        Map<String, Set<Integer>> classCoverage = coverage.get(className);
        if (classCoverage == null) return Level.UNKNOWN;
        Set<Integer> methodCoverage = classCoverage.get(methodName);
        if (methodCoverage == null) return Level.UNKNOWN;
        return (methodCoverage.contains(offset)) ? Level.LIVE : Level.DEAD;
    }

    public boolean methodReached(String className, String methodName) {
        return methodsReached.contains(String.format("%s::%s", className, methodName));
    }

    public static BytecodeCoverage fromFile(String coverageFile) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringElementContentWhitespace(true);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        File file = new File(coverageFile);
        Document doc = builder.parse(file);

        Map<String, Map<String, Set<Integer>>> coverage = new HashMap<>();
        Set<String> methodsReached = new HashSet<>();
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
                String offsets = bytecode.getAttribute("offsets");

                if (offsets.isEmpty()) {
                    continue;
                } else {
                    methodsReached.add(String.format("%s::%s", className, methodName));
                }

                Set<Integer> methodCoverage = Arrays.stream(offsets.split(","))
                        .filter(s -> s.length() > 0)
                        .map(Integer::parseInt)
                        .collect(Collectors.toSet());
                classCoverage.put(methodName, methodCoverage);
            }
            coverage.put(className, classCoverage);
        }
        return new BytecodeCoverage(coverage, methodsReached);
    }
}
