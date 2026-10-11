package uk.ac.wlv.journly;

import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class PasswordHasher {

    // More iterations make guessing passwords slower for an attacker
    private static final int ITERATIONS = 100000;
    private static final int KEY_LENGTH = 256; // bits

    // Random 16 byte salt, returned as text so it fits in the database
    public static String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    // Hash = PBKDF2 of the password mixed with the salt
    public static String hash(String password, String salt) {
        try {
            byte[] saltBytes = Base64.decode(salt, Base64.NO_WRAP);
            KeySpec spec = new PBEKeySpec(password.toCharArray(),
                    saltBytes, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    // Hash the typed password again and compare with the stored hash
    public static boolean verify(String password, String salt, String expectedHash) {
        String actual = hash(password, salt);
        // isEqual takes the same time however many characters match
        return MessageDigest.isEqual(actual.getBytes(), expectedHash.getBytes());
    }
}
