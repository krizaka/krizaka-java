package com.krizaka.users.application.service;

import com.krizaka.users.domain.exception.BadCredentialsException;
import com.krizaka.users.domain.exception.ConfigurationException;
import com.krizaka.users.domain.exception.InvalidRequestException;
import com.krizaka.users.domain.exception.RegistrationClosedException;
import com.krizaka.users.domain.exception.UserAlreadyExistsException;
import com.krizaka.users.domain.exception.UserNotFoundException;
import com.krizaka.users.domain.model.User;
import com.krizaka.users.domain.model.UserCredential;
import com.krizaka.users.domain.model.UserRegisteredEvent;
import com.krizaka.users.domain.model.UserSecurityInfo;
import com.krizaka.users.domain.model.VerificationToken;
import com.krizaka.users.domain.ports.inbound.IdentityService;
import com.krizaka.users.domain.ports.inbound.PasswordPolicyService;
import com.krizaka.users.domain.ports.outbound.AuthorityRepositoryPort;
import com.krizaka.users.domain.ports.outbound.CryptographyPort;
import com.krizaka.users.domain.ports.outbound.UserCredentialRepositoryPort;
import com.krizaka.users.domain.ports.outbound.UserEventPublisher;
import com.krizaka.users.domain.ports.outbound.UserInterceptionRepositoryPort;
import com.krizaka.users.domain.ports.outbound.UserRepositoryPort;
import com.krizaka.users.domain.ports.outbound.VerificationTokenRepositoryPort;
import com.krizaka.users.infrastructure.config.IdentityInfrastructureProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StreamUtils;

/**
 * Concrete implementation of the {@link IdentityService} port interface. Decoupled from direct
 * persistence and cryptography frameworks using outbound ports.
 */
@Service
class IdentityServiceImpl implements IdentityService {

  /** Keys the pipeline writes itself; a preference may not spell them (see rejectReservedKeys). */
  private static final Set<String> RESERVED_PREFERENCE_KEYS =
      Set.of("userId", "conversationId", "roles");

  private static final Logger logger = LoggerFactory.getLogger(IdentityServiceImpl.class);
  private static final String ONBOARDING_KEY = "onboarding";
  private static final String INVALID_CREDENTIALS_MSG = "Invalid email or password";
  private static final String ROLE_USER = "ROLE_USER";
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final UserRepositoryPort userRepository;
  private final AuthorityRepositoryPort authorityRepository;
  private final VerificationTokenRepositoryPort verificationTokenRepository;
  private final UserInterceptionRepositoryPort userInterceptionRepository;
  private final UserCredentialRepositoryPort userCredentialRepository;
  private final CryptographyPort cryptography;
  private final PasswordPolicyService passwordPolicy;
  private final IdentityInfrastructureProperties properties;
  private final UserEventPublisher userEventPublisher;
  private final TransactionTemplate transactionTemplate;

  IdentityServiceImpl(
      UserRepositoryPort userRepository,
      AuthorityRepositoryPort authorityRepository,
      VerificationTokenRepositoryPort verificationTokenRepository,
      UserInterceptionRepositoryPort userInterceptionRepository,
      UserCredentialRepositoryPort userCredentialRepository,
      CryptographyPort cryptography,
      PasswordPolicyService passwordPolicy,
      IdentityInfrastructureProperties properties,
      UserEventPublisher userEventPublisher,
      PlatformTransactionManager transactionManager) {
    this.userRepository = userRepository;
    this.authorityRepository = authorityRepository;
    this.verificationTokenRepository = verificationTokenRepository;
    this.userInterceptionRepository = userInterceptionRepository;
    this.userCredentialRepository = userCredentialRepository;
    this.cryptography = cryptography;
    this.passwordPolicy = passwordPolicy;
    this.properties = properties;
    this.userEventPublisher = userEventPublisher;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  @Transactional(readOnly = true)
  public User getUser(String userId) {
    return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
  }

  @Override
  @Transactional
  public User updatePreferences(String userId, Map<String, Object> preferences) {
    return mergeAndSavePreferences(userId, preferences);
  }

  /**
   * Internal helper that merges new preferences into the existing user record and persists the
   * result. Extracted to avoid transactional self-invocation (SonarQube java:S6809).
   */
  private User mergeAndSavePreferences(String userId, Map<String, Object> preferences) {
    rejectReservedKeys(preferences);
    User user =
        userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    Map<String, Object> merged = new HashMap<>(user.preferences());
    merged.putAll(preferences != null ? preferences : Map.of());

    User updatedUser =
        new User(
            user.id(),
            user.username(),
            user.email(),
            user.enabled(),
            user.authorities(),
            merged,
            user.activeInterceptions(),
            user.rateLimitTier());

    return userRepository.save(updatedUser);
  }

  /**
   * Refuses preference keys that spell a namespace the hosting application reads, or a key it owns.
   *
   * <p>Depth, not the fix: an application that builds its runtime context from preferences should
   * place what a user wrote under {@code preference.}, so a stored key cannot impersonate its own
   * configuration. This keeps such a key from being stored at all, so a context builder written
   * tomorrow without that discipline finds no bad data waiting for it. The application declares its
   * namespaces in {@code krizaka.users.preferences.reserved-prefixes}.
   */
  private void rejectReservedKeys(Map<String, Object> preferences) {
    if (preferences == null) {
      return;
    }
    List<String> prefixes = properties.preferences().reservedPrefixes();
    List<String> reserved =
        preferences.keySet().stream()
            .filter(
                key ->
                    key == null
                        || key.startsWith("preference.")
                        || prefixes.stream().anyMatch(key::startsWith)
                        || RESERVED_PREFERENCE_KEYS.contains(key))
            .map(String::valueOf)
            .sorted()
            .toList();
    if (!reserved.isEmpty()) {
      throw new InvalidRequestException("Reserved preference keys: " + reserved);
    }
  }

  @Override
  @Transactional
  public User authenticate(String email, String password) {
    Optional<UserSecurityInfo> securityInfoOpt = userRepository.findSecurityInfoByEmail(email);
    if (securityInfoOpt.isEmpty()) {
      throw new BadCredentialsException(INVALID_CREDENTIALS_MSG);
    }

    UserSecurityInfo securityInfo = securityInfoOpt.get();
    User user = securityInfo.user();

    if (securityInfo.passwordHash() == null) {
      logger.debug("Credential authentication rejected for federated user: email={}", email);
      throw new BadCredentialsException(INVALID_CREDENTIALS_MSG);
    }

    if (!cryptography.matchesPassword(password, securityInfo.passwordHash())) {
      throw new BadCredentialsException(INVALID_CREDENTIALS_MSG);
    }

    rehashIfWeak(email, password, securityInfo.passwordHash());
    recheckBreach(user, password);
    return user;
  }

  /** The right password is looked up in the breach corpus again; never blocks the sign-in. */
  private void recheckBreach(User user, String password) {
    try {
      passwordPolicy.recheckAtSignIn(user.id().toString(), password);
    } catch (RuntimeException e) {
      logger.warn("Breach recheck skipped ({})", e.getClass().getSimpleName(), e);
    }
  }

  /**
   * The password is right: a hash weaker than today's (legacy BCrypt, scrypt) becomes Argon2id now,
   * the only moment the clear password is at hand. Sessions stay valid; a failure never blocks the
   * sign-in.
   */
  private void rehashIfWeak(String email, String password, String storedHash) {
    if (!cryptography.needsRehash(storedHash)) {
      return;
    }
    try {
      userRepository.rehashPasswordByEmail(
          email, cryptography.encodePassword(PasswordPolicyService.normalise(password)));
    } catch (RuntimeException e) {
      logger.warn("Password rehash skipped ({})", e.getClass().getSimpleName(), e);
    }
  }

  @Override
  public User register(String username, String email, String password, String language) {
    if (!properties.registration().enabled()) {
      logger.warn("Registration refused (registration closed)");
      throw new RegistrationClosedException();
    }
    String passwordHash = cryptography.encodePassword(passwordPolicy.accept(password));
    String userId = UUID.randomUUID().toString();

    Map<String, Object> preferences = new HashMap<>();
    if (language != null) {
      preferences.put("language", language);
    }

    User validatedUser =
        new User(UUID.fromString(userId), username, email, false, Set.of(), preferences);

    boolean verificationEnabled = properties.emailVerification().enabled();
    boolean enabled = !verificationEnabled;

    String[] plaintextTokenHolder = new String[1];

    User user;
    try {
      user =
          transactionTemplate.execute(
              status -> {
                User newUser =
                    new User(
                        validatedUser.id(),
                        validatedUser.username(),
                        validatedUser.email(),
                        enabled,
                        validatedUser.authorities(),
                        validatedUser.preferences(),
                        validatedUser.activeInterceptions(),
                        validatedUser.rateLimitTier());

                User createdUser = userRepository.create(newUser, passwordHash);

                authorityRepository.saveAuthority(userId, ROLE_USER);

                if (verificationEnabled) {
                  byte[] randomBytes = new byte[32];
                  SECURE_RANDOM.nextBytes(randomBytes);
                  String plaintextToken =
                      Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
                  plaintextTokenHolder[0] = plaintextToken;
                  String tokenHash = cryptography.hashToken(plaintextToken);
                  Instant expiry = Instant.now().plus(24, ChronoUnit.HOURS);

                  VerificationToken token =
                      new VerificationToken(
                          UUID.randomUUID().toString(),
                          userId,
                          "EMAIL_VERIFICATION",
                          tokenHash,
                          expiry,
                          false);
                  verificationTokenRepository.save(token);
                  logger.info("Generated verification token for registered user: {}", email);
                }

                boolean interceptionsEnabled = properties.interceptions().enabled();
                List<String> activeInterceptions = List.of();
                if (interceptionsEnabled) {
                  userInterceptionRepository.triggerInterception(
                      UUID.fromString(userId), ONBOARDING_KEY, ONBOARDING_KEY);
                  activeInterceptions = List.of(ONBOARDING_KEY);
                }

                Set<String> authorities = Set.of(ROLE_USER);
                return new User(
                    createdUser.id(),
                    createdUser.username(),
                    createdUser.email(),
                    createdUser.enabled(),
                    authorities,
                    createdUser.preferences(),
                    activeInterceptions,
                    createdUser.rateLimitTier());
              });
    } catch (DataIntegrityViolationException ex) {
      logger.warn(
          "Registration rejected — Concurrency safety trigger, email already registered: {}",
          email,
          ex);
      throw new UserAlreadyExistsException("An account with this email already exists.");
    }

    if (user == null) {
      throw new IllegalStateException("TransactionTemplate returned null user");
    }
    userEventPublisher.publish(new UserRegisteredEvent(user, plaintextTokenHolder[0]));
    logger.debug(
        "New user registered: username={}, id={}, enabled={}",
        validatedUser.username(),
        userId,
        enabled);
    return user;
  }

  @Override
  @Transactional
  public void resolveInterception(
      UUID userId, String interceptionType, String schemaId, Map<String, Object> responses) {
    mergeAndSavePreferences(userId.toString(), responses);
    userInterceptionRepository.deleteInterception(userId, interceptionType);
    logger.info(
        "Resolved interception '{}' (schema: '{}') for user {}",
        interceptionType,
        schemaId,
        userId);
  }

  @Override
  @Transactional
  public boolean verifyToken(String token) {
    String tokenHash = cryptography.hashToken(token);
    Optional<VerificationToken> tokenOpt =
        verificationTokenRepository.findByTokenHashAndUsedFalse(tokenHash);
    if (tokenOpt.isEmpty()) {
      logger.warn("Verification token not found or already used");
      return false;
    }

    VerificationToken row = tokenOpt.get();
    if (row.expiryTimestamp() == null || row.expiryTimestamp().isBefore(Instant.now())) {
      logger.warn("Verification token has expired");
      return false;
    }

    verificationTokenRepository.markAsUsed(row.id());

    Optional<User> userOpt = userRepository.findById(row.userId());
    if (userOpt.isPresent()) {
      User user = userOpt.get();
      User updatedUser =
          new User(
              user.id(),
              user.username(),
              user.email(),
              true,
              user.authorities(),
              user.preferences(),
              user.activeInterceptions(),
              user.rateLimitTier());
      userRepository.save(updatedUser);
    }

    logger.info("Successfully verified token and enabled user {}", row.userId());
    return true;
  }

  @Override
  public boolean requiresEmailVerification() {
    return properties.emailVerification().enabled();
  }

  @Override
  public String loadInterceptionSchema(String schemaId) {
    if (properties.interceptions().schemas().isEmpty()) {
      throw new ConfigurationException("Interception schemas not configured; schemaId=" + schemaId);
    }
    Resource resource = properties.interceptions().schemas().get(schemaId);
    if (resource == null || !resource.exists()) {
      throw new ConfigurationException(
          "Configured interception schema file does not exist: " + resource);
    }
    try (InputStream is = resource.getInputStream()) {
      return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
    } catch (IOException e) {
      logger.error("Failed to read schema file: {}", resource, e);
      throw new InvalidRequestException("Failed to read interception schema: " + schemaId);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserCredential> getUserCredentials(String userId) {
    return userCredentialRepository.findByUserId(userId);
  }

  @Override
  @Transactional
  public void createUserCredential(String userId, String providerName, String apiKey) {
    userCredentialRepository.create(userId, providerName, apiKey);
  }

  @Override
  @Transactional
  public void rotateUserCredential(String userId, String providerName, String apiKey) {
    userCredentialRepository.rotate(userId, providerName, apiKey);
  }

  @Override
  @Transactional
  public void deleteUserCredential(String userId, String providerName) {
    userCredentialRepository.delete(userId, providerName);
  }

  @Override
  // Not read-only: an old AES-CBC row is re-sealed (UPDATE) on its first read (krizaka-java#218).
  @Transactional
  public Optional<String> getDecryptedApiKey(String userId, String providerName) {
    return userCredentialRepository.findApiKeyByUserIdAndProviderName(userId, providerName);
  }
}
