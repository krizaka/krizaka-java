package com.krizaka.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class IpLiteralTest {

  @Test
  void aNameOrAMalformedAddressIsNotALiteralAndNothingIsResolved() {
    for (String bad :
        List.of(
            "dead.beef",
            "cafe.bad",
            "abc.de",
            "1.2.3",
            "::g",
            "1.2.3.4.5",
            "256.1.1.1",
            "1..2.3",
            "+1.2.3.4",
            "1:2:3:4:5:6:7",
            "1::2::3",
            ":::",
            "1:2:3:4:5:6:7:8:9",
            "12345::",
            "::1.2.3",
            "1.2.3.4::",
            ":1::",
            "1:2:3:4:5:6:7::8")) {
      assertThat(IpLiteral.parse(bad)).as(bad).isEmpty();
    }
  }

  @Test
  void validAddressesParse() {
    assertThat(IpLiteral.parse("10.0.0.1")).containsExactly(10, 0, 0, 1);
    assertThat(IpLiteral.parse("::1")).hasSize(16).endsWith((byte) 1);
    assertThat(IpLiteral.parse("::")).hasSize(16).containsOnly(0);
    assertThat(IpLiteral.parse("fc00::")).hasSize(16).startsWith((byte) 0xfc);
    assertThat(IpLiteral.parse("2001:db8:0:0:0:0:0:1")).hasSize(16);
    assertThat(IpLiteral.parse("::ffff:1.2.3.4")).containsExactly(1, 2, 3, 4);
    assertThat(IpLiteral.parse("64:ff9b::1.2.3.4")).hasSize(16);
  }
}
