package com.krizaka.billing.client.provisioning;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PutExchange;

/**
 * The billing service's pack endpoints, declared; {@code HttpPackProvisioningClient} adapts them.
 */
@HttpExchange("/internal/v1/billing/packs")
interface PackProvisioningExchange {

  @PutExchange("/{packKey}")
  void provision(@PathVariable String packKey, @RequestBody PackProvisionBody body);

  @DeleteExchange("/{packKey}")
  ResponseEntity<Void> withdraw(@PathVariable String packKey);
}
