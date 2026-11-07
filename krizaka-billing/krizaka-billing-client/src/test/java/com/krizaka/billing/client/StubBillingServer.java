package com.krizaka.billing.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A stand-in for the billing service: a JDK HTTP server on a free port that records every request
 * and answers with the reply the test chose. No mocking of the client under test, so the same
 * checks hold whatever HTTP stack the adapters are built on.
 */
public final class StubBillingServer implements AutoCloseable {

  /** What the adapter sent. */
  public record Received(String method, String path, String authorization, String body) {}

  /** What the stub answers; an empty body is a bodiless response. */
  public record Reply(int status, String body) {

    public static Reply json(int status, String body) {
      return new Reply(status, body);
    }

    public static Reply noContent() {
      return new Reply(204, "");
    }
  }

  private final HttpServer server;
  private final List<Received> requests = new CopyOnWriteArrayList<>();
  private volatile Duration delay = Duration.ZERO;
  private volatile Function<Received, Reply> handler = request -> Reply.noContent();

  public StubBillingServer() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    server.createContext("/", this::handle);
    server.start();
  }

  public void replyWith(Reply reply) {
    handler = request -> reply;
  }

  /** Every answer waits this long, to exercise a read timeout. */
  public void delayAnswersBy(Duration delay) {
    this.delay = delay;
  }

  public String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  public List<Received> requests() {
    return requests;
  }

  public Received only() {
    if (requests.size() != 1) {
      throw new AssertionError("expected exactly one request, got " + requests);
    }
    return requests.get(0);
  }

  private void handle(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    Received received =
        new Received(
            exchange.getRequestMethod(),
            exchange.getRequestURI().getRawPath(),
            exchange.getRequestHeaders().getFirst("Authorization"),
            body);
    requests.add(received);
    pause();
    Reply reply = handler.apply(received);
    byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
    if (bytes.length > 0) {
      exchange.getResponseHeaders().add("Content-Type", "application/json");
    }
    exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
    if (bytes.length > 0) {
      exchange.getResponseBody().write(bytes);
    }
    exchange.close();
  }

  private void pause() {
    try {
      // A latch nobody releases: waits out the delay, and wakes early only if interrupted.
      new CountDownLatch(1).await(delay.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
