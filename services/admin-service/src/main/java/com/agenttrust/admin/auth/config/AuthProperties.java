package com.agenttrust.admin.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Admin-service authentication configuration.
 *
 * We intentionally avoid hard validation annotations here because Sprint 2
 * is being implemented incrementally; missing values should not crash startup.
 * Instead, the auth components (login/JWKS) will enforce required values at runtime.
 */
@ConfigurationProperties(prefix = "agenttrust.auth")
public class AuthProperties {

    private final BootstrapAdmin bootstrapAdmin = new BootstrapAdmin();
    private final Jwt jwt = new Jwt();

    public BootstrapAdmin getBootstrapAdmin() {
        return bootstrapAdmin;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public static final class BootstrapAdmin {
        /**
         * Bootstrap admin username provided by environment variable.
         * If blank, bootstrap will be skipped.
         */
        private String username;

        /**
         * Bootstrap admin password provided by environment variable.
         * Only a password hash is stored in the database.
         * If blank, bootstrap will be skipped.
         */
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static final class Jwt {

        /**
         * JWT issuer. Keep stable across services so they can validate tokens consistently.
         * Default is service-oriented and can be overridden later via config.
         */
        private String issuer = "agenttrust-admin";

        /**
         * Access token TTL in seconds. Sprint 2 requirement: 15 minutes.
         */
        private long ttlSeconds = 900;

        private final Keystore keystore = new Keystore();

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public Keystore getKeystore() {
            return keystore;
        }

        public static final class Keystore {

            /**
             * Local dev keystore path. Must be gitignored.
             */
            private String path = ".local/keys/admin-jwt.jks";

            /**
             * Keystore password. Required for signing; should be provided via env var in real use.
             */
            private String password;

            /**
             * Key alias inside the keystore.
             */
            private String keyAlias = "admin-jwt";

            public String getPath() {
                return path;
            }

            public void setPath(String path) {
                this.path = path;
            }

            public String getPassword() {
                return password;
            }

            public void setPassword(String password) {
                this.password = password;
            }

            public String getKeyAlias() {
                return keyAlias;
            }

            public void setKeyAlias(String keyAlias) {
                this.keyAlias = keyAlias;
            }
        }
    }
}
