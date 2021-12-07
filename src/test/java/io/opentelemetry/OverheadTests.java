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
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

public class OverheadTests {

  private static final Network NETWORK = Network.newNetwork();
  private static GenericContainer<?> collector;
  private final NamingConventions namingConventions = new NamingConventions();
  private final Map<String,Long> runDurations = new HashMap<>();

  @BeforeAll
  static void setUp() {
    collector = CollectorContainer.build(NETWORK);
    collector.start();
  }

  @AfterAll
  static void tearDown() {
    collector.close();
  }

  @Disabled
  @TestFactory
  Stream<DynamicTest> runAllTestConfigurations() {
    return Configs.all().map(config ->
        dynamicTest(config.getName(), () -> runTestConfig(config))
    );
  }

  void runTestConfig(TestConfig config) {
    runDurations.clear();
    config.getAgents().forEach(agent -> {
      try {
        logProgress(config, agent);
        runAppOnce(config, agent);
      } catch (Exception e) {
        fail("Unhandled exception in " + config.getName(), e);
      }
    });
    List<AppPerfResults> results = new ResultsCollector(namingConventions.local, runDurations).collect(config);
    new MainResultsPersister(config).write(results);
  }

  private void logProgress(TestConfig config, Agent agent) {
    List<TestConfig> configs = Configs.all().collect(Collectors.toList());
    int totalConfigs = configs.size();
    int thisConfigNum = configs.indexOf(config);
    int agents = config.getAgents().size();
    int thisAgentNum = config.getAgents().indexOf(agent);

    int totalAgentRunsAcrossAllConfigs = Configs.all().reduce(0, (integer, testConfig) -> testConfig.getAgents().size(), Integer::sum);
    int currentPosition = 0;
    for (TestConfig testConfig : configs) {
      List<Agent> thisConfigAgents = testConfig.getAgents();
      for (Agent thisConfigAgent : thisConfigAgents) {
        currentPosition++;
        if((testConfig == config) && (thisConfigAgent == agent)){
          break;
        }
      }
    }
    String output = String.format("Config %d/%d Agent %d/%d - Total %d/%d\n", thisConfigNum + 1, totalConfigs, thisAgentNum + 1, agents, currentPosition, totalAgentRunsAcrossAllConfigs);
    System.out.printf(output);
    writeProgress(output);
  }

  private void writeProgress(String output) {
    try {
      Files.writeString(Path.of("/tmp/progress.txt"), output);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  void runAppOnce(TestConfig config, Agent agent) throws Exception {
    GenericContainer<?> postgres = new PostgresContainer(NETWORK).build();
    postgres.start();

    GenericContainer<?> petclinic = new PetClinicRestContainer(NETWORK, collector, agent, namingConventions).build();
    long start = System.currentTimeMillis();
    petclinic.start();
    writeStartupTimeFile(agent, start);

    if(config.getWarmupSeconds() > 0){
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
    while(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start) < testConfig.getWarmupSeconds()){
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
