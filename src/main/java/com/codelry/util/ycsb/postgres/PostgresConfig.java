package com.codelry.util.ycsb.postgres;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PostgreSQL connection and test configuration.
 *
 * <p>Resolution order (highest wins): JVM system property, constructor overrides,
 * environment variables, {@code db.properties}.
 */
public class PostgresConfig {

  private static final Logger logger = LoggerFactory.getLogger(PostgresConfig.class);

  public static final String PROPERTY_FILE = "db.properties";

  public static final String POSTGRES_JDBC_URL = "postgres.jdbcUrl";
  public static final String POSTGRES_HOST = "postgres.host";
  public static final String POSTGRES_PORT = "postgres.port";
  public static final String POSTGRES_DATABASE = "postgres.database";
  public static final String POSTGRES_TABLE = "postgres.table";
  public static final String POSTGRES_USERNAME = "postgres.username";
  public static final String POSTGRES_PASSWORD = "postgres.password";
  public static final String POSTGRES_SSL = "postgres.ssl";
  public static final String POSTGRES_SSL_MODE = "postgres.sslmode";
  public static final String POSTGRES_AUTH = "postgres.auth";
  public static final String POSTGRES_POOL_SIZE = "postgres.pool.size";
  public static final String POSTGRES_DEBUG = "postgres.debug";
  public static final String BATCH_SIZE = "batchsize";
  public static final String TEST_CONFIRMATION_RESPONSE = "test.confirmation.response";

  public static final String DATABRICKS_HOST = "databricks.host";
  public static final String DATABRICKS_CLIENT_ID = "databricks.clientId";
  public static final String DATABRICKS_CLIENT_SECRET = "databricks.clientSecret";
  public static final String DATABRICKS_ENDPOINT = "databricks.endpoint";

  public static final String AUTH_PASSWORD = "password";
  public static final String AUTH_OAUTH = "oauth";

  public static final String FIELD_COUNT_PROPERTY = "fieldcount";
  public static final String FIELD_COUNT_DEFAULT = "10";
  public static final String FIELD_NAME_PREFIX_PROPERTY = "fieldnameprefix";
  public static final String FIELD_NAME_PREFIX_DEFAULT = "field";

  private final String jdbcUrl;
  private final String host;
  private final int port;
  private final String database;
  private final String table;
  private final String username;
  private final String password;
  private final boolean ssl;
  private final String sslMode;
  private final String auth;
  private final int poolSize;
  private final boolean debug;
  private final int batchSize;
  private final String confirmationResponse;
  private final String databricksHost;
  private final String databricksClientId;
  private final String databricksClientSecret;
  private final String databricksEndpoint;
  private final int fieldCount;
  private final String fieldNamePrefix;
  private final Properties properties;

  public PostgresConfig() {
    this(new Properties());
  }

  public PostgresConfig(Properties overrides) {
    Properties loaded = loadPropertyFile();
    Properties fromEnv = new Properties();
    applyEnvironmentDefaults(fromEnv);
    loaded.putAll(fromEnv);
    if (overrides != null) {
      loaded.putAll(overrides);
    }

    DatabaseUrlParts urlParts = parseDatabaseUrl(firstNonEmpty(
        System.getProperty(POSTGRES_JDBC_URL),
        loaded.getProperty(POSTGRES_JDBC_URL, "")));

    boolean sslDefault = Boolean.parseBoolean(loaded.getProperty(POSTGRES_SSL, "false"));

    this.jdbcUrl = urlParts.jdbcUrl;
    this.host = firstNonEmpty(
        System.getProperty(POSTGRES_HOST),
        loaded.getProperty(POSTGRES_HOST, ""),
        urlParts.host,
        "localhost");
    this.port = Integer.parseInt(firstNonEmpty(
        System.getProperty(POSTGRES_PORT),
        loaded.getProperty(POSTGRES_PORT, ""),
        urlParts.port,
        "5432"));
    this.database = firstNonEmpty(
        System.getProperty(POSTGRES_DATABASE),
        loaded.getProperty(POSTGRES_DATABASE, ""),
        urlParts.database,
        "ycsb");
    this.table = firstNonEmpty(
        System.getProperty(POSTGRES_TABLE),
        loaded.getProperty(POSTGRES_TABLE, "usertable"));
    this.username = firstNonEmpty(
        System.getProperty(POSTGRES_USERNAME),
        loaded.getProperty(POSTGRES_USERNAME, ""),
        urlParts.username,
        "postgres");
    this.password = firstNonEmpty(
        System.getProperty(POSTGRES_PASSWORD),
        loaded.getProperty(POSTGRES_PASSWORD, ""),
        urlParts.password);
    this.ssl = Boolean.parseBoolean(firstNonEmpty(
        System.getProperty(POSTGRES_SSL),
        loaded.getProperty(POSTGRES_SSL, Boolean.toString(sslDefault))));
    this.sslMode = firstNonEmpty(
        System.getProperty(POSTGRES_SSL_MODE),
        loaded.getProperty(POSTGRES_SSL_MODE, ""),
        urlParts.sslMode,
        ssl ? "require" : "prefer");
    this.auth = firstNonEmpty(
        System.getProperty(POSTGRES_AUTH),
        loaded.getProperty(POSTGRES_AUTH, AUTH_PASSWORD)).toLowerCase();
    this.poolSize = Integer.parseInt(firstNonEmpty(
        System.getProperty(POSTGRES_POOL_SIZE),
        loaded.getProperty(POSTGRES_POOL_SIZE, "32")));
    this.debug = Boolean.parseBoolean(firstNonEmpty(
        System.getProperty(POSTGRES_DEBUG),
        loaded.getProperty(POSTGRES_DEBUG, "false")));
    this.batchSize = Integer.parseInt(firstNonEmpty(
        System.getProperty(BATCH_SIZE),
        loaded.getProperty(BATCH_SIZE, "1")));
    this.confirmationResponse = firstNonEmpty(
        System.getProperty(TEST_CONFIRMATION_RESPONSE),
        loaded.getProperty(TEST_CONFIRMATION_RESPONSE, "")).toLowerCase();
    this.databricksHost = firstNonEmpty(
        System.getProperty(DATABRICKS_HOST),
        loaded.getProperty(DATABRICKS_HOST, ""));
    this.databricksClientId = firstNonEmpty(
        System.getProperty(DATABRICKS_CLIENT_ID),
        loaded.getProperty(DATABRICKS_CLIENT_ID, ""));
    this.databricksClientSecret = firstNonEmpty(
        System.getProperty(DATABRICKS_CLIENT_SECRET),
        loaded.getProperty(DATABRICKS_CLIENT_SECRET, ""));
    this.databricksEndpoint = firstNonEmpty(
        System.getProperty(DATABRICKS_ENDPOINT),
        loaded.getProperty(DATABRICKS_ENDPOINT, ""));
    this.fieldCount = Integer.parseInt(firstNonEmpty(
        System.getProperty(FIELD_COUNT_PROPERTY),
        loaded.getProperty(FIELD_COUNT_PROPERTY, FIELD_COUNT_DEFAULT)));
    this.fieldNamePrefix = firstNonEmpty(
        System.getProperty(FIELD_NAME_PREFIX_PROPERTY),
        loaded.getProperty(FIELD_NAME_PREFIX_PROPERTY, FIELD_NAME_PREFIX_DEFAULT));

    this.properties = new Properties();
    this.properties.putAll(loaded);
    applyResolvedProperties();

    if (debug) {
      Configurator.setRootLevel(Level.DEBUG);
      Configurator.setLevel("com.codelry.util.ycsb.postgres", Level.DEBUG);
      logger.debug("PostgreSQL debug logging enabled");
    }
  }

  private static void applyEnvironmentDefaults(Properties target) {
    putEnv(target, POSTGRES_JDBC_URL, "POSTGRES_JDBC_URL");
    applyDatabaseUrlEnv(target, "JDBC_DATABASE_URL", "DATABASE_URL");
    putEnv(target, POSTGRES_HOST, "POSTGRES_HOST");
    putEnv(target, POSTGRES_PORT, "POSTGRES_PORT");
    putEnv(target, POSTGRES_DATABASE, "POSTGRES_DATABASE");
    putEnv(target, POSTGRES_TABLE, "POSTGRES_TABLE");
    putEnv(target, POSTGRES_USERNAME, "POSTGRES_USERNAME");
    putEnv(target, POSTGRES_PASSWORD, "POSTGRES_PASSWORD", "OAUTH_TOKEN");
    putEnv(target, POSTGRES_SSL, "POSTGRES_SSL");
    putEnv(target, POSTGRES_SSL_MODE, "POSTGRES_SSLMODE");
    putEnv(target, POSTGRES_AUTH, "POSTGRES_AUTH");
    putEnv(target, POSTGRES_POOL_SIZE, "POSTGRES_POOL_SIZE");
    putEnv(target, POSTGRES_DEBUG, "POSTGRES_DEBUG");
    putEnv(target, BATCH_SIZE, "BATCHSIZE");
    putEnv(target, DATABRICKS_HOST, "DATABRICKS_HOST");
    putEnv(target, DATABRICKS_CLIENT_ID, "DATABRICKS_CLIENT_ID");
    putEnv(target, DATABRICKS_CLIENT_SECRET, "DATABRICKS_CLIENT_SECRET");
    putEnv(target, DATABRICKS_ENDPOINT, "DATABRICKS_ENDPOINT", "ENDPOINT_NAME");
  }

  /**
   * Expand libpq-style URLs from the environment into component properties so callers can
   * override individual fields (host, user, etc.) without fighting an opaque JDBC URL.
   */
  private static void applyDatabaseUrlEnv(Properties target, String... envKeys) {
    if (!isBlank(target.getProperty(POSTGRES_JDBC_URL))) {
      return;
    }
    for (String envKey : envKeys) {
      String value = System.getenv(envKey);
      if (isBlank(value)) {
        continue;
      }
      DatabaseUrlParts parts = parseDatabaseUrl(value.trim());
      putIfAbsent(target, POSTGRES_JDBC_URL, parts.jdbcUrl);
      putIfAbsent(target, POSTGRES_HOST, parts.host);
      putIfAbsent(target, POSTGRES_PORT, parts.port);
      putIfAbsent(target, POSTGRES_DATABASE, parts.database);
      putIfAbsent(target, POSTGRES_USERNAME, parts.username);
      putIfAbsent(target, POSTGRES_PASSWORD, parts.password);
      putIfAbsent(target, POSTGRES_SSL_MODE, parts.sslMode);
      return;
    }
  }

  private static void putEnv(Properties target, String propertyKey, String... envKeys) {
    if (!isBlank(target.getProperty(propertyKey))) {
      return;
    }
    for (String envKey : envKeys) {
      String value = System.getenv(envKey);
      if (!isBlank(value)) {
        target.setProperty(propertyKey, value.trim());
        return;
      }
    }
  }

  private static void putIfAbsent(Properties target, String propertyKey, String value) {
    if (!isBlank(value) && isBlank(target.getProperty(propertyKey))) {
      target.setProperty(propertyKey, value);
    }
  }

  private void applyResolvedProperties() {
    properties.setProperty(POSTGRES_HOST, host);
    properties.setProperty(POSTGRES_PORT, Integer.toString(port));
    properties.setProperty(POSTGRES_DATABASE, database);
    properties.setProperty(POSTGRES_TABLE, table);
    properties.setProperty(POSTGRES_USERNAME, username);
    properties.setProperty(POSTGRES_PASSWORD, password);
    properties.setProperty(POSTGRES_SSL, Boolean.toString(ssl));
    properties.setProperty(POSTGRES_SSL_MODE, sslMode);
    properties.setProperty(POSTGRES_AUTH, auth);
    properties.setProperty(POSTGRES_POOL_SIZE, Integer.toString(poolSize));
    properties.setProperty(POSTGRES_DEBUG, Boolean.toString(debug));
    properties.setProperty(BATCH_SIZE, Integer.toString(batchSize));
    properties.setProperty(FIELD_COUNT_PROPERTY, Integer.toString(fieldCount));
    properties.setProperty(FIELD_NAME_PREFIX_PROPERTY, fieldNamePrefix);
    if (!jdbcUrl.isEmpty()) {
      properties.setProperty(POSTGRES_JDBC_URL, jdbcUrl);
    }
    if (!databricksHost.isEmpty()) {
      properties.setProperty(DATABRICKS_HOST, databricksHost);
    }
    if (!databricksClientId.isEmpty()) {
      properties.setProperty(DATABRICKS_CLIENT_ID, databricksClientId);
    }
    if (!databricksClientSecret.isEmpty()) {
      properties.setProperty(DATABRICKS_CLIENT_SECRET, databricksClientSecret);
    }
    if (!databricksEndpoint.isEmpty()) {
      properties.setProperty(DATABRICKS_ENDPOINT, databricksEndpoint);
    }
  }

  public Properties asProperties() {
    Properties copy = new Properties();
    copy.putAll(properties);
    return copy;
  }

  public String getJdbcUrl() {
    if (jdbcUrl != null && !jdbcUrl.isBlank()) {
      return jdbcUrl;
    }
    StringBuilder url = new StringBuilder("jdbc:postgresql://")
        .append(host).append(':').append(port).append('/').append(database);
    url.append("?sslmode=").append(sslMode);
    return url.toString();
  }

  public boolean isOAuth() {
    return AUTH_OAUTH.equalsIgnoreCase(auth);
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public String getDatabase() {
    return database;
  }

  public String getTable() {
    return table;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public boolean isSsl() {
    return ssl;
  }

  public String getSslMode() {
    return sslMode;
  }

  public String getAuth() {
    return auth;
  }

  public int getPoolSize() {
    return poolSize;
  }

  public boolean isDebug() {
    return debug;
  }

  public int getBatchSize() {
    return Math.max(1, batchSize);
  }

  public String getConfirmationResponse() {
    return confirmationResponse;
  }

  public String getDatabricksHost() {
    return databricksHost;
  }

  public String getDatabricksClientId() {
    return databricksClientId;
  }

  public String getDatabricksClientSecret() {
    return databricksClientSecret;
  }

  public String getDatabricksEndpoint() {
    return databricksEndpoint;
  }

  public int getFieldCount() {
    return fieldCount;
  }

  public String getFieldNamePrefix() {
    return fieldNamePrefix;
  }

  public String[] fieldColumns() {
    String[] columns = new String[fieldCount];
    for (int i = 0; i < fieldCount; i++) {
      columns[i] = fieldNamePrefix + i;
    }
    return columns;
  }

  static DatabaseUrlParts parseDatabaseUrl(String rawUrl) {
    if (rawUrl == null || rawUrl.isBlank()) {
      return DatabaseUrlParts.empty();
    }

    String trimmed = rawUrl.trim();
    try {
      String normalized = trimmed;
      if (normalized.startsWith("jdbc:postgresql://")) {
        normalized = "postgresql://" + normalized.substring("jdbc:postgresql://".length());
      } else if (normalized.startsWith("postgres://")) {
        normalized = "postgresql://" + normalized.substring("postgres://".length());
      }

      URI uri = URI.create(normalized);
      String userInfo = uri.getRawUserInfo();
      String username = "";
      String password = "";
      if (userInfo != null && !userInfo.isBlank()) {
        int colon = userInfo.indexOf(':');
        if (colon >= 0) {
          username = urlDecode(userInfo.substring(0, colon));
          password = urlDecode(userInfo.substring(colon + 1));
        } else {
          username = urlDecode(userInfo);
        }
      }

      String host = uri.getHost() == null ? "" : uri.getHost();
      String port = uri.getPort() > 0 ? Integer.toString(uri.getPort()) : "";
      String path = uri.getPath() == null ? "" : uri.getPath();
      String database = path.startsWith("/") ? path.substring(1) : path;
      if (database.contains("/")) {
        database = database.substring(0, database.indexOf('/'));
      }

      String sslMode = "";
      String query = uri.getRawQuery();
      StringBuilder jdbcQuery = new StringBuilder();
      if (query != null && !query.isBlank()) {
        jdbcQuery.append('?').append(query);
        for (String part : query.split("&")) {
          int eq = part.indexOf('=');
          if (eq > 0 && "sslmode".equalsIgnoreCase(part.substring(0, eq))) {
            sslMode = urlDecode(part.substring(eq + 1));
          }
        }
      }

      String jdbcUrl = "jdbc:postgresql://" + host
          + (port.isEmpty() ? "" : ":" + port)
          + "/" + database
          + jdbcQuery;

      return new DatabaseUrlParts(jdbcUrl, host, port, database, username, password, sslMode);
    } catch (IllegalArgumentException e) {
      logger.warn("Unable to parse database URL; using as opaque JDBC URL");
      String jdbcUrl = trimmed.startsWith("jdbc:") ? trimmed : "jdbc:" + trimmed;
      return new DatabaseUrlParts(jdbcUrl, "", "", "", "", "", "");
    }
  }

  private static String urlDecode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

  private static Properties loadPropertyFile() {
    Properties properties = new Properties();
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    if (classLoader == null) {
      classLoader = PostgresConfig.class.getClassLoader();
    }
    try (InputStream in = classLoader.getResourceAsStream(PROPERTY_FILE)) {
      if (in != null) {
        properties.load(in);
      }
    } catch (IOException e) {
      logger.warn("Unable to load {}", PROPERTY_FILE, e);
    }
    return properties;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static String firstNonEmpty(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }
    return "";
  }

  static final class DatabaseUrlParts {
    final String jdbcUrl;
    final String host;
    final String port;
    final String database;
    final String username;
    final String password;
    final String sslMode;

    DatabaseUrlParts(String jdbcUrl, String host, String port, String database,
                     String username, String password, String sslMode) {
      this.jdbcUrl = jdbcUrl;
      this.host = host;
      this.port = port;
      this.database = database;
      this.username = username;
      this.password = password;
      this.sslMode = sslMode;
    }

    static DatabaseUrlParts empty() {
      return new DatabaseUrlParts("", "", "", "", "", "", "");
    }
  }
}
