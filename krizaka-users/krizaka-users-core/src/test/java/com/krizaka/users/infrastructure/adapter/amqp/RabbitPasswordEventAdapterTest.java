package com.krizaka.users.infrastructure.adapter.amqp;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import com.krizaka.messaging.topology.MessagingExchanges;
import com.krizaka.users.domain.model.PasswordResetRequestedEvent;
import com.krizaka.users.persistence.domain.model.OutboxMessage;
import com.krizaka.users.persistence.domain.ports.OutboxStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RabbitPasswordEventAdapterTest {

  @Mock private OutboxStore outboxStore;

  private RabbitPasswordEventAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter =
        new RabbitPasswordEventAdapter(
            outboxStore, new MessagingExchanges("platform.events", "platform.dlx"));
  }

  @Test
  void shouldPublishEvent() {
    PasswordResetRequestedEvent event =
        new PasswordResetRequestedEvent("test@sovkora.com", "token123");

    adapter.publish(event);

    verify(outboxStore)
        .append(
            new OutboxMessage(
                "password", "test@sovkora.com", "platform.events", "evt.password.reset", event));
  }

  @Test
  void shouldThrowWhenEventIsNull() {
    assertThrows(NullPointerException.class, () -> adapter.publish(null));
  }
}
