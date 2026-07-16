package com.platform.auth;

import com.platform.common.exception.BusinessException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class Totp {
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private Totp() {}

    public static String secret() {
        byte[] bytes = new byte[20];
        new SecureRandom().nextBytes(bytes);
        StringBuilder out = new StringBuilder(32);
        int buffer = 0, bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff); bits += 8;
            while (bits >= 5) { out.append(BASE32[(buffer >> (bits -= 5)) & 31]); }
        }
        if (bits > 0) out.append(BASE32[(buffer << (5 - bits)) & 31]);
        return out.toString();
    }

    public static long verify(String secret, String code, long epochSeconds) {
        if (code == null || !code.matches("\\d{6}")) return -1;
        long counter = epochSeconds / 30;
        for (long candidate = counter - 1; candidate <= counter + 1; candidate++) {
            if (generate(secret, candidate).equals(code)) return candidate;
        }
        return -1;
    }

    public static String generate(String secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decode(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 15;
            int binary = ((hash[offset] & 127) << 24) | ((hash[offset + 1] & 255) << 16)
                    | ((hash[offset + 2] & 255) << 8) | (hash[offset + 3] & 255);
            return String.format("%06d", binary % 1_000_000);
        } catch (Exception ex) {
            throw new BusinessException("AUTH-400", "TOTP operation failed");
        }
    }

    private static byte[] decode(String value) {
        int buffer = 0, bits = 0, count = 0;
        byte[] result = new byte[value.length() * 5 / 8];
        for (char c : value.toUpperCase().toCharArray()) {
            int digit = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c);
            if (digit < 0) throw new BusinessException("AUTH-400", "invalid TOTP secret");
            buffer = (buffer << 5) | digit; bits += 5;
            if (bits >= 8) result[count++] = (byte) ((buffer >> (bits -= 8)) & 255);
        }
        return java.util.Arrays.copyOf(result, count);
    }
}
