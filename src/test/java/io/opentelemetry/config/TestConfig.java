/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.config;

import io.opentelemetry.agents.Agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Defines a test config.
 */
public class TestConfig {

  private final static int DEFAULT_NUMBER_OF_PASSES = 1;
  private final static int DEFAULT_MAX_REQUEST_RATE = 0;  // none
  private final static int DEFAULT_CONCURRENT_CONNECTIONS = 5;
  private final static int DEFAULT_K6_ITERATIONS = 500;

  private final String name;
  private final String description;
  private final List<Agent> agents;
  private final int numberOfPasses;
  private final int maxRequestRate;
  private final int concurrentConnections;
  private final int k6Iterations;
  private final int warmupSeconds;

  public TestConfig(Builder builder) {
    this.name = builder.name;
    this.description = builder.description;
    this.agents = Collections.unmodifiableList(builder.agents);
    this.numberOfPasses = builder.numberOfPasses;
    this.maxRequestRate = builder.maxRequestRate;
    this.concurrentConnections = builder.concurrentConnections;
    this.k6Iterations = builder.k6Iterations;
    this.warmupSeconds = builder.warmupSeconds;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public List<Agent> getAgents() {
    return agents;
  }

  /**
   * Represents the number of times this {@link TestConfig} will be executed (meaning: executing the k6 script with
   * configured settings for all agents).
   */
  public int getNumberOfPasses() {
    return numberOfPasses;
  }

  public int getMaxRequestRate() {
    return maxRequestRate;
  }

  public int getConcurrentConnections() {
    return concurrentConnections;
  }

  /**
   * The number of times the k6 script will be run against the tested app.
   * <p>
   * Also see <a href="https://k6.io/docs/using-k6/k6-options/reference/#iterations">k6 docs</a>.
   */
  public int getK6Iterations() {
    return k6Iterations;
  }

  public int getWarmupSeconds() {
    return warmupSeconds;
  }

  public static Builder builder() {
    return new Builder();
  }

  static class Builder {
    private String name;
    private String description;
    private List<Agent> agents = new ArrayList<>();
    private int numberOfPasses = DEFAULT_NUMBER_OF_PASSES;
    private int maxRequestRate = DEFAULT_MAX_REQUEST_RATE;
    private int concurrentConnections = DEFAULT_CONCURRENT_CONNECTIONS;
    private int k6Iterations = DEFAULT_K6_ITERATIONS;
    public int warmupSeconds = 0;

    Builder name(String name) {
      this.name = name;
      return this;
    }

    Builder description(String description) {
      this.description = description;
      return this;
    }

    Builder withAgents(Agent... agents) {
      this.agents.addAll(Arrays.asList(agents));
      return this;
    }

    Builder numberOfPasses(int numberOfPasses) {
      this.numberOfPasses = numberOfPasses;
      return this;
    }

    Builder maxRequestRate(int maxRequestRate) {
      this.maxRequestRate = maxRequestRate;
      return this;
    }

    Builder concurrentConnections(int concurrentConnections) {
      this.concurrentConnections = concurrentConnections;
      return this;
    }

    Builder k6Iterations(int totalIterations) {
      this.k6Iterations = totalIterations;
      return this;
    }

    Builder warmupSeconds(int warmupSeconds) {
      this.warmupSeconds = warmupSeconds;
      return this;
    }

    TestConfig build() {
      return new TestConfig(this);
    }
  }
}
