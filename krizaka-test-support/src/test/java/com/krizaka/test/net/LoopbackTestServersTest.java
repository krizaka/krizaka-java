package com.krizaka.test.net;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;

class LoopbackTestServersTest {

  @Test
  void httpServerListensOnTheLoopbackOnly() throws IOException {
    HttpServer server = LoopbackTestServers.httpServer();
    try {
      server.start();
      assertThat(server.getAddress().getAddress().isLoopbackAddress()).isTrue();
      assertThat(server.getAddress().getPort()).isPositive();
    } finally {
      server.stop(0);
    }
  }

  @Test
  void serverSocketListensOnTheLoopbackOnly() throws IOException {
    try (ServerSocket socket = LoopbackTestServers.serverSocket()) {
      assertThat(socket.getInetAddress().isLoopbackAddress()).isTrue();
    }
  }

  @Test
  void aSpringBootTestServerDefaultsToTheLoopback() {
    StandardEnvironment environment = new StandardEnvironment();

    new LoopbackServerAddressPostProcessor()
        .postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
  }

  @Test
  void aTestThatSetsTheAddressItselfWins() {
    StandardEnvironment environment = new StandardEnvironment();
    Properties own = new Properties();
    own.setProperty("server.address", "::1");
    environment.getPropertySources().addFirst(new PropertiesPropertySource("test", own));

    new LoopbackServerAddressPostProcessor()
        .postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty("server.address")).isEqualTo("::1");
  }

  @Test
  void springBootLoadsThePostProcessorFromTheLibrary() throws IOException {
    StringBuilder registered = new StringBuilder();
    for (URL factories :
        Collections.list(getClass().getClassLoader().getResources("META-INF/spring.factories"))) {
      try (InputStream in = factories.openStream()) {
        registered.append(new String(in.readAllBytes(), StandardCharsets.UTF_8));
      }
    }

    assertThat(registered.toString())
        .contains(EnvironmentPostProcessor.class.getName())
        .contains(LoopbackServerAddressPostProcessor.class.getName());
  }
}
