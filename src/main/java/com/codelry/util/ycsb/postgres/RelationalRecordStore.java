package com.codelry.util.ycsb.postgres;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import com.codelry.util.ycsb.ByteIterator;
import com.codelry.util.ycsb.Status;
import com.codelry.util.ycsb.StringByteIterator;
import com.zaxxer.hikari.HikariDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classic YCSB JDBC-style relational store: {@code YCSB_KEY} plus TEXT field columns.
 */
public class RelationalRecordStore implements RecordStore {

  private static final Logger logger = LoggerFactory.getLogger(RelationalRecordStore.class);

  private static final String KEY_COLUMN = "YCSB_KEY";
  private static final Object INIT_COORDINATOR = new Object();
  private static final AtomicInteger INIT_COUNT = new AtomicInteger(0);

  private static HikariDataSource dataSource;
  private static int batchSize = 1;

  private final List<PendingInsert> pendingInserts = new ArrayList<>();

  RelationalRecordStore(PostgresConfig config) {
    INIT_COUNT.incrementAndGet();
    synchronized (INIT_COORDINATOR) {
      if (dataSource == null) {
        try {
          dataSource = PostgresDataSourceFactory.create(config);
          batchSize = config.getBatchSize();
          logger.info("PostgreSQL relational store ready (batchsize={})", batchSize);
        } catch (RuntimeException e) {
          INIT_COUNT.decrementAndGet();
          throw e;
        }
      }
    }
  }

  @Override
  public void disconnect() {
    flushPendingInserts();
    synchronized (INIT_COORDINATOR) {
      if (INIT_COUNT.decrementAndGet() <= 0 && dataSource != null) {
        logger.debug("Closing PostgreSQL connection pool");
        dataSource.close();
        dataSource = null;
        INIT_COUNT.set(0);
      }
    }
  }

  @Override
  public Status read(String table, String key, Set<String> fields, Map<String, ByteIterator> result) {
    flushPendingInserts();
    String sql = buildReadSql(table, fields);
    try (Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, key);
      try (ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          return Status.NOT_FOUND;
        }
        int columnCount = rs.getMetaData().getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
          String column = rs.getMetaData().getColumnLabel(i);
          if (KEY_COLUMN.equalsIgnoreCase(column)) {
            continue;
          }
          String value = rs.getString(i);
          if (value != null) {
            result.put(column.toLowerCase(), new StringByteIterator(value));
          }
        }
        return Status.OK;
      }
    } catch (SQLException e) {
      logger.error("read failed for key {}: {}", key, e.getMessage(), e);
      return Status.ERROR;
    }
  }

  @Override
  public Status insert(String table, String key, Map<String, ByteIterator> values) {
    if (batchSize <= 1) {
      return executeInsert(table, key, values);
    }

    pendingInserts.add(new PendingInsert(table, key, StringByteIterator.getStringMap(values)));
    if (pendingInserts.size() < batchSize) {
      return Status.BATCHED_OK;
    }
    return flushPendingInserts();
  }

  @Override
  public Status update(String table, String key, Map<String, ByteIterator> values) {
    flushPendingInserts();
    if (values == null || values.isEmpty()) {
      return Status.BAD_REQUEST;
    }

    List<String> columns = new ArrayList<>(values.keySet());
    StringBuilder sql = new StringBuilder("UPDATE ")
        .append(SqlUtil.quoteIdent(table))
        .append(" SET ");
    for (int i = 0; i < columns.size(); i++) {
      if (i > 0) {
        sql.append(", ");
      }
      sql.append(SqlUtil.quoteIdent(columns.get(i))).append(" = ?");
    }
    sql.append(" WHERE ").append(SqlUtil.quoteIdent(KEY_COLUMN)).append(" = ?");

    try (Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(sql.toString())) {
      int index = 1;
      for (String column : columns) {
        statement.setString(index++, values.get(column).toString());
      }
      statement.setString(index, key);
      int updated = statement.executeUpdate();
      return updated > 0 ? Status.OK : Status.NOT_FOUND;
    } catch (SQLException e) {
      logger.error("update failed for key {}: {}", key, e.getMessage(), e);
      return Status.ERROR;
    }
  }

  @Override
  public Status delete(String table, String key) {
    flushPendingInserts();
    String sql = "DELETE FROM " + SqlUtil.quoteIdent(table)
        + " WHERE " + SqlUtil.quoteIdent(KEY_COLUMN) + " = ?";
    try (Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, key);
      int deleted = statement.executeUpdate();
      return deleted > 0 ? Status.OK : Status.NOT_FOUND;
    } catch (SQLException e) {
      logger.error("delete failed for key {}: {}", key, e.getMessage(), e);
      return Status.ERROR;
    }
  }

  @Override
  public Status scan(String table, String startkey, int recordcount, Set<String> fields,
                     Vector<HashMap<String, ByteIterator>> result) {
    flushPendingInserts();
    String sql = buildScanSql(table, fields);
    try (Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, startkey);
      statement.setInt(2, recordcount);
      try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
          HashMap<String, ByteIterator> row = new HashMap<>();
          int columnCount = rs.getMetaData().getColumnCount();
          for (int i = 1; i <= columnCount; i++) {
            String column = rs.getMetaData().getColumnLabel(i);
            if (KEY_COLUMN.equalsIgnoreCase(column)) {
              continue;
            }
            String value = rs.getString(i);
            if (value != null) {
              row.put(column.toLowerCase(), new StringByteIterator(value));
            }
          }
          result.add(row);
        }
      }
      return Status.OK;
    } catch (SQLException e) {
      logger.error("scan failed from key {}: {}", startkey, e.getMessage(), e);
      return Status.ERROR;
    }
  }

  private Status executeInsert(String table, String key, Map<String, ByteIterator> values) {
    Map<String, String> stringValues = StringByteIterator.getStringMap(values);
    List<String> columns = new ArrayList<>(stringValues.keySet());
    String sql = buildInsertSql(table, columns);
    try (Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      bindInsert(statement, key, columns, stringValues);
      statement.executeUpdate();
      return Status.OK;
    } catch (SQLException e) {
      logger.error("insert failed for key {}: {}", key, e.getMessage(), e);
      return Status.ERROR;
    }
  }

  private Status flushPendingInserts() {
    if (pendingInserts.isEmpty()) {
      return Status.OK;
    }

    List<PendingInsert> batch = new ArrayList<>(pendingInserts);
    pendingInserts.clear();

    String table = batch.get(0).table;
    List<String> columns = new ArrayList<>(batch.get(0).values.keySet());
    String sql = buildInsertSql(table, columns);

    try (Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      for (PendingInsert insert : batch) {
        if (!insert.table.equals(table)) {
          throw new SQLException("Mixed tables in insert batch are not supported");
        }
        bindInsert(statement, insert.key, columns, insert.values);
        statement.addBatch();
      }
      statement.executeBatch();
      return Status.OK;
    } catch (SQLException e) {
      logger.error("batched insert failed: {}", e.getMessage(), e);
      return Status.ERROR;
    }
  }

  private static void bindInsert(PreparedStatement statement, String key, List<String> columns,
                                 Map<String, String> values) throws SQLException {
    statement.setString(1, key);
    int index = 2;
    for (String column : columns) {
      statement.setString(index++, values.get(column));
    }
  }

  private static String buildInsertSql(String table, List<String> columns) {
    StringBuilder sql = new StringBuilder("INSERT INTO ")
        .append(SqlUtil.quoteIdent(table))
        .append(" (")
        .append(SqlUtil.quoteIdent(KEY_COLUMN));
    for (String column : columns) {
      sql.append(", ").append(SqlUtil.quoteIdent(column));
    }
    sql.append(") VALUES (?");
    for (int i = 0; i < columns.size(); i++) {
      sql.append(", ?");
    }
    sql.append(')');
    return sql.toString();
  }

  private static String buildReadSql(String table, Set<String> fields) {
    StringBuilder sql = new StringBuilder("SELECT ");
    if (fields == null || fields.isEmpty()) {
      sql.append('*');
    } else {
      boolean first = true;
      for (String field : fields) {
        if (!first) {
          sql.append(", ");
        }
        sql.append(SqlUtil.quoteIdent(field));
        first = false;
      }
    }
    sql.append(" FROM ").append(SqlUtil.quoteIdent(table))
        .append(" WHERE ").append(SqlUtil.quoteIdent(KEY_COLUMN)).append(" = ?");
    return sql.toString();
  }

  private static String buildScanSql(String table, Set<String> fields) {
    StringBuilder sql = new StringBuilder("SELECT ");
    if (fields == null || fields.isEmpty()) {
      sql.append('*');
    } else {
      boolean first = true;
      for (String field : fields) {
        if (!first) {
          sql.append(", ");
        }
        sql.append(SqlUtil.quoteIdent(field));
        first = false;
      }
    }
    sql.append(" FROM ").append(SqlUtil.quoteIdent(table))
        .append(" WHERE ").append(SqlUtil.quoteIdent(KEY_COLUMN)).append(" >= ?")
        .append(" ORDER BY ").append(SqlUtil.quoteIdent(KEY_COLUMN))
        .append(" LIMIT ?");
    return sql.toString();
  }

  private record PendingInsert(String table, String key, Map<String, String> values) {
  }
}
