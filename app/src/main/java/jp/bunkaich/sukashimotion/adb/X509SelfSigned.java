package jp.bunkaich.sukashimotion.adb;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import io.github.muntashirakon.adb.KeyPair;

/** Pure-JDK RSA-2048 self-signed X.509 certificate for ADB wireless pairing. */
final class X509SelfSigned {

    static final class Result {
        final PrivateKey privateKey;
        final Certificate certificate;
        Result(PrivateKey privateKey, Certificate certificate) {
            this.privateKey = privateKey;
            this.certificate = certificate;
        }
    }

    private X509SelfSigned() {}

    static Result generate() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048, new SecureRandom());
            java.security.KeyPair kp = kpg.generateKeyPair();
            byte[] spki = kp.getPublic().getEncoded();

            byte[] sha256WithRsa = {0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x0b};
            byte[] oidCn = {0x55, 0x04, 0x03};

            SimpleDateFormat sdf = new SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date notBefore = new Date(System.currentTimeMillis() - 86400_000L);
            Date notAfter = new Date(System.currentTimeMillis() + 10L * 365 * 86400_000L);
            byte[] notBeforeBytes = sdf.format(notBefore).getBytes("ASCII");
            byte[] notAfterBytes = sdf.format(notAfter).getBytes("ASCII");

            byte[] version = tlv(0xA0, tlv(0x02, new byte[]{2}));
            byte[] serial = tlv(0x02, BigInteger.probablePrime(128, new SecureRandom()).toByteArray());
            byte[] sigAlg = tlv(0x30, concat(tlv(0x06, sha256WithRsa), tlv(0x05, new byte[0])));
            byte[] name = tlv(0x30, tlv(0x31,
                    tlv(0x30, concat(tlv(0x06, oidCn), tlv(0x0C, "Folduo".getBytes("UTF-8"))))));
            byte[] validity = tlv(0x30, concat(tlv(0x17, notBeforeBytes), tlv(0x17, notAfterBytes)));

            byte[] tbs = tlv(0x30, concat(version, serial, sigAlg, name, validity, name, spki));

            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(kp.getPrivate());
            sig.update(tbs);
            byte[] sigBytes = sig.sign();

            byte[] certDer = tlv(0x30, concat(tbs, sigAlg, tlv(0x03, concat(new byte[]{0}, sigBytes))));

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(certDer));
            return new Result(kp.getPrivate(), cert);
        } catch (Exception e) {
            throw new IllegalStateException("X509 certificate generation failed", e);
        }
    }

    private static byte[] derLen(int len) {
        if (len < 0x80) return new byte[]{(byte) len};
        byte[] tmp = new byte[4];
        int n = 0;
        while (len > 0) { tmp[n++] = (byte) (len & 0xFF); len >>>= 8; }
        byte[] out = new byte[1 + n];
        out[0] = (byte) (0x80 | n);
        for (int i = 0; i < n; i++) out[1 + i] = tmp[n - 1 - i];
        return out;
    }

    private static byte[] tlv(int tag, byte[] value) {
        byte[] len = derLen(value.length);
        byte[] out = new byte[1 + len.length + value.length];
        out[0] = (byte) tag;
        System.arraycopy(len, 0, out, 1, len.length);
        System.arraycopy(value, 0, out, 1 + len.length, value.length);
        return out;
    }

    private static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] a : arrays) total += a.length;
        byte[] out = new byte[total];
        int off = 0;
        for (byte[] a : arrays) { System.arraycopy(a, 0, out, off, a.length); off += a.length; }
        return out;
    }
}
