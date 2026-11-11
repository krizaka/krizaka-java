package com.krizaka.users.domain.model;

/**
 * What an account is in an organization. Owners and administrators manage it; a member only belongs
 * to it; a service account is a product's account (such as {@code svc_folioviv}) that the
 * organization owns and whose keys it can see and revoke.
 */
public enum OrganizationRole {
  /** Owns the organization: manages everything, and the organization always keeps one. */
  OWNER,
  /** Manages members, service accounts and their keys, but not owners or other administrators. */
  ADMIN,
  /** Belongs to the organization. */
  MEMBER,
  /** A product's service account. */
  SERVICE;

  /**
   * Whether this role manages the organization.
   *
   * @return true for an owner or an administrator
   */
  public boolean canManage() {
    return this == OWNER || this == ADMIN;
  }

  /**
   * Whether a person can be given this role (a service account is attached, never invited).
   *
   * @return true for owner, administrator and member
   */
  public boolean isPerson() {
    return this != SERVICE;
  }
}
