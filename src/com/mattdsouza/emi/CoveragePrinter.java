package com.mattdsouza.emi;

import org.apache.commons.cli.*;
import org.xml.sax.SAXException;
import soot.*;
import soot.tagkit.BytecodeOffsetTag;
import soot.tagkit.Tag;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Helper utility to print coverage.
public class CoveragePrinter {
    public static void main(String[] args) throws Exception {
        CommandLine options = parseOptions(args);
        String classpath = options.getOptionValue("classpath");
        String coverageFile = options.getOptionValue("coverage");

        List<String> sootOptions = new ArrayList<>();
        // Add classes to Soot classpath
        sootOptions.add("-cp");
        sootOptions.add(classpath);
        // Direct Soot to transform classes in directory
        sootOptions.add("-process-dir");
        sootOptions.add(classpath);
        // Prepend Soot classpath to default classpath
        sootOptions.add("-pp");
        // Direct Soot to remember bytecode offsets
        sootOptions.add("-keep-bytecode-offset");
        // Do not output anything
        sootOptions.add("-f");
        sootOptions.add("n");

        // Parse coverage and add our transformer to the Soot pipeline
        BytecodeCoverage coverage = null;
        try {
            coverage = BytecodeCoverage.fromFile(coverageFile);
        } catch (ParserConfigurationException | IOException | SAXException e) {
            e.printStackTrace();
        }
        PackManager.v().getPack("jtp").add(new Transform("jtp.mytransform", new CoveragePrintingTransformer(coverage)));

        // Run Soot
        String[] sootArgs = new String[sootOptions.size()];
        soot.Main.main(sootOptions.toArray(sootArgs));
    }

    static CommandLine parseOptions(String[] args) {
        Options options = new Options();

        Option classpath = new Option("cp", "classpath", true,
                "Classpath pointing to class files to dump coverage info on");
        classpath.setRequired(true);
        options.addOption(classpath);

        Option coverage = new Option("c", "coverage", true, "JaCoCo coverage report file (XML)");
        coverage.setRequired(true);
        options.addOption(coverage);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        try {
            return parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp(CoveragePrinter.class.getName(), options);
            System.exit(1);
        }
        return null;
    }
}


class CoveragePrintingTransformer extends BodyTransformer {
    BytecodeCoverage coverage;

    CoveragePrintingTransformer(BytecodeCoverage coverage) {
        this.coverage = coverage;
    }

    @Override
    protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
        String clazz = b.getMethod().getDeclaringClass().getName();
        String method = b.getMethod().getName();
        System.out.println(clazz + "::" + method);
        UnitPatchingChain units = b.getUnits();
        for (Unit unit : units) {
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
        }
    }
}
