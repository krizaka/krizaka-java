package com.krizaka.test.net;

import java.util.Properties;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;

/**
 * Binds the web server of every Spring Boot test to the loopback.
 *
 * <p>Registered in {@code META-INF/spring.factories}, so a module that has {@code
 * krizaka-test-support} in test scope gets {@code server.address=127.0.0.1} for each
 * {@code @SpringBootTest(webEnvironment = RANDOM_PORT | DEFINED_PORT)} with nothing to write. The
 * value is a default of the lowest precedence: a test that sets {@code server.address} wins.
 */
public final class LoopbackServerAddressPostProcessor implements EnvironmentPostProcessor {

  static final String SOURCE_NAME = "krizakaLoopbackTestServer";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    if (environment.getPropertySources().contains(SOURCE_NAME)) {
      return;
    }
    Properties defaults = new Properties();
    defaults.setProperty("server.address", LoopbackTestServers.LOOPBACK_ADDRESS);
    environment.getPropertySources().addLast(new PropertiesPropertySource(SOURCE_NAME, defaults));
  }
}
