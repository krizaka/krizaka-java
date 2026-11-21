package com.krizaka.ratelimit;

import java.util.ArrayList;
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
    byte[] v4 = ipv4(text);
    return v4 == null ? NONE : v4;
  }

  /** A strict dotted quad (4 decimal parts, 0-255, digits only), or null. */
  private static byte[] ipv4(String text) {
    String[] parts = text.split("\\.", -1);
    if (parts.length != IPV4_LENGTH) {
      return null;
    }
    byte[] out = new byte[IPV4_LENGTH];
    for (int i = 0; i < IPV4_LENGTH; i++) {
      String part = parts[i];
      if (part.isEmpty() || part.length() > 3 || !isDigits(part)) {
        return null;
      }
      int value = Integer.parseInt(part);
      if (value > 255) {
        return null;
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
    boolean ok;
    if (gap < 0) {
      ok = groups(text, head, true);
    } else {
      ok =
          groups(text.substring(0, gap), head, false)
              && groups(text.substring(gap + 2), tail, true);
    }
    int size = head.size() + tail.size();
    if (!ok || (gap < 0 ? size != 8 : size > 7)) {
      return NONE;
    }
    int[] words = new int[8];
    for (int i = 0; i < head.size(); i++) {
      words[i] = head.get(i);
    }
    for (int i = 0; i < tail.size(); i++) {
      words[8 - tail.size() + i] = tail.get(i);
    }
    byte[] out = new byte[IPV6_LENGTH];
    for (int i = 0; i < 8; i++) {
      out[2 * i] = (byte) (words[i] >> 8);
      out[2 * i + 1] = (byte) words[i];
    }
    return isIpv4Mapped(out) ? java.util.Arrays.copyOfRange(out, 12, 16) : out;
  }

  /** Splits colon-separated 16-bit groups into {@code into}; the last may be a dotted quad. */
  private static boolean groups(String text, List<Integer> into, boolean mayEndWithIpv4) {
    if (text.isEmpty()) {
      return true;
    }
    String[] parts = text.split(":", -1);
    for (int i = 0; i < parts.length; i++) {
      String part = parts[i];
      if (part.indexOf('.') >= 0) {
        byte[] v4 = i == parts.length - 1 && mayEndWithIpv4 ? ipv4(part) : null;
        if (v4 == null) {
          return false;
        }
        into.add(((v4[0] & 0xFF) << 8) | (v4[1] & 0xFF));
        into.add(((v4[2] & 0xFF) << 8) | (v4[3] & 0xFF));
      } else if (part.isEmpty() || part.length() > 4 || !isHex(part)) {
        return false;
      } else {
        into.add(Integer.parseInt(part, 16));
      }
    }
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
