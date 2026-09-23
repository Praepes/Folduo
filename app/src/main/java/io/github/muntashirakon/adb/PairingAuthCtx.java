// SPDX-License-Identifier: GPL-3.0-or-later OR Apache-2.0

package io.github.muntashirakon.adb;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import javax.security.auth.Destroyable;

import io.github.muntashirakon.crypto.spake2.Spake2Context;
import io.github.muntashirakon.crypto.spake2.Spake2Role;

import jp.bunkaich.sukashimotion.adb.CryptoCompat;

@RequiresApi(Build.VERSION_CODES.GINGERBREAD)
class PairingAuthCtx implements Destroyable {
    // The following values are taken from the following source and are subjected to change
    // https://github.com/aosp-mirror/platform_system_core/blob/android-11.0.0_r1/adb/pairing_auth/pairing_auth.cpp
    private static final byte[] CLIENT_NAME = StringCompat.getBytes("adb pair client\u0000", "UTF-8");
    private static final byte[] SERVER_NAME = StringCompat.getBytes("adb pair server\u0000", "UTF-8");

    // The following values are taken from the following source and are subjected to change
    // https://github.com/aosp-mirror/platform_system_core/blob/android-11.0.0_r1/adb/pairing_auth/aes_128_gcm.cpp
    private static final byte[] INFO = StringCompat.getBytes("adb pairing_auth aes-128-gcm key", "UTF-8");
    private static final int HKDF_KEY_LENGTH = 128 / 8;
    public static final int GCM_IV_LENGTH = 12; // in bytes

    private final byte[] mMsg;
    private final Spake2Context mSpake2Ctx;
    private final byte[] mSecretKey = new byte[HKDF_KEY_LENGTH];
    private long mDecIv = 0;
    private long mEncIv = 0;
    private boolean mIsDestroyed = false;

    @Nullable
    public static PairingAuthCtx createAlice(byte[] password) {
        Spake2Context spake25519 = new Spake2Context(Spake2Role.Alice, CLIENT_NAME, SERVER_NAME);
        try {
            return new PairingAuthCtx(spake25519, password);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return null;
        }
    }

    @VisibleForTesting
    @Nullable
    public static PairingAuthCtx createBob(byte[] password) {
        Spake2Context spake25519 = new Spake2Context(Spake2Role.Bob, SERVER_NAME, CLIENT_NAME);
        try {
            return new PairingAuthCtx(spake25519, password);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return null;
        }
    }

    private PairingAuthCtx(Spake2Context spake25519, byte[] password)
            throws IllegalArgumentException, IllegalStateException {
        mSpake2Ctx = spake25519;
        mMsg = mSpake2Ctx.generateMessage(password);
    }

    public byte[] getMsg() {
        return mMsg;
    }

    public boolean initCipher(byte[] theirMsg) throws IllegalArgumentException, IllegalStateException {
        if (mIsDestroyed) return false;
        byte[] keyMaterial = mSpake2Ctx.processMessage(theirMsg);
        if (keyMaterial == null) return false;
        byte[] derived = CryptoCompat.hkdfSha256(keyMaterial, null, INFO, mSecretKey.length);
        System.arraycopy(derived, 0, mSecretKey, 0, mSecretKey.length);
        return true;
    }

    @Nullable
    public byte[] encrypt(@NonNull byte[] in) {
        return encryptDecrypt(true, in, ByteBuffer.allocate(GCM_IV_LENGTH)
                .order(ByteOrder.LITTLE_ENDIAN).putLong(mEncIv++).array());
    }

    @Nullable
    public byte[] decrypt(@NonNull byte[] in) {
        return encryptDecrypt(false, in, ByteBuffer.allocate(GCM_IV_LENGTH)
                .order(ByteOrder.LITTLE_ENDIAN).putLong(mDecIv++).array());
    }

    @Override
    public boolean isDestroyed() {
        return mIsDestroyed;
    }

    @Override
    public void destroy() {
        mIsDestroyed = true;
        Arrays.fill(mSecretKey, (byte) 0);
        mSpake2Ctx.destroy();
    }

    @Nullable
    private byte[] encryptDecrypt(boolean forEncryption, @NonNull byte[] in, @NonNull byte[] iv) {
        if (mIsDestroyed) return null;
        return CryptoCompat.aesGcm(forEncryption, mSecretKey, iv, in);
    }
}
