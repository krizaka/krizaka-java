package com.krizaka.users.infrastructure.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.krizaka.users.domain.model.User;
import com.krizaka.users.domain.ports.inbound.IdentityService;
import com.krizaka.users.infrastructure.adapter.rest.InterceptionController.ResolveInterceptionRequest;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class InterceptionControllerTest {

  private final IdentityService identityService = mock(IdentityService.class);
  private final InterceptionController controller = new InterceptionController(identityService);

  private static User user(UUID id) {
    return new User(id, "alice", "alice@sovkora.com", true, Set.of("ROLE_USER"), Map.of());
  }

  @Test
  void getSchema_returnsSchemaJson() {
    when(identityService.loadInterceptionSchema("schema-1")).thenReturn("{\"k\":1}");
    var response = controller.getSchema("schema-1", user(UUID.randomUUID()));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("{\"k\":1}");
  }

  @Test
  void resolve_delegatesToIdentityService() {
    UUID id = UUID.randomUUID();
    Map<String, Object> responses = Map.of("answer", "yes");
    var request = new ResolveInterceptionRequest("CLARIFY", "schema-1", responses);

    var response = controller.resolve(request, user(id));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("resolved", true);
    verify(identityService).resolveInterception(id, "CLARIFY", "schema-1", responses);
  }
}
