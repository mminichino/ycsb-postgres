package com.codelry.util.ycsb.postgres;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class TestRunClean extends PostgresTest {

  @Test
  void runTestClean() {
    Properties properties = new Properties();
    new RunPrep().testSetup(properties);

    assertDoesNotThrow(() ->
        new RunClean().testClean(properties)
    );
  }
}
