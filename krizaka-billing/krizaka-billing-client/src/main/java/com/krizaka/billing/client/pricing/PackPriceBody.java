package com.krizaka.billing.client.pricing;

/** Wire shape of one pack's price. */
record PackPriceBody(String packKey, int priceCents, long includedCredits, boolean active) {}
