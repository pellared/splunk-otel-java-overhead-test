package io.opentelemetry.agents;

import java.nio.file.Path;
import java.util.Optional;

@FunctionalInterface
interface AgentJarResolver {

  Optional<Path> resolve() ;

  static AgentJarResolver none() {
    return Optional::empty;
  }

  static AgentJarResolver url(String url) {
    return new UrlResolver(url);
  }
}
