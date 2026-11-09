package com.codelry.util.ycsb.postgres;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestPostgresConfig {

  @Test
  void defaultsAndFieldColumns() {
    Properties properties = new Properties();
    properties.setProperty(PostgresConfig.POSTGRES_JDBC_URL, "");
    properties.setProperty(PostgresConfig.POSTGRES_HOST, "db.example");
    properties.setProperty(PostgresConfig.POSTGRES_PORT, "5432");
    properties.setProperty(PostgresConfig.POSTGRES_DATABASE, "ycsb");
    properties.setProperty(PostgresConfig.POSTGRES_USERNAME, "postgres");
    properties.setProperty(PostgresConfig.POSTGRES_PASSWORD, "");
    properties.setProperty(PostgresConfig.POSTGRES_SSL_MODE, "prefer");
    properties.setProperty(PostgresConfig.FIELD_COUNT_PROPERTY, "3");
    properties.setProperty(PostgresConfig.FIELD_NAME_PREFIX_PROPERTY, "field");

    PostgresConfig config = new PostgresConfig(properties);

    assertEquals("db.example", config.getHost());
    assertEquals(5432, config.getPort());
    assertEquals("ycsb", config.getDatabase());
    assertEquals("usertable", config.getTable());
    assertFalse(config.isOAuth());
    assertArrayEquals(new String[] {"field0", "field1", "field2"}, config.fieldColumns());
    assertEquals("jdbc:postgresql://db.example:5432/ycsb?sslmode=prefer", config.getJdbcUrl());
  }

  @Test
  void jdbcUrlOverride() {
    Properties properties = new Properties();
    properties.setProperty(PostgresConfig.POSTGRES_JDBC_URL,
        "jdbc:postgresql://custom:6543/lakebase?sslmode=require");

    PostgresConfig config = new PostgresConfig(properties);
    assertEquals("jdbc:postgresql://custom:6543/lakebase?sslmode=require", config.getJdbcUrl());
  }

  @Test
  void parsesLibpqStyleDatabaseUrl() {
    PostgresConfig.DatabaseUrlParts parts = PostgresConfig.parseDatabaseUrl(
        "postgresql://user%40example.com@ep-host.example.com/databricks_postgres?sslmode=require");

    assertEquals("jdbc:postgresql://ep-host.example.com/databricks_postgres?sslmode=require",
        parts.jdbcUrl);
    assertEquals("ep-host.example.com", parts.host);
    assertEquals("databricks_postgres", parts.database);
    assertEquals("user@example.com", parts.username);
    assertEquals("require", parts.sslMode);
  }

  @Test
  void oauthMode() {
    Properties properties = new Properties();
    properties.setProperty(PostgresConfig.POSTGRES_AUTH, PostgresConfig.AUTH_OAUTH);
    properties.setProperty(PostgresConfig.DATABRICKS_HOST, "https://example.databricks.com");
    properties.setProperty(PostgresConfig.DATABRICKS_CLIENT_ID, "client");
    properties.setProperty(PostgresConfig.DATABRICKS_CLIENT_SECRET, "secret");
    properties.setProperty(PostgresConfig.DATABRICKS_ENDPOINT, "projects/p/branches/b/endpoints/e");

    PostgresConfig config = new PostgresConfig(properties);
    assertTrue(config.isOAuth());
    assertEquals("https://example.databricks.com", config.getDatabricksHost());
    assertEquals("projects/p/branches/b/endpoints/e", config.getDatabricksEndpoint());
  }
}
