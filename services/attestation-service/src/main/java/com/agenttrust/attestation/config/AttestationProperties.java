package com.agenttrust.attestation.config;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Attestation-service configuration.
 *
 * - Bodyless verification (no Content-Digest yet)
 * - RFC 9421 signature verification (ED25519)
 * - Nonce replay defense via Redis
 *
 * We intentionally avoid hard validation annotations so incremental Sprint 3 work
 * does not crash startup due to missing values. Runtime components enforce required
 * settings and return deterministic RFC 9457 errors.
 */
@ConfigurationProperties(prefix = "agenttrust.attestation")
public class AttestationProperties {

    private final Profile profile = new Profile();
    private final Replay replay = new Replay();
    private final Keys keys = new Keys();

    public Profile getProfile() {
        return profile;
    }

    public Replay getReplay() {
        return replay;
    }

    public Keys getKeys() {
        return keys;
    }

    public static final class Profile {

        /**
         * Sprint 3 decision: bodyless verification only.
         */
        private boolean bodyless = true;

        /**
         * Signature parameters required in Signature-Input.
         * RFC 9421 binds these via "@signature-params".
         */
        private List<String> requiredSignatureParams = new ArrayList<>();

        /**
         * Covered components required in the signature base.
         * For RFC 9421 correctness, "@signature-params" must be included.
         */
        private List<String> requiredCoveredComponents = new ArrayList<>();

        /**
         * Allowed algorithms for this profile (e.g., "ed25519").
         */
        private List<String> allowedAlgorithms = new ArrayList<>();

        /**
         * Max allowed created/expires window in seconds (default 480 seconds = 8 minutes).
         */
        private int maxWindowSeconds = 480;

        public boolean isBodyless() {
            return bodyless;
        }

        public void setBodyless(boolean bodyless) {
            this.bodyless = bodyless;
        }

        public List<String> getRequiredSignatureParams() {
            return requiredSignatureParams;
        }

        public void setRequiredSignatureParams(List<String> requiredSignatureParams) {
            this.requiredSignatureParams = (requiredSignatureParams != null) ? requiredSignatureParams : new ArrayList<>();
        }

        public List<String> getRequiredCoveredComponents() {
            return requiredCoveredComponents;
        }

        public void setRequiredCoveredComponents(List<String> requiredCoveredComponents) {
            this.requiredCoveredComponents = (requiredCoveredComponents != null) ? requiredCoveredComponents : new ArrayList<>();
        }

        public List<String> getAllowedAlgorithms() {
            return allowedAlgorithms;
        }

        public void setAllowedAlgorithms(List<String> allowedAlgorithms) {
            this.allowedAlgorithms = (allowedAlgorithms != null) ? allowedAlgorithms : new ArrayList<>();
        }

        public int getMaxWindowSeconds() {
            return maxWindowSeconds;
        }

        public void setMaxWindowSeconds(int maxWindowSeconds) {
            this.maxWindowSeconds = maxWindowSeconds;
        }
    }

    public static final class Replay {

        private boolean enabled = true;

        /**
         * Prefix for Redis replay keys.
         * Full key shape is enforced by ReplayProtectionService.
         */
        private String keyPrefix = "replay";

        /**
         * Default TTL for replay keys if a tighter TTL cannot be derived deterministically.
         */
        private int defaultTtlSeconds = 480;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        public int getDefaultTtlSeconds() {
            return defaultTtlSeconds;
        }

        public void setDefaultTtlSeconds(int defaultTtlSeconds) {
            this.defaultTtlSeconds = defaultTtlSeconds;
        }
    }

    public static final class Keys {

        private final Registry registry = new Registry();

        public Registry getRegistry() {
            return registry;
        }

        public static final class Registry {

            /**
             * Bootstrap tenant-scoped key registry loaded from YAML config.
             * Store PUBLIC keys only.
             */
            private List<KeyEntry> entries = new ArrayList<>();

            public List<KeyEntry> getEntries() {
                return entries;
            }

            public void setEntries(List<KeyEntry> entries) {
                this.entries = (entries != null) ? entries : new ArrayList<>();
            }
        }

        public static final class KeyEntry {

            private String tenantId;
            private String keyId;

            /**
             * e.g., "ACTIVE", "REVOKED". Kept as String for incremental hardening.
             */
            private String status = "ACTIVE";

            /**
             * Base64-encoded raw Ed25519 public key bytes (32 bytes).
             */
            private String publicKeyBase64;

            /**
             * Optional. If set, keys beyond notAfter should be treated as unavailable.
             */
            private Instant notAfter;

            public String getTenantId() {
                return tenantId;
            }

            public void setTenantId(String tenantId) {
                this.tenantId = tenantId;
            }

            public String getKeyId() {
                return keyId;
            }

            public void setKeyId(String keyId) {
                this.keyId = keyId;
            }

            public String getStatus() {
                return status;
            }

            public void setStatus(String status) {
                this.status = status;
            }

            public String getPublicKeyBase64() {
                return publicKeyBase64;
            }

            public void setPublicKeyBase64(String publicKeyBase64) {
                this.publicKeyBase64 = publicKeyBase64;
            }

            public Instant getNotAfter() {
                return notAfter;
            }

            public void setNotAfter(Instant notAfter) {
                this.notAfter = notAfter;
            }
        }
    }
}
