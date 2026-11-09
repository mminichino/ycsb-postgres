package com.codelry.util.ycsb.postgres;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds a shared HikariCP pool for password or Lakebase OAuth authentication.
 */
final class PostgresDataSourceFactory {

  private static final Logger logger = LoggerFactory.getLogger(PostgresDataSourceFactory.class);

  private PostgresDataSourceFactory() {
  }

  static HikariDataSource create(PostgresConfig config) {
    HikariConfig hikari = new HikariConfig();
    hikari.setPoolName("ycsb-postgres");
    hikari.setMaximumPoolSize(Math.max(config.getPoolSize(), 8));
    hikari.setMinimumIdle(1);
    hikari.setAutoCommit(true);

    if (config.isOAuth()) {
      logger.info("Initializing PostgreSQL pool with Databricks Lakebase OAuth");
      hikari.setDataSource(new LakebaseOAuthDataSource(config));
      // Recycle connections before the typical 60-minute token expiry.
      hikari.setMaxLifetime(45L * 60L * 1000L);
      hikari.setIdleTimeout(15L * 60L * 1000L);
    } else {
      logger.info("Initializing PostgreSQL pool with password authentication: {}", config.getJdbcUrl());
      hikari.setJdbcUrl(config.getJdbcUrl());
      hikari.setUsername(config.getUsername());
      hikari.setPassword(config.getPassword());
      hikari.setDriverClassName("org.postgresql.Driver");
    }

    return new HikariDataSource(hikari);
  }

  static DataSource createUnpooled(PostgresConfig config) {
    if (config.isOAuth()) {
      return new LakebaseOAuthDataSource(config);
    }
    HikariConfig hikari = new HikariConfig();
    hikari.setJdbcUrl(config.getJdbcUrl());
    hikari.setUsername(config.getUsername());
    hikari.setPassword(config.getPassword());
    hikari.setDriverClassName("org.postgresql.Driver");
    hikari.setMaximumPoolSize(2);
    hikari.setMinimumIdle(1);
    hikari.setPoolName("ycsb-postgres-admin");
    return new HikariDataSource(hikari);
  }
}
