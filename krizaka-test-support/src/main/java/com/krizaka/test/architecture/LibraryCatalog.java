package com.krizaka.test.architecture;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * What krizaka-java already provides, as {@link ReuseRules} reads it: its public types and the
 * third-party APIs its facades wrap. Neither list is typed into a rule.
 *
 * <ul>
 *   <li>The public types are {@value #PUBLIC_TYPES}, one fully qualified name per line, generated
 *       from the sources of this repository (see {@code PublicTypesIndexTest}, which fails when the
 *       file is stale and rewrites it with {@code -Dkrizaka.regenerate=true}).
 *   <li>The facades are declared next to each facade, in the {@value #FACADES} resource of its
 *       module: {@code <wrapped API, class or package> = <fully qualified facade>[, <package of the
 *       module that wraps it>...]}. Every such resource on the classpath is read.
 * </ul>
 *
 * @param publicTypes the fully qualified public types, by simple name
 * @param facades the facade (fully qualified) to use, by wrapped API (class or package name)
 */
public record LibraryCatalog(Map<String, List<String>> publicTypes, Map<String, String> facades) {

  /** Classpath location of the generated list of public types. */
  public static final String PUBLIC_TYPES = "META-INF/krizaka/public-types.txt";

  /** Classpath location of a module's facade declarations. */
  public static final String FACADES = "META-INF/krizaka/facades.properties";

  /**
   * The catalog of the classpath.
   *
   * @return the public types and facades declared by every krizaka-java artifact present
   */
  public static LibraryCatalog fromClasspath() {
    ClassLoader loader = LibraryCatalog.class.getClassLoader();
    List<String> names = new ArrayList<>();
    Map<String, String> facades = new LinkedHashMap<>();
    try {
      for (URL url : Collections.list(resources(loader, PUBLIC_TYPES))) {
        try (InputStream in = url.openStream()) {
          names.addAll(new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList());
        }
      }
      for (URL url : Collections.list(resources(loader, FACADES))) {
        try (InputStream in = url.openStream()) {
          Properties declared = new Properties();
          declared.load(in);
          declared.forEach(
              (api, facade) -> facades.put(api.toString().trim(), facade.toString().trim()));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return of(names, facades);
  }

  private static Enumeration<URL> resources(ClassLoader loader, String name) throws IOException {
    return loader.getResources(name);
  }

  /**
   * A catalog from explicit lists (tests, and the generator's comparison).
   *
   * @param fullyQualifiedPublicTypes one name per element; blank lines and {@code #} comments are
   *     ignored
   * @param facades the facade to use, by wrapped API
   * @return the catalog
   */
  public static LibraryCatalog of(
      List<String> fullyQualifiedPublicTypes, Map<String, String> facades) {
    Map<String, List<String>> bySimpleName = new TreeMap<>();
    for (String line : fullyQualifiedPublicTypes) {
      String name = line.trim();
      if (name.isEmpty() || name.startsWith("#")) {
        continue;
      }
      String simple = name.substring(name.lastIndexOf('.') + 1);
      bySimpleName.computeIfAbsent(simple, k -> new ArrayList<>()).add(name);
    }
    return new LibraryCatalog(bySimpleName, Map.copyOf(facades));
  }
}
