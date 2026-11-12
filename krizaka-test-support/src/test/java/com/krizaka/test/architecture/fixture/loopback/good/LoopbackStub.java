package com.krizaka.test.architecture.fixture.loopback.good;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/** Binds the loopback: what the rule accepts. */
public class LoopbackStub {

  public InetSocketAddress address() {
    return new InetSocketAddress("127.0.0.1", 0);
  }

  public ServerSocket socket() throws IOException {
    return new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
  }
}
