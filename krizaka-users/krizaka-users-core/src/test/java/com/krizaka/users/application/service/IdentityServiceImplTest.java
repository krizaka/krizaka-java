package com.krizaka.users.application.service;

import static com.krizaka.users.TestConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.krizaka.users.domain.exception.BadCredentialsException;
import com.krizaka.users.domain.exception.ConfigurationException;
import com.krizaka.users.domain.exception.InvalidRequestException;
import com.krizaka.users.domain.exception.UserNotFoundException;
import com.krizaka.users.domain.model.Persona;
import com.krizaka.users.domain.model.User;
import com.krizaka.users.domain.model.UserCredential;
import com.krizaka.users.domain.model.UserSecurityInfo;
import com.krizaka.users.domain.model.VerificationToken;
import com.krizaka.users.domain.ports.outbound.AuthorityRepositoryPort;
import com.krizaka.users.domain.ports.outbound.CryptographyPort;
import com.krizaka.users.domain.ports.outbound.UserCredentialRepositoryPort;
import com.krizaka.users.domain.ports.outbound.UserEventPublisher;
import com.krizaka.users.domain.ports.outbound.UserInterceptionRepositoryPort;
import com.krizaka.users.domain.ports.outbound.UserRepositoryPort;
import com.krizaka.users.domain.ports.outbound.VerificationTokenRepositoryPort;
import com.krizaka.users.infrastructure.config.IdentityInfrastructureProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

/** Unit tests for {@link IdentityServiceImpl}. */
@ExtendWith(MockitoExtension.class)
class IdentityServiceImplTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  @Mock private UserRepositoryPort userRepository;
  @Mock private AuthorityRepositoryPort authorityRepository;
  @Mock private VerificationTokenRepositoryPort verificationTokenRepository;
  @Mock private UserInterceptionRepositoryPort userInterceptionRepository;
  @Mock private UserCredentialRepositoryPort userCredentialRepository;
  @Mock private CryptographyPort cryptography;
  @Mock private IdentityInfrastructureProperties properties;
  @Mock private UserEventPublisher userEventPublisher;
  @Mock private PlatformTransactionManager transactionManager;

  private IdentityServiceImpl service;

  private static final UUID USER_ID = Persona.freeUser().id();
  private static final String EMAIL = Persona.freeUser().email();
  private static final String USERNAME = Persona.freeUser().username();

  private User stubUser() {
    return Persona.freeUser();
  }

  @Mock private com.krizaka.users.domain.ports.inbound.PasswordPolicyService passwordPolicy;

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.lenient()
        .when(passwordPolicy.accept(org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(inv -> inv.getArgument(0));
    org.mockito.Mockito.lenient()
        .when(properties.preferences())
        .thenReturn(new IdentityInfrastructureProperties.Preferences(java.util.List.of()));
    org.mockito.Mockito.lenient()
        .when(properties.registration())
        .thenReturn(new IdentityInfrastructureProperties.Registration(true));
    service =
        new IdentityServiceImpl(
            userRepository,
            authorityRepository,
            verificationTokenRepository,
            userInterceptionRepository,
            userCredentialRepository,
            cryptography,
            passwordPolicy,
            properties,
            userEventPublisher,
            transactionManager);
  }

  // --- getUser ---

  @Test
  void getUser_existingUser_returnsUser() {
    User user = stubUser();
    when(userRepository.findById(USER_ID.toString())).thenReturn(Optional.of(user));

    User result = service.getUser(USER_ID.toString());

    assertEquals(user, result);
  }

  @Test
  void getUser_notFound_throwsUserNotFoundException() {
    when(userRepository.findById(anyString())).thenReturn(Optional.empty());

    assertThrows(UserNotFoundException.class, () -> service.getUser("nonexistent"));
  }

  // --- authenticate ---

  @Test
  void authenticate_validCredentials_returnsUser() {
    User user = stubUser();
    UserSecurityInfo info = new UserSecurityInfo(user, HASHED_PASSWORD);
    when(userRepository.findSecurityInfoByEmail(EMAIL)).thenReturn(Optional.of(info));
    when(cryptography.matchesPassword(TEST_PASSWORD, HASHED_PASSWORD)).thenReturn(true);

    User result = service.authenticate(EMAIL, TEST_PASSWORD);

    assertEquals(user, result);
  }

  @Test
  void authenticate_weakLegacyHash_isRehashedWithoutInvalidatingSessions() {
    when(userRepository.findSecurityInfoByEmail(EMAIL))
        .thenReturn(Optional.of(new UserSecurityInfo(stubUser(), HASHED_PASSWORD)));
    when(cryptography.matchesPassword(TEST_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
    when(cryptography.needsRehash(HASHED_PASSWORD)).thenReturn(true);
    when(cryptography.encodePassword(TEST_PASSWORD)).thenReturn("{argon2}new");

    service.authenticate(EMAIL, TEST_PASSWORD);

    verify(userRepository).rehashPasswordByEmail(EMAIL, "{argon2}new");
    verify(userRepository, never()).updatePasswordHashByEmail(any(), any());
  }

  @Test
  void authenticate_recheckBreachAtSignIn_neverBlocksTheSignIn() {
    User user = stubUser();
    when(userRepository.findSecurityInfoByEmail(EMAIL))
        .thenReturn(Optional.of(new UserSecurityInfo(user, HASHED_PASSWORD)));
    when(cryptography.matchesPassword(TEST_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
    when(passwordPolicy.recheckAtSignIn(user.id().toString(), TEST_PASSWORD))
        .thenThrow(new IllegalStateException("breach service down"));

    assertEquals(user, service.authenticate(EMAIL, TEST_PASSWORD));

    verify(passwordPolicy).recheckAtSignIn(user.id().toString(), TEST_PASSWORD);
  }

  @Test
  void authenticate_currentHash_isLeftAlone() {
    when(userRepository.findSecurityInfoByEmail(EMAIL))
        .thenReturn(Optional.of(new UserSecurityInfo(stubUser(), HASHED_PASSWORD)));
    when(cryptography.matchesPassword(TEST_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
    when(cryptography.needsRehash(HASHED_PASSWORD)).thenReturn(false);

    service.authenticate(EMAIL, TEST_PASSWORD);

    verify(userRepository, never()).rehashPasswordByEmail(any(), any());
  }

  @Test
  void authenticate_unknownEmail_throwsBadCredentials() {
    when(userRepository.findSecurityInfoByEmail(anyString())).thenReturn(Optional.empty());

    assertThrows(BadCredentialsException.class, () -> service.authenticate("unknown@x.io", "pw"));
  }

  @Test
  void authenticate_federatedUser_nullPasswordHash_throwsBadCredentials() {
    User user = stubUser();
    UserSecurityInfo info = new UserSecurityInfo(user, null);
    when(userRepository.findSecurityInfoByEmail(EMAIL)).thenReturn(Optional.of(info));

    assertThrows(BadCredentialsException.class, () -> service.authenticate(EMAIL, TEST_PASSWORD));
  }

  @Test
  void authenticate_wrongPassword_throwsBadCredentials() {
    User user = stubUser();
    UserSecurityInfo info = new UserSecurityInfo(user, HASHED_PASSWORD);
    when(userRepository.findSecurityInfoByEmail(EMAIL)).thenReturn(Optional.of(info));
    when(cryptography.matchesPassword("wrong", HASHED_PASSWORD)).thenReturn(false);

    assertThrows(BadCredentialsException.class, () -> service.authenticate(EMAIL, "wrong"));
  }

  // --- updatePreferences ---

  @Test
  void updatePreferences_mergesAndSaves() {
    User user = stubUser();
    when(userRepository.findById(USER_ID.toString())).thenReturn(Optional.of(user));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    User result = service.updatePreferences(USER_ID.toString(), Map.of("theme", "dark"));

    assertEquals("dark", result.preferences().get("theme"));
  }

  @Test
  void updatePreferences_refusesKeysThePlatformReads() {
    // The hosting application declares its namespace (krizaka.users.preferences.reserved-prefixes).
    when(properties.preferences())
        .thenReturn(
            new IdentityInfrastructureProperties.Preferences(java.util.List.of("sovkora.")));
    // ADR-064. Depth: the namespace at every context builder is the fix; this keeps the bad key
    // from being stored for a builder that forgets it.
    for (String reserved :
        java.util.List.of(
            "sovkora.metering.deferred",
            "sovkora.guard.subject",
            "preference.language",
            "userId")) {
      Map<String, Object> written = Map.of("theme", "dark", reserved, "x");
      assertThrows(
          com.krizaka.users.domain.exception.InvalidRequestException.class,
          () -> service.updatePreferences(USER_ID.toString(), written),
          reserved);
    }
    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void updatePreferences_nullPreferences_preservesExisting() {
    User user = Persona.freeUser(Map.of("key", "value"));
    when(userRepository.findById(USER_ID.toString())).thenReturn(Optional.of(user));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    User result = service.updatePreferences(USER_ID.toString(), null);

    assertEquals("value", result.preferences().get("key"));
  }

  @Test
  void register_registrationClosed_refusesAndCreatesNothing() {
    when(properties.registration())
        .thenReturn(new IdentityInfrastructureProperties.Registration(false));

    assertThrows(
        com.krizaka.users.domain.exception.RegistrationClosedException.class,
        () -> service.register(USERNAME, EMAIL, TEST_PASSWORD, "en"));

    verify(userRepository, never()).create(any(), any());
    verify(authorityRepository, never()).saveAuthority(any(), any());
    verify(cryptography, never()).encodePassword(any());
  }

  @Test
  void register_duplicateEmail_throwsUserAlreadyExists() {
    IdentityInfrastructureProperties.EmailVerification emailVerif =
        mock(IdentityInfrastructureProperties.EmailVerification.class);
    when(emailVerif.enabled()).thenReturn(false);
    when(properties.emailVerification()).thenReturn(emailVerif);

    when(cryptography.encodePassword(anyString())).thenReturn("hash");

    // Mock TransactionTemplate to throw DataIntegrityViolationException
    doThrow(new DataIntegrityViolationException("duplicate"))
        .when(transactionManager)
        .getTransaction(any());

    assertThrows(Exception.class, () -> service.register(USERNAME, EMAIL, TEST_PASSWORD, "en"));
  }

  // --- verifyToken ---

  @Test
  void verifyToken_validToken_enablesUserAndReturnsTrue() {
    String tokenId = UUID.randomUUID().toString();
    String userId = USER_ID.toString();
    VerificationToken token =
        new VerificationToken(
            tokenId,
            userId,
            "EMAIL_VERIFICATION",
            HASHED,
            Instant.parse("2099-01-01T00:00:00Z"),
            false);

    when(cryptography.hashToken("plaintoken")).thenReturn(HASHED);
    when(verificationTokenRepository.findByTokenHashAndUsedFalse(HASHED))
        .thenReturn(Optional.of(token));

    User user = stubUser();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    boolean result = service.verifyToken("plaintoken");

    assertTrue(result);
    verify(verificationTokenRepository).markAsUsed(tokenId);
    verify(userRepository).save(any(User.class));
  }

  @Test
  void verifyToken_notFound_returnsFalse() {
    when(cryptography.hashToken("unknown")).thenReturn("hash");
    when(verificationTokenRepository.findByTokenHashAndUsedFalse("hash"))
        .thenReturn(Optional.empty());

    assertFalse(service.verifyToken("unknown"));
  }

  @Test
  void verifyToken_expired_returnsFalse() {
    VerificationToken token =
        new VerificationToken(
            "id",
            USER_ID.toString(),
            "EMAIL_VERIFICATION",
            HASHED,
            Instant.now(FIXED_CLOCK).minus(1, ChronoUnit.HOURS),
            false);

    when(cryptography.hashToken("expired")).thenReturn(HASHED);
    when(verificationTokenRepository.findByTokenHashAndUsedFalse(HASHED))
        .thenReturn(Optional.of(token));

    assertFalse(service.verifyToken("expired"));
  }

  // --- requiresEmailVerification ---

  @Test
  void requiresEmailVerification_delegatesToProperties() {
    IdentityInfrastructureProperties.EmailVerification emailVerif =
        mock(IdentityInfrastructureProperties.EmailVerification.class);
    when(emailVerif.enabled()).thenReturn(true);
    when(properties.emailVerification()).thenReturn(emailVerif);

    assertTrue(service.requiresEmailVerification());
  }

  // --- loadInterceptionSchema ---

  @Test
  void loadInterceptionSchema_emptySchemas_throwsConfigurationException() {
    IdentityInfrastructureProperties.Interceptions interceptions =
        mock(IdentityInfrastructureProperties.Interceptions.class);
    when(interceptions.schemas()).thenReturn(Map.of());
    when(properties.interceptions()).thenReturn(interceptions);

    assertThrows(ConfigurationException.class, () -> service.loadInterceptionSchema("onboarding"));
  }

  // --- Credential operations ---

  @Test
  void getUserCredentials_delegatesToRepository() {
    List<UserCredential> creds = List.of(new UserCredential(PROVIDER_OPENAI, true));
    when(userCredentialRepository.findByUserId(USER_ID.toString())).thenReturn(creds);

    List<UserCredential> result = service.getUserCredentials(USER_ID.toString());

    assertEquals(1, result.size());
    assertEquals(PROVIDER_OPENAI, result.getFirst().providerName());
  }

  @Test
  void createUserCredential_delegatesToRepository() {
    service.createUserCredential(USER_ID.toString(), PROVIDER_OPENAI, "sk-key");

    verify(userCredentialRepository).create(USER_ID.toString(), PROVIDER_OPENAI, "sk-key");
  }

  @Test
  void getDecryptedApiKey_mayWriteBecauseAnOldRowIsReSealedOnItsFirstRead() throws Exception {
    // krizaka-java#218 review: a read-only transaction refuses the re-seal UPDATE of an AES-CBC
    // row.
    var tx =
        IdentityServiceImpl.class
            .getMethod("getDecryptedApiKey", String.class, String.class)
            .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
    assertNotNull(tx);
    assertFalse(tx.readOnly());
  }

  @Test
  void rotateUserCredential_delegatesToRepository() {
    service.rotateUserCredential(USER_ID.toString(), PROVIDER_OPENAI, "sk-key-2");

    verify(userCredentialRepository).rotate(USER_ID.toString(), PROVIDER_OPENAI, "sk-key-2");
  }

  @Test
  void deleteUserCredential_delegatesToRepository() {
    service.deleteUserCredential(USER_ID.toString(), PROVIDER_OPENAI);

    verify(userCredentialRepository).delete(USER_ID.toString(), PROVIDER_OPENAI);
  }

  @Test
  void getDecryptedApiKey_delegatesToRepository() {
    when(userCredentialRepository.findApiKeyByUserIdAndProviderName(
            USER_ID.toString(), PROVIDER_OPENAI))
        .thenReturn(Optional.of("decrypted-key"));

    Optional<String> result = service.getDecryptedApiKey(USER_ID.toString(), PROVIDER_OPENAI);

    assertTrue(result.isPresent());
    assertEquals("decrypted-key", result.get());
  }

  // --- resolveInterception ---

  @Test
  void resolveInterception_mergesPreferencesAndDeletesInterception() {
    User user = stubUser();
    when(userRepository.findById(USER_ID.toString())).thenReturn(Optional.of(user));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    Map<String, Object> responses = Map.of("theme", "dark", "lang", "fr");
    service.resolveInterception(USER_ID, "onboarding", "onboarding-schema", responses);

    verify(userInterceptionRepository).deleteInterception(USER_ID, "onboarding");
    verify(userRepository).save(any(User.class));
  }

  @Test
  void register_successfulWithoutVerificationOrInterceptions_returnsUser() {
    IdentityInfrastructureProperties.EmailVerification emailVerif =
        mock(IdentityInfrastructureProperties.EmailVerification.class);
    when(emailVerif.enabled()).thenReturn(false);
    when(properties.emailVerification()).thenReturn(emailVerif);

    IdentityInfrastructureProperties.Interceptions interceptions =
        mock(IdentityInfrastructureProperties.Interceptions.class);
    when(interceptions.enabled()).thenReturn(false);
    when(properties.interceptions()).thenReturn(interceptions);

    when(cryptography.encodePassword(anyString())).thenReturn("hash");
    User createdUser = stubUser();
    when(userRepository.create(any(), any())).thenReturn(createdUser);
    when(transactionManager.getTransaction(any()))
        .thenReturn(mock(org.springframework.transaction.TransactionStatus.class));

    User result = service.register(USERNAME, EMAIL, TEST_PASSWORD, "en");

    assertNotNull(result);
    assertEquals(createdUser.username(), result.username());
    verify(userRepository).create(any(), eq("hash"));
    verify(authorityRepository).saveAuthority(anyString(), eq("ROLE_USER"));
    verify(userInterceptionRepository, never()).triggerInterception(any(), any(), any());
  }

  @Test
  void register_successfulWithVerificationAndInterceptions_returnsUser() {
    IdentityInfrastructureProperties.EmailVerification emailVerif =
        mock(IdentityInfrastructureProperties.EmailVerification.class);
    when(emailVerif.enabled()).thenReturn(true);
    when(properties.emailVerification()).thenReturn(emailVerif);

    IdentityInfrastructureProperties.Interceptions interceptions =
        mock(IdentityInfrastructureProperties.Interceptions.class);
    when(interceptions.enabled()).thenReturn(true);
    when(properties.interceptions()).thenReturn(interceptions);

    when(cryptography.encodePassword(anyString())).thenReturn("hash");
    when(cryptography.hashToken(anyString())).thenReturn("token-hash");
    User createdUser = stubUser();
    when(userRepository.create(any(), any())).thenReturn(createdUser);
    when(transactionManager.getTransaction(any()))
        .thenReturn(mock(org.springframework.transaction.TransactionStatus.class));

    User result = service.register(USERNAME, EMAIL, TEST_PASSWORD, "en");

    assertNotNull(result);
    verify(userRepository).create(any(), eq("hash"));
    verify(authorityRepository).saveAuthority(anyString(), eq("ROLE_USER"));
    verify(verificationTokenRepository).save(any());
    verify(userInterceptionRepository)
        .triggerInterception(any(), eq("onboarding"), eq("onboarding"));
  }

  @Test
  void loadInterceptionSchema_nonExistentResource_throwsConfigurationException() {
    IdentityInfrastructureProperties.Interceptions interceptions =
        mock(IdentityInfrastructureProperties.Interceptions.class);
    org.springframework.core.io.Resource resource =
        mock(org.springframework.core.io.Resource.class);
    when(resource.exists()).thenReturn(false);
    when(interceptions.schemas()).thenReturn(Map.of("onboarding", resource));
    when(properties.interceptions()).thenReturn(interceptions);

    assertThrows(ConfigurationException.class, () -> service.loadInterceptionSchema("onboarding"));
  }

  @Test
  void loadInterceptionSchema_resourceReadError_throwsInvalidRequestException()
      throws java.io.IOException {
    IdentityInfrastructureProperties.Interceptions interceptions =
        mock(IdentityInfrastructureProperties.Interceptions.class);
    org.springframework.core.io.Resource resource =
        mock(org.springframework.core.io.Resource.class);
    when(resource.exists()).thenReturn(true);
    when(resource.getInputStream()).thenThrow(new java.io.IOException("Read error"));
    when(interceptions.schemas()).thenReturn(Map.of("onboarding", resource));
    when(properties.interceptions()).thenReturn(interceptions);

    assertThrows(InvalidRequestException.class, () -> service.loadInterceptionSchema("onboarding"));
  }

  @Test
  void loadInterceptionSchema_successful_returnsContent() throws java.io.IOException {
    IdentityInfrastructureProperties.Interceptions interceptions =
        mock(IdentityInfrastructureProperties.Interceptions.class);
    org.springframework.core.io.Resource resource =
        mock(org.springframework.core.io.Resource.class);
    when(resource.exists()).thenReturn(true);
    java.io.ByteArrayInputStream bis =
        new java.io.ByteArrayInputStream("schema-content".getBytes());
    when(resource.getInputStream()).thenReturn(bis);
    when(interceptions.schemas()).thenReturn(Map.of("onboarding", resource));
    when(properties.interceptions()).thenReturn(interceptions);

    String result = service.loadInterceptionSchema("onboarding");

    assertEquals("schema-content", result);
  }
}
