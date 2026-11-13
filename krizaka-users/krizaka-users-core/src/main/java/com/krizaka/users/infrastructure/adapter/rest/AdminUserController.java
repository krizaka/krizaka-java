package com.krizaka.users.infrastructure.adapter.rest;

import com.krizaka.users.domain.model.AdminActor;
import com.krizaka.users.domain.model.AdminAuditEntry;
import com.krizaka.users.domain.model.AdminUserPage;
import com.krizaka.users.domain.model.AdminUserView;
import com.krizaka.users.domain.model.RateLimitInfo;
import com.krizaka.users.domain.model.User;
import com.krizaka.users.domain.ports.inbound.AdminUserService;
import com.krizaka.users.infrastructure.adapter.rest.dto.AdminEraseRequest;
import com.krizaka.users.infrastructure.adapter.rest.dto.AdminRoleRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The administration of accounts and of the rate-limit tiers' daily quota, for the operator
 * console: administrators only, every action audited. Sessions are stateless tokens: suspending an
 * account stops its sign-ins and key use here at once, while a token already issued lives out its
 * lifetime in the services that only verify its signature.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminUserController {

  private final AdminUserService service;

  public AdminUserController(AdminUserService service) {
    this.service = Objects.requireNonNull(service, "AdminUserService must not be null");
  }

  private static AdminActor actor(User user) {
    return new AdminActor(user.id().toString(), user.email());
  }

  @GetMapping("/users")
  public ResponseEntity<AdminUserPage> list(
      @RequestParam(name = "q", defaultValue = "") String query,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    return ResponseEntity.ok(service.list(query, page, size));
  }

  @GetMapping("/users/audit")
  public ResponseEntity<List<AdminAuditEntry>> audit(
      @RequestParam(required = false) String target,
      @RequestParam(defaultValue = "200") int limit) {
    return ResponseEntity.ok(service.audit(target, limit));
  }

  @GetMapping("/users/{id}")
  public ResponseEntity<AdminUserView> get(@PathVariable String id) {
    return ResponseEntity.ok(service.get(id));
  }

  @PostMapping("/users/{id}/suspend")
  public ResponseEntity<AdminUserView> suspend(
      @PathVariable String id, @AuthenticationPrincipal User admin) {
    return ResponseEntity.ok(service.suspend(actor(admin), id));
  }

  @PostMapping("/users/{id}/restore")
  public ResponseEntity<AdminUserView> restore(
      @PathVariable String id, @AuthenticationPrincipal User admin) {
    return ResponseEntity.ok(service.restore(actor(admin), id));
  }

  @PutMapping("/users/{id}/admin")
  public ResponseEntity<AdminUserView> setAdmin(
      @PathVariable String id,
      @RequestBody AdminRoleRequest request,
      @AuthenticationPrincipal User admin) {
    return ResponseEntity.ok(service.setAdmin(actor(admin), id, request.admin()));
  }

  @GetMapping("/users/{id}/export")
  public ResponseEntity<Map<String, Object>> export(
      @PathVariable String id, @AuthenticationPrincipal User admin) {
    return ResponseEntity.ok(service.export(actor(admin), id));
  }

  @DeleteMapping("/users/{id}")
  public ResponseEntity<Void> erase(
      @PathVariable String id,
      @RequestBody AdminEraseRequest request,
      @AuthenticationPrincipal User admin) {
    service.erase(actor(admin), id, request.confirmEmail());
    return ResponseEntity.noContent().build();
  }

  /** What the console edits: {@code dailyRequests} null clears the quota (the edge's default). */
  public record DailyRequestsBody(Integer dailyRequests) {}

  /** A rate-limit tier as the console reads it. */
  public record TierBody(
      String tierKey, int requestsPerMinute, int concurrentJobs, Integer dailyRequests) {

    static TierBody of(RateLimitInfo info) {
      return new TierBody(
          info.tierKey(), info.requestsPerMinute(), info.concurrentJobs(), info.dailyRequests());
    }
  }

  /** A tier with its daily request quota; 404 when the tier does not exist. */
  @GetMapping("/rate-limits/{tierKey}")
  public ResponseEntity<TierBody> rateLimit(@PathVariable String tierKey) {
    return service
        .rateLimit(tierKey)
        .map(TierBody::of)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /** Sets (or clears) the daily quota of a tier, audited; 400 out of bounds, 404 unknown tier. */
  @PutMapping("/rate-limits/{tierKey}/daily-requests")
  public ResponseEntity<TierBody> setDailyRequests(
      @PathVariable String tierKey,
      @RequestBody DailyRequestsBody body,
      @AuthenticationPrincipal User admin) {
    return service
        .setDailyRequests(actor(admin), tierKey, body.dailyRequests())
        .map(TierBody::of)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
