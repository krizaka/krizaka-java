package com.krizaka.users.infrastructure.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.krizaka.users.domain.exception.InvalidRequestException;
import com.krizaka.users.domain.model.ApiKey;
import com.krizaka.users.domain.model.Organization;
import com.krizaka.users.domain.model.OrganizationActor;
import com.krizaka.users.domain.model.OrganizationMember;
import com.krizaka.users.domain.model.OrganizationRole;
import com.krizaka.users.domain.model.OrganizationServiceKey;
import com.krizaka.users.domain.model.User;
import com.krizaka.users.domain.ports.inbound.OrganizationService;
import com.krizaka.users.infrastructure.adapter.rest.dto.AttachServiceAccountRequest;
import com.krizaka.users.infrastructure.adapter.rest.dto.CreateOrganizationRequest;
import com.krizaka.users.infrastructure.adapter.rest.dto.OrganizationMemberRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class OrganizationControllerTest {

  @Mock private OrganizationService service;

  private final User person =
      new User(UUID.randomUUID(), "ada", "ada@x.test", true, Set.of("ROLE_USER"), Map.of());
  private final User operator =
      new User(UUID.randomUUID(), "op", "op@x.test", true, Set.of("ROLE_ADMIN"), Map.of());
  private final Organization org = new Organization("o1", "krizaka", "Krizaka", Instant.EPOCH);

  private OrganizationController controller() {
    return new OrganizationController(service);
  }

  @Test
  void createReturns201AndPassesThePlatformAdminFlag() {
    var asOperator = new OrganizationActor(operator.id().toString(), true);
    when(service.create(asOperator, "krizaka", "Krizaka")).thenReturn(org);

    var response =
        controller().create(new CreateOrganizationRequest("krizaka", "Krizaka"), operator);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().slug()).isEqualTo("krizaka");
  }

  @Test
  void readsMapTheDomainRows() {
    var actor = new OrganizationActor(person.id().toString(), false);
    when(service.listMine(actor)).thenReturn(List.of(org));
    when(service.get(actor, "o1")).thenReturn(org);
    when(service.members(actor, "o1"))
        .thenReturn(
            List.of(new OrganizationMember("o1", "u", OrganizationRole.SERVICE, Instant.EPOCH)));
    var key = new ApiKey("k", "u", "prod", "oz_live_abcd", Instant.EPOCH, null);
    when(service.serviceKeys(actor, "o1"))
        .thenReturn(List.of(new OrganizationServiceKey(key, "svc_folioviv")));

    assertThat(controller().mine(person).getBody()).extracting("id").containsExactly("o1");
    assertThat(controller().get("o1", person).getBody().name()).isEqualTo("Krizaka");
    assertThat(controller().members("o1", person).getBody().getFirst().role()).isEqualTo("SERVICE");
    var row = controller().serviceKeys("o1", person).getBody().getFirst();
    assertThat(row.ownerUsername()).isEqualTo("svc_folioviv");
    assertThat(row.keyPrefix()).isEqualTo("oz_live_abcd");
  }

  @Test
  void addMemberParsesTheRoleAndRefusesAnUnknownOne() {
    var actor = new OrganizationActor(person.id().toString(), false);
    when(service.addMember(actor, "o1", "u", OrganizationRole.ADMIN))
        .thenReturn(new OrganizationMember("o1", "u", OrganizationRole.ADMIN, null));

    assertThat(
            controller()
                .addMember("o1", new OrganizationMemberRequest("u", " admin "), person)
                .getBody()
                .role())
        .isEqualTo("ADMIN");
    assertThatThrownBy(
            () -> controller().addMember("o1", new OrganizationMemberRequest("u", "boss"), person))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(
            () -> controller().addMember("o1", new OrganizationMemberRequest("u", null), person))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void attachReturns201() {
    var actor = new OrganizationActor(person.id().toString(), false);
    when(service.attachServiceAccount(actor, "o1", "svc"))
        .thenReturn(new OrganizationMember("o1", "svc", OrganizationRole.SERVICE, null));

    var response =
        controller().attachServiceAccount("o1", new AttachServiceAccountRequest("svc"), person);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void deletesAnswer204OrNotFound() {
    var actor = new OrganizationActor(person.id().toString(), false);
    when(service.removeMember(actor, "o1", "m")).thenReturn(true);
    when(service.removeMember(actor, "o1", "x")).thenReturn(false);
    when(service.revokeServiceKey(actor, "o1", "k")).thenReturn(true);
    when(service.revokeServiceKey(actor, "o1", "nope")).thenReturn(false);

    assertThat(controller().removeMember("o1", "m", person).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(controller().removeMember("o1", "x", person).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(controller().revokeServiceKey("o1", "k", person).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(controller().revokeServiceKey("o1", "nope", person).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
