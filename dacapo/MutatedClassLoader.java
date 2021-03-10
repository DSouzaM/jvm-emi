package org.dacapo.harness;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;

import org.dacapo.parser.Config;

/**
 * Custom class loader based on DacapoClassLoader. Attempts to load classes from
 * the provided mutation directory before delegating to the parent class.
 */
public class MutatedClassLoader extends URLClassLoader {
  private static final String MUTATED_PATH_PROP = "mutated_path";
  public static MutatedClassLoader create(ClassLoader parent) {
    String mutatedPath = System.getProperty(MUTATED_PATH_PROP);
    if (mutatedPath == null) {
        System.err.println("Expected a mutated path to be set. Use -Dmutated_path to set one.");
        System.exit(-1);
    }

    MutatedClassLoader result = null;
    try {
      result = new MutatedClassLoader(new URL[]{ Paths.get(mutatedPath).toUri().toURL() }, parent);
    } catch (Exception ex) {
      System.err.println("Something went wrong while constructing the mutated class loader.");
      ex.printStackTrace();
      System.exit(-1);
    }
    return result;
  }

  /**
   * @param urls
   * @param parent
   */
  public MutatedClassLoader(URL[] urls, ClassLoader parent) {
    super(urls, parent);
  }

  /**
   * Reverse the logic of the default classloader, by trying the mutated path first.
   */
  @Override
  protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    // First, check if the class has already been loaded
    Class<?> c = findLoadedClass(name);
    if (c == null) {
      try {

        // Next, try to resolve it from the mutated path. 
        c = super.findClass(name);
      } catch (ClassNotFoundException e) {
        // And if all else fails delegate to the parent.
        c = super.loadClass(name, resolve);
      }
    }
    if (resolve) {
      resolveClass(c);
    }
    return c;
  }
}
