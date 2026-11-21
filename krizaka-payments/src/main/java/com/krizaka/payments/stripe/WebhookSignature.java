package com.krizaka.payments.stripe;

import com.krizaka.payments.PaymentsException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;

/**
 * Checks {@code Stripe-Signature} ({@code t=…,v1=…}) over the raw body, constant-time, inside a
 * replay window. The clock is passed in (the SDK's own check reads the system clock).
 */
public final class WebhookSignature {

  // @fixed: the processor's documented replay tolerance for a webhook timestamp
  private static final Duration TOLERANCE = Duration.ofSeconds(300);

  private static final String HMAC = "HmacSHA256";

  private WebhookSignature() {}

  /**
   * Verifies a signature.
   *
   * @param rawBody the body, byte for byte as received
   * @param header the signature header, {@code null} when absent
   * @param secret the endpoint's signing secret
   * @param now the current time
   * @throws PaymentsException 400 on a missing, malformed, wrong or replayed signature
   */
  public static void verify(String rawBody, @Nullable String header, String secret, Instant now) {
    long timestamp = Long.MIN_VALUE;
    var signatures = new java.util.ArrayList<String>();
    for (String item : (header == null ? "" : header).split(",")) {
      if (item.startsWith("t=")) {
        timestamp = parseLong(item.substring(2));
      } else if (item.startsWith("v1=")) {
        signatures.add(item.substring(3));
      }
    }
    requirePresent(timestamp, signatures);
    requireFresh(timestamp, now);
    byte[] expected = hmac(rawBody, secret, timestamp);
    if (signatures.stream().noneMatch(s -> MessageDigest.isEqual(fromHex(s), expected))) {
      throw new PaymentsException(400, "Bad signature");
    }
  }

  private static void requirePresent(long timestamp, java.util.List<String> signatures) {
    if (timestamp == Long.MIN_VALUE || signatures.isEmpty()) {
      throw new PaymentsException(400, "Missing signature");
    }
  }

  private static void requireFresh(long timestamp, Instant now) {
    if (Duration.between(Instant.ofEpochSecond(timestamp), now).abs().compareTo(TOLERANCE) > 0) {
      throw new PaymentsException(400, "Timestamp outside the tolerance");
    }
  }

  /**
   * A signed header, for tests and a setup self-check.
   *
   * @param rawBody the body
   * @param secret the signing secret
   * @param timestamp epoch seconds
   * @return {@code t=…,v1=…}
   */
  public static String sign(String rawBody, String secret, long timestamp) {
    return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(hmac(rawBody, secret, timestamp));
  }

  private static long parseLong(String value) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return Long.MIN_VALUE;
    }
  }

  private static byte[] fromHex(String hex) {
    if (hex.length() % 2 != 0 || !hex.chars().allMatch(HexFormat::isHexDigit)) {
      return new byte[0];
    }
    return HexFormat.of().parseHex(hex);
  }

  private static byte[] hmac(String rawBody, String secret, long timestamp) {
    try {
      Mac mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC));
      return mac.doFinal((timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA256 is a required JDK algorithm", e);
    }
  }
}
