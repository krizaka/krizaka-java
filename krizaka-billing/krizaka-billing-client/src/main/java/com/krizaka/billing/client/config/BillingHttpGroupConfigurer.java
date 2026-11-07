package com.krizaka.billing.client.config;

import com.krizaka.security.token.ServiceTokenProvider;
import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.HttpServiceGroupConfigurer.Groups;

/**
 * Configures the {@value #GROUP} HTTP service group: base URL, timeouts and the service token. The
 * one place a billing client is built, so {@code /internal/v1} authentication is not a thing the
 * next declared method can forget.
 *
 * <p><b>Where the settings come from.</b> The Spring Boot group properties {@code
 * spring.http.serviceclient.billing.base-url}, {@code .connect-timeout} and {@code .read-timeout}
 * win when set; otherwise the {@code krizaka.billing.*} keys of {@link BillingProperties} apply, so
 * a host configured before the move to declared clients keeps working unchanged. Timeouts default
 * tight: both calls sit on the interactive path, so a slow billing service must degrade through the
 * fail modes rather than hold a user's chat turn open.
 */
final class BillingHttpGroupConfigurer implements RestClientHttpServiceGroupConfigurer {

  /** The group every billing exchange is imported into. */
  static final String GROUP = "billing";

  private static final String GROUP_PROPERTIES = "spring.http.serviceclient." + GROUP + ".";

  private final BillingProperties properties;
  private final Environment environment;

  BillingHttpGroupConfigurer(BillingProperties properties, Environment environment) {
    this.properties = properties;
    this.environment = environment;
  }

  @Override
  public void configureGroups(Groups<RestClient.Builder> groups) {
    groups.filterByName(GROUP).forEachClient((group, builder) -> configure(builder));
  }

  private void configure(RestClient.Builder builder) {
    String groupBaseUrl = environment.getProperty(GROUP_PROPERTIES + "base-url");
    builder.baseUrl(StringUtils.hasText(groupBaseUrl) ? groupBaseUrl : properties.baseUrl());
    Optional<Duration> connect = groupTimeout("connect-timeout");
    Optional<Duration> read = groupTimeout("read-timeout");
    if (connect.isEmpty() || read.isEmpty()) {
      SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
      requestFactory.setConnectTimeout(connect.orElse(properties.connectTimeout()));
      requestFactory.setReadTimeout(read.orElse(properties.readTimeout()));
      builder.requestFactory(requestFactory);
    }
    ServiceTokenProvider tokens =
        new ServiceTokenProvider(properties.serviceSecret(), "krizaka-billing-client");
    builder.requestInitializer(request -> request.getHeaders().setBearerAuth(tokens.token()));
  }

  private Optional<Duration> groupTimeout(String key) {
    return Optional.ofNullable(
        Binder.get(environment).bind(GROUP_PROPERTIES + key, Duration.class).orElse(null));
  }
}
