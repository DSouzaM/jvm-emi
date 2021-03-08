package com.mattdsouza.emi.heaps;


import com.sun.management.HotSpotDiagnosticMXBean;

import java.lang.management.ManagementFactory;

public class HeapDumper {
    public static final String DUMP_FILE_PROP = "dump_file";
    private static boolean dumped = false;
    public static void dump() {
        if (dumped) return;
        String dumpFile = System.getProperty(DUMP_FILE_PROP);
        if (dumpFile == null) {
            System.err.println("No dump file specified. Use -Ddump_file to indicate a heap dump file.");
            return;
        }
        try {
            ManagementFactory.newPlatformMXBeanProxy(
                    ManagementFactory.getPlatformMBeanServer(), "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class
            ).dumpHeap(dumpFile, true);
            System.err.println("Dumped heap successfully.");
        } catch (Exception ex) {
            System.err.println("Failed to dump heap. Message: " + ex.getMessage());
        }
        dumped = true;
    }
}
