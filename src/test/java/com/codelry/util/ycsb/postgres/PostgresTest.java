package com.codelry.util.ycsb.postgres;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class PostgresTest {

  protected PostgreSQLContainer postgres;

  @BeforeEach
  void startPostgres() {
    postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17"))
        .withDatabaseName("ycsb")
        .withUsername("test")
        .withPassword("test");
    postgres.start();
    System.setProperty(PostgresConfig.POSTGRES_JDBC_URL, postgres.getJdbcUrl());
    System.setProperty(PostgresConfig.POSTGRES_USERNAME, postgres.getUsername());
    System.setProperty(PostgresConfig.POSTGRES_PASSWORD, postgres.getPassword());
    System.setProperty(PostgresConfig.POSTGRES_DATABASE, postgres.getDatabaseName());
    System.setProperty(PostgresConfig.POSTGRES_SSL_MODE, "disable");
    System.setProperty(PostgresConfig.POSTGRES_AUTH, PostgresConfig.AUTH_PASSWORD);
  }

  @AfterEach
  void stopPostgres() {
    System.clearProperty(PostgresConfig.POSTGRES_JDBC_URL);
    System.clearProperty(PostgresConfig.POSTGRES_USERNAME);
    System.clearProperty(PostgresConfig.POSTGRES_PASSWORD);
    System.clearProperty(PostgresConfig.POSTGRES_DATABASE);
    System.clearProperty(PostgresConfig.POSTGRES_SSL_MODE);
    System.clearProperty(PostgresConfig.POSTGRES_AUTH);
    if (postgres != null) {
      postgres.stop();
      postgres = null;
    }
  }
}
