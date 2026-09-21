package com.josh.mcpadapter;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 hex digests. Duplicated from catalog-service's own Checksums class
 * deliberately — mcp-adapter is an independently deployable module (Section 7:
 * it runs beside each developer's assistant, not bundled with the service).
 */
final class Checksums {

    private Checksums() {}

    static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", e);
        }
    }
}
