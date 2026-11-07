package com.krizaka.billing.client.provisioning;

import com.krizaka.billing.domain.exception.PackProvisioningException;
import com.krizaka.billing.domain.model.EntitlementGrant;
import com.krizaka.billing.domain.model.PackProvision;
import com.krizaka.billing.domain.port.PackProvisioningClient;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Writes a pack's price and grants to the billing service.
 *
 * <p><b>Fails hard, unlike every other adapter in this module.</b> {@code HttpPackPricingClient}
 * degrades to an empty price table and {@code HttpEntitlementProvider} fails open, because both sit
 * on an interactive path where a billing outage must not take a page down. This one sits inside a
 * pack install, where the opposite is true: swallowing the failure produces a catalogue entry
 * promising an entitlement billing cannot grant, and the first person to notice is a customer who
 * has paid and is locked out (ADR-036, invariant #3). The installer needs the exception so it can
 * compensate.
 *
 * <p>No cache and no retry: this is an admin-speed write, called once per install. Retrying it
 * would only widen the window in which the installer is holding a transaction open.
 */
final class HttpPackProvisioningClient implements PackProvisioningClient {

  private static final Logger log = LoggerFactory.getLogger(HttpPackProvisioningClient.class);

  private final PackProvisioningExchange exchange;

  HttpPackProvisioningClient(PackProvisioningExchange exchange) {
    this.exchange = exchange;
  }

  @Override
  public void provision(PackProvision provision) {
    try {
      exchange.provision(provision.packKey(), toBody(provision));
      log.info(
          "Provisioned pack {} at {} cents with {} entitlement(s)",
          provision.packKey(),
          provision.priceCents(),
          provision.entitlements().size());
    } catch (RestClientException e) {
      throw new PackProvisioningException(
          "Could not provision pack " + provision.packKey() + " in billing", e);
    }
  }

  @Override
  public boolean withdraw(String packKey) {
    if (packKey == null || packKey.isBlank()) {
      return false;
    }
    try {
      return exchange.withdraw(packKey).getStatusCode().is2xxSuccessful();
    } catch (RestClientResponseException e) {
      // The billing service answered, with a refusal: the pack is not withdrawn, and nothing to
      // throw about while another failure is being compensated. The exception carries the refusal
      // body, which says why billing said no; the status is in the message for a quick read.
      log.warn("Billing refused to withdraw pack {}: {}", packKey, e.getStatusCode(), e);
      return false;
    } catch (RestClientException e) {
      // Compensation is best-effort by construction: it runs while another failure is already
      // being handled, and throwing here would replace the error the caller must report with
      // this one. Logged loudly because a pack left sellable is a pack someone can buy.
      log.error("Could not withdraw pack {} while compensating an install", packKey, e);
      return false;
    }
  }

  private static PackProvisionBody toBody(PackProvision provision) {
    List<EntitlementGrantBody> grants =
        provision.entitlements().stream().map(HttpPackProvisioningClient::toBody).toList();
    return new PackProvisionBody(
        provision.packKey(),
        provision.priceCents(),
        provision.includedCredits(),
        provision.isActive(),
        grants);
  }

  private static EntitlementGrantBody toBody(EntitlementGrant grant) {
    return new EntitlementGrantBody(grant.key(), grant.valueType(), grant.value());
  }
}
