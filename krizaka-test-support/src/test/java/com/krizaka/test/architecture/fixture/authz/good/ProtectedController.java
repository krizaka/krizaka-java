package com.krizaka.test.architecture.fixture.authz.good;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Fixture: a controller with method-level @PreAuthorize — AuthzRules must accept it. */
@RestController
public class ProtectedController {

  @GetMapping("/api/studios/{id}")
  @PreAuthorize("hasRole('STUDIO_ADMIN')")
  public String get(@PathVariable String id) {
    return id;
  }

  @DeleteMapping("/api/studios/{id}")
  @PreAuthorize("hasRole('STUDIO_ADMIN')")
  public String delete(@PathVariable String id) {
    return id;
  }
}
