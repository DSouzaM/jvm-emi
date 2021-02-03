### What? Why?
The upstream version of Soot cannot propagate bytecode offsets to `Unit`s.
This part is important for translating bytecode coverage to Jimple coverage.
Sadly, it could easily propagate that info if ASM remembered the offsets, but it doesn't.

Luckily, the author of [this post](https://github.com/soot-oss/soot/issues/787#issuecomment-655441204) and the developer he cited created patches for Soot and ASM to get the job done. Neither is upstreamed, so I manually applied the patches myself.

### Instructions
1. Patch ASM:

    a. Check out repo: https://gitlab.ow2.org/asm/asm

    b. Manually apply changeset: https://gitlab.ow2.org/dhr_1/asm/-/commit/3769b3f0285af57a9338440464138a071ffe701a
    
    c. Build: https://asm.ow2.io/developer-guide.html#building 

2. Install ASM into local maven. 
Even though the change only affects asm and asm-tree, I installed all of them because the patches were applied
on top of 9.0.1, and the original libraries are 8.0.1. I was worried about compatibility between them
(but for some reason not compatibility with Soot 🤷‍).

    Commands:
    ```
    mvn install:install-file -Dfile=asm/build/libs/asm-9.0.1-SNAPSHOT.jar -DgroupId=org.ow2.asm -DartifactId=asm -Dversion=8.0.1 -Dpackaging=jar -DgeneratePom=true
    mvn install:install-file -Dfile=asm-tree/build/libs/asm-tree-9.0.1-SNAPSHOT.jar -DgroupId=org.ow2.asm -DartifactId=asm-tree -Dversion=8.0.1 -Dpackaging=jar -DgeneratePom=true
    mvn install:install-file -Dfile=asm-util/build/libs/asm-util-9.0.1-SNAPSHOT.jar -DgroupId=org.ow2.asm -DartifactId=asm-util -Dversion=8.0.1 -Dpackaging=jar -DgeneratePom=true
    mvn install:install-file -Dfile=asm-commons/build/libs/asm-commons-9.0.1-SNAPSHOT.jar -DgroupId=org.ow2.asm -DartifactId=asm-commons -Dversion=8.0.1 -Dpackaging=jar -DgeneratePom=true
    ```

3. Patch Soot

    a. Check out repo: https://github.com/soot-oss/soot
    
    b. Manually apply changeset: https://github.com/vaibhavbsharma/soot/commit/975a478d583c2423b089f48fa3b90bfad484bf4f
    
    c. Build with dependencies: https://github.com/soot-oss/soot/wiki/Building-Soot-from-the-Command-Line-(Recommended)#alternative---step-2-build-soot-jar-including-dependencies

    The patched Soot jar with the patched ASM dependency will be in `target/`.
