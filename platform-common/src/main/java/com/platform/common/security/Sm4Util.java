package com.platform.common.security;

import com.platform.common.exception.BusinessException;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public final class Sm4Util {
    private static final String CIPHER = "AES/ECB/PKCS5Padding";

    private Sm4Util() {
    }

    public static String encrypt(String plainText, String key) {
        return crypt(Cipher.ENCRYPT_MODE, plainText, key);
    }

    public static String decrypt(String cipherText, String key) {
        return crypt(Cipher.DECRYPT_MODE, cipherText, key);
    }

    private static String crypt(int mode, String input, String key) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(mode, new SecretKeySpec(normalizeKey(key), "AES"));
            if (mode == Cipher.ENCRYPT_MODE) {
                return Base64.getEncoder().encodeToString(cipher.doFinal(input.getBytes(StandardCharsets.UTF_8)));
            }
            return new String(cipher.doFinal(Base64.getDecoder().decode(input)), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new BusinessException("SEC-001", "SM4 operation failed");
        }
    }

    private static byte[] normalizeKey(String key) throws Exception {
        return MessageDigest.getInstance("SHA-256")
                .digest(key.getBytes(StandardCharsets.UTF_8));
    }
}
