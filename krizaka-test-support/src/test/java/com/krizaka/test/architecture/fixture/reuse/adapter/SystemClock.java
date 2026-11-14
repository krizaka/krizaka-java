package com.krizaka.test.architecture.fixture.reuse.adapter;

/** An adapter of the port, named as it likes. */
public class SystemClock implements com.krizaka.test.architecture.fixture.reuse.lib.Clock {
  @Override
  public long now() {
    return 1;
  }
}
