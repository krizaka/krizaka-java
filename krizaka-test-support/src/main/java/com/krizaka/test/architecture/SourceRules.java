package com.krizaka.test.architecture;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Rules read from source and configuration files, where bytecode cannot see the defect. */
public final class SourceRules {

  private static final Pattern VIRTUAL_THREADS_ENABLED =
      Pattern.compile("threads:[ \\t]*\\R\\s*virtual:[ \\t]*\\R\\s*enabled:[ \\t]*true");

  private SourceRules() {}

  /**
   * No production class outside a {@code @Configuration} imports Spring's {@code Environment}.
   *
   * <p>Configuration is bound once, typed and validated, by a {@code @ConfigurationProperties}
   * record; a bean that reads {@code Environment} by key is a stringly-typed side door that no
   * startup check sees.
   *
   * @param sourceRoot the module's {@code src/main/java}
   */
  public static void assertNoEnvironmentInjection(Path sourceRoot) {
    List<String> violations = new ArrayList<>();
    for (Path file :
        Subjects.require("SRC-001", "Java sources under " + sourceRoot, javaFiles(sourceRoot))) {
      boolean configuration = false;
      List<String> imports = new ArrayList<>();
      List<String> lines = read(file).lines().toList();
      for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i).trim();
        if (line.contains("@Configuration")) {
          configuration = true;
        }
        if (isComment(line)) {
          continue;
        }
        if (line.contains("org.springframework.core.env.Environment")) {
          imports.add(file.getFileName() + ":" + (i + 1) + " -> " + line);
        }
      }
      if (!configuration) {
        violations.addAll(imports);
      }
    }
    if (!violations.isEmpty()) {
      fail(
          "[SRC-001] production code reads Environment directly — bind a @ConfigurationProperties"
              + " record instead:\n  "
              + String.join("\n  ", violations));
    }
  }

  /**
   * A service that serves HTTP runs its requests on virtual threads ({@code
   * spring.threads.virtual.enabled: true} in its {@code application.yml}).
   *
   * <p>Without it, blocking I/O on the default Tomcat pool caps the service at 200 concurrent
   * requests — silently. A configuration rule, because the defect is an omission: there is no class
   * to inspect for a setting nobody wrote.
   *
   * @param moduleRoot the service module's directory
   */
  public static void assertVirtualThreadsEnabled(Path moduleRoot) {
    Path config = moduleRoot.resolve("src/main/resources/application.yml");
    Subjects.require(
        "SRC-002",
        "application.yml at " + config,
        Files.isRegularFile(config) ? List.of(config) : List.of());
    // Comments stripped: a rationale that names the setting is not the setting.
    String code = read(config).replaceAll("(?m)^\\s*#.*$", "");
    if (!VIRTUAL_THREADS_ENABLED.matcher(code).find()) {
      fail(
          "[SRC-002] spring.threads.virtual.enabled=true is missing — blocking I/O on a platform"
              + " thread caps this service at the Tomcat pool size, silently: "
              + config);
    }
  }

  private static List<Path> javaFiles(Path sourceRoot) {
    if (!Files.isDirectory(sourceRoot)) {
      return List.of();
    }
    try (Stream<Path> paths = Files.walk(sourceRoot)) {
      return paths.filter(p -> p.toString().endsWith(".java")).toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to walk " + sourceRoot, e);
    }
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read " + file, e);
    }
  }

  private static boolean isComment(String trimmedLine) {
    return trimmedLine.startsWith("//")
        || trimmedLine.startsWith("*")
        || trimmedLine.startsWith("/*");
  }
}
