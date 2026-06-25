package com.platform.common.security;

import com.platform.common.exception.BusinessException;
import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import java.util.Base64;

public final class Sm4Util {
    private static final int BLOCK_SIZE = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private Sm4Util() {
    }

    public static String encrypt(String plainText, String key) {
        byte[] iv = new byte[BLOCK_SIZE];
        RANDOM.nextBytes(iv);
        byte[] encrypted = process(true, plainText.getBytes(StandardCharsets.UTF_8), normalizeKey(key), iv);
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    public static String decrypt(String cipherText, String key) {
        byte[] combined = Base64.getDecoder().decode(cipherText);
        if (combined.length <= BLOCK_SIZE) {
            throw new BusinessException("SEC-001", "invalid SM4 payload");
        }
        byte[] iv = Arrays.copyOfRange(combined, 0, BLOCK_SIZE);
        byte[] encrypted = Arrays.copyOfRange(combined, BLOCK_SIZE, combined.length);
        return new String(process(false, encrypted, normalizeKey(key), iv), StandardCharsets.UTF_8);
    }

    private static byte[] process(boolean encrypt, byte[] input, byte[] key, byte[] iv) {
        try {
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(
                    CBCBlockCipher.newInstance(new SM4Engine()), new PKCS7Padding());
            cipher.init(encrypt, new ParametersWithIV(new KeyParameter(key), iv));
            byte[] output = new byte[cipher.getOutputSize(input.length)];
            int length = cipher.processBytes(input, 0, input.length, output, 0);
            length += cipher.doFinal(output, length);
            return Arrays.copyOf(output, length);
        } catch (Exception ex) {
            throw new BusinessException("SEC-001", "SM4 operation failed");
        }
    }

    private static byte[] normalizeKey(String key) {
        try {
            return Arrays.copyOf(MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8)), BLOCK_SIZE);
        } catch (Exception ex) {
            throw new BusinessException("SEC-001", "SM4 key normalization failed");
        }
    }
}
