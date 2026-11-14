package com.krizaka.test.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The list of krizaka-java's public types ({@link LibraryCatalog#PUBLIC_TYPES}) is generated from
 * the sources of this repository: this test fails when the committed file is stale, and rewrites it
 * with {@code -Dkrizaka.regenerate=true}.
 */
class PublicTypesIndexTest {

  private static final Pattern PACKAGE =
      Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE);
  private static final Pattern TOP_LEVEL =
      Pattern.compile(
          "^public\\s+(?:(?:final|abstract|sealed|non-sealed|static)\\s+)*(?:class|interface|record|enum|@interface)\\s+(\\w+)",
          Pattern.MULTILINE);
  private static final List<String> NOT_LIBRARY =
      List.of(
          "krizaka-test-support",
          "krizaka-quality",
          "krizaka-rewrite-style",
          "krizaka-parent",
          "krizaka-bom",
          "canary",
          "site");
  private static final Pattern PLUMBING =
      Pattern.compile(".*(Configuration|Properties|Application|Test|Tests|IT)$");

  private static List<String> generate(Path root) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      return files
          .filter(p -> p.toString().endsWith(".java"))
          .filter(p -> p.toString().contains("/src/main/java/"))
          .filter(p -> !p.getFileName().toString().equals("package-info.java"))
          // a deployable application ("<x>-service") is not a library a product depends on
          .filter(p -> !root.relativize(p).toString().matches(".*krizaka-[\\w-]*-service/.*"))
          .filter(
              p ->
                  NOT_LIBRARY.stream()
                      .noneMatch(m -> root.relativize(p).toString().startsWith(m + "/")))
          .filter(p -> !root.relativize(p).toString().contains("/examples/"))
          .flatMap(p -> typesOf(p).stream())
          .filter(name -> !PLUMBING.matcher(name.substring(name.lastIndexOf('.') + 1)).matches())
          .distinct()
          .sorted()
          .toList();
    }
  }

  private static List<String> typesOf(Path file) {
    try {
      String source = Files.readString(file);
      Matcher pkg = PACKAGE.matcher(source);
      Matcher type = TOP_LEVEL.matcher(source);
      if (pkg.find()
          && type.find()
          && type.group(1).equals(file.getFileName().toString().replace(".java", ""))) {
        return List.of(pkg.group(1) + "." + type.group(1));
      }
      return List.of();
    } catch (IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  @Test
  void theCommittedListIsTheOneGeneratedFromTheSources() throws IOException {
    Path root = Path.of("..").toAbsolutePath().normalize();
    Path file = Path.of("src/main/resources/" + LibraryCatalog.PUBLIC_TYPES);
    List<String> generated = generate(root);
    if (Boolean.getBoolean("krizaka.regenerate")) {
      Files.write(file, generated);
    }

    assertThat(generated).isNotEmpty().contains("com.krizaka.messaging.outbox.OutboxStore");
    assertThat(Files.readAllLines(file))
        .as("run the module's tests with -Dkrizaka.regenerate=true and commit the file")
        .isEqualTo(generated);
  }
}
