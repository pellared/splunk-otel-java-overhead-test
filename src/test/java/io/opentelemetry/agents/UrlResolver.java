package io.opentelemetry.agents;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.Objects.requireNonNull;

final class UrlResolver implements AgentJarResolver {

  private static final ConcurrentMap<String, Path> agentJarCache = new ConcurrentHashMap<>();
  private static final OkHttpClient client = new OkHttpClient();

  private final String url;

  UrlResolver(String url) {
    this.url = url;
  }

  @Override
  public Optional<Path> resolve() {
    Path path = agentJarCache.computeIfAbsent(url, UrlResolver::doResolve);
    return Optional.of(path);
  }

  private static Path doResolve(String url) {
    try {
      if (url.startsWith("file://")) {
        Path source = Path.of(URI.create(url));
        Path result = Paths.get(".", source.getFileName().toString());
        Files.copy(source, result, StandardCopyOption.REPLACE_EXISTING);
        return result;
      }

      Request request = new Request.Builder().url(url).build();
      try (Response response = client.newCall(request).execute();
           ResponseBody responseBody = requireNonNull(response.body())) {

        byte[] raw = responseBody.bytes();
        Path path = Files.createTempFile(Paths.get("."), "javaagent", ".jar");
        Files.write(path, raw, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        return path;
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to download the agent jar: " + url, e);
    }
  }
}
