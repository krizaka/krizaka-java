package com.krizaka.billing.client.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.billing.domain.exception.PackProvisioningException;
import com.krizaka.billing.domain.model.EntitlementGrant;
import com.krizaka.billing.domain.model.PackProvision;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** The adapter's mapping and failure contract, against a stubbed exchange: no HTTP involved. */
class HttpPackProvisioningClientTest {

  private final List<PackProvisionBody> sent = new ArrayList<>();
  private @Nullable RuntimeException failure;
  private ResponseEntity<Void> answer = ResponseEntity.noContent().build();

  private final HttpPackProvisioningClient client =
      new HttpPackProvisioningClient(
          new PackProvisioningExchange() {
            @Override
            public void provision(String packKey, PackProvisionBody body) {
              fail();
              sent.add(body);
            }

            @Override
            public ResponseEntity<Void> withdraw(String packKey) {
              fail();
              return answer;
            }

            private void fail() {
              if (failure != null) {
                throw failure;
              }
            }
          });

  @Test
  void provisionSendsThePackAsItsWireBody() {
    client.provision(
        new PackProvision(
            "studio",
            1900,
            500,
            true,
            List.of(new EntitlementGrant("capability.studio", "BOOLEAN", "true"))));

    assertThat(sent)
        .containsExactly(
            new PackProvisionBody(
                "studio",
                1900,
                500,
                true,
                List.of(new EntitlementGrantBody("capability.studio", "BOOLEAN", "true"))));
  }

  @Test
  void provisionFailsHardSoTheInstallerCanCompensate() {
    failure = new RestClientException("down");

    assertThatThrownBy(() -> client.provision(new PackProvision("studio", 1, 1, true, List.of())))
        .isInstanceOf(PackProvisioningException.class)
        .hasCause(failure);
  }

  @Test
  void withdrawIsTrueOnA2xx() {
    assertThat(client.withdraw("studio")).isTrue();
  }

  @Test
  void withdrawIsFalseWhenBillingRefuses() {
    failure =
        new RestClientResponseException("no", HttpStatus.NOT_FOUND, "Not Found", null, null, null);

    assertThat(client.withdraw("studio")).isFalse();
  }

  @Test
  void withdrawIsFalseWhenBillingIsUnreachable() {
    failure = new ResourceAccessException("down");

    assertThat(client.withdraw("studio")).isFalse();
  }
}
