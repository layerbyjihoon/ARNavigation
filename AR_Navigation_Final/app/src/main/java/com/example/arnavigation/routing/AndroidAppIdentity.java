package com.example.arnavigation.routing;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Resolves Android-restricted REST API identity headers from the installed APK. */
final class AndroidAppIdentity {
    private AndroidAppIdentity() {}

    static String packageName(Context context) {
        return context.getPackageName();
    }

    @SuppressWarnings("deprecation")
    static String sha1CertificateFingerprint(Context context) {
        try {
            PackageManager manager = context.getPackageManager();
            PackageInfo info;
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info = manager.getPackageInfo(
                        context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                if (info.signingInfo == null) return "";
                signatures = info.signingInfo.getApkContentsSigners();
            } else {
                info = manager.getPackageInfo(
                        context.getPackageName(), PackageManager.GET_SIGNATURES);
                signatures = info.signatures;
            }
            if (signatures == null || signatures.length == 0) return "";
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(signatures[0].toByteArray());
            StringBuilder fingerprint = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                fingerprint.append(String.format(Locale.ROOT, "%02X", value & 0xff));
            }
            return fingerprint.toString();
        } catch (PackageManager.NameNotFoundException | NoSuchAlgorithmException error) {
            return "";
        }
    }
}
