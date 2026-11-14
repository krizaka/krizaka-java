package com.krizaka.billing.service.infrastructure.adapter.payments;

import com.krizaka.payments.EventStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The ids of the payment notifications billing applied ({@code billing_payment_event}). The primary
 * key arbitrates: two deliveries of one event racing on two instances insert one row.
 */
@Repository
public class PaymentEventRepository implements EventStore {

  private final JdbcTemplate jdbcTemplate;

  /**
   * Wires the store.
   *
   * @param jdbcTemplate the billing database
   */
  public PaymentEventRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public boolean markSeen(String id, String type) {
    return jdbcTemplate.update(
            "INSERT INTO billing_payment_event (event_id, event_type) VALUES (?, ?)"
                + " ON CONFLICT (event_id) DO NOTHING",
            id,
            type)
        == 1;
  }

  @Override
  public void forget(String id) {
    jdbcTemplate.update("DELETE FROM billing_payment_event WHERE event_id = ?", id);
  }
}
