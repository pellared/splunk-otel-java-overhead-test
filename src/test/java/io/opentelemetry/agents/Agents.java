package io.opentelemetry.agents;

import static io.opentelemetry.agents.AgentVersion.LATEST_VERSION;

public final class Agents {

  public final static Agent NONE = Agent.builder()
      .name("none")
      .description("No Instrumentation")
      .build();

  public final static Agent SPLUNK_OTEL = Agent.builder()
      .name("splunk-otel")
      .description("Splunk OpenTelemetry Java agent")
      .version(LATEST_VERSION)
      .url(splunkAgentUrl(LATEST_VERSION))
      .build();

  public final static Agent SPLUNK_LOGGING = Agent.builder()
      .name("splunk-logging")
      .description("Splunk OpenTelemetry Java agent w/ logging exporter")
      .version(LATEST_VERSION)
      .url(splunkAgentUrl(LATEST_VERSION))
      .additionalJvmArgs("-Dotel.traces.exporter=logging")
      .build();

  public final static Agent OTEL = Agent.builder()
      .name("otel")
      .description("OpenTelemetry Instrumentation for Java")
      .version("1.16.0")
      .url(otelAgentUrl("1.16.0"))
      .build();

  public final static Agent SPLUNK_PROFILER = Agent.builder()
      .name("cpu:text")
      .description("Splunk OpenTelemetry Java agent with AlwaysOn Profiling")
      .version(LATEST_VERSION)
      .url(splunkAgentUrl(LATEST_VERSION))
      .additionalJvmArgs("-Dsplunk.profiler.enabled=true")
      .build();

  private static String splunkAgentUrl(String version) {
    return "https://github.com/signalfx/splunk-otel-java/releases/download/v" + version + "/splunk-otel-javaagent.jar";
  }

  private static String otelAgentUrl(String version) {
    return "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v" + version + "/opentelemetry-javaagent.jar";
  }

  private Agents() {
  }
}
