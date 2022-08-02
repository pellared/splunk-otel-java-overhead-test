/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry;

import io.opentelemetry.agents.Agent;
import io.opentelemetry.config.Configs;
import io.opentelemetry.config.TestConfig;
import io.opentelemetry.containers.K6Container;
import io.opentelemetry.containers.PetClinicRestContainer;
import io.opentelemetry.containers.RemotePostgresContainer;
import io.opentelemetry.results.AppPerfResults;
import io.opentelemetry.results.MainResultsPersister;
import io.opentelemetry.results.ResultsCollector;
import io.opentelemetry.util.NamingConventions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

// Overhead tests but with remote collector and postgres components.
public class OverheadWithExternalsTests {
  private static final Logger logger = LoggerFactory.getLogger(OverheadWithExternalsTests.class);

  private static final Network NETWORK = Network.newNetwork();
  public static final String ENV_EXTERNALS_HOST = "EXTERNALS_HOST";

  private final NamingConventions namingConventions = new NamingConventions();
  private final Map<String, Long> runDurations = new HashMap<>();

  @Test
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
    writeProgress(output);
  }

  private void writeProgress(String output) {
    try {
      Files.writeString(Path.of("/tmp/progress.txt"), output);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void runAppOnce(TestConfig config, Agent agent) throws Exception {
    RemotePostgresContainer postgres = RemotePostgresContainer.build(getPostgresHost());
    postgres.start();
    try {
      runApp(config, agent);
    } finally {
      postgres.stop();
    }
  }

  private void runApp(TestConfig config, Agent agent) throws Exception {
    verifyExternals();

    GenericContainer<?> petclinic = new PetClinicRestContainer(NETWORK, agent, namingConventions, getPostgresHost(), getCollectorHost()).build();
    long start = System.currentTimeMillis();
    try {
      logger.info("Starting petclinic container");
      petclinic.start();
    } finally {
      logger.info("Petclinic container has started or failed to start.");
    }
    writeStartupTimeFile(agent, start);

    if (config.getWarmupSeconds() > 0) {
      doWarmupPhase(config, petclinic);
    }

    long testStart = System.currentTimeMillis();
    startRecording(agent, petclinic);

    GenericContainer<?> k6 = new K6Container(NETWORK, agent, config, namingConventions).build();
    k6.start();

    long runDuration = System.currentTimeMillis() - testStart;
    runDurations.put(agent.getName(), runDuration);

    // This is required to get a graceful exit of the VM before testcontainers kills it forcibly.
    // Without it, our jfr file will be empty.
    petclinic.execInContainer("kill", "1");
    while (petclinic.isRunning()) {
      TimeUnit.MILLISECONDS.sleep(500);
    }
  }

  private void verifyExternals() {
    assertNotNull(getPostgresHost(), "You must define EXTERNALS_HOST env var");
  }

  private String getPostgresHost() {
    return System.getenv(ENV_EXTERNALS_HOST);
  }

  private String getCollectorHost() {
    return System.getenv(ENV_EXTERNALS_HOST);
  }

  private void startRecording(Agent agent, GenericContainer<?> petclinic) throws Exception {
    Path outFile = namingConventions.container.jfrFile(agent);
    String[] command = {"jcmd", "1", "JFR.start", "settings=/app/overhead.jfc", "dumponexit=true", "name=petclinic", "filename=" + outFile};
    petclinic.execInContainer(command);
  }

  private void doWarmupPhase(TestConfig testConfig, GenericContainer<?> petclinic) throws IOException, InterruptedException {
    logger.info("Performing startup warming phase for " + testConfig.getWarmupSeconds() + " seconds...");

    logger.info("Starting disposable JFR warmup recording...");
    String[] startCommand = {"jcmd", "1", "JFR.start", "settings=/app/overhead.jfc", "dumponexit=true", "name=warmup", "filename=warmup.jfr"};
    petclinic.execInContainer(startCommand);

    long deadline =
        System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(testConfig.getWarmupSeconds());
    while (System.currentTimeMillis() < deadline) {
      GenericContainer<?> k6 = new GenericContainer<>(
          DockerImageName.parse("loadimpact/k6"))
          .withNetwork(NETWORK)
          .withCopyFileToContainer(
              MountableFile.forHostPath("./k6"), "/app")
          .withCommand("run", "-u", "5", "-i", "200", "/app/basic.js")
          .withStartupCheckStrategy(new OneShotStartupCheckStrategy());
      k6.start();
    }

    logger.info("Stopping disposable JFR warmup recording...");
    String[] stopCommand = {"jcmd", "1", "JFR.stop", "name=warmup"};
    petclinic.execInContainer(stopCommand);

    logger.info("Warmup complete.");
  }

  private void writeStartupTimeFile(Agent agent, long start) throws IOException {
    long delta = System.currentTimeMillis() - start;
    Path startupPath = namingConventions.local.startupDurationFile(agent);
    Files.writeString(startupPath, String.valueOf(delta));
  }
}
