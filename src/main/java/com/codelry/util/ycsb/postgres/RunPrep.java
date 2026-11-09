package com.codelry.util.ycsb.postgres;

import com.codelry.util.ycsb.TestSetup;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Properties;
import java.util.Scanner;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prepare a PostgreSQL table for testing.
 */
public final class RunPrep extends TestSetup {

  public static final Logger logger = LoggerFactory.getLogger(RunPrep.class);

  @Override
  public void testSetup(Properties properties) {
    try {
      PostgresConfig postgresConfig = new PostgresConfig(properties);
      requestConfirmation(postgresConfig);
      prepareTable(postgresConfig);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      System.exit(1);
    }
  }

  public void requestConfirmation(PostgresConfig postgresConfig) {
    if (System.getProperty("ycsb.run.confirm") != null
        || postgresConfig.getConfirmationResponse().equals("yes")) {
      return;
    }

    Scanner scanner = new Scanner(System.in);

    logger.info("WARNING: This will drop and recreate table {} in database {}.",
        postgresConfig.getTable(), postgresConfig.getDatabase());
    logger.info("Proceed with benchmark run? (y/n): ");
    String response = scanner.nextLine().toLowerCase();

    if (response.equals("y") || response.equals("yes")) {
      System.setProperty("ycsb.run.confirm", "true");
      scanner.close();
    } else {
      logger.warn("Benchmark run cancelled.");
      System.exit(0);
    }
  }

  public static void prepareTable(PostgresConfig postgresConfig) {
    DataSource dataSource = PostgresDataSourceFactory.createUnpooled(postgresConfig);
    try {
      String table = postgresConfig.getTable();
      String[] columns = postgresConfig.fieldColumns();

      StringBuilder create = new StringBuilder("CREATE TABLE ")
          .append(SqlUtil.quoteIdent(table))
          .append(" (")
          .append(SqlUtil.quoteIdent("YCSB_KEY"))
          .append(" VARCHAR(255) PRIMARY KEY");
      for (String column : columns) {
        create.append(", ")
            .append(SqlUtil.quoteIdent(column))
            .append(" TEXT");
      }
      create.append(')');

      try (Connection connection = dataSource.getConnection();
           Statement statement = connection.createStatement()) {
        logger.info("Dropping table {} if it exists", table);
        statement.execute("DROP TABLE IF EXISTS " + SqlUtil.quoteIdent(table));

        logger.info("Creating table {} with {} field columns", table, columns.length);
        statement.execute(create.toString());
      }
    } catch (Exception e) {
      logger.error("PostgreSQL table preparation failed: {}", e.getMessage(), e);
      System.exit(1);
    } finally {
      closeQuietly(dataSource);
    }
  }

  private static void closeQuietly(DataSource dataSource) {
    if (dataSource instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception e) {
        logger.debug("Error closing admin data source: {}", e.getMessage());
      }
    }
  }

  public RunPrep() {
    super();
  }
}
