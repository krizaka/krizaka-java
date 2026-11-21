package com.krizaka.users.service.infrastructure.config;

import com.krizaka.security.web.SecurityBaseline;
import com.krizaka.users.domain.model.User;
import com.krizaka.users.domain.ports.inbound.ApiKeyService;
import com.krizaka.users.domain.ports.inbound.IdentityService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless security chain of the identity service: public authentication endpoints, JWT-only
 * sessions everywhere else (the service validates the tokens it issues — same shared-secret decoder
 * every host uses), {@code oz_} API keys resolved in-process.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(InternalProperties.class)
public class SecurityConfig {

  private static final String ADMIN = "ROLE_ADMIN";
  private static final String USER = "ROLE_USER";

  private final IdentityService identityService;
  private final ApiKeyService apiKeyService;
  private final JwtDecoder identityJwtDecoder;
  private final InternalProperties internal;

  public SecurityConfig(
      InternalProperties internal,
      IdentityService identityService,
      ApiKeyService apiKeyService,
      @Qualifier("identityJwtDecoder") JwtDecoder identityJwtDecoder) {
    this.internal = internal;
    this.identityService = identityService;
    this.apiKeyService = apiKeyService;
    this.identityJwtDecoder = identityJwtDecoder;
  }

  /** The authority a machine-to-machine caller presents on /internal/v1 (ADR-035). */
  private static final String SERVICE_AUTHORITY = SecurityBaseline.SERVICE_AUTHORITY;

  @Bean
  public AuthenticationManager authenticationManager() {
    return authentication -> {
      if (authentication instanceof BearerTokenAuthenticationToken bearerToken) {
        String token = bearerToken.getToken();
        User user = resolvePrincipal(token);
        if (!user.enabled()) {
          throw new BadCredentialsException("Invalid or inactive session token");
        }
        var authorities = user.authorities().stream().map(SimpleGrantedAuthority::new).toList();
        return new UsernamePasswordAuthenticationToken(user, token, authorities);
      }
      throw new ProviderNotFoundException("Unsupported authentication token type");
    };
  }

  private User resolvePrincipal(String token) {
    return apiKeyService.authenticate(token).orElseGet(() -> resolveSessionPrincipal(token));
  }

  private User resolveSessionPrincipal(String token) {
    Jwt decoded;
    try {
      decoded = identityJwtDecoder.decode(token);
    } catch (JwtException ex) {
      throw new BadCredentialsException("Invalid or inactive session token", ex);
    }

    // A service token authenticates a *process*, so there is no user row to hydrate. Identity
    // resolves authorities from the user record rather than from the token's claims (unlike
    // billing and studio, which use a roles-claim converter), so without this branch a correctly
    // signed SERVICE token would fail the user lookup and 401 — and the tempting repair would be
    // to reopen /internal/v1. The signature is still verified above: only the principal differs.
    if (SERVICE_AUTHORITY.equals(singleRoleOf(decoded))) {
      return serviceUser(decoded.getSubject());
    }

    try {
      return identityService.getUser(decoded.getSubject());
    } catch (RuntimeException ex) {
      throw new BadCredentialsException("Invalid or inactive session token", ex);
    }
  }

  /** The token's sole role, or {@code null} when it does not carry exactly one. */
  private static String singleRoleOf(Jwt decoded) {
    List<String> roles = decoded.getClaimAsStringList("roles");
    return roles != null && roles.size() == 1 ? roles.getFirst() : null;
  }

  /**
   * The principal a service token authenticates as.
   *
   * <p>Disabled-by-construction as a user: it holds a random id, no e-mail and only the {@code
   * SERVICE} authority, so it can satisfy {@code hasAuthority("SERVICE")} on {@code
   * /internal/v1/**} and nothing else. It is never persisted.
   */
  private static User serviceUser(String subject) {
    return new User(
        UUID.nameUUIDFromBytes(("service:" + subject).getBytes(StandardCharsets.UTF_8)),
        subject,
        subject + "@service.internal",
        true,
        Set.of(SERVICE_AUTHORITY),
        Map.of(),
        List.of());
  }

  @Bean
  @SuppressWarnings("java:S4502") // Justified: CSRF disabled for stateless API.
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
    SecurityBaseline.apply(
            http,
            internal.allowedCallers(),
            authz ->
                authz
                    .requestMatchers(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/verify",
                        "/api/v1/auth/oauth",
                        "/api/v1/auth/forgot",
                        "/api/v1/auth/reset",
                        // The issuer's public keys (ADR-001): verifiers fetch them unauthenticated.
                        "/.well-known/jwks.json")
                    .permitAll()
                    .requestMatchers("/api/v1/profile/**")
                    .hasAnyAuthority(ADMIN, USER)
                    .requestMatchers("/api/v1/api-keys/**")
                    .hasAnyAuthority(ADMIN, USER)
                    // Organizations that own accounts: the service checks the caller's role in
                    // each.
                    .requestMatchers("/api/v1/organizations/**")
                    .hasAnyAuthority(ADMIN, USER)
                    .requestMatchers("/api/v1/credentials/**")
                    .hasAnyAuthority(ADMIN, USER)
                    // The operator console's account administration: administrators only (and
                    // @PreAuthorize).
                    .requestMatchers("/api/v1/admin/**")
                    .hasAuthority(ADMIN))
        .oauth2ResourceServer(
            oauth2 ->
                // The default resolver: the token is read from the Authorization header only, never
                // from the query string (CodeRules.assertNoUriQueryBearerTokens).
                oauth2.opaqueToken(opaque -> opaque.authenticationManager(authenticationManager)));
    return http.build();
  }
}
