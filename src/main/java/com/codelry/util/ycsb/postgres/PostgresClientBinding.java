package com.codelry.util.ycsb.postgres;

import com.codelry.util.ycsb.ByteIterator;
import com.codelry.util.ycsb.DB;
import com.codelry.util.ycsb.DBException;
import com.codelry.util.ycsb.Status;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PostgreSQL client for YCSB framework.
 *
 * <p>Stores records in a classic relational table ({@code YCSB_KEY} + TEXT field columns).
 * Supports password authentication and optional Databricks Lakebase OAuth via the Databricks SDK.
 */
public class PostgresClientBinding extends DB {

  private static final Logger LOGGER = LoggerFactory.getLogger(PostgresClientBinding.class);

  private RecordStore recordStore;

  @Override
  public void init() throws DBException {
    Properties props = new Properties();
    props.putAll(getProperties());
    PostgresConfig postgresConfig = new PostgresConfig(props);
    props.putAll(postgresConfig.asProperties());

    try {
      recordStore = new RelationalRecordStore(postgresConfig);
    } catch (Exception e) {
      LOGGER.error("Could not initialize PostgreSQL connection pool", e);
      throw new DBException(e);
    }
  }

  @Override
  public void cleanup() {
    if (recordStore != null) {
      recordStore.disconnect();
    }
  }

  @Override
  public Status delete(String table, String key) {
    return recordStore.delete(table, key);
  }

  @Override
  public Status insert(String table, String key, Map<String, ByteIterator> values) {
    return recordStore.insert(table, key, values);
  }

  @Override
  public Status read(String table, String key, Set<String> fields,
                     Map<String, ByteIterator> result) {
    return recordStore.read(table, key, fields, result);
  }

  @Override
  public Status update(String table, String key, Map<String, ByteIterator> values) {
    return recordStore.update(table, key, values);
  }

  @Override
  public Status scan(String table, String startkey, int recordcount,
                     Set<String> fields, Vector<HashMap<String, ByteIterator>> result) {
    return recordStore.scan(table, startkey, recordcount, fields, result);
  }
}
