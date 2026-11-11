package com.krizaka.billing.client.provisioning;

/** Wire shape of one entitlement a pack grants. */
record EntitlementGrantBody(String key, String valueType, String value) {}
