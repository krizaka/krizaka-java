package com.krizaka.test.architecture.fixture.reuse.adapter;

/** Same name as the port but it implements it: an adapter, allowed. */
public class Clock implements com.krizaka.test.architecture.fixture.reuse.lib.Clock {
  @Override
  public long now() {
    return 2;
  }
}
