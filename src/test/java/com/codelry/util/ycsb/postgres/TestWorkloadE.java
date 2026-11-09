package com.codelry.util.ycsb.postgres;

import com.codelry.util.ycsb.RunBenchmark;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class TestWorkloadE extends PostgresTest {

  @Test
  void runWorkloadE() {
    assertDoesNotThrow(() ->
        RunBenchmark.main(new String[] { "-w", "e" })
    );
  }
}
