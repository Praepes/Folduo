package jp.bunkaich.sukashimotion;

import android.os.Build;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Detects supported Galaxy Z Fold models by model string pattern rather than a
 * single hard-coded value. Display capability is still validated at runtime in
 * {@link DualDisplayControl}; this gate only prevents obviously incompatible
 * devices from starting the service.
 */
final class DeviceCompat {
    // SM-F966B, SM-F966Z, SM-F9660 (China), SM-F966U, SM-F966N, etc.
    private static final Pattern FOLD7 = Pattern.compile("^SM-F966[0-9A-Z]$");
    // Galaxy Z Fold8 Ultra: SM-F968B, SM-F9680, etc.
    private static final Pattern FOLD8U = Pattern.compile("^SM-F968[0-9A-Z]$");

    private static final Set<Pattern> SUPPORTED = Set.of(FOLD7, FOLD8U);

    /** True when the device model matches a known foldable family. */
    static boolean isSupported() {
        return isSupported(Build.MODEL);
    }

    static boolean isSupported(String model) {
        if (model == null) return false;
        for (Pattern p : SUPPORTED) {
            if (p.matcher(model).matches()) return true;
        }
        return false;
    }

    /** True when the device is a China-mainland variant (SM-F9660, SM-F9680). */
    static boolean isChinaVariant() {
        return Build.MODEL != null && Build.MODEL.endsWith("0");
    }

    /** A human-readable device family name. */
    static String familyName() {
        if (Build.MODEL == null) return "Unknown";
        if (FOLD7.matcher(Build.MODEL).matches()) return "Galaxy Z Fold7";
        if (FOLD8U.matcher(Build.MODEL).matches()) return "Galaxy Z Fold8 Ultra";
        return Build.MODEL;
    }
}
