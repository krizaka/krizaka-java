package com.krizaka.users.domain.ports.inbound;

import com.krizaka.users.domain.model.Organization;
import com.krizaka.users.domain.model.OrganizationActor;
import com.krizaka.users.domain.model.OrganizationMember;
import com.krizaka.users.domain.model.OrganizationRole;
import com.krizaka.users.domain.model.OrganizationServiceKey;
import java.util.List;

/**
 * Inbound port of organizations that own accounts (sovkora#372): the people who belong to a company
 * and the service accounts its products use, with one view of the keys of those accounts.
 *
 * <p>Reading needs a membership; changing needs the owner or administrator role. The platform
 * administrator role may act on any organization. A refusal is an {@link
 * com.krizaka.users.domain.exception.OrganizationAccessDeniedException}; an organization the actor
 * cannot see is reported as not found.
 */
public interface OrganizationService {

  /** The prefix of a product's service account username. */
  String SERVICE_ACCOUNT_PREFIX = "svc_";

  /**
   * Creates an organization; its creator becomes its first owner.
   *
   * @param actor who creates it
   * @param slug lowercase letters, digits and hyphens, unique
   * @param name the display name
   * @return the organization
   * @throws com.krizaka.users.domain.exception.InvalidRequestException if the slug or name is not
   *     valid
   * @throws com.krizaka.users.domain.exception.OrganizationConflictException if the slug is taken
   */
  Organization create(OrganizationActor actor, String slug, String name);

  /** The organizations the actor belongs to. */
  List<Organization> listMine(OrganizationActor actor);

  /** One organization, for a member. */
  Organization get(OrganizationActor actor, String organizationId);

  /** The members and service accounts of an organization, for a member. */
  List<OrganizationMember> members(OrganizationActor actor, String organizationId);

  /**
   * Adds a person, or changes their role. Only an owner may grant the owner or administrator role.
   *
   * @throws com.krizaka.users.domain.exception.InvalidRequestException if the role is {@code
   *     SERVICE}
   * @throws com.krizaka.users.domain.exception.UserNotFoundException if the user does not exist
   * @throws com.krizaka.users.domain.exception.OrganizationConflictException if it would leave the
   *     organization without an owner
   */
  OrganizationMember addMember(
      OrganizationActor actor, String organizationId, String userId, OrganizationRole role);

  /**
   * Attaches a product's service account ({@code svc_*}) to the organization.
   *
   * @throws com.krizaka.users.domain.exception.InvalidRequestException if the account is not a
   *     {@code svc_} account
   */
  OrganizationMember attachServiceAccount(
      OrganizationActor actor, String organizationId, String userId);

  /**
   * Removes an account from the organization. The last owner cannot be removed, and an
   * administrator cannot remove an owner or another administrator.
   *
   * @return true if a membership was removed
   */
  boolean removeMember(OrganizationActor actor, String organizationId, String userId);

  /** The keys of the organization's service accounts, with their owners. */
  List<OrganizationServiceKey> serviceKeys(OrganizationActor actor, String organizationId);

  /**
   * Revokes a key of one of the organization's service accounts.
   *
   * @return true if a key was revoked, false if the organization has no such key
   */
  boolean revokeServiceKey(OrganizationActor actor, String organizationId, String keyId);
}
