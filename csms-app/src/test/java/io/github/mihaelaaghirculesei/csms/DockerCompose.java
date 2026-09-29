package io.github.mihaelaaghirculesei.csms;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Reads deploy/docker-compose.yml, the single source of container images for tests. */
final class DockerCompose {

  private static final String COMPOSE_FILE_PROPERTY = "chargehub.compose-file";

  private DockerCompose() {}

  static String image(String service) {
    Map<?, ?> services = asMap(load().get("services"), "services");
    Map<?, ?> definition = asMap(services.get(service), "services." + service);
    if (!(definition.get("image") instanceof String image)) {
      throw new IllegalStateException("No image for service '" + service + "' in " + composeFile());
    }
    return image;
  }

  private static Map<?, ?> load() {
    // SafeConstructor: plain maps, lists and scalars only, never arbitrary Java types.
    Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
    try (Reader reader = Files.newBufferedReader(composeFile())) {
      return asMap(yaml.load(reader), "document root");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Path composeFile() {
    String path = System.getProperty(COMPOSE_FILE_PROPERTY);
    if (path == null) {
      throw new IllegalStateException(
          "System property '" + COMPOSE_FILE_PROPERTY + "' is not set; run tests through Gradle");
    }
    return Path.of(path);
  }

  private static Map<?, ?> asMap(Object node, String location) {
    if (!(node instanceof Map<?, ?> map)) {
      throw new IllegalStateException(
          "Expected a mapping at '" + location + "' in " + composeFile());
    }
    return map;
  }
}
