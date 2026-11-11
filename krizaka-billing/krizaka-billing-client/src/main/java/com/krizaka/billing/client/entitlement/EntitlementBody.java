package com.krizaka.billing.client.entitlement;

import java.time.Instant;
import java.util.Map;

/** Wire shape of an actor's entitlements. */
record EntitlementBody(
    String actorId,
    String planKey,
    Map<String, String> entitlements,
    long availableCredits,
    Instant expiresAt) {}
