package com.codelry.util.ycsb.postgres;

import com.codelry.util.ycsb.RunBenchmark;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Live Databricks Lakebase test using {@code JDBC_DATABASE_URL} and {@code OAUTH_TOKEN}
 * from the environment (typically loaded from {@code .env} by Gradle).
 */
@EnabledIfEnvironmentVariable(named = "JDBC_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OAUTH_TOKEN", matches = ".+")
class TestDatabricksWorkloadA {

  @Test
  void runWorkloadAAgainstLakebase() {
    PostgresConfig config = new PostgresConfig();
    Assumptions.assumeFalse(config.getJdbcUrl().isBlank(), "JDBC URL must resolve");
    Assumptions.assumeFalse(config.getUsername().isBlank(), "username must resolve from URL");
    Assumptions.assumeFalse(config.getPassword().isBlank(), "OAUTH_TOKEN must be set");
    assertFalse(config.isOAuth(), "token-as-password uses postgres.auth=password");

    // System properties win over classpath db.properties that YCSB re-applies during setup.
    System.setProperty(PostgresConfig.POSTGRES_JDBC_URL, config.getJdbcUrl());
    System.setProperty(PostgresConfig.POSTGRES_USERNAME, config.getUsername());
    System.setProperty(PostgresConfig.POSTGRES_PASSWORD, config.getPassword());
    System.setProperty(PostgresConfig.POSTGRES_DATABASE, config.getDatabase());
    System.setProperty(PostgresConfig.POSTGRES_HOST, config.getHost());
    System.setProperty(PostgresConfig.POSTGRES_SSL_MODE, config.getSslMode());
    System.setProperty(PostgresConfig.POSTGRES_AUTH, PostgresConfig.AUTH_PASSWORD);
    System.setProperty(PostgresConfig.TEST_CONFIRMATION_RESPONSE, "yes");
    System.setProperty("recordcount", "100");
    System.setProperty("operationcount", "100");
    System.setProperty("threadcount", "4");

    try {
      assertDoesNotThrow(() -> RunBenchmark.main(new String[] { "-w", "a" }));
    } finally {
      System.clearProperty(PostgresConfig.POSTGRES_JDBC_URL);
      System.clearProperty(PostgresConfig.POSTGRES_USERNAME);
      System.clearProperty(PostgresConfig.POSTGRES_PASSWORD);
      System.clearProperty(PostgresConfig.POSTGRES_DATABASE);
      System.clearProperty(PostgresConfig.POSTGRES_HOST);
      System.clearProperty(PostgresConfig.POSTGRES_SSL_MODE);
      System.clearProperty(PostgresConfig.POSTGRES_AUTH);
      System.clearProperty(PostgresConfig.TEST_CONFIRMATION_RESPONSE);
      System.clearProperty("recordcount");
      System.clearProperty("operationcount");
      System.clearProperty("threadcount");
    }
  }
}
