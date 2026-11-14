package com.krizaka.billing.service.infrastructure.adapter.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.krizaka.billing.service.application.payment.PackPaymentService;
import com.krizaka.payments.PaymentEvents.Outcome;
import com.krizaka.payments.PaymentsException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

/** The webhook reads the raw body within its bound and answers a refusal as Problem Details. */
class PaymentWebhookControllerTest {

  private final PackPaymentService service = mock(PackPaymentService.class);
  private final PaymentWebhookController controller = new PaymentWebhookController(service);

  private static MockHttpServletRequest request(String body) {
    var request = new MockHttpServletRequest("POST", PaymentWebhookController.PATH);
    request.setContent(body.getBytes(StandardCharsets.UTF_8));
    return request;
  }

  private static MockHttpServletRequest withDeclaredLength(String body, long length) {
    var request =
        new MockHttpServletRequest("POST", PaymentWebhookController.PATH) {
          @Override
          public long getContentLengthLong() {
            return length;
          }
        };
    request.setContent(body.getBytes(StandardCharsets.UTF_8));
    return request;
  }

  @Test
  @DisplayName("[#3685] the raw body and the signature reach the service byte for byte")
  void passesTheRawBody() throws Exception {
    String body = "{\"id\":\"evt_1\",\"note\":\"é\"}";
    when(service.receive(body, "t=1,v1=ab")).thenReturn(Outcome.APPLIED);

    var response = controller.receive(request(body), "t=1,v1=ab");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .isEqualTo(new PaymentWebhookController.WebhookReceipt("applied"));
  }

  @Test
  @DisplayName("[#3685] a body over the bound is refused unread (413)")
  void aLargeBodyIsRefused() throws Exception {
    var declared = withDeclaredLength("{}", PaymentWebhookController.MAX_BODY_BYTES + 1L);
    assertThat(controller.receive(declared, "sig").getStatusCode())
        .isEqualTo(HttpStatus.CONTENT_TOO_LARGE);

    // chunked: no length declared, the read itself stops past the bound
    var undeclared =
        withDeclaredLength("x".repeat(PaymentWebhookController.MAX_BODY_BYTES + 1), -1L);
    assertThat(controller.receive(undeclared, "sig").getStatusCode())
        .isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
    verifyNoInteractions(service);
  }

  @Test
  @DisplayName(
      "[#3685] a refused notification is answered with the port's status as Problem Details")
  void aRefusalIsAProblem() {
    var response = controller.refused(new PaymentsException(400, "Bad signature"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).extracting(ProblemDetail::getDetail).isEqualTo("Bad signature");
    assertThat(controller.refused(new PaymentsException(503, "off")).getStatusCode())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }
}
