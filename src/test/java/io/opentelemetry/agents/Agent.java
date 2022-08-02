package io.opentelemetry.agents;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class Agent {

  public static Builder builder() {
    return new Builder();
  }

  private final String name;
  private final String description;
  private final String version;
  private final AgentJarResolver jarResolver;
  private final List<String> additionalJvmArgs;

  Agent(Builder builder) {
    this.name = builder.name;
    this.description = builder.description;
    this.version = builder.version;
    this.jarResolver = builder.jarResolver;
    this.additionalJvmArgs = builder.additionalJvmArgs;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getVersion() {
    return version;
  }

  public List<String> getAdditionalJvmArgs() {
    return Collections.unmodifiableList(additionalJvmArgs);
  }

  public Optional<Path> getJarPath() {
    return jarResolver.resolve();
  }

  public static final class Builder {

    private String name;
    private String description;
    private String version;
    private AgentJarResolver jarResolver = AgentJarResolver.none();
    private List<String> additionalJvmArgs = Collections.emptyList();

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder version(String version) {
      this.version = version;
      return this;
    }

    public Builder url(String url) {
      this.jarResolver = AgentJarResolver.url(url);
      return this;
    }

    public Builder additionalJvmArgs(String... additionalJvmArgs) {
      this.additionalJvmArgs = List.of(additionalJvmArgs);
      return this;
    }

    public Agent build() {
      return new Agent(this);
    }
  }
}
