java_binary(
    name = "mutator",
    main_class = "com.mattdsouza.emi.MutantGenerator",
    runtime_deps = [":emi-lib"]
)

java_binary(
    name = "runner",
    main_class = "com.mattdsouza.emi.MutantRunner",
    runtime_deps = [":emi-lib"]
)

java_binary(
    name = "outputchecker",
    main_class = "com.mattdsouza.emi.OutputChecker",
    runtime_deps = [":emi-lib"]
)

java_binary(
    name = "covprinter",
    main_class = "com.mattdsouza.emi.CoveragePrinter",
    runtime_deps = [":emi-lib"]
)

java_binary(
    name = "heapprinter",
    main_class = "com.mattdsouza.emi.heaps.HeapPrinter",
    runtime_deps = [":emi-lib"]
)

java_binary(
    name = "heapdumpinstrumentor",
    main_class = "com.mattdsouza.emi.heaps.HeapDumpInstrumentor",
    srcs = [
        "src/com/mattdsouza/emi/heaps/HeapDumpInstrumentor.java"
    ],
    deps = [
        ":heap-dump",
        ":deps"
    ],
    jvm_flags = [
        "-Dheap_dump_library=$(location :heap-dump)"
    ]
)

java_library(
    name = "heap-dump",
    srcs = [
        "src/com/mattdsouza/emi/heaps/HeapDumper.java"
    ]
)

java_library(
    name = "emi-lib",
    srcs = glob(["src/**/*.java"]),
    deps = [":deps"]
)

java_import(
    name = "deps",
    jars = glob(["lib/*.jar"])
)
