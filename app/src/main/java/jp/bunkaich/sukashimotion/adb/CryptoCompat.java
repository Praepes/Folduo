package jp.bunkaich.sukashimotion.adb;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** HKDF-SHA256 and AES-128-GCM without BouncyCastle. */
public final class CryptoCompat {

    private CryptoCompat() {}

    public static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int len) {
        byte[] actualSalt = (salt != null && salt.length > 0) ? salt : new byte[32];
        try {
            byte[] prk = hmacSha256(actualSalt, ikm);
            byte[] out = new byte[len];
            byte[] t = new byte[0];
            int offset = 0, i = 1;
            while (offset < len) {
                byte[] counter = new byte[t.length + (info == null ? 0 : info.length) + 1];
                System.arraycopy(t, 0, counter, 0, t.length);
                if (info != null && info.length > 0)
                    System.arraycopy(info, 0, counter, t.length, info.length);
                counter[counter.length - 1] = (byte) i;
                t = hmacSha256(prk, counter);
                System.arraycopy(t, 0, out, offset, Math.min(t.length, len - offset));
                offset += t.length;
                i++;
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    public static byte[] aesGcm(boolean encrypt, byte[] key, byte[] iv, byte[] in) {
        if (key.length != 16) throw new IllegalArgumentException("AES-128 key must be 16 bytes");
        if (iv.length != 12) throw new IllegalArgumentException("GCM IV must be 12 bytes");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return cipher.doFinal(in);
        } catch (Exception e) {
            return null;
        }
    }
}
