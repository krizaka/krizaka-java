package com.krizaka.users.infrastructure.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * The issuer's ES256 keys (ADR-001): one signing key and the public halves it publishes — the
 * current kid and the retired ones, so a rotation never invalidates tokens in flight. Holds no
 * material when the issuer runs in the legacy HS256 mode.
 */
public final class IssuerKeys {

  private static final IssuerKeys NONE = new IssuerKeys(null, List.of());

  private final ECKey signing;
  private final List<ECKey> published;

  private IssuerKeys(ECKey signing, List<ECKey> published) {
    this.signing = signing;
    this.published = published;
  }

  public static IssuerKeys none() {
    return NONE;
  }

  /** Parses the configured PEMs; a pair that does not match fails at startup, without the key. */
  public static IssuerKeys from(JwtProperties.Signing config) {
    if (config == null
        || (blank(config.privateKey()) && blank(config.publicKey()) && blank(config.keyId()))) {
      return NONE;
    }
    if (blank(config.privateKey()) || blank(config.publicKey()) || blank(config.keyId())) {
      throw new IllegalArgumentException(
          "krizaka.users.jwt.signing needs private-key, public-key and key-id together");
    }
    try {
      ECPublicKey publicKey = publicKey(config.publicKey());
      ECPrivateKey privateKey = privateKey(config.privateKey());
      ECKey current = jwk(publicKey, config.keyId(), privateKey);
      proveMatching(current);
      List<ECKey> published = new ArrayList<>();
      published.add(current.toPublicJWK());
      for (var retired : config.retired().entrySet()) {
        published.add(jwk(publicKey(retired.getValue()), retired.getKey(), null));
      }
      return new IssuerKeys(current, List.copyOf(published));
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "krizaka.users.jwt.signing: key is not a valid P-256 PEM pair ("
              + e.getClass().getSimpleName()
              + ")",
          e);
    }
  }

  public boolean enabled() {
    return signing != null;
  }

  /** The current signing key (private). */
  public ECKey signing() {
    return signing;
  }

  /** The public keys to publish. */
  public JWKSet publicSet() {
    return new JWKSet(new ArrayList<>(published));
  }

  public Optional<ECKey> byKeyId(String kid) {
    return published.stream().filter(k -> k.getKeyID().equals(kid)).findFirst();
  }

  private static ECKey jwk(ECPublicKey pub, String kid, ECPrivateKey priv) {
    if (!Curve.P_256.equals(Curve.forECParameterSpec(pub.getParams()))) {
      throw new IllegalArgumentException("krizaka.users.jwt.signing: the curve must be P-256");
    }
    var builder =
        new ECKey.Builder(Curve.P_256, pub)
            .keyID(kid)
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.ES256);
    if (priv != null) {
      builder.privateKey(priv);
    }
    return builder.build();
  }

  private static void proveMatching(ECKey key) throws JOSEException {
    JWSSigner signer = new ECDSASigner(key);
    var object = new JWSObject(new JWSHeader(JWSAlgorithm.ES256), new Payload("check"));
    object.sign(signer);
    JWSVerifier verifier = new ECDSAVerifier(key.toECPublicKey());
    if (!object.verify(verifier)) {
      throw new IllegalArgumentException(
          "krizaka.users.jwt.signing: private-key does not match public-key");
    }
  }

  /** Parses an SPKI PEM P-256 public key (caller keys, retired keys). */
  static ECPublicKey publicKey(String pem) throws GeneralSecurityException {
    return (ECPublicKey)
        KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der(pem)));
  }

  private static ECPrivateKey privateKey(String pem) throws GeneralSecurityException {
    return (ECPrivateKey)
        KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der(pem)));
  }

  private static byte[] der(String pem) {
    return Base64.getMimeDecoder().decode(pem.replaceAll("-----[A-Z ]+-----", "").strip());
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }
}
