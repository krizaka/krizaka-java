package com.krizaka.users.infrastructure.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.users.domain.model.RateLimitInfo;
import com.krizaka.users.domain.model.User;
import com.krizaka.users.domain.ports.inbound.AdminUserService;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminUserControllerRateLimitTest {

  private final AdminUserService service = mock(AdminUserService.class);
  private final AdminUserController controller = new AdminUserController(service);
  private final User admin =
      new User(UUID.randomUUID(), "ops", "ops@krizaka.com", true, Set.of("ROLE_ADMIN"), Map.of());

  @Test
  void theAdminReadsATierWithItsDailyQuota() {
    when(service.rateLimit("service"))
        .thenReturn(Optional.of(new RateLimitInfo("service", 1000, 20, 50_000)));

    var response = controller.rateLimit("service");

    assertThat(response.getBody().dailyRequests()).isEqualTo(50_000);
  }

  @Test
  void theAdminSetsTheQuotaAndGetsTheUpdatedRow() {
    when(service.setDailyRequests(any(), org.mockito.ArgumentMatchers.eq("service"), any()))
        .thenReturn(Optional.of(new RateLimitInfo("service", 1000, 20, 9)));

    var response =
        controller.setDailyRequests("service", new AdminUserController.DailyRequestsBody(9), admin);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody().dailyRequests()).isEqualTo(9);
  }

  @Test
  void anUnknownTierIsA404() {
    when(service.rateLimit("ghost")).thenReturn(Optional.empty());
    when(service.setDailyRequests(any(), any(), any())).thenReturn(Optional.empty());

    assertThat(controller.rateLimit("ghost").getStatusCode().value()).isEqualTo(404);
    assertThat(
            controller
                .setDailyRequests("ghost", new AdminUserController.DailyRequestsBody(1), admin)
                .getStatusCode()
                .value())
        .isEqualTo(404);
  }
}
