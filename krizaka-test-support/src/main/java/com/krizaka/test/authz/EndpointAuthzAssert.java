package com.krizaka.test.authz;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.function.Supplier;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Fluent MockMvc assertions for the three authorization invariants every Krizaka endpoint must hold
 * (audit-03, gate item 4):
 *
 * <ul>
 *   <li>no token → 401 Unauthorized
 *   <li>valid token, insufficient role → 403 Forbidden
 *   <li>valid token, correct role, other tenant's resource → 404 Not Found
 * </ul>
 *
 * <p>The 404 (not 403) on a cross-tenant access is intentional: a 403 leaks that the resource
 * exists; a 404 is indistinguishable from a legitimately absent resource.
 *
 * <p>Usage in a {@code @WebMvcTest} or {@code @SpringBootTest} class:
 *
 * <pre>{@code
 * EndpointAuthzAssert
 *     .forEndpoint(mockMvc, () -> get("/api/studios/{id}", studioId))
 *     .returns401WhenUnauthenticated()
 *     .returns403WhenAuthenticated(jwt().authorities(new SimpleGrantedAuthority("ROLE_VIEWER")))
 *     .returns404CrossTenant(
 *         () -> get("/api/studios/{id}", otherTenantStudioId)
 *                 .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDIO_ADMIN"))));
 * }</pre>
 *
 * <p>The factory-supplier pattern ({@code Supplier<MockHttpServletRequestBuilder>}) ensures each
 * assertion starts from a fresh builder: calling {@code .with(postProcessor)} on a shared builder
 * would permanently accumulate processors and corrupt subsequent assertions.
 *
 * <p>The {@code MockMvc} passed here must have Spring Security's filter chain applied (e.g. via
 * {@code @AutoConfigureMockMvc} or {@code springSecurity()} in a standalone setup); without it, the
 * security filters are absent and the 401/403 assertions will fail.
 */
public final class EndpointAuthzAssert {

  private final MockMvc mvc;
  private final Supplier<MockHttpServletRequestBuilder> requestFactory;

  private EndpointAuthzAssert(MockMvc mvc, Supplier<MockHttpServletRequestBuilder> requestFactory) {
    this.mvc = mvc;
    this.requestFactory = requestFactory;
  }

  /**
   * Entry point.
   *
   * @param mvc the MockMvc with the Spring Security filter chain applied
   * @param requestFactory a supplier that produces a fresh builder for the endpoint under test on
   *     each call; re-use of a single mutated builder would corrupt subsequent assertions
   */
  public static EndpointAuthzAssert forEndpoint(
      MockMvc mvc, Supplier<MockHttpServletRequestBuilder> requestFactory) {
    return new EndpointAuthzAssert(mvc, requestFactory);
  }

  /**
   * Asserts that the endpoint returns 401 when no authentication credential is provided.
   *
   * @return this, for chaining
   * @throws Exception on MockMvc or assertion failure
   */
  public EndpointAuthzAssert returns401WhenUnauthenticated() throws Exception {
    return expectStatus(requestFactory.get(), status().isUnauthorized());
  }

  /**
   * Asserts that the endpoint returns 403 when authenticated with the given insufficient authority.
   *
   * <p>Pass a Spring Security Test {@code RequestPostProcessor} such as {@code
   * SecurityMockMvcRequestPostProcessors.jwt().authorities(new
   * SimpleGrantedAuthority("ROLE_VIEWER"))} to simulate a caller who has a valid token but lacks
   * the required role.
   *
   * @param insufficientAuth a post-processor that authenticates the request with an authority that
   *     does not satisfy the endpoint's access rule
   * @return this, so the next assertion can follow
   * @throws Exception when the request cannot be performed or the status differs
   */
  public EndpointAuthzAssert returns403WhenAuthenticated(RequestPostProcessor insufficientAuth)
      throws Exception {
    return expectStatus(requestFactory.get().with(insufficientAuth), status().isForbidden());
  }

  /**
   * Asserts that the endpoint returns 404 when an authenticated caller accesses a resource that
   * belongs to a different tenant.
   *
   * <p>The caller builds the cross-tenant request (different resource id, correct role for their
   * own tenant) and supplies authentication through the builder; this method only asserts the
   * result:
   *
   * <pre>{@code
   * .returns404CrossTenant(
   *     () -> get("/api/studios/{id}", otherTenantId)
   *             .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDIO_ADMIN"))))
   * }</pre>
   *
   * @param crossTenantRequest a supplier that produces a request to a resource that belongs to
   *     another tenant, with valid authentication for the requesting tenant's role
   * @return this, for chaining
   * @throws Exception on MockMvc or assertion failure
   */
  public EndpointAuthzAssert returns404CrossTenant(
      Supplier<MockHttpServletRequestBuilder> crossTenantRequest) throws Exception {
    return expectStatus(crossTenantRequest.get(), status().isNotFound());
  }

  private EndpointAuthzAssert expectStatus(
      MockHttpServletRequestBuilder request, ResultMatcher expected) throws Exception {
    mvc.perform(request).andExpect(expected);
    return this;
  }
}
