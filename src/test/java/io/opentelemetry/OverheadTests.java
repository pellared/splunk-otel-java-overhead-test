/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry;

import io.opentelemetry.agents.Agent;
import io.opentelemetry.config.Configs;
import io.opentelemetry.config.TestConfig;
import io.opentelemetry.containers.CollectorContainer;
import io.opentelemetry.containers.K6Container;
import io.opentelemetry.containers.PetClinicRestContainer;
import io.opentelemetry.containers.PostgresContainer;
import io.opentelemetry.results.AppPerfResults;
import io.opentelemetry.results.MainResultsPersister;
import io.opentelemetry.results.ResultsCollector;
import io.opentelemetry.util.NamingConventions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.fail;

public class OverheadTests {

  private static final Network NETWORK = Network.newNetwork();
  private static GenericContainer<?> collector;

  private final NamingConventions namingConventions = new NamingConventions();
  private final Map<String, Long> runDurations = new HashMap<>();

  @BeforeAll
  static void setUp() {
    collector = CollectorContainer.build(NETWORK);
    collector.start();
  }

  @AfterAll
  static void tearDown() {
    collector.close();
  }

  @Test
  @Disabled
  void runOverheadTest() {
    TestConfig config = Configs.RELEASE;

    MainResultsPersister resultsPersister = new MainResultsPersister(config, namingConventions);
    List<AppPerfResults> allResults = new ArrayList<>();

    for (int currentPass = 0; currentPass < config.getNumberOfPasses(); ++currentPass) {
      List<AppPerfResults> singlePassResults = runSinglePass(config, currentPass);
      resultsPersister.writePass(singlePassResults);
      allResults.addAll(singlePassResults);
    }

    resultsPersister.writeAll(allResults);
  }

  private List<AppPerfResults> runSinglePass(TestConfig config, int currentPass) {
    runDurations.clear();
    config.getAgents().forEach(agent -> {
      try {
        logProgress(currentPass, config, agent);
        runAppOnce(config, agent);
      } catch (Exception e) {
        fail("Unhandled exception in " + config.getName(), e);
      }
    });
    return new ResultsCollector(namingConventions.local, runDurations).collect(config);
  }

  private void logProgress(int currentPass, TestConfig config, Agent agent) {
    int numberOfAgents = config.getAgents().size();
    int currentAgent = config.getAgents().indexOf(agent);

    int currentPassTotal = currentPass * numberOfAgents + currentAgent;
    int totalNumberOfPasses = numberOfAgents * config.getNumberOfPasses();

    String output = String.format("Pass %d/%d Agent %d/%d - Total %d/%d\n",
        currentPass + 1, config.getNumberOfPasses(),
        currentAgent + 1, numberOfAgents,
        currentPassTotal + 1, totalNumberOfPasses);

    System.out.printf(output);
  }

  void runAppOnce(TestConfig config, Agent agent) throws Exception {
    GenericContainer<?> postgres = new PostgresContainer(NETWORK).build();
    postgres.start();

    GenericContainer<?> petclinic = new PetClinicRestContainer(NETWORK, collector, agent, namingConventions).build();
    long start = System.currentTimeMillis();
    petclinic.start();
    writeStartupTimeFile(agent, start);

    if (config.getWarmupSeconds() > 0) {
      doWarmupPhase(config);
    }

    startRecording(agent, petclinic);

    GenericContainer<?> k6 = new K6Container(NETWORK, agent, config, namingConventions).build();
    k6.start();

    // This is required to get a graceful exit of the VM before testcontainers kills it forcibly.
    // Without it, our jfr file will be empty.
    petclinic.execInContainer("kill", "1");
    while (petclinic.isRunning()) {
      TimeUnit.MILLISECONDS.sleep(500);
    }
    postgres.stop();
  }

  private void startRecording(Agent agent, GenericContainer<?> petclinic) throws Exception {
    Path outFile = namingConventions.container.jfrFile(agent);
    String[] command = {"jcmd", "1", "JFR.start", "settings=profile", "dumponexit=true", "name=petclinic", "filename=" + outFile};
    petclinic.execInContainer(command);
  }

  private void doWarmupPhase(TestConfig testConfig) {
    long start = System.currentTimeMillis();
    System.out.println("Performing startup warming phase for " + testConfig.getWarmupSeconds() + " seconds...");
    while (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start) < testConfig.getWarmupSeconds()) {
      GenericContainer<?> k6 = new GenericContainer<>(
          DockerImageName.parse("loadimpact/k6"))
          .withNetwork(NETWORK)
          .withCopyFileToContainer(
              MountableFile.forHostPath("./k6"), "/app")
          .withCommand("run", "-u", "5", "-i", "25", "/app/basic.js")
          .withStartupCheckStrategy(new OneShotStartupCheckStrategy());
      k6.start();
    }
    System.out.println("Warmup complete.");
  }

  private void writeStartupTimeFile(Agent agent, long start) throws IOException {
    long delta = System.currentTimeMillis() - start;
    Path startupPath = namingConventions.local.startupDurationFile(agent);
    Files.writeString(startupPath, String.valueOf(delta));
  }
}
