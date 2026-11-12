package com.krizaka.test.net;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/**
 * Test servers that listen on the loopback only, on a free port.
 *
 * <p>A server bound to every interface is reachable from the network while the tests run, and macOS
 * asks the developer to accept incoming connections for every new build. Every stub HTTP server and
 * probe socket of a test is created here (or names {@link InetAddress#getLoopbackAddress()}
 * itself); {@code CodeRules.testServersBindToLoopbackRule} fails a test class that binds the
 * wildcard address.
 */
public final class LoopbackTestServers {

  /** The value of {@code server.address} the library sets for every Spring Boot test. */
  public static final String LOOPBACK_ADDRESS = "127.0.0.1";

  private LoopbackTestServers() {}

  /**
   * A socket address on the loopback.
   *
   * @param port the port, {@code 0} for a free one
   * @return the loopback address and port
   */
  public static InetSocketAddress address(int port) {
    return new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
  }

  /**
   * An HTTP server bound to the loopback on a free port, not started.
   *
   * @return the server
   * @throws IOException when the port cannot be bound
   */
  public static HttpServer httpServer() throws IOException {
    return HttpServer.create(address(0), 0);
  }

  /**
   * A server socket bound to the loopback on a free port.
   *
   * @return the socket, to close
   * @throws IOException when the port cannot be bound
   */
  public static ServerSocket serverSocket() throws IOException {
    return new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
  }
}
