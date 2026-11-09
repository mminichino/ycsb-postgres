package com.codelry.util.ycsb.postgres;

import com.codelry.util.ycsb.TestCleanup;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Properties;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clean up PostgreSQL table after testing.
 */
public final class RunClean extends TestCleanup {

  public static final Logger logger = LoggerFactory.getLogger(RunClean.class);

  @Override
  public void testClean(Properties properties) {
    try {
      dropTable(properties);
    } catch (Exception e) {
      logger.error("Error: {}", String.valueOf(e));
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }

  public static void dropTable(Properties properties) {
    PostgresConfig postgresConfig = new PostgresConfig(properties);
    DataSource dataSource = PostgresDataSourceFactory.createUnpooled(postgresConfig);

    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement()) {
      logger.info("Dropping table {}", postgresConfig.getTable());
      statement.execute("DROP TABLE IF EXISTS " + SqlUtil.quoteIdent(postgresConfig.getTable()));
    } catch (Exception e) {
      logger.error("Error dropping table: {}", e.getMessage(), e);
      System.exit(1);
    } finally {
      if (dataSource instanceof AutoCloseable closeable) {
        try {
          closeable.close();
        } catch (Exception e) {
          logger.debug("Error closing admin data source: {}", e.getMessage());
        }
      }
    }
  }

  public RunClean() {
    super();
  }
}
