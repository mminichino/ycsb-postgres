package com.codelry.util.ycsb.postgres;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

import com.databricks.sdk.WorkspaceClient;
import com.databricks.sdk.core.DatabricksConfig;
import com.databricks.sdk.service.postgres.DatabaseCredential;
import com.databricks.sdk.service.postgres.GenerateDatabaseCredentialRequest;

/**
 * JDBC {@link DataSource} that mints a fresh Lakebase OAuth token for each new connection.
 */
final class LakebaseOAuthDataSource implements DataSource {

  private final WorkspaceClient workspaceClient;
  private final String jdbcUrl;
  private final String username;
  private final String endpoint;
  private PrintWriter logWriter;
  private int loginTimeoutSeconds;

  LakebaseOAuthDataSource(PostgresConfig config) {
    if (config.getDatabricksHost().isBlank()
        || config.getDatabricksClientId().isBlank()
        || config.getDatabricksClientSecret().isBlank()
        || config.getDatabricksEndpoint().isBlank()) {
      throw new IllegalArgumentException(
          "OAuth auth requires databricks.host, databricks.clientId, "
              + "databricks.clientSecret, and databricks.endpoint");
    }
    this.workspaceClient = new WorkspaceClient(new DatabricksConfig()
        .setHost(config.getDatabricksHost())
        .setClientId(config.getDatabricksClientId())
        .setClientSecret(config.getDatabricksClientSecret()));
    this.jdbcUrl = config.getJdbcUrl();
    this.username = config.getUsername();
    this.endpoint = config.getDatabricksEndpoint();
  }

  @Override
  public Connection getConnection() throws SQLException {
    DatabaseCredential credential = workspaceClient.postgres().generateDatabaseCredential(
        new GenerateDatabaseCredentialRequest().setEndpoint(endpoint));
    return DriverManager.getConnection(jdbcUrl, username, credential.getToken());
  }

  @Override
  public Connection getConnection(String username, String password) {
    throw new UnsupportedOperationException(
        "Lakebase OAuth DataSource ignores caller credentials; use getConnection()");
  }

  @Override
  public PrintWriter getLogWriter() {
    return logWriter;
  }

  @Override
  public void setLogWriter(PrintWriter out) {
    this.logWriter = out;
  }

  @Override
  public void setLoginTimeout(int seconds) {
    this.loginTimeoutSeconds = seconds;
  }

  @Override
  public int getLoginTimeout() {
    return loginTimeoutSeconds;
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    throw new SQLFeatureNotSupportedException("getParentLogger not supported");
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    if (iface.isInstance(this)) {
      return iface.cast(this);
    }
    throw new SQLException("Not a wrapper for " + iface.getName());
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) {
    return iface.isInstance(this);
  }
}
