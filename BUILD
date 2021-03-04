java_binary(
    name = "mutator",
    main_class = "com.mattdsouza.emi.MutantGenerator",
    runtime_deps = [":lib"]
)

java_binary(
    name = "runner",
    main_class = "com.mattdsouza.emi.MutantRunner",
    runtime_deps = [":lib"]
)

java_binary(
    name = "outputchecker",
    main_class = "com.mattdsouza.emi.OutputChecker",
    runtime_deps = [":lib"]
)

java_binary(
    name = "covprinter",
    main_class = "com.mattdsouza.emi.CoveragePrinter",
    runtime_deps = [":lib"]
)

java_binary(
    name = "heapprinter",
    main_class = "com.mattdsouza.emi.heaps.HeapPrinter",
    runtime_deps = [":lib"]
)

java_library(
    name = "lib",
    srcs = glob(["src/**/*.java"]),
    deps = [":deps"]
)

java_import(
    name = "deps",
    jars = glob(["lib/*.jar"])
)
