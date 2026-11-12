package com.krizaka.users.domain.exception;

import java.io.Serial;

/**
 * A change the rules refuse because it would cut a product off Sovkora: erasing a {@code svc_}
 * account, or revoking its last active key. The code is stable ({@link #ACCOUNT_PROTECTED}, {@link
 * #LAST_KEY}); it is an {@link AdminActionRefusedException} so the administration audits the
 * refusal.
 */
public class ServiceAccountProtectedException extends AdminActionRefusedException {

  /** The account of a product is never erased. */
  public static final String ACCOUNT_PROTECTED = "SERVICE_ACCOUNT_PROTECTED";

  /** The last active key of a product's account is replaced (rotation), never only removed. */
  public static final String LAST_KEY = "SERVICE_ACCOUNT_LAST_KEY";

  @Serial private static final long serialVersionUID = 1L;

  public ServiceAccountProtectedException(String code, String message) {
    super(code, message);
  }
}
