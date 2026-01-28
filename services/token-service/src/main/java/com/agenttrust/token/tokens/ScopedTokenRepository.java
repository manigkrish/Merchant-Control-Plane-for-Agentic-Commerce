package com.agenttrust.token.tokens;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScopedTokenRepository extends JpaRepository<ScopedToken, Long> {

    /**
     * Hot-path lookup for validation. Token hash is computed from the raw X-Scoped-Token value.
     */
    Optional<ScopedToken> findByTenantIdAndTokenHash(String tenantId, String tokenHash);

    Optional<ScopedToken> findByTokenId(UUID tokenId);
}
