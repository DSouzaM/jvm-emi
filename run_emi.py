import argparse
import os
import secrets
import subprocess
import sys


def execute(command, cwd=None):
    subprocess.check_call(command, stdout=sys.stdout, cwd=cwd)


def make_dir(*parts):
    path = os.path.join(*parts)
    os.makedirs(path, exist_ok=True)
    return path


def check_path(*parts, hint=None):
    path = os.path.join(*parts)
    if not os.path.exists(path):
        error = f"Path {path} not found."
        if hint:
            error += " " + hint
        raise Exception(error)
    return path


def log(msg: str):
    print(f"\033[36m* {msg}\033[0m")



def run_emi(registry: str, seed: str):
    project_root = os.getcwd()
    bazel_bin = check_path(project_root, "bazel-bin", hint="Executable should be run from the bazel project root.")
    heap_dump_lib = check_path(bazel_bin, "libheap-dump.jar", hint="The heap-dump library should be built.")
    jacoco_lib = check_path(project_root, "lib", "jacocoagent.jar")
    jacoco_cli = check_path(project_root, "lib", "jacococli.jar")


    os.chdir(registry)
    run_script = check_path(registry, "run")
    dumps = make_dir(registry, "dumps")
    coverage = make_dir(registry, "coverage")
    mutants = make_dir(registry, "mutants")

    current = seed
    while True:
        current_path = current if current == "seed" else check_path(mutants, current)

        # 1. Generate heap dump
        heap_dump_file = os.path.join(dumps, f"{current}.hprof")
        log(f"Running {current}. Dumping heap to {heap_dump_file}.")
        execute([run_script, current_path, "--dump-lib", heap_dump_lib, "--output", heap_dump_file])

        # Currently disabled, since heap dumps are more of a nuisance than a benefit right now
        # 2. Compare outputs
        # if current != "seed":
        #     log(f"Comparing heap dump of {current} with baseline.")
        #     baseline_heap_dump_file = os.path.join(dumps, "seed.hprof")
        #     execute(
        #         ["bazel", "run", ":heapdiffer", "--", "--first", baseline_heap_dump_file, "--second", heap_dump_file],
        #         cwd=project_root
        #     )

        # 3. Profile mutant
        log(f"Re-running {current} to obtain coverage.")
        coverage_file = os.path.join(coverage, f"{current}.exec")
        execute([run_script, current_path, "--dump-lib", heap_dump_lib, "--output", coverage_file, "--profile", "--jacoco", jacoco_lib])
        # Convert to an XML report
        coverage_report = os.path.join(coverage, f"{current}.xml")
        execute(["java", "-jar", jacoco_cli, "report", coverage_file, "--classfiles", current_path, "--xml", coverage_report])

        # 4. Generate EMI mutant
        next = secrets.token_hex(8)
        log(f"Creating a mutant of {current} as {next}.")
        execute(
            ["bazel", "run", ":mutator", "--", "--registry", registry, "--variant", current, "--new-variant", next, "--coverage", coverage_report],
            cwd=project_root
        )

        current = next


def main():
    args = parse_args()
    run_emi(args.registry, args.seed)
    seed = args.seed





def parse_args():
    parser = argparse.ArgumentParser(description="Driver for the EMI loop.")
    parser.add_argument("registry", help="path to the EMI registry")
    parser.add_argument("--seed", default="seed",
                        help="Variant to start EMI mutations from (default is seed)")

    return parser.parse_args()


if __name__ == "__main__":
    main()
