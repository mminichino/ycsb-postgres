package com.codelry.util.ycsb.postgres;

/**
 * Helpers for building quoted PostgreSQL identifiers and SQL fragments.
 */
final class SqlUtil {

  private SqlUtil() {
  }

  /**
   * Quote an identifier for PostgreSQL. Classic YCSB JDBC uses uppercase column names.
   */
  static String quoteIdent(String name) {
    return '"' + name.toUpperCase().replace("\"", "\"\"") + '"';
  }

  static String columnList(String[] columns) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < columns.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(quoteIdent(columns[i]));
    }
    return sb.toString();
  }

  static String placeholders(int count) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < count; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append('?');
    }
    return sb.toString();
  }
}
