package com.krizaka.users.infrastructure.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Encoder/decoder of the identity tokens (ADR-001). Session tokens are ES256, signed by this issuer
 * only and published at {@code /.well-known/jwks.json}; the decoder verifies them against the
 * issuer's own keys by {@code kid}. HS256 stays signed and verified when no issuer key is
 * configured, until the dated cutover; {@code SERVICE} tokens are per-caller ES256 (slice 3).
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

  static SecretKeySpec hmacKey(JwtProperties properties) {
    return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Bean
  public JwtSecretCheck jwtSecretCheck(JwtProperties properties, Environment environment) {
    properties.requireRealSecretUnlessLocal(environment.acceptsProfiles(Profiles.of("local")));
    return new JwtSecretCheck();
  }

  /** Marker: the session secret passed {@code requireRealSecretUnlessLocal}. */
  public static final class JwtSecretCheck {
    JwtSecretCheck() {}
  }

  @Bean
  public IssuerKeys issuerKeys(JwtProperties properties) {
    return IssuerKeys.from(properties.signing());
  }

  @Bean
  public JwtEncoder identityJwtEncoder(JwtProperties properties, IssuerKeys keys) {
    if (keys.enabled()) {
      return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(keys.signing())));
    }
    if (properties.secret() == null) {
      throw new IllegalStateException("krizaka.users.jwt: no signing key and no secret");
    }
    return new NimbusJwtEncoder(new ImmutableSecret<>(hmacKey(properties)));
  }

  @Bean
  public JwtDecoder identityJwtDecoder(JwtProperties properties, IssuerKeys keys) {
    return new IdentityTokenDecoder(keys, properties, Clock.systemUTC());
  }

  /** As {@link #identityJwtDecoder}, with an explicit clock (tests of the cutover). */
  JwtDecoder identityJwtDecoder(JwtProperties properties, IssuerKeys keys, Clock clock) {
    return new IdentityTokenDecoder(keys, properties, clock);
  }
}
