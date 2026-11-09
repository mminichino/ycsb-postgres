package com.codelry.util.ycsb.postgres;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class TestRunPrep extends PostgresTest {

  @Test
  void runCreateTable() {
    Properties properties = new Properties();

    assertDoesNotThrow(() ->
        new RunPrep().testSetup(properties)
    );
  }
}
