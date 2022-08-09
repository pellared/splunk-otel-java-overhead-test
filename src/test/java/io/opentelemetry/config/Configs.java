/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.config;

import io.opentelemetry.agents.Agents;

/**
 * Defines all test configurations
 */
public final class Configs {

  public static final TestConfig RELEASE = TestConfig.builder()
      .name("release_30vu_8500iter")
      .description("multiple agent configurations compared")
      .withAgents(Agents.NONE, Agents.SPLUNK_OTEL, Agents.SPLUNK_LOGGING)
      .numberOfPasses(10)
      .maxRequestRate(900)
      .concurrentConnections(30)
      .k6Iterations(8500)
      .warmupSeconds(60)
      .build();

  private Configs() {
  }
}
