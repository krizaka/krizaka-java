package com.krizaka.test.architecture.fixture.authz.bad;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Fixture: a controller with no authorization annotation — AuthzRules must reject it. */
@RestController
public class UnprotectedController {

  @GetMapping("/api/studios")
  public String list() {
    return "studios";
  }
}
