package com.krizaka.users.infrastructure.config;

import com.krizaka.security.token.ServiceTokenVerifier;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.security.interfaces.ECPublicKey;
import java.text.ParseException;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Routes a token to the one verifier its header allows (ADR-001). The algorithm is never taken from
 * the token alone: an ES256 token whose {@code kid} the issuer published is a session (issuer
 * keys); any other ES256 token is a {@code SERVICE} token, checked against its caller's own public
 * key and refused unless it carries exactly the {@code SERVICE} role; HS256 verifies only while the
 * shared secret is configured and before the dated cutover; every other algorithm, {@code none}
 * included, is refused.
 */
final class IdentityTokenDecoder implements JwtDecoder {

  private final IssuerKeys keys;
  private final JwtProperties properties;
  private final Clock clock;
  private final JwtDecoder session;
  private final JwtDecoder service;
  private final JwtDecoder legacy;

  IdentityTokenDecoder(IssuerKeys keys, JwtProperties properties, Clock clock) {
    this.keys = keys;
    this.properties = properties;
    this.clock = clock;
    var processor = new DefaultJWTProcessor<SecurityContext>();
    processor.setJWSKeySelector(
        new JWSVerificationKeySelector<>(
            JWSAlgorithm.ES256, new ImmutableJWKSet<SecurityContext>(keys.publicSet())));
    this.session = new NimbusJwtDecoder(processor);
    this.service = new ServiceTokenVerifier(callerKeys(properties.callers()), clock);
    if (properties.secret() == null) {
      this.legacy = null;
    } else {
      var legacyDecoder =
          NimbusJwtDecoder.withSecretKey(JwtConfig.hmacKey(properties))
              .macAlgorithm(MacAlgorithm.HS256)
              .build();
      var ts = new JwtTimestampValidator();
      ts.setClock(clock);
      legacyDecoder.setJwtValidator(ts);
      this.legacy = legacyDecoder;
    }
  }

  private static Map<String, ECPublicKey> callerKeys(Map<String, String> pems) {
    Map<String, ECPublicKey> out = new HashMap<>();
    pems.forEach(
        (name, pem) -> {
          try {
            out.put(name, IssuerKeys.publicKey(pem));
          } catch (Exception e) {
            throw new IllegalArgumentException(
                "krizaka.users.jwt.callers." + name + " is not a valid P-256 public key PEM", e);
          }
        });
    return out;
  }

  @Override
  public Jwt decode(String token) {
    JWSHeader header;
    try {
      header = (JWSHeader) JWTParser.parse(token).getHeader();
    } catch (ParseException | ClassCastException e) {
      throw new BadJwtException("Malformed token", e);
    }
    if (JWSAlgorithm.ES256.equals(header.getAlgorithm())) {
      return decodeEs256(token, header.getKeyID());
    }
    if (JWSAlgorithm.HS256.equals(header.getAlgorithm()) && hs256Open()) {
      return legacy.decode(token);
    }
    throw new BadJwtException("Unsupported token algorithm");
  }

  private boolean hs256Open() {
    return legacy != null && properties.hs256Accepted(clock.instant());
  }

  private Jwt decodeEs256(String token, String kid) {
    if (kid != null && keys.byKeyId(kid).isPresent()) {
      return session.decode(token);
    }
    return requireServiceRole(service.decode(token));
  }

  private static Jwt requireServiceRole(Jwt jwt) {
    List<String> roles = jwt.getClaimAsStringList("roles");
    if (roles == null || roles.size() != 1 || !"SERVICE".equals(roles.getFirst())) {
      throw new BadJwtException("A caller key signs SERVICE tokens only");
    }
    return jwt;
  }
}
