package com.agenttrust.admin.auth.keys;

import com.agenttrust.admin.auth.config.AuthProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

@Component
public class JwtRsaKeyManager {

    private static final Logger log = LoggerFactory.getLogger(JwtRsaKeyManager.class);

    private final AuthProperties authProperties;

    private volatile RSAPublicKey publicKey;
    private volatile RSAPrivateKey privateKey;
    private volatile String keyId;

    public JwtRsaKeyManager(AuthProperties authProperties) {
        this.authProperties = Objects.requireNonNull(authProperties, "authProperties");
        loadOrCreateKeys();
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public RSAPrivateKey privateKey() {
        return privateKey;
    }

    public String keyId() {
        return keyId;
    }

    /**
     * Public JWKS payload (no private key material).
     * Used by GET /.well-known/jwks.json
     */
    public Map<String, Object> jwksJson() {
        RSAKey jwk = new RSAKey.Builder(publicKey)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID(keyId)
                .build();

        return new JWKSet(jwk).toJSONObject();
    }

    private synchronized void loadOrCreateKeys() {
        if (publicKey != null && privateKey != null && keyId != null) {
            return;
        }

        Path basePath = Paths.get(authProperties.getJwt().getKeystore().getPath()).toAbsolutePath();
        Path dir = basePath.getParent() != null ? basePath.getParent() : Paths.get(".").toAbsolutePath();

        Path privatePath = Paths.get(basePath.toString() + ".pkcs8");
        Path publicPath = Paths.get(basePath.toString() + ".spki");
        Path kidPath = Paths.get(basePath.toString() + ".kid");

        try {
            Files.createDirectories(dir);

            if (Files.exists(privatePath) && Files.exists(publicPath) && Files.exists(kidPath)) {
                RSAPrivateKey loadedPrivate = (RSAPrivateKey) readPrivateKey(privatePath);
                RSAPublicKey loadedPublic = (RSAPublicKey) readPublicKey(publicPath);
                String loadedKid = Files.readString(kidPath, StandardCharsets.UTF_8).trim();

                if (loadedKid.isBlank()) {
                    throw new IllegalStateException("kid file is blank: " + kidPath);
                }

                this.privateKey = loadedPrivate;
                this.publicKey = loadedPublic;
                this.keyId = loadedKid;

                log.info("Loaded admin JWT RSA key material from {} (kid={})", basePath, keyId);
                return;
            }

            // Create new keypair
            KeyPair keyPair = generateRsaKeyPair();
            RSAPrivateKey newPrivate = (RSAPrivateKey) keyPair.getPrivate();
            RSAPublicKey newPublic = (RSAPublicKey) keyPair.getPublic();

            String newKid = computeKid(newPublic);

            writeAtomic(privatePath, newPrivate.getEncoded());
            writeAtomic(publicPath, newPublic.getEncoded());
            writeAtomicString(kidPath, newKid);

            // Best-effort permissions for private key file on POSIX filesystems (WSL should support this)
            setOwnerReadWriteOnly(privatePath);

            this.privateKey = newPrivate;
            this.publicKey = newPublic;
            this.keyId = newKid;

            log.info("Generated new admin JWT RSA key material at {} (kid={})", basePath, keyId);

        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Failed to load or create admin JWT key material", e);
        }
    }

    private static KeyPair generateRsaKeyPair() throws GeneralSecurityException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }

    private static String computeKid(RSAPublicKey pub) throws GeneralSecurityException {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] digest = sha256.digest(pub.getEncoded());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private static PrivateKey readPrivateKey(Path path) throws IOException, GeneralSecurityException {
        byte[] pkcs8 = Files.readAllBytes(path);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    }

    private static PublicKey readPublicKey(Path path) throws IOException, GeneralSecurityException {
        byte[] spki = Files.readAllBytes(path);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(new X509EncodedKeySpec(spki));
    }

    private static void writeAtomic(Path path, byte[] bytes) throws IOException {
        Path tmp = Paths.get(path.toString() + ".tmp");
        Files.write(tmp, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void writeAtomicString(Path path, String value) throws IOException {
        writeAtomic(path, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void setOwnerReadWriteOnly(Path path) {
        try {
            // chmod 600 equivalent on POSIX. If not supported (e.g., Windows FS), ignore.
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (Exception ignored) {
            // best-effort only
        }
    }
}
