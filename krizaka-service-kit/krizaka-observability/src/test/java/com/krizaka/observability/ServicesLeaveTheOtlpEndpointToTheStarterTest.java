package com.krizaka.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * The starter owns where traces, metrics and logs go ({@code OBSERVABILITY_OTLP_ENDPOINT}); a
 * service's own configuration never sets an OTLP endpoint, URL or header by hand, which would win
 * over the starter and bypass the names (krizaka-internal#2843).
 */
class ServicesLeaveTheOtlpEndpointToTheStarterTest {

  /** Spring Boot's OTLP destinations, old and new names. */
  private static final Pattern HAND_SET_OTLP =
      Pattern.compile(
          "management\\.(?:otlp\\.(?:tracing|logging)\\.(?:endpoint|headers.*)"
              + "|otlp\\.metrics\\.export\\.(?:url|headers.*)"
              + "|opentelemetry\\.(?:tracing|logging)\\.export\\.otlp\\.(?:endpoint|headers.*))");

  private static final Pattern OLD_VARIABLES =
      Pattern.compile("OTEL_(?:TRACES_ENDPOINT|EXPORTER_)");

  private static final Set<String> SKIPPED_DIRECTORIES =
      Set.of(".git", "node_modules", "target", "site", ".next", "out");

  private static final Pattern CONFIG_FILE =
      Pattern.compile("application(?:-[\\w-]+)?\\.(?:ya?ml|properties)");

  @Test
  void noServiceConfigurationSetsAnOtlpDestination() throws IOException {
    List<Path> files = serviceConfigurations();
    List<String> refused = new ArrayList<>();
    for (Path file : files) {
      flatten(file)
          .forEach(
              (key, value) -> {
                if (HAND_SET_OTLP.matcher(key).matches()
                    || OLD_VARIABLES.matcher(String.valueOf(value)).find()) {
                  refused.add(repositoryRoot().relativize(file) + ": " + key);
                }
              });
    }

    // The scan sees the three services, so the rule cannot pass on an empty walk.
    assertThat(files).hasSizeGreaterThanOrEqualTo(3);
    assertThat(refused)
        .as("drop these lines: the starter maps OBSERVABILITY_OTLP_ENDPOINT for every signal")
        .isEmpty();
  }

  private static List<Path> serviceConfigurations() throws IOException {
    List<Path> found = new ArrayList<>();
    Files.walkFileTree(
        repositoryRoot(),
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
            String name = String.valueOf(directory.getFileName());
            return SKIPPED_DIRECTORIES.contains(name)
                ? FileVisitResult.SKIP_SUBTREE
                : FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            if (CONFIG_FILE.matcher(file.getFileName().toString()).matches()
                && file.toString().contains("/src/main/resources/")) {
              found.add(file);
            }
            return FileVisitResult.CONTINUE;
          }
        });
    return found.stream().sorted().toList();
  }

  private static Path repositoryRoot() {
    Path directory = Path.of("").toAbsolutePath();
    while (!Files.exists(directory.resolve(".release-please-manifest.json"))) {
      directory = directory.getParent();
      assertThat(directory).as("the repository root above the module").isNotNull();
    }
    return directory;
  }

  private static Map<String, Object> flatten(Path file) throws IOException {
    Map<String, Object> flat = new LinkedHashMap<>();
    if (file.getFileName().toString().endsWith(".properties")) {
      Properties properties = new Properties();
      try (Reader reader = Files.newBufferedReader(file)) {
        properties.load(reader);
      }
      properties.forEach((key, value) -> flat.put(String.valueOf(key), value));
      return flat;
    }
    try (InputStream input = Files.newInputStream(file)) {
      for (Object document : new Yaml(new SafeConstructor(new LoaderOptions())).loadAll(input)) {
        flatten("", document, flat);
      }
    }
    return flat;
  }

  private static void flatten(String prefix, Object node, Map<String, Object> flat) {
    if (node instanceof Map<?, ?> map) {
      map.forEach(
          (key, value) ->
              flatten(prefix.isEmpty() ? String.valueOf(key) : prefix + "." + key, value, flat));
    } else {
      flat.put(prefix, String.valueOf(node));
    }
  }
}
