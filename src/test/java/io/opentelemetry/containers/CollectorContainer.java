/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.containers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public class CollectorContainer {

  static final int OTLP_PORT = 4317;
  static final int SIGNALFX_METRICS_PORT = 9943;
  static final int COLLECTOR_HEALTH_CHECK_PORT = 13133;

  public static GenericContainer<?> build(Network network) {

    return new GenericContainer<>(
        DockerImageName.parse("otel/opentelemetry-collector-contrib:0.55.0"))
        .withNetwork(network)
        .withNetworkAliases("collector")
        .withExposedPorts(OTLP_PORT, SIGNALFX_METRICS_PORT, COLLECTOR_HEALTH_CHECK_PORT)
        .waitingFor(Wait.forHttp("/health").forPort(COLLECTOR_HEALTH_CHECK_PORT))
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("collector.yaml"), "/etc/otel.yaml")
        .withCommand("--config /etc/otel.yaml");
  }
}
