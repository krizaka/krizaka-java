package com.krizaka.users.infrastructure.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.krizaka.users.domain.model.User;
import com.krizaka.users.domain.ports.inbound.IdentityService;
import com.krizaka.users.infrastructure.adapter.rest.dto.UserDescriptor;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ProfileControllerTest {

  private final IdentityService identityService = mock(IdentityService.class);
  private final ProfileController controller = new ProfileController(identityService);

  private static User user(UUID id) {
    return new User(
        id, "alice", "alice@sovkora.com", true, Set.of("ROLE_USER"), Map.of("lang", "en"));
  }

  @Test
  void me_mapsPrincipalToDescriptor() {
    UUID id = UUID.randomUUID();
    var response = controller.me(user(id));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    UserDescriptor body = response.getBody();
    assertThat(body.id()).isEqualTo(id.toString());
    assertThat(body.username()).isEqualTo("alice");
    assertThat(body.email()).isEqualTo("alice@sovkora.com");
    assertThat(body.authorities()).containsExactly("ROLE_USER");
    assertThat(body.preferences()).containsEntry("lang", "en");
    verifyNoInteractions(identityService);
  }

  @Test
  void updatePreferences_delegatesAndReturnsRefreshedProfile() {
    UUID id = UUID.randomUUID();
    Map<String, Object> prefs = Map.of("theme", "dark");
    User updated = new User(id, "alice", "alice@sovkora.com", true, Set.of("ROLE_USER"), prefs);
    when(identityService.updatePreferences(id.toString(), prefs)).thenReturn(updated);

    var response = controller.updatePreferences(prefs, user(id));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().preferences()).containsEntry("theme", "dark");
    verify(identityService).updatePreferences(id.toString(), prefs);
  }
}
