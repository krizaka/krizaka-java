package com.krizaka.billing.service.infrastructure.adapter.payments;

import com.krizaka.billing.service.application.payment.PackPaymentService;
import com.krizaka.payments.PaymentEvents;
import com.krizaka.payments.PaymentsException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The payments processor's webhook (krizaka-internal#3685).
 *
 * <p>Open to anonymous callers: the processor presents no session token, and its signature over the
 * raw body is the gate, checked by {@link PackPaymentService} before anything is believed. The body
 * is read byte for byte (the signature covers those bytes) and bounded, so an anonymous caller
 * cannot make the service buffer an unbounded body.
 */
@RestController
@RequestMapping(PaymentWebhookController.PATH)
class PaymentWebhookController {

  /** The webhook's path, opened to anonymous callers by the security configuration. */
  static final String PATH = "/api/v1/billing/payments/webhook";

  /** The processor's signature header (Stripe's; another processor brings its own adapter). */
  static final String SIGNATURE_HEADER = "Stripe-Signature";

  // @fixed: a processor's event is a few KiB; 64 KiB bounds what an anonymous caller can send
  static final int MAX_BODY_BYTES = 64 * 1024;

  private final PackPaymentService packPaymentService;

  PaymentWebhookController(PackPaymentService packPaymentService) {
    this.packPaymentService = packPaymentService;
  }

  /**
   * Receives one notification.
   *
   * @param request the raw request, its body read as received
   * @param signature the processor's signature header, absent on a forged call
   * @return {@code 200} with what was done, {@code 413} when the body is over the bound
   * @throws IOException when the body cannot be read
   */
  @PostMapping
  ResponseEntity<?> receive(
      HttpServletRequest request,
      @RequestHeader(name = SIGNATURE_HEADER, required = false) @Nullable String signature)
      throws IOException {
    if (request.getContentLengthLong() > MAX_BODY_BYTES) {
      return tooLarge();
    }
    byte[] body;
    try (InputStream in = request.getInputStream()) {
      body = in.readNBytes(MAX_BODY_BYTES + 1);
    }
    if (body.length > MAX_BODY_BYTES) {
      return tooLarge();
    }
    PaymentEvents.Outcome outcome =
        packPaymentService.receive(new String(body, StandardCharsets.UTF_8), signature);
    return ResponseEntity.ok(new WebhookReceipt(outcome.name().toLowerCase(Locale.ROOT)));
  }

  /**
   * A refused notification, as Problem Details with the port's status: the processor retries what
   * is not {@code 2xx}, so a forged call is answered {@code 400} and nothing is applied.
   *
   * @param e the refusal
   * @return the structured body
   */
  @ExceptionHandler(PaymentsException.class)
  ResponseEntity<ProblemDetail> refused(PaymentsException e) {
    HttpStatus status = HttpStatus.resolve(e.status());
    return refusal(status == null ? HttpStatus.BAD_REQUEST : status, e.getMessage());
  }

  private static ResponseEntity<ProblemDetail> tooLarge() {
    return refusal(
        HttpStatus.CONTENT_TOO_LARGE,
        "A payment notification is at most " + MAX_BODY_BYTES + " bytes");
  }

  private static ResponseEntity<ProblemDetail> refusal(HttpStatus status, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle("Payment notification refused");
    return ResponseEntity.status(status).body(problem);
  }

  /**
   * What the webhook did with a notification.
   *
   * @param outcome {@code applied}, {@code duplicate} or {@code ignored}
   */
  record WebhookReceipt(String outcome) {}
}
