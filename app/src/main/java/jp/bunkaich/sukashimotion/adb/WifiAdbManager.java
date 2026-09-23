package jp.bunkaich.sukashimotion.adb;

import android.content.Context;
import android.os.Build;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyFactory;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;

import io.github.muntashirakon.adb.AdbConnection;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.KeyPair;
import io.github.muntashirakon.adb.PairingConnectionCtx;

/**
 * On-device WiFi ADB pairing and connection.
 * After pairing, the device trusts this app's RSA key for future connections.
 * The ADB connection can then start Shizuku without a PC.
 */
public final class WifiAdbManager {
    public static final String DEVICE_NAME = "Folduo";

    private static final String KEY_FILE = "adb_key.p8";
    private static final String CERT_FILE = "adb_cert.der";

    private final File keyFile;
    private final File certFile;
    private KeyPair keyPair;

    public WifiAdbManager(Context context) {
        File dir = context.getFilesDir();
        keyFile = new File(dir, KEY_FILE);
        certFile = new File(dir, CERT_FILE);
    }

    public synchronized KeyPair loadOrCreateKeyPair() throws Exception {
        if (keyPair != null) return keyPair;
        if (keyFile.exists() && certFile.exists()) {
            try { return keyPair = loadKeyPair(); }
            catch (Exception e) { keyFile.delete(); certFile.delete(); }
        }
        X509SelfSigned.Result r = X509SelfSigned.generate();
        save(new FileOutputStream(keyFile), r.privateKey.getEncoded());
        save(new FileOutputStream(certFile), r.certificate.getEncoded());
        return keyPair = new KeyPair(r.privateKey, r.certificate);
    }

    private KeyPair loadKeyPair() throws Exception {
        byte[] pkBytes = readAll(keyFile);
        byte[] certBytes = readAll(certFile);
        java.security.PrivateKey pk = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(pkBytes));
        Certificate cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(certBytes));
        return new KeyPair(pk, cert);
    }

    /** Pair with the device's own adbd using the 6-digit pairing code. */
    public void pair(String host, int port, String code) throws Exception {
        KeyPair kp = loadOrCreateKeyPair();
        PairingConnectionCtx pcc = new PairingConnectionCtx(
                host, port, code.getBytes("UTF-8"), kp, DEVICE_NAME);
        try { pcc.start(); } finally { pcc.close(); }
    }

    /** Connect to adbd's main wireless debugging port. */
    public AdbConnection connect(String host, int port) throws Exception {
        KeyPair kp = loadOrCreateKeyPair();
        AdbConnection conn = new AdbConnection.Builder(host, port)
                .setPrivateKey(kp.getPrivateKey())
                .setCertificate(kp.getCertificate())
                .setApi(Build.VERSION.SDK_INT)
                .build();
        boolean ok = conn.connect(15_000, java.util.concurrent.TimeUnit.MILLISECONDS, true);
        if (!ok) { conn.close(); throw new java.io.IOException("ADB connection timed out"); }
        return conn;
    }

    /** Execute a shell command over ADB and return the output. */
    public static String shell(AdbConnection conn, String command) throws Exception {
        AdbStream stream = conn.open("shell:" + command);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            byte[] buf = new byte[4096];
            int n;
            while ((n = stream.read(buf, 0, buf.length)) >= 0) out.write(buf, 0, n);
        } finally { stream.close(); }
        return out.toString("UTF-8").trim();
    }

    /** Start Shizuku via ADB shell command. */
    public static String startShizuku(AdbConnection conn) throws Exception {
        return shell(conn,
            "sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh " +
            "|| sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh " +
            "|| app_process -Djava.class.path=$(pm path moe.shizuku.privileged.api | head -1 | cut -d: -f2) / moe.shizuku.server.ShizukuService");
    }

    public boolean hasSavedKeys() { return keyFile.exists() && certFile.exists(); }

    private static void save(FileOutputStream fos, byte[] data) throws Exception {
        try { fos.write(data); } finally { fos.close(); }
    }

    private static byte[] readAll(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int off = 0, n;
            while ((n = in.read(buf, off, buf.length - off)) > 0) off += n;
            return buf;
        }
    }
}
