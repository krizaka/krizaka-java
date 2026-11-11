package com.krizaka.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.krizaka.users.domain.model.User;
import com.krizaka.users.infrastructure.config.JwtProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class TokenServiceImplTest {

  private static final String SECRET = "unit-test-identity-jwt-secret-256bit-key!";

  private final JwtProperties properties =
      new JwtProperties(SECRET, "krizaka-users", Duration.ofMinutes(30));

  private final SecretKeySpec key =
      new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

  private final TokenServiceImpl service =
      new TokenServiceImpl(new NimbusJwtEncoder(new ImmutableSecret<>(key)), properties);

  @Test
  @DisplayName("Issues an HS256 JWT carrying sub, roles, tier, username and email claims")
  void issuesClaimsEnrichedToken() {
    UUID id = UUID.randomUUID();
    User user =
        new User(
            id,
            "testuser",
            "test@sovkora.com",
            true,
            Set.of("ROLE_ADMIN"),
            Map.of(),
            List.of(),
            "premium");

    String token = service.issue(user);

    Jwt jwt =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().decode(token);
    assertThat(jwt.getSubject()).isEqualTo(id.toString());
    assertThat(jwt.getClaimAsString("username")).isEqualTo("testuser");
    assertThat(jwt.getClaimAsString("email")).isEqualTo("test@sovkora.com");
    assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ROLE_ADMIN");
    assertThat(jwt.getClaimAsString("tier")).isEqualTo("premium");
    assertThat(jwt.getClaimAsString("iss")).isEqualTo("krizaka-users");
    assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
  }

  @Test
  @DisplayName("Omits the tier claim when the user has no tier")
  void omitsTierWhenAbsent() {
    User user =
        new User(UUID.randomUUID(), "u", "u@sovkora.com", true, Set.of("ROLE_USER"), Map.of());

    Jwt jwt =
        NimbusJwtDecoder.withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS256)
            .build()
            .decode(service.issue(user));
    assertThat(jwt.getClaims()).doesNotContainKey("tier");
  }

  @Test
  @DisplayName("Rejects a null user")
  void rejectsNullUser() {
    assertThrows(NullPointerException.class, () -> service.issue(null));
  }

  @Test
  @DisplayName("With a signing key configured the token header is ES256 and names the kid")
  void headerIsEs256WithKid() throws Exception {
    var generator = java.security.KeyPairGenerator.getInstance("EC");
    generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
    var pair = generator.generateKeyPair();
    var signing =
        new JwtProperties.Signing(
            pem("PRIVATE KEY", pair.getPrivate().getEncoded()),
            pem("PUBLIC KEY", pair.getPublic().getEncoded()),
            "k7",
            Map.of());
    var props = new JwtProperties(SECRET, "https://id.test", Duration.ofMinutes(30), signing);
    var config = new com.krizaka.users.infrastructure.config.JwtConfig();
    var keys = config.issuerKeys(props);
    var svc = new TokenServiceImpl(config.identityJwtEncoder(props, keys), props);

    String token =
        svc.issue(
            new User(
                UUID.randomUUID(), "u", "u@x.io", true, Set.of("ROLE_USER"), Map.of(), List.of()));

    Jwt jwt = config.identityJwtDecoder(props, keys).decode(token);
    assertThat(jwt.getHeaders()).containsEntry("alg", "ES256").containsEntry("kid", "k7");
  }

  @Test
  @DisplayName(
      "A signing block bound from empty defaults stays on HS256, as in the deployed context")
  void blankSigningBlockStaysHs256() {
    var props =
        new JwtProperties(
            SECRET,
            "krizaka-users",
            Duration.ofMinutes(30),
            new JwtProperties.Signing("", "", "", Map.of()));
    var svc = new TokenServiceImpl(new NimbusJwtEncoder(new ImmutableSecret<>(key)), props);

    String token =
        svc.issue(
            new User(
                UUID.randomUUID(), "u", "u@x.io", true, Set.of("ROLE_USER"), Map.of(), List.of()));

    Jwt jwt =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().decode(token);
    assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
  }

  private static String pem(String type, byte[] der) {
    return "-----BEGIN "
        + type
        + "-----\n"
        + java.util.Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
            .encodeToString(der)
        + "\n-----END "
        + type
        + "-----";
  }
}
