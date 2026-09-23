// SPDX-License-Identifier: GPL-3.0-or-later OR Apache-2.0

package io.github.muntashirakon.adb;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;

import javax.security.auth.DestroyFailedException;

public final class KeyPair {
    private final PrivateKey mPrivateKey;
    private final Certificate mCertificate;
    private final RSAPublicKey mPublicKey;

    public KeyPair(PrivateKey privateKey, Certificate certificate) {
        mPrivateKey = privateKey;
        mCertificate = certificate;
        mPublicKey = toRSAPublicKey(certificate.getPublicKey());
    }

    public PrivateKey getPrivateKey() {
        return mPrivateKey;
    }

    public RSAPublicKey getPublicKey() {
        return mPublicKey;
    }

    public Certificate getCertificate() {
        return mCertificate;
    }

    public void destroy() throws DestroyFailedException {
        try {
            mPrivateKey.destroy();
        } catch (NoSuchMethodError ignore) {
        }
    }

    /**
     * Conscrypt 解析证书后，getPublicKey() 可能返回 X509PublicKey（底层是 RSA 但未实现
     * RSAPublicKey 接口）。libadb 多处需 RSAPublicKey，这里统一规范化为标准实现。
     */
    private static RSAPublicKey toRSAPublicKey(PublicKey pub) {
        if (pub instanceof RSAPublicKey) {
            return (RSAPublicKey) pub;
        }
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(pub.getEncoded()));
        } catch (Exception e) {
            throw new IllegalArgumentException("证书公钥不是 RSA（或无法解析）", e);
        }
    }
}
