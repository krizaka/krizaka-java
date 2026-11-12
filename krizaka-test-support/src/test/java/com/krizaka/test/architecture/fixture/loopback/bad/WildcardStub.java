package com.krizaka.test.architecture.fixture.loopback.bad;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/** Binds every interface: what the rule refuses. */
public class WildcardStub {

  public InetSocketAddress anyAddress() {
    return new InetSocketAddress(0);
  }

  public ServerSocket anySocket() throws IOException {
    return new ServerSocket(0);
  }
}
