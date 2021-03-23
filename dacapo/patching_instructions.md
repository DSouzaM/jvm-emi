How to apply the patch to the DaCapo jar:
```
jar uf <dacapo jar> <folder>
cp <dacapo jar> <patched jar>
pushd <folder>/harness
javac org/dacapo/harness/Benchmark.java org/dacapo/harness/MutatedClassLoader.java
popd 
jar uf <patched jar> -C <folder> harness/org/dacapo/harness/Benchmark.class
jar uf <patched jar> -C <folder> harness/org/dacapo/harness/MutatedClassLoader.class
```
