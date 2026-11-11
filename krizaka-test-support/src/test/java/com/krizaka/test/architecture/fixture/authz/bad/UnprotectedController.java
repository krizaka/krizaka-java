package com.krizaka.test.architecture.fixture.authz.bad;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/** Fixture: a controller with no authorization annotation — AuthzRules must reject it. */
@RestController
public class UnprotectedController {

  @RequestMapping(path = "/api/studios", method = RequestMethod.GET)
  public String list() {
    return "studios";
  }
}
