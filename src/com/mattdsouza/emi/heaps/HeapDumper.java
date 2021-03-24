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
            return;
        }
        try {
            ManagementFactory.newPlatformMXBeanProxy(
                    ManagementFactory.getPlatformMBeanServer(), "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class
            ).dumpHeap(dumpFile, true);
        } catch (Exception ex) {}
        dumped = true;
    }
}
