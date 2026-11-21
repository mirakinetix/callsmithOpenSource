package com.mirakinetix.callsmith.infrastructure.persistence.sqlite;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

final class SecretCipher {
    static final int VERSION = 1;
    static final int SALT_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    // These parameters are part of the version 1 format.
    private static final int ITERATIONS = 600_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretCipher() {}

    static SecretKey derive(char[] passphrase, byte[] salt) {
        if (passphrase == null || passphrase.length == 0 || passphrase.length > 1024
                || salt == null || salt.length != SALT_BYTES) {
            throw new IllegalStateException("Invalid passphrase or secret storage metadata.");
        }
        var spec = new PBEKeySpec(passphrase, salt, ITERATIONS, 256);
        try {
            byte[] bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            try {
                return new SecretKeySpec(bytes, "AES");
            } finally {
                Arrays.fill(bytes, (byte) 0);
            }
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Secret storage cryptography is unavailable.");
        } finally {
            spec.clearPassword();
        }
    }

    static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    static byte[] context(String purpose, String... parts) {
        byte[][] fields = new byte[parts.length + 1][];
        fields[0] = purpose.getBytes(StandardCharsets.UTF_8);
        int size = Integer.BYTES; // Format version
        for (int index = 0; index < fields.length; index++) {
            if (index > 0) {
                fields[index] = parts[index - 1].getBytes(StandardCharsets.UTF_8);
            }
            size = Math.addExact(size, Math.addExact(Integer.BYTES, fields[index].length));
        }
        ByteBuffer output = ByteBuffer.allocate(size).putInt(VERSION);
        for (byte[] field : fields) {
            output.putInt(field.length).put(field);
        }
        return output.array();
    }

    static String encrypt(SecretKey key, byte[] context, String value) {
        byte[] plaintext = value.getBytes(StandardCharsets.UTF_8);
        try {
            if (!value.equals(new String(plaintext, StandardCharsets.UTF_8))) {
                throw new IllegalStateException("The secret contains invalid Unicode.");
            }
            byte[] nonce = randomBytes(NONCE_BYTES);
            byte[] encrypted = cipher(Cipher.ENCRYPT_MODE, key, nonce, context).doFinal(plaintext);
            byte[] envelope = ByteBuffer.allocate(nonce.length + encrypted.length)
                    .put(nonce).put(encrypted).array();
            return Base64.getEncoder().encodeToString(envelope);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Callsmith could not protect the secret.");
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    static String decrypt(SecretKey key, byte[] context, String value) {
        try {
            byte[] envelope = Base64.getDecoder().decode(value);
            if (envelope.length < NONCE_BYTES + TAG_BITS / Byte.SIZE) {
                throw new IllegalArgumentException();
            }
            byte[] nonce = Arrays.copyOf(envelope, NONCE_BYTES);
            byte[] plaintext = cipher(Cipher.DECRYPT_MODE, key, nonce, context)
                    .doFinal(envelope, NONCE_BYTES, envelope.length - NONCE_BYTES);
            try {
                return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(plaintext)).toString();
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
        } catch (GeneralSecurityException | IllegalArgumentException | java.nio.charset.CharacterCodingException exception) {
            throw new IllegalStateException("Callsmith could not authenticate the protected secret.");
        }
    }

    private static Cipher cipher(int operation, SecretKey key, byte[] nonce, byte[] context)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(operation, key, new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(context);
        return cipher;
    }
}
