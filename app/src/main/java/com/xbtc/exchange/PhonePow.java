package com.xbtc.exchange;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Deterministic, local proof-of-work helper for a foreground demonstration only.
 * It never reads, writes, resets, sends or credits BTC/XBTC wallet balances.
 * Not a consensus algorithm and not connected to any blockchain or mining pool.
 */
public final class PhonePow {
    private PhonePow() {}

    public static byte[] hash(MessageDigest sha, String challenge, long nonce) {
        if (nonce < 0) throw new IllegalArgumentException("Nonce must be nonnegative");
        return sha.digest((challenge + "|" + nonce).getBytes(StandardCharsets.UTF_8));
    }

    /** Local 12-leading-zero-bit demonstration target. */
    public static boolean isLocalProof(byte[] digest) {
        return digest != null && digest.length == 32
            && digest[0] == 0 && (digest[1] & 0xf0) == 0;
    }

    public static String hex(byte[] digest) {
        final char[] alphabet = "0123456789abcdef".toCharArray();
        final char[] result = new char[digest.length * 2];
        for (int i = 0; i < digest.length; i++) {
            int v = digest[i] & 255;
            result[i * 2] = alphabet[v >>> 4];
            result[i * 2 + 1] = alphabet[v & 15];
        }
        return new String(result);
    }
}
