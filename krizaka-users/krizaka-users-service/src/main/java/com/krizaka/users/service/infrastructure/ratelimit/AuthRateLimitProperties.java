package com.krizaka.users.service.infrastructure.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limits of the public authentication endpoints (krizaka-users#17). Every figure is overridable
 * through {@code krizaka.users-service.rate-limit.*}; the defaults are the production stance.
 *
 * @param enabled master switch (only ever turned off in a load test)
 * @param trustedProxies addresses / CIDRs whose {@code X-Forwarded-For} is believed; the direct
 *     peer is the edge, so the private ranges are trusted by default
 * @param clientIpHeader optional header carrying the real client address when a CDN fronts the edge
 *     (e.g. {@code CF-Connecting-IP}, {@code DO-Connecting-IP}); honoured only from a trusted peer
 * @param login per-IP budget of {@code /login}
 * @param oauth per-IP budget of {@code /oauth}
 * @param register per-IP budget of {@code /register}
 * @param forgot per-IP budget of {@code /forgot}
 * @param reset per-IP budget of {@code /reset}
 * @param verify per-IP budget of {@code /verify}
 * @param emailMessages per-address budget of the endpoints that send an e-mail
 */
@ConfigurationProperties(prefix = "krizaka.users-service.rate-limit")
public record AuthRateLimitProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue({
          "127.0.0.0/8",
          "::1",
          "10.0.0.0/8", // NOSONAR java:S1313 RFC 1918 private range, trusted on purpose
          "172.16.0.0/12", // NOSONAR java:S1313 RFC 1918 private range, trusted on purpose
          "192.168.0.0/16", // NOSONAR java:S1313 RFC 1918 private range, trusted on purpose
          "fc00::/7"
        })
        List<String> trustedProxies,
    @DefaultValue("") String clientIpHeader,
    @DefaultValue Budget login,
    @DefaultValue Budget oauth,
    @DefaultValue Budget register,
    @DefaultValue Budget forgot,
    @DefaultValue Budget reset,
    @DefaultValue Budget verify,
    @DefaultValue Budget emailMessages) {

  /**
   * A nested value object cannot carry per-field defaults through {@code @DefaultValue} arrays
   * (Boot then drops the whole properties bean silently), so an unset budget is filled here.
   */
  public AuthRateLimitProperties {
    login = orDefault(login, 20, Duration.ofMinutes(1));
    oauth = orDefault(oauth, 30, Duration.ofMinutes(1));
    register = orDefault(register, 5, Duration.ofHours(1));
    forgot = orDefault(forgot, 5, Duration.ofHours(1));
    reset = orDefault(reset, 10, Duration.ofMinutes(15));
    verify = orDefault(verify, 20, Duration.ofMinutes(1));
    emailMessages = orDefault(emailMessages, 3, Duration.ofHours(1));
  }

  private static Budget orDefault(Budget given, int requests, Duration window) {
    int r = given != null && given.requests() > 0 ? given.requests() : requests;
    Duration w = given != null && given.window() != null ? given.window() : window;
    return new Budget(r, w);
  }

  /**
   * A budget: {@code requests} per {@code window}.
   *
   * @param requests tokens in the bucket
   * @param window time to refill the whole bucket
   */
  public record Budget(int requests, Duration window) {}
}
