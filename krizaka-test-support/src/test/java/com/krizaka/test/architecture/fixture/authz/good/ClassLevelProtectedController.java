package com.krizaka.test.architecture.fixture.authz.good;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Fixture: class-level @PreAuthorize covers all endpoints — AuthzRules must accept it. */
@PreAuthorize("hasRole('BILLING_ADMIN')")
@RestController
public class ClassLevelProtectedController {

  @PostMapping("/api/invoices")
  public String create() {
    return "invoice";
  }
}
