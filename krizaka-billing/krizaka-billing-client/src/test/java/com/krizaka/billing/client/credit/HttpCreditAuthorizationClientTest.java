package com.krizaka.billing.client.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.billing.domain.model.BillableCapability;
import com.krizaka.billing.domain.model.ConsumptionReport;
import com.krizaka.billing.domain.model.CreditHoldCommand;
import com.krizaka.billing.domain.model.CreditHoldResponse;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/** The adapter's mapping and failure contract, against a stubbed exchange: no HTTP involved. */
class HttpCreditAuthorizationClientTest {

  private static final CreditHoldCommand HOLD =
      new CreditHoldCommand("actor-1", BillableCapability.VIDEO, null, "correlation-1", null, 360);

  private static final ConsumptionReport REPORT =
      new ConsumptionReport(null, null, null, null, null, null, null, null, null, 4000L, null);

  private final List<CreditWire.HoldRequest> holds = new ArrayList<>();
  private CreditWire.@Nullable HoldResponse holdAnswer =
      new CreditWire.HoldResponse("hold-1", true, false, 360, 40, 3);
  private @Nullable RuntimeException failure;

  private final HttpCreditAuthorizationClient client =
      new HttpCreditAuthorizationClient(
          new CreditExchange() {
            @Override
            public CreditWire.@Nullable HoldResponse hold(CreditWire.HoldRequest body) {
              holds.add(body);
              fail();
              return holdAnswer;
            }

            @Override
            public void settle(String holdId, CreditWire.Settle body) {
              fail();
            }

            @Override
            public void settleMeasured(String holdId, CreditWire.MeasuredSettle body) {
              fail();
            }

            @Override
            public Boolean settleAggregate(String holdId, CreditWire.AggregateSettle body) {
              fail();
              return Boolean.TRUE;
            }

            @Override
            public void release(String holdId, CreditWire.Release body) {
              fail();
            }

            private void fail() {
              if (failure != null) {
                throw failure;
              }
            }
          });

  @Test
  void holdSendsTheCommandAndReturnsTheDomainAnswer() {
    CreditHoldResponse response = client.hold(HOLD);

    assertThat(response).isEqualTo(new CreditHoldResponse("hold-1", true, false, 360, 40, 3));
    assertThat(holds)
        .containsExactly(
            new CreditWire.HoldRequest("actor-1", "VIDEO", null, "correlation-1", null, 360));
  }

  @Test
  void anyOtherHoldFailurePropagates() {
    failure = new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThatThrownBy(() -> client.hold(HOLD)).isSameAs(failure);

    failure = new HttpClientErrorException(HttpStatus.FORBIDDEN);
    assertThatThrownBy(() -> client.hold(HOLD)).isSameAs(failure);

    failure = new ResourceAccessException("down");
    assertThatThrownBy(() -> client.hold(HOLD)).isInstanceOf(RestClientException.class);
  }

  @Test
  void anEmptyHoldAnswerIsAFailureNotANullHold() {
    holdAnswer = null;

    assertThatThrownBy(() -> client.hold(HOLD)).isInstanceOf(RestClientException.class);
  }

  @Test
  void settlementsAndReleaseNeverThrow() {
    failure = new ResourceAccessException("down");

    assertThatCode(
            () -> {
              client.release("hold-1", "failed");
              client.settleMeasured("hold-1", REPORT, "key");
            })
        .doesNotThrowAnyException();
    assertThat(client.settleAggregate("hold-1", List.of(), "key")).isFalse();
  }
}
