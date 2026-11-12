package com.krizaka.users.application.service;

import com.krizaka.users.domain.exception.InvalidRequestException;
import com.krizaka.users.domain.exception.OrganizationAccessDeniedException;
import com.krizaka.users.domain.exception.OrganizationConflictException;
import com.krizaka.users.domain.exception.OrganizationNotFoundException;
import com.krizaka.users.domain.exception.UserNotFoundException;
import com.krizaka.users.domain.model.ApiKey;
import com.krizaka.users.domain.model.Organization;
import com.krizaka.users.domain.model.OrganizationActor;
import com.krizaka.users.domain.model.OrganizationMember;
import com.krizaka.users.domain.model.OrganizationRole;
import com.krizaka.users.domain.model.OrganizationServiceKey;
import com.krizaka.users.domain.model.User;
import com.krizaka.users.domain.ports.inbound.OrganizationService;
import com.krizaka.users.domain.ports.outbound.ApiKeyRepositoryPort;
import com.krizaka.users.domain.ports.outbound.OrganizationRepositoryPort;
import com.krizaka.users.domain.ports.outbound.UserRepositoryPort;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Concrete implementation of {@link OrganizationService}. */
@Service
class OrganizationServiceImpl implements OrganizationService {

  private static final Logger logger = LoggerFactory.getLogger(OrganizationServiceImpl.class);
  private static final Pattern SLUG = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");
  private static final int MAX_NAME_LENGTH = 150;
  private static final String ACTOR_REQUIRED = "actor must not be null";
  private static final String USER_ID_REQUIRED = "userId must not be null";

  private final OrganizationRepositoryPort organizations;
  private final UserRepositoryPort users;
  private final ApiKeyRepositoryPort apiKeys;
  private final ServiceAccountKeyRevocation serviceAccountKeys;

  OrganizationServiceImpl(
      OrganizationRepositoryPort organizations,
      UserRepositoryPort users,
      ApiKeyRepositoryPort apiKeys,
      ServiceAccountKeyRevocation serviceAccountKeys) {
    this.organizations = Objects.requireNonNull(organizations, "organizations is required");
    this.users = Objects.requireNonNull(users, "users is required");
    this.apiKeys = Objects.requireNonNull(apiKeys, "apiKeys is required");
    this.serviceAccountKeys =
        Objects.requireNonNull(serviceAccountKeys, "serviceAccountKeys is required");
  }

  @Override
  public Organization create(OrganizationActor actor, String slug, String name) {
    Objects.requireNonNull(actor, ACTOR_REQUIRED);
    String cleanSlug = slug == null ? "" : slug.strip();
    if (!SLUG.matcher(cleanSlug).matches()) {
      throw new InvalidRequestException(
          "An organization slug is lowercase letters, digits and hyphens (1 to 63 characters)");
    }
    String cleanName = name == null ? "" : name.strip();
    if (cleanName.isEmpty() || cleanName.length() > MAX_NAME_LENGTH) {
      throw new InvalidRequestException(
          "An organization name is 1 to " + MAX_NAME_LENGTH + " characters");
    }
    if (organizations.findBySlug(cleanSlug).isPresent()) {
      throw new OrganizationConflictException("The slug is already taken: " + cleanSlug);
    }
    Organization created =
        organizations.create(
            new Organization(UUID.randomUUID().toString(), cleanSlug, cleanName, null));
    organizations.saveMember(
        new OrganizationMember(created.id(), actor.userId(), OrganizationRole.OWNER, null));
    logger.info("Organization {} ({}) created by {}", created.slug(), created.id(), actor.userId());
    return created;
  }

  @Override
  public List<Organization> listMine(OrganizationActor actor) {
    Objects.requireNonNull(actor, ACTOR_REQUIRED);
    return organizations.findByMember(actor.userId());
  }

  @Override
  public Organization get(OrganizationActor actor, String organizationId) {
    Organization organization = organization(organizationId);
    requireMember(actor, organizationId);
    return organization;
  }

  @Override
  public List<OrganizationMember> members(OrganizationActor actor, String organizationId) {
    organization(organizationId);
    requireMember(actor, organizationId);
    return organizations.findMembers(organizationId);
  }

  @Override
  public OrganizationMember addMember(
      OrganizationActor actor, String organizationId, String userId, OrganizationRole role) {
    Objects.requireNonNull(userId, USER_ID_REQUIRED);
    Objects.requireNonNull(role, "role must not be null");
    organization(organizationId);
    OrganizationRole actorRole = requireManager(actor, organizationId);
    if (!role.isPerson()) {
      throw new InvalidRequestException("A service account is attached, not invited");
    }
    users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    Optional<OrganizationMember> existing = organizations.findMember(organizationId, userId);
    if (existing.map(m -> m.role() == OrganizationRole.SERVICE).orElse(false)) {
      throw new InvalidRequestException("That account is a service account of the organization");
    }
    boolean needsOwner = role != OrganizationRole.MEMBER || isManager(existing);
    if (needsOwner && !isOwner(actor, actorRole)) {
      throw new OrganizationAccessDeniedException(
          "Only an owner grants or changes the owner and administrator roles");
    }
    boolean demotesLastOwner =
        existing.map(m -> m.role() == OrganizationRole.OWNER).orElse(false)
            && role != OrganizationRole.OWNER
            && organizations.countOwners(organizationId) <= 1;
    if (demotesLastOwner) {
      throw new OrganizationConflictException("An organization always keeps one owner");
    }
    return organizations.saveMember(new OrganizationMember(organizationId, userId, role, null));
  }

  @Override
  public OrganizationMember attachServiceAccount(
      OrganizationActor actor, String organizationId, String userId) {
    Objects.requireNonNull(userId, USER_ID_REQUIRED);
    organization(organizationId);
    requireManager(actor, organizationId);
    User account = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    if (account.username() == null || !account.username().startsWith(SERVICE_ACCOUNT_PREFIX)) {
      throw new InvalidRequestException(
          "Only a " + SERVICE_ACCOUNT_PREFIX + " account is attached as a service account");
    }
    if (!account.enabled()) {
      throw new InvalidRequestException("The service account is disabled");
    }
    return organizations.saveMember(
        new OrganizationMember(organizationId, userId, OrganizationRole.SERVICE, null));
  }

  @Override
  public boolean removeMember(OrganizationActor actor, String organizationId, String userId) {
    Objects.requireNonNull(userId, USER_ID_REQUIRED);
    organization(organizationId);
    OrganizationRole actorRole = requireManager(actor, organizationId);
    Optional<OrganizationMember> target = organizations.findMember(organizationId, userId);
    if (target.isEmpty()) {
      return false;
    }
    OrganizationRole role = target.get().role();
    if (role == OrganizationRole.OWNER && organizations.countOwners(organizationId) <= 1) {
      throw new OrganizationConflictException("An organization always keeps one owner");
    }
    if ((role == OrganizationRole.OWNER || role == OrganizationRole.ADMIN)
        && !isOwner(actor, actorRole)) {
      throw new OrganizationAccessDeniedException(
          "Only an owner removes an owner or an administrator");
    }
    return organizations.deleteMember(organizationId, userId) > 0;
  }

  @Override
  public List<OrganizationServiceKey> serviceKeys(OrganizationActor actor, String organizationId) {
    organization(organizationId);
    requireManager(actor, organizationId);
    Map<String, String> usernames = new HashMap<>();
    return organizations.findServiceKeys(organizationId).stream()
        .map(
            key ->
                new OrganizationServiceKey(
                    key,
                    usernames.computeIfAbsent(
                        key.userId(), id -> users.findById(id).map(User::username).orElse(id))))
        .toList();
  }

  @Override
  @Transactional
  public boolean revokeServiceKey(OrganizationActor actor, String organizationId, String keyId) {
    Objects.requireNonNull(keyId, "keyId must not be null");
    organization(organizationId);
    requireManager(actor, organizationId);
    Optional<ApiKey> key =
        organizations.findServiceKeys(organizationId).stream()
            .filter(k -> k.id().equals(keyId))
            .findFirst();
    if (key.isEmpty()) {
      return false;
    }
    Optional<User> owner = users.findById(key.get().userId());
    boolean revoked =
        owner.isPresent() && ServiceAccountKeyRevocation.isServiceAccount(owner.get())
            ? serviceAccountKeys.revoke(
                owner.get(),
                keyId,
                actor.userId(),
                users.findById(actor.userId()).map(User::username).orElse(actor.userId()))
            : apiKeys.deleteByIdAndUserId(keyId, key.get().userId()) > 0;
    if (revoked) {
      logger.info(
          "Key {} of service account {} revoked from organization {} by {}",
          keyId,
          key.get().userId(),
          organizationId,
          actor.userId());
    }
    return revoked;
  }

  private Organization organization(String organizationId) {
    Objects.requireNonNull(organizationId, "organizationId must not be null");
    return organizations
        .findById(organizationId)
        .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
  }

  /** A member sees the organization; anyone else is told it does not exist. */
  private void requireMember(OrganizationActor actor, String organizationId) {
    Objects.requireNonNull(actor, ACTOR_REQUIRED);
    if (actor.platformAdmin()) {
      return;
    }
    if (organizations.findMember(organizationId, actor.userId()).isEmpty()) {
      throw new OrganizationNotFoundException(organizationId);
    }
  }

  /** The actor's role, when it lets them manage; a member who may not is refused. */
  private OrganizationRole requireManager(OrganizationActor actor, String organizationId) {
    Objects.requireNonNull(actor, ACTOR_REQUIRED);
    Optional<OrganizationMember> membership =
        organizations.findMember(organizationId, actor.userId());
    if (actor.platformAdmin()) {
      return membership.map(OrganizationMember::role).orElse(OrganizationRole.ADMIN);
    }
    OrganizationRole role =
        membership
            .map(OrganizationMember::role)
            .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
    if (!role.canManage()) {
      throw new OrganizationAccessDeniedException(
          "Only an owner or an administrator manages the organization");
    }
    return role;
  }

  private static boolean isOwner(OrganizationActor actor, OrganizationRole role) {
    return role == OrganizationRole.OWNER || actor.platformAdmin();
  }

  private static boolean isManager(Optional<OrganizationMember> member) {
    return member.map(m -> m.role().canManage()).orElse(false);
  }
}
