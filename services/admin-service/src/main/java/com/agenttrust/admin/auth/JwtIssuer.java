package com.agenttrust.admin.auth;

import com.agenttrust.admin.auth.config.AuthProperties;
import com.agenttrust.admin.auth.keys.JwtRsaKeyManager;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class JwtIssuer {

    private final AuthProperties authProperties;
    private final JwtRsaKeyManager keyManager;

    public JwtIssuer(AuthProperties authProperties, JwtRsaKeyManager keyManager) {
        this.authProperties = Objects.requireNonNull(authProperties, "authProperties");
        this.keyManager = Objects.requireNonNull(keyManager, "keyManager");
    }

    public String issueAdminToken(String subject, String tenantId, List<String> roles) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(roles, "roles");

        long ttlSeconds = authProperties.getJwt().getTtlSeconds();
        if (ttlSeconds <= 0) {
            throw new IllegalStateException("agenttrust.auth.jwt.ttl-seconds must be > 0");
        }

        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(authProperties.getJwt().getIssuer())
                .subject(subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .jwtID(UUID.randomUUID().toString())
                .claim("tenantId", tenantId)
                .claim("roles", roles)
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(keyManager.keyId())
                .build();

        SignedJWT jwt = new SignedJWT(header, claims);

        try {
            jwt.sign(new RSASSASigner(keyManager.privateKey()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }
}
