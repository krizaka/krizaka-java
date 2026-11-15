package com.krizaka.users.infrastructure.adapter.federation;

import com.krizaka.users.domain.model.ExtractedProfile;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * OAuth2 provider verifier for GitHub access tokens.
 *
 * <p>GitHub has no ID token: the access token is checked against <b>our</b> OAuth app first ({@code
 * POST /applications/{client_id}/token}, basic auth with the app's id and secret). A token issued
 * to any other application answers 404 and is refused (ADR-001, finding 3). The answer carries the
 * user; a private e-mail address is read from {@code /user/emails} (the verified primary one), and
 * without one the GitHub noreply address stands in.
 *
 * <p>Per §2.16 [ERR-120], all HTTP calls use Spring {@link RestClient}.
 */
class GithubProviderVerifier extends AbstractProviderVerifier {

  private static final String EMAIL_FIELD = "email";

  private static final String GITHUB_API = "https://api.github.com";
  private static final MediaType GITHUB_JSON =
      MediaType.parseMediaType("application/vnd.github+json");

  private final RestClient restClient;
  private final String clientId;
  private final Supplier<String> clientSecret;

  GithubProviderVerifier(RestClient restClient, String clientId, String clientSecret) {
    this(restClient, clientId, () -> clientSecret);
  }

  /** The secret is asked at each call: the vault is the source, a rotation needs no restart. */
  GithubProviderVerifier(RestClient restClient, String clientId, Supplier<String> clientSecret) {
    this.restClient = restClient;
    this.clientId = Objects.requireNonNull(clientId, "GitHub client id");
    this.clientSecret = Objects.requireNonNull(clientSecret, "GitHub client secret");
    logger.info("GitHub OAuth2 provider verifier initialized");
  }

  @Override
  protected String providerId() {
    return "github";
  }

  @Override
  protected String tokenLabel() {
    return "GitHub access token";
  }

  @Override
  protected ExtractedProfile doVerifyAndExtract(String accessToken) {
    JsonNode check = checkTokenBelongsToUs(accessToken);
    JsonNode user = check.get("user");
    if (user == null || user.isNull()) {
      throw new IllegalArgumentException("GitHub token check returned no user");
    }

    String id = extractField(user, "id");
    String login = extractField(user, "login");
    if (id == null || login == null) {
      throw new IllegalArgumentException("GitHub token check returned an incomplete user");
    }
    String name = textOrNull(user, "name");
    String avatarUrl = textOrNull(user, "avatar_url");
    // The public address (GitHub shows only verified ones), else the verified primary, else the
    // noreply address.
    String email = extractField(user, EMAIL_FIELD);
    if (email == null || email.isBlank()) {
      email = verifiedPrimaryEmail(accessToken);
    }
    if (email == null || email.isBlank()) {
      email = id + "+" + login + "@users.noreply.github.com";
    }

    String displayName = name != null && !name.isBlank() ? name : login;
    return new ExtractedProfile(email, id, displayName, avatarUrl);
  }

  private String basicAuth() {
    String secret = Objects.requireNonNull(clientSecret.get(), "GitHub client secret");
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
  }

  private JsonNode checkTokenBelongsToUs(String accessToken) {
    try {
      JsonNode body =
          restClient
              .post()
              .uri(GITHUB_API + "/applications/{clientId}/token", clientId)
              .header(HttpHeaders.AUTHORIZATION, basicAuth())
              .accept(GITHUB_JSON)
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("access_token", accessToken))
              .retrieve()
              .body(JsonNode.class);
      if (body == null) {
        throw new IllegalArgumentException("GitHub token check returned an empty answer");
      }
      return body;
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)
          || e.getStatusCode().isSameCodeAs(HttpStatus.UNPROCESSABLE_CONTENT)) {
        throw new IllegalArgumentException("GitHub token was not issued to this application", e);
      }
      throw new IllegalArgumentException("GitHub token check failed: " + e.getStatusCode(), e);
    }
  }

  /** The verified primary address (scope {@code user:email}); {@code null} when unreadable. */
  private String verifiedPrimaryEmail(String accessToken) {
    try {
      JsonNode emails =
          restClient
              .get()
              .uri(GITHUB_API + "/user/emails")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
              .accept(GITHUB_JSON)
              .retrieve()
              .body(JsonNode.class);
      if (emails == null || !emails.isArray()) {
        return null;
      }
      String anyVerified = null;
      for (JsonNode e : emails) {
        boolean verified = e.path("verified").asBoolean(false);
        if (!verified) {
          continue;
        }
        if (e.path("primary").asBoolean(false)) {
          return textOrNull(e, EMAIL_FIELD);
        }
        if (anyVerified == null) {
          anyVerified = textOrNull(e, EMAIL_FIELD);
        }
      }
      return anyVerified;
    } catch (RestClientException e) {
      logger.debug(
          "GitHub e-mail addresses unreadable ({}); using the noreply address", e.getMessage(), e);
      return null;
    }
  }
}
