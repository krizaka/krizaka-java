package com.krizaka.billing.client.provisioning;

import java.util.List;

/** Wire body of the pack write. */
record PackProvisionBody(
    String packKey,
    int priceCents,
    long includedCredits,
    boolean isActive,
    List<EntitlementGrantBody> entitlements) {}
