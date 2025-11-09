package com.example.brightbuds_app.utils;

import android.util.Base64;
import android.util.Log;

import com.example.brightbuds_app.BuildConfig;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * EncryptionUtil
 * -----------------------
 * Provides AES-256 encryption and decryption for sensitive fields like name, gender, and displayName.
 * Uses PBKDF2 for key derivation and random IV for strong encryption.
 *
 * The secret key is read from an environment variable `BRIGHTBUDS_KEY`
 * so that no sensitive keys are hardcoded in source control.
 */
public class EncryptionUtil {

    private static final String TAG = "EncryptionUtil";
    private static final String AES_MODE = "AES/CBC/PKCS5Padding";
    private static final String SALT = "brightbuds_salt_value"; // non-sensitive static salt

    /** Derive AES key from passphrase */
    private static SecretKeySpec generateKey() throws Exception {
        String keyString = BuildConfig.BRIGHTBUDS_KEY;
        if (keyString == null || keyString.isEmpty()) {
            throw new IllegalStateException("Missing BRIGHTBUDS_KEY environment variable");
        }

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(keyString.toCharArray(), SALT.getBytes(StandardCharsets.UTF_8), 65536, 256);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    public static String encrypt(String data) {
        if (data == null) return "";
        try {
            Cipher cipher = Cipher.getInstance(AES_MODE);
            SecretKeySpec key = generateKey();

            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.encodeToString(combined, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "❌ Encryption failed: " + e.getMessage());
            return "";
        }
    }

    public static String decrypt(String base64Data) {
        if (base64Data == null || base64Data.trim().isEmpty()) return "";
        try {
            byte[] combined = Base64.decode(base64Data, Base64.DEFAULT);
            if (combined.length < 17) return "";

            byte[] iv = new byte[16];
            byte[] encrypted = new byte[combined.length - 16];
            System.arraycopy(combined, 0, iv, 0, 16);
            System.arraycopy(combined, 16, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(AES_MODE);
            SecretKeySpec key = generateKey();
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "❌ Decryption failed: " + e.getMessage());
            return "";
        }
    }
}
