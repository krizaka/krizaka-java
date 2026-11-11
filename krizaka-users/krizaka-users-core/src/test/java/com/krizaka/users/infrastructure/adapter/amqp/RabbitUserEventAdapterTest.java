package com.krizaka.users.infrastructure.adapter.amqp;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import com.krizaka.messaging.topology.MessagingExchanges;
import com.krizaka.users.domain.model.User;
import com.krizaka.users.domain.model.UserRegisteredEvent;
import com.krizaka.users.persistence.domain.model.OutboxMessage;
import com.krizaka.users.persistence.domain.ports.OutboxStore;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RabbitUserEventAdapterTest {

  @Mock private OutboxStore outboxStore;

  private RabbitUserEventAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter =
        new RabbitUserEventAdapter(
            outboxStore, new MessagingExchanges("platform.events", "platform.dlx"));
  }

  @Test
  void shouldPublishEvent() {
    User user =
        new User(
            UUID.randomUUID(), "testuser", "test@sovkora.com", true, Set.of("ROLE_USER"), Map.of());
    UserRegisteredEvent event = new UserRegisteredEvent(user, "token123");

    adapter.publish(event);

    verify(outboxStore)
        .append(
            new OutboxMessage(
                "user", user.id().toString(), "platform.events", "evt.user.registered", event));
  }

  @Test
  void shouldThrowWhenEventIsNull() {
    assertThrows(NullPointerException.class, () -> adapter.publish(null));
  }
}
