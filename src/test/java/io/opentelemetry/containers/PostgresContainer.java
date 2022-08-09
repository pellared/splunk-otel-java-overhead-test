/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.containers;

import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

public class PostgresContainer {

  public static final String PASSWORD = "petclinic";
  public static final String USERNAME = "petclinic";
  public static final String DATABASE_NAME = "petclinic";

  private final Network network;

  public PostgresContainer(Network network) {
    this.network = network;
  }

  public PostgreSQLContainer<?> build() throws Exception {
    return new PostgreSQLContainer<>("postgres:9.6.22")
        .withNetwork(network)
        .withNetworkAliases("postgres")
        .withUsername(USERNAME)
        .withPassword(PASSWORD)
        .withDatabaseName(DATABASE_NAME)
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("initDB.sql"), "/docker-entrypoint-initdb.d/initDB.sql")
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("populateDB.sql"), "/docker-entrypoint-initdb.d/populateDB.sql")
        .withReuse(false);
  }

}
