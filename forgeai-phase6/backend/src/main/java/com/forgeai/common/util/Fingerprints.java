package com.forgeai.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 fingerprints used to notice when the input of a pipeline stage has changed. */
public final class Fingerprints {

    private Fingerprints() {
    }

    /** Hex SHA-256 of the parts, joined with a separator that cannot occur in hex or UUID text. */
    public static String sha256(String... parts) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\u0000", parts).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
