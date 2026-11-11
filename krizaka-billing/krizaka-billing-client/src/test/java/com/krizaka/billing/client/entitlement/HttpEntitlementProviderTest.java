package com.krizaka.billing.client.entitlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.billing.domain.model.EntitlementSnapshot;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/** The adapter's mapping, cache, eviction and fail-open, against a stubbed exchange. */
class HttpEntitlementProviderTest {

  private final AtomicInteger calls = new AtomicInteger();
  private @Nullable EntitlementBody answer =
      new EntitlementBody(
          "actor-1",
          "premium",
          Map.of("capability.chat", "true"),
          340,
          Instant.parse("2099-01-01T00:00:00Z"));
  private boolean down;

  private final HttpEntitlementProvider provider =
      new HttpEntitlementProvider(
          actorId -> {
            calls.incrementAndGet();
            if (down) {
              throw new ResourceAccessException("down");
            }
            return answer;
          });

  @Test
  void theSnapshotIsMappedToTheDomain() {
    EntitlementSnapshot snapshot = provider.forActor("actor-1");

    assertThat(snapshot.resolved()).isTrue();
    assertThat(snapshot.planKey()).isEqualTo("premium");
    assertThat(snapshot.allows("capability.chat")).isTrue();
    assertThat(snapshot.availableCredits()).isEqualTo(340);
  }

  @Test
  void aSnapshotIsServedFromTheCacheUntilItExpires() {
    provider.forActor("actor-1");
    provider.forActor("actor-1");

    assertThat(calls).hasValue(1);
  }

  @Test
  void anEvictionMakesTheNextReadGoBackToBilling() {
    provider.forActor("actor-1");

    provider.evict("actor-1");
    provider.forActor("actor-1");

    assertThat(calls).hasValue(2);
  }

  @Test
  void evictingAnActorNeverSeenIsNotAnError() {
    provider.evict("actor-never-seen");

    assertThat(calls).hasValue(0);
  }

  @Test
  void anOutageFailsOpen() {
    down = true;

    assertThat(provider.forActor("actor-1").resolved()).isFalse();
  }

  @Test
  void aSnapshotTheDomainRefusesFailsOpen() {
    answer =
        new EntitlementBody("actor-1", " ", Map.of(), 0, Instant.parse("2099-01-01T00:00:00Z"));

    assertThat(provider.forActor("actor-1").resolved()).isFalse();
  }

  @Test
  void anEmptyAnswerFailsOpen() {
    answer = null;

    assertThat(provider.forActor("actor-1").resolved()).isFalse();
  }
}
