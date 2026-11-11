package com.krizaka.test.architecture.fixture.authz.good;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Fixture: a controller with method-level @PreAuthorize — AuthzRules must accept it. */
@RestController
public class ProtectedController {

  @PreAuthorize("hasRole('STUDIO_ADMIN')")
  @GetMapping("/api/studios/current")
  public String get() {
    return "studio";
  }

  @PreAuthorize("hasRole('STUDIO_ADMIN')")
  @DeleteMapping("/api/studios/current")
  public String delete() {
    return "deleted";
  }
}
