package com.krizaka.ratelimit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parses an IP address literal into its bytes without ever resolving a name: no {@code
 * InetAddress.getByName}, so a client-supplied string never costs a DNS query.
 */
final class IpLiteral {

  private static final int IPV4_LENGTH = 4;
  private static final int IPV6_LENGTH = 16;
  private static final byte[] NONE = new byte[0];

  private IpLiteral() {}

  /**
   * The bytes of an address literal: 4 for IPv4 (an IPv4-mapped IPv6 address included), 16 for
   * IPv6; empty when the text is not a literal address.
   */
  static byte[] parse(String text) {
    if (text.indexOf(':') >= 0) {
      return ipv6(text);
    }
    return ipv4(text);
  }

  /** A strict dotted quad (4 decimal parts, 0-255, digits only), or empty. */
  private static byte[] ipv4(String text) {
    String[] parts = text.split("\\.", -1);
    if (parts.length != IPV4_LENGTH) {
      return NONE;
    }
    byte[] out = new byte[IPV4_LENGTH];
    for (int i = 0; i < IPV4_LENGTH; i++) {
      String part = parts[i];
      if (part.isEmpty() || part.length() > 3 || !isDigits(part)) {
        return NONE;
      }
      int value = Integer.parseInt(part);
      if (value > 255) {
        return NONE;
      }
      out[i] = (byte) value;
    }
    return out;
  }

  private static byte[] ipv6(String text) {
    int gap = text.indexOf("::");
    if (gap >= 0 && text.indexOf("::", gap + 1) >= 0) {
      return NONE;
    }
    List<Integer> head = new ArrayList<>();
    List<Integer> tail = new ArrayList<>();
    boolean ok =
        gap < 0
            ? groups(text, head, true)
            : groups(text.substring(0, gap), head, false)
                && groups(text.substring(gap + 2), tail, true);
    if (!ok || !fits(gap >= 0, head.size() + tail.size())) {
      return NONE;
    }
    byte[] out = new byte[IPV6_LENGTH];
    store(out, head, 0);
    store(out, tail, IPV6_LENGTH - 2 * tail.size());
    return isIpv4Mapped(out) ? Arrays.copyOfRange(out, 12, 16) : out;
  }

  /** Eight groups, or fewer when a {@code ::} stands for at least one zero group. */
  private static boolean fits(boolean compressed, int size) {
    return compressed ? size <= 7 : size == 8;
  }

  private static void store(byte[] out, List<Integer> words, int offset) {
    for (int i = 0; i < words.size(); i++) {
      out[offset + (2 * i)] = (byte) (words.get(i) >> 8);
      out[offset + (2 * i) + 1] = (byte) (int) words.get(i);
    }
  }

  /** Splits colon-separated 16-bit groups into {@code into}; the last may be a dotted quad. */
  private static boolean groups(String text, List<Integer> into, boolean mayEndWithIpv4) {
    if (text.isEmpty()) {
      return true;
    }
    String[] parts = text.split(":", -1);
    for (int i = 0; i < parts.length; i++) {
      if (!addGroup(parts[i], mayEndWithIpv4 && i == parts.length - 1, into)) {
        return false;
      }
    }
    return true;
  }

  private static boolean addGroup(String part, boolean mayBeIpv4, List<Integer> into) {
    if (part.indexOf('.') >= 0) {
      byte[] v4 = mayBeIpv4 ? ipv4(part) : NONE;
      if (v4.length > 0) {
        into.add(((v4[0] & 0xFF) << 8) | (v4[1] & 0xFF));
        into.add(((v4[2] & 0xFF) << 8) | (v4[3] & 0xFF));
      }
      return v4.length > 0;
    }
    if (part.isEmpty() || part.length() > 4 || !isHex(part)) {
      return false;
    }
    into.add(Integer.parseInt(part, 16));
    return true;
  }

  private static boolean isIpv4Mapped(byte[] bytes) {
    for (int i = 0; i < 10; i++) {
      if (bytes[i] != 0) {
        return false;
      }
    }
    return bytes[10] == (byte) 0xFF && bytes[11] == (byte) 0xFF;
  }

  private static boolean isDigits(String text) {
    return text.chars().allMatch(c -> c >= '0' && c <= '9');
  }

  private static boolean isHex(String text) {
    return text.chars()
        .allMatch(c -> (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'));
  }
}
