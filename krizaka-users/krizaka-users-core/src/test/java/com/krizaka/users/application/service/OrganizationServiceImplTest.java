package com.krizaka.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
import com.krizaka.users.domain.model.User;
import com.krizaka.users.domain.ports.outbound.ApiKeyRepositoryPort;
import com.krizaka.users.domain.ports.outbound.OrganizationRepositoryPort;
import com.krizaka.users.domain.ports.outbound.UserRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceImplTest {

  private static final String ORG = "org-1";

  @Mock private OrganizationRepositoryPort organizations;
  @Mock private UserRepositoryPort users;
  @Mock private ApiKeyRepositoryPort apiKeys;

  private OrganizationServiceImpl service;
  private final OrganizationActor owner = new OrganizationActor("owner", false);
  private final OrganizationActor admin = new OrganizationActor("admin", false);
  private final OrganizationActor member = new OrganizationActor("member", false);
  private final OrganizationActor stranger = new OrganizationActor("stranger", false);

  @BeforeEach
  void setUp() {
    service = new OrganizationServiceImpl(organizations, users, apiKeys);
  }

  private void orgExists() {
    when(organizations.findById(ORG))
        .thenReturn(Optional.of(new Organization(ORG, "krizaka", "Krizaka", Instant.EPOCH)));
  }

  private void role(OrganizationActor actor, OrganizationRole role) {
    when(organizations.findMember(ORG, actor.userId()))
        .thenReturn(Optional.of(new OrganizationMember(ORG, actor.userId(), role, null)));
  }

  private static User user(String id, String username, boolean enabled) {
    return new User(
        UUID.nameUUIDFromBytes(id.getBytes()),
        username,
        id + "@x.test",
        enabled,
        Set.of(),
        Map.of());
  }

  @Test
  void createStoresTheOrganizationAndMakesItsCreatorTheOwner() {
    when(organizations.findBySlug("krizaka")).thenReturn(Optional.empty());
    when(organizations.create(any())).thenAnswer(i -> i.getArgument(0));

    Organization created = service.create(owner, " krizaka ", " Krizaka ");

    assertThat(created.slug()).isEqualTo("krizaka");
    assertThat(created.name()).isEqualTo("Krizaka");
    ArgumentCaptor<OrganizationMember> saved = ArgumentCaptor.forClass(OrganizationMember.class);
    verify(organizations).saveMember(saved.capture());
    assertThat(saved.getValue().userId()).isEqualTo("owner");
    assertThat(saved.getValue().role()).isEqualTo(OrganizationRole.OWNER);
  }

  @Test
  void createRefusesABadSlugABlankNameAndATakenSlug() {
    assertThatThrownBy(() -> service.create(owner, "Bad Slug", "X"))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> service.create(owner, "-x", "X"))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> service.create(owner, null, "X"))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> service.create(owner, "ok", "  "))
        .isInstanceOf(InvalidRequestException.class);
    when(organizations.findBySlug("taken"))
        .thenReturn(Optional.of(new Organization("o", "taken", "T", null)));
    assertThatThrownBy(() -> service.create(owner, "taken", "T"))
        .isInstanceOf(OrganizationConflictException.class);
    verify(organizations, never()).create(any());
  }

  @Test
  void aStrangerCannotTellAnOrganizationExists() {
    orgExists();
    when(organizations.findMember(ORG, "stranger")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(stranger, ORG))
        .isInstanceOf(OrganizationNotFoundException.class);
    assertThatThrownBy(() -> service.members(stranger, ORG))
        .isInstanceOf(OrganizationNotFoundException.class);
    assertThatThrownBy(() -> service.serviceKeys(stranger, ORG))
        .isInstanceOf(OrganizationNotFoundException.class);
  }

  @Test
  void aMemberReadsButCannotManage() {
    orgExists();
    role(member, OrganizationRole.MEMBER);
    when(organizations.findMembers(ORG)).thenReturn(List.of());

    assertThat(service.members(member, ORG)).isEmpty();
    assertThatThrownBy(() -> service.addMember(member, ORG, "u", OrganizationRole.MEMBER))
        .isInstanceOf(OrganizationAccessDeniedException.class);
    assertThatThrownBy(() -> service.attachServiceAccount(member, ORG, "u"))
        .isInstanceOf(OrganizationAccessDeniedException.class);
    assertThatThrownBy(() -> service.serviceKeys(member, ORG))
        .isInstanceOf(OrganizationAccessDeniedException.class);
    assertThatThrownBy(() -> service.revokeServiceKey(member, ORG, "k"))
        .isInstanceOf(OrganizationAccessDeniedException.class);
  }

  @Test
  void theNonexistentOrganizationIsNotFoundForEveryone() {
    when(organizations.findById("nope")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(new OrganizationActor("a", true), "nope"))
        .isInstanceOf(OrganizationNotFoundException.class);
  }

  @Test
  void anAdministratorAddsMembersButOnlyAnOwnerGrantsManagingRoles() {
    orgExists();
    role(admin, OrganizationRole.ADMIN);
    when(users.findById("u")).thenReturn(Optional.of(user("u", "ada", true)));
    when(organizations.findMember(ORG, "u")).thenReturn(Optional.empty());
    when(organizations.saveMember(any())).thenAnswer(i -> i.getArgument(0));

    assertThat(service.addMember(admin, ORG, "u", OrganizationRole.MEMBER).role())
        .isEqualTo(OrganizationRole.MEMBER);
    assertThatThrownBy(() -> service.addMember(admin, ORG, "u", OrganizationRole.ADMIN))
        .isInstanceOf(OrganizationAccessDeniedException.class);
    assertThatThrownBy(() -> service.addMember(admin, ORG, "u", OrganizationRole.OWNER))
        .isInstanceOf(OrganizationAccessDeniedException.class);
  }

  @Test
  void anAdministratorCannotDemoteAnotherAdministrator() {
    orgExists();
    role(admin, OrganizationRole.ADMIN);
    when(users.findById("b")).thenReturn(Optional.of(user("b", "bea", true)));
    when(organizations.findMember(ORG, "b"))
        .thenReturn(Optional.of(new OrganizationMember(ORG, "b", OrganizationRole.ADMIN, null)));

    assertThatThrownBy(() -> service.addMember(admin, ORG, "b", OrganizationRole.MEMBER))
        .isInstanceOf(OrganizationAccessDeniedException.class);
  }

  @Test
  void anOwnerGrantsRolesButNeverLeavesTheOrganizationWithoutOwner() {
    orgExists();
    role(owner, OrganizationRole.OWNER);
    when(users.findById("owner")).thenReturn(Optional.of(user("owner", "olga", true)));
    when(organizations.findMember(ORG, "owner"))
        .thenReturn(
            Optional.of(new OrganizationMember(ORG, "owner", OrganizationRole.OWNER, null)));
    when(organizations.countOwners(ORG)).thenReturn(1L);

    assertThatThrownBy(() -> service.addMember(owner, ORG, "owner", OrganizationRole.ADMIN))
        .isInstanceOf(OrganizationConflictException.class);
    verify(organizations, never()).saveMember(any());
  }

  @Test
  void aServiceAccountIsAttachedNotInvited() {
    orgExists();
    role(admin, OrganizationRole.ADMIN);

    assertThatThrownBy(() -> service.addMember(admin, ORG, "u", OrganizationRole.SERVICE))
        .isInstanceOf(InvalidRequestException.class);

    when(users.findById("ghost")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.addMember(admin, ORG, "ghost", OrganizationRole.MEMBER))
        .isInstanceOf(UserNotFoundException.class);

    when(users.findById("svc")).thenReturn(Optional.of(user("svc", "svc_folioviv", true)));
    when(organizations.findMember(ORG, "svc"))
        .thenReturn(
            Optional.of(new OrganizationMember(ORG, "svc", OrganizationRole.SERVICE, null)));
    assertThatThrownBy(() -> service.addMember(admin, ORG, "svc", OrganizationRole.MEMBER))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void attachServiceAccountTakesOnlyEnabledSvcAccounts() {
    orgExists();
    role(admin, OrganizationRole.ADMIN);
    when(organizations.saveMember(any())).thenAnswer(i -> i.getArgument(0));
    when(users.findById("svc")).thenReturn(Optional.of(user("svc", "svc_verikora", true)));
    when(users.findById("person")).thenReturn(Optional.of(user("person", "oussama", true)));
    when(users.findById("off")).thenReturn(Optional.of(user("off", "svc_old", false)));

    assertThat(service.attachServiceAccount(admin, ORG, "svc").role())
        .isEqualTo(OrganizationRole.SERVICE);
    assertThatThrownBy(() -> service.attachServiceAccount(admin, ORG, "person"))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> service.attachServiceAccount(admin, ORG, "off"))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void removeMemberKeepsTheLastOwnerAndProtectsManagersFromAdministrators() {
    orgExists();
    role(admin, OrganizationRole.ADMIN);
    when(organizations.findMember(ORG, "o2"))
        .thenReturn(Optional.of(new OrganizationMember(ORG, "o2", OrganizationRole.OWNER, null)));
    when(organizations.countOwners(ORG)).thenReturn(1L);
    assertThatThrownBy(() -> service.removeMember(admin, ORG, "o2"))
        .isInstanceOf(OrganizationConflictException.class);

    when(organizations.countOwners(ORG)).thenReturn(2L);
    assertThatThrownBy(() -> service.removeMember(admin, ORG, "o2"))
        .isInstanceOf(OrganizationAccessDeniedException.class);

    when(organizations.findMember(ORG, "m"))
        .thenReturn(Optional.of(new OrganizationMember(ORG, "m", OrganizationRole.MEMBER, null)));
    when(organizations.deleteMember(ORG, "m")).thenReturn(1L);
    assertThat(service.removeMember(admin, ORG, "m")).isTrue();

    when(organizations.findMember(ORG, "nobody")).thenReturn(Optional.empty());
    assertThat(service.removeMember(admin, ORG, "nobody")).isFalse();
  }

  @Test
  void theOrganizationSeesTheKeysOfItsServiceAccountsWithTheirOwners() {
    orgExists();
    role(admin, OrganizationRole.ADMIN);
    ApiKey key = new ApiKey("k1", "svc-id", "prod", "oz_live_abcd", Instant.EPOCH, null);
    when(organizations.findServiceKeys(ORG)).thenReturn(List.of(key));
    when(users.findById("svc-id")).thenReturn(Optional.of(user("svc-id", "svc_folioviv", true)));

    var rows = service.serviceKeys(admin, ORG);

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().ownerUsername()).isEqualTo("svc_folioviv");
    assertThat(rows.getFirst().key().keyPrefix()).isEqualTo("oz_live_abcd");
  }

  @Test
  void revokingAKeyWorksOnlyForKeysOfTheOrganization() {
    orgExists();
    role(admin, OrganizationRole.ADMIN);
    ApiKey key = new ApiKey("k1", "svc-id", "prod", "oz_live_abcd", Instant.EPOCH, null);
    when(organizations.findServiceKeys(ORG)).thenReturn(List.of(key));
    when(apiKeys.deleteByIdAndUserId("k1", "svc-id")).thenReturn(1L);

    assertThat(service.revokeServiceKey(admin, ORG, "k1")).isTrue();
    assertThat(service.revokeServiceKey(admin, ORG, "someone-elses-key")).isFalse();
    verify(apiKeys, never()).deleteByIdAndUserId("someone-elses-key", "svc-id");
  }

  @Test
  void thePlatformAdministratorActsOnAnyOrganization() {
    orgExists();
    OrganizationActor operator = new OrganizationActor("op", true);
    when(organizations.findMember(ORG, "op")).thenReturn(Optional.empty());
    when(organizations.findMembers(ORG)).thenReturn(List.of());
    when(users.findById("svc")).thenReturn(Optional.of(user("svc", "svc_folioviv", true)));
    when(organizations.saveMember(any())).thenAnswer(i -> i.getArgument(0));

    assertThat(service.members(operator, ORG)).isEmpty();
    assertThat(service.attachServiceAccount(operator, ORG, "svc").role())
        .isEqualTo(OrganizationRole.SERVICE);
  }

  @Test
  void listMineAsksForTheActorsOrganizations() {
    when(organizations.findByMember("owner")).thenReturn(List.of());
    assertThat(service.listMine(owner)).isEmpty();
  }
}
