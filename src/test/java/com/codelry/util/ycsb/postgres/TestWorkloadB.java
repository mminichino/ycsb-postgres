package com.codelry.util.ycsb.postgres;

import com.codelry.util.ycsb.RunBenchmark;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class TestWorkloadB extends PostgresTest {

  @Test
  void runWorkloadB() {
    assertDoesNotThrow(() ->
        RunBenchmark.main(new String[] { "-w", "b" })
    );
  }
}
