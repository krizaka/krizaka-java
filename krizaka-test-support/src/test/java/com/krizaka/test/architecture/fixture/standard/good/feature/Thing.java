package com.krizaka.test.architecture.fixture.standard.good.feature;

/** A well-formed class. */
public final class Thing {

  private final String name;

  public Thing(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }
}
