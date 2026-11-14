package com.krizaka.billing.service.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.krizaka.billing.service.application.service.PackPricingService;
import com.krizaka.billing.service.application.service.PackSubscriptionService;
import com.krizaka.billing.service.domain.model.CatalogPack;
import com.krizaka.payments.EventStore;
import com.krizaka.payments.PaymentEvent;
import com.krizaka.payments.PaymentEvents.Outcome;
import com.krizaka.payments.PaymentsException;
import com.krizaka.payments.PaymentsPort;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a verified notification may settle, and what billing refuses when it cannot verify. */
class PackPaymentServiceTest {

  private final PaymentsPort port = mock(PaymentsPort.class);
  private final PackPricingService packs = mock(PackPricingService.class);
  private final PackSubscriptionService subscriptions = mock(PackSubscriptionService.class);
  private final Set<String> seen = new HashSet<>();
  private final EventStore store =
      new EventStore() {
        @Override
        public boolean markSeen(String id, String type) {
          return seen.add(id);
        }

        @Override
        public void forget(String id) {
          seen.remove(id);
        }
      };
  private final PackPaymentService service =
      new PackPaymentService(Optional.of(port), store, packs, subscriptions);

  private void arrives(PaymentEvent event) {
    when(port.parseWebhook("body", "sig")).thenReturn(event);
  }

  @Test
  @DisplayName("[#3685] a paid pack checkout records its payment for its account and pack")
  void aPaidPackSettles() {
    when(packs.find("reels"))
        .thenReturn(Optional.of(new CatalogPack("reels", 4900, 0, true, List.of())));
    arrives(new PaymentEvent.PaymentPaid("evt_1", "pack:reels", "actor-1", "cus_1", "cs_1"));

    assertThat(service.receive("body", "sig")).isEqualTo(Outcome.APPLIED);
    verify(subscriptions).recordSettledPayment("cs_1", "actor-1", "reels");
    assertThat(service.receive("body", "sig")).isEqualTo(Outcome.DUPLICATE);
  }

  @Test
  @DisplayName("[#3685] with no payments processor configured, every notification is refused")
  void noPortFailsClosed() {
    var unconfigured = new PackPaymentService(Optional.empty(), store, packs, subscriptions);

    assertThatThrownBy(() -> unconfigured.receive("body", "sig"))
        .isInstanceOfSatisfying(
            PaymentsException.class, e -> assertThat(e.status()).isEqualTo(503));
    assertThat(seen).isEmpty();
    verifyNoInteractions(subscriptions);
  }

  @Test
  @DisplayName("[#3685] a refused signature records nothing")
  void aRefusedSignatureRecordsNothing() {
    when(port.parseWebhook("body", null))
        .thenThrow(new PaymentsException(400, "Missing signature"));

    assertThatThrownBy(() -> service.receive("body", null)).isInstanceOf(PaymentsException.class);
    assertThat(seen).isEmpty();
    verifyNoInteractions(subscriptions);
  }

  @Test
  @DisplayName(
      "[#3685] a payment with no reference, or for something other than a pack, settles nothing")
  void onlyAReferencedPackSettles() {
    arrives(new PaymentEvent.PaymentPaid("evt_2", "pack:reels", "actor-1", null));
    assertThat(service.receive("body", "sig")).isEqualTo(Outcome.IGNORED);

    arrives(new PaymentEvent.PaymentPaid("evt_3", "audit", "actor-1", null, "cs_3"));
    assertThat(service.receive("body", "sig")).isEqualTo(Outcome.IGNORED);

    when(packs.find("gone")).thenReturn(Optional.empty());
    arrives(new PaymentEvent.PaymentPaid("evt_4", "pack:gone", "actor-1", null, "cs_4"));
    assertThat(service.receive("body", "sig")).isEqualTo(Outcome.IGNORED);

    verify(subscriptions, never()).recordSettledPayment(any(), any(), any());
  }

  @Test
  @DisplayName("[#3685] an event billing does not handle is ignored and not recorded")
  void otherEventsAreIgnored() {
    arrives(new PaymentEvent.Ignored("evt_5"));

    assertThat(service.receive("body", "sig")).isEqualTo(Outcome.IGNORED);
    assertThat(seen).isEmpty();
  }
}
