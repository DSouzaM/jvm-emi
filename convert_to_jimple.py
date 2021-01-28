#!/usr/bin/python3
import sys
import os
from argparse import ArgumentParser
import subprocess

def _find_soot(directory):
    jars = [x for x in os.listdir(directory) if x.startswith("soot") and x.endswith(".jar")]
    if len(jars) == 0:
        raise Exception(f"No Soot jar found at {directory}")
    elif len(jars) > 1:
        raise Exception(f"Multiple Soot jars found at {directory}: {jars}")
    return os.path.join(directory, jars[0])

def main():
    parser = ArgumentParser(description="Convert from classfiles to Soot's Jimple format.")
    parser.add_argument("directory", type=str, help="directory containing classfiles")

    args = parser.parse_args()

    soot_jar = _find_soot(os.getcwd())

    subprocess.run(f"java -cp {soot_jar} soot.Main -cp out -pp -f J -process-dir {args.directory}", shell=True, check=True)



if __name__ == "__main__":
    main()
