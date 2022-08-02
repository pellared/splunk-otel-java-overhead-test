/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.results;

import io.opentelemetry.config.TestConfig;
import io.opentelemetry.util.NamingConventions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class MainResultsPersister {

  private final TestConfig config;
  private final Path outputDir;

  public MainResultsPersister(TestConfig config, NamingConventions namingConventions) {
    this.config = config;
    this.outputDir = Paths.get(namingConventions.localResults(), config.getName());
  }

  public void writePass(List<AppPerfResults> singlePassResults) {
    ensureCreated(outputDir);
    new CsvPersister(outputDir.resolve("results.csv")).write(singlePassResults);

    // TODO: have these average the results and print them out at the end
    new ConsoleResultsPersister().write(singlePassResults);
    new FileSummaryPersister(outputDir.resolve("summary.txt")).write(singlePassResults);
  }

  public void writeAll(List<AppPerfResults> results) {
    new YamlSummaryPersister(outputDir.resolve("results.yaml")).write(results);
    new ConfigPersister(outputDir.resolve("config.json")).write(config);
  }

  private void ensureCreated(Path outputDir) {
    try {
      Files.createDirectories(outputDir);
    } catch (IOException e) {
      throw new RuntimeException("Error creating output directory", e);
    }
  }
}
