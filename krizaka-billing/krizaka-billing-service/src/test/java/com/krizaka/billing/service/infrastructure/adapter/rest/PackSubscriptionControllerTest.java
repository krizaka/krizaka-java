package com.krizaka.billing.service.infrastructure.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.krizaka.billing.service.application.service.PackSubscriptionService;
import com.krizaka.billing.service.application.service.PackSubscriptionService.PackSubscriptionView;
import com.krizaka.billing.service.domain.exception.PaymentRequiredException;
import com.krizaka.billing.service.domain.model.SubscriptionStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/** The subscribe route: who gets the operator's grant, and what a missing payment looks like. */
class PackSubscriptionControllerTest {

  private final PackSubscriptionService service = mock(PackSubscriptionService.class);
  private final PackSubscriptionController controller = new PackSubscriptionController(service);
  private final Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("actor-1").build();
  private final PackSubscriptionView view =
      new PackSubscriptionView(
          UUID.randomUUID(), "actor-1", "p", SubscriptionStatus.ACTIVE, Instant.now(), null);

  @Test
  @DisplayName("[#269] a user subscribes through the payment gate, never the admin grant")
  void aUserGoesThroughThePaymentGate() {
    when(service.subscribe("actor-1", "p", "pi_1")).thenReturn(view);

    var response =
        controller.subscribe(
            "p", "pi_1", jwt, new TestingAuthenticationToken("u", "x", "ROLE_USER"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    verify(service).subscribe("actor-1", "p", "pi_1");
    verifyNoMoreInteractions(service);
  }

  @Test
  @DisplayName("[#269] an admin keeps the grant without a payment")
  void anAdminIsGrantedWithoutPayment() {
    when(service.subscribeAsAdmin("actor-1", "p")).thenReturn(view);

    var response =
        controller.subscribe(
            "p", null, jwt, new TestingAuthenticationToken("a", "x", "ROLE_ADMIN"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    verify(service).subscribeAsAdmin("actor-1", "p");
    verifyNoMoreInteractions(service);
  }

  @Test
  @DisplayName("[#269] a missing payment is a 402 Problem Details")
  void missingPaymentIs402() {
    var response = controller.handlePaymentRequired(new PaymentRequiredException("p"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
    ProblemDetail body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getStatus()).isEqualTo(402);
    assertThat(body.getDetail()).contains("p");
  }
}
