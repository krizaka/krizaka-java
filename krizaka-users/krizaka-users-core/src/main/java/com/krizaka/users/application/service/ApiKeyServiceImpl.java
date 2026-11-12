package com.krizaka.users.application.service;

import com.krizaka.users.domain.exception.ApiKeyLimitExceededException;
import com.krizaka.users.domain.exception.InvalidRequestException;
import com.krizaka.users.domain.model.ApiKey;
import com.krizaka.users.domain.model.AuthenticatedApiKey;
import com.krizaka.users.domain.model.GeneratedApiKey;
import com.krizaka.users.domain.model.User;
import com.krizaka.users.domain.ports.inbound.ApiKeyService;
import com.krizaka.users.domain.ports.outbound.ApiKeyRepositoryPort;
import com.krizaka.users.domain.ports.outbound.CryptographyPort;
import com.krizaka.users.domain.ports.outbound.UserRepositoryPort;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concrete implementation of the {@link ApiKeyService} port. Generates cryptographically strong
 * secrets, persists only their SHA-256 hash, and resolves presented bearer tokens back to their
 * owning user.
 */
@Service
class ApiKeyServiceImpl implements ApiKeyService {

  private static final String USER_ID_REQUIRED = "userId must not be null";

  private static final Logger logger = LoggerFactory.getLogger(ApiKeyServiceImpl.class);
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  /** Discriminator distinguishing an API-key bearer token from an opaque session token. */
  static final String TOKEN_PREFIX = "oz_";

  private static final String SECRET_PREFIX = TOKEN_PREFIX + "live_";
  private static final int SECRET_BYTES = 32;
  private static final int DISPLAY_PREFIX_LENGTH = 12;
  private static final int MAX_NAME_LENGTH = 100;
  private static final int MAX_SCOPES = 8;
  private static final java.util.regex.Pattern SCOPE_PATTERN =
      java.util.regex.Pattern.compile("^[a-z][a-z0-9-]{0,23}:[a-z][a-z0-9-]{0,23}$");

  private final ApiKeyRepositoryPort apiKeyRepository;
  private final UserRepositoryPort userRepository;
  private final CryptographyPort cryptography;
  private final ServiceAccountKeyRevocation serviceAccountKeys;

  ApiKeyServiceImpl(
      ApiKeyRepositoryPort apiKeyRepository,
      UserRepositoryPort userRepository,
      CryptographyPort cryptography,
      ServiceAccountKeyRevocation serviceAccountKeys) {
    this.apiKeyRepository =
        Objects.requireNonNull(apiKeyRepository, "apiKeyRepository is required");
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository is required");
    this.cryptography = Objects.requireNonNull(cryptography, "cryptography is required");
    this.serviceAccountKeys =
        Objects.requireNonNull(serviceAccountKeys, "serviceAccountKeys is required");
  }

  @Override
  public List<ApiKey> list(String userId) {
    Objects.requireNonNull(userId, USER_ID_REQUIRED);
    return apiKeyRepository.findByUserId(userId);
  }

  @Override
  public GeneratedApiKey create(String userId, String name) {
    return create(userId, name, Set.of());
  }

  @Override
  public GeneratedApiKey create(String userId, String name, Set<String> scopes) {
    Objects.requireNonNull(userId, USER_ID_REQUIRED);
    Set<String> cleanScopes = normaliseScopes(scopes);
    String cleanName = normaliseName(name);

    if (apiKeyRepository.countByUserId(userId) >= MAX_KEYS_PER_USER) {
      throw new ApiKeyLimitExceededException(
          "API key limit reached (" + MAX_KEYS_PER_USER + " maximum). Revoke a key first.");
    }

    byte[] randomBytes = new byte[SECRET_BYTES];
    SECURE_RANDOM.nextBytes(randomBytes);
    String plaintext =
        SECRET_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    String keyHash = cryptography.hashToken(plaintext);
    String keyPrefix = plaintext.substring(0, DISPLAY_PREFIX_LENGTH);
    String id = UUID.randomUUID().toString();

    apiKeyRepository.save(id, userId, cleanName, keyPrefix, keyHash, cleanScopes);
    logger.info(
        "Generated API key '{}' ({}) for user {} scopes={}",
        cleanName,
        keyPrefix,
        userId,
        cleanScopes);
    return new GeneratedApiKey(id, cleanName, keyPrefix, plaintext, cleanScopes);
  }

  @Override
  @Transactional
  public boolean revoke(String userId, String keyId) {
    Objects.requireNonNull(userId, USER_ID_REQUIRED);
    Objects.requireNonNull(keyId, "keyId must not be null");
    Optional<User> serviceAccount =
        userRepository.findById(userId).filter(ServiceAccountKeyRevocation::isServiceAccount);
    if (serviceAccount.isPresent()) {
      // A product's key: audited, and never the last one (rotation creates the new key first).
      return serviceAccountKeys.revoke(
          serviceAccount.get(), keyId, userId, serviceAccount.get().username());
    }
    boolean revoked = apiKeyRepository.deleteByIdAndUserId(keyId, userId) > 0;
    if (revoked) {
      logger.info("Revoked API key {} for user {}", keyId, userId);
    }
    return revoked;
  }

  @Override
  public Optional<AuthenticatedApiKey> authenticateKey(String presentedKey) {
    if (presentedKey == null || !presentedKey.startsWith(TOKEN_PREFIX)) {
      return Optional.empty();
    }
    // Hashing is deliberately performed outside any transaction (ERR-109).
    String keyHash = cryptography.hashToken(presentedKey);
    Optional<ApiKey> match = apiKeyRepository.findByKeyHash(keyHash);
    if (match.isEmpty()) {
      return Optional.empty();
    }
    ApiKey apiKey = match.get();
    Optional<User> user = userRepository.findById(apiKey.userId()).filter(User::enabled);
    user.ifPresent(u -> apiKeyRepository.touchLastUsed(apiKey.id(), Instant.now()));
    return user.map(u -> new AuthenticatedApiKey(u, apiKey.scopes()));
  }

  private static Set<String> normaliseScopes(Set<String> scopes) {
    if (scopes == null || scopes.isEmpty()) {
      return Set.of();
    }
    if (scopes.size() > MAX_SCOPES) {
      throw new InvalidRequestException("An API key carries at most " + MAX_SCOPES + " scopes");
    }
    Set<String> clean = new java.util.TreeSet<>();
    for (String scope : scopes) {
      String s = scope == null ? "" : scope.strip();
      if (!SCOPE_PATTERN.matcher(s).matches()) {
        throw new InvalidRequestException("Invalid API key scope (expected area:action): " + scope);
      }
      clean.add(s);
    }
    return Set.copyOf(clean);
  }

  private static String normaliseName(String name) {
    String trimmed = name == null ? "" : name.strip();
    if (trimmed.isBlank()) {
      throw new InvalidRequestException("API key name must not be blank");
    }
    return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
  }
}
