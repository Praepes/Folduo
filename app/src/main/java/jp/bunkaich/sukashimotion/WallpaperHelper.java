package jp.bunkaich.sukashimotion;

import android.os.Bundle;

/**
 * Detects and applies the Samsung stock interactive wallpaper required for
 * fine-grained hinge angles. Uses the ShellBridge (Shizuku shell authority)
 * to query and modify wallpaper settings without a PC.
 */
final class WallpaperHelper {

    /** The internal video asset Samsung uses for the angle-reporting wallpaper. */
    static final String INNER_WALLPAPER = "video_002.mp4";
    static final String COVER_WALLPAPER = "sub_wallpaper_002";

    /** Check whether the correct interactive wallpaper is already set. */
    static Bundle checkStatus(IShellBridge bridge) {
        Bundle result = new Bundle();
        try {
            String innerDump = shellExec("dumpsys wallpaper | grep -i 'video_002\\|FoldInteractive\\|SprWallpaper'");
            String coverDump = shellExec("dumpsys wallpaper | grep -i 'sub_wallpaper_002\\|cover'");

            boolean innerOk = innerDump.contains("video_002") || innerDump.contains("FoldInteractive");
            boolean coverOk = coverDump.contains("sub_wallpaper_002");

            result.putBoolean("innerOk", innerOk);
            result.putBoolean("coverOk", coverOk);
            result.putBoolean("allOk", innerOk && coverOk);
            result.putString("innerInfo", innerDump.isEmpty() ? "Not detected" : innerDump);
            result.putString("coverInfo", coverDump.isEmpty() ? "Not detected" : coverDump);
            result.putBoolean("ok", true);
        } catch (Exception e) {
            result.putString("error", ShellBridge.message(e));
        }
        return result;
    }

    /**
     * Apply the interactive wallpaper for the inner screen using Samsung's
     * wallpaper content provider. This sets the home-screen wallpaper only.
     */
    static Bundle applyInner() {
        Bundle result = new Bundle();
        try {
            // Use Samsung's wallpaper manager to set the interactive wallpaper.
            // The asset is already installed on the device; we just activate it.
            String output = shellExec(
                "am broadcast -a com.samsung.android.wallpaper.APPLY_WALLPAPER " +
                "--es wallpaper_type interactive " +
                "--es wallpaper_name video_002 " +
                "--ei which 1 2>&1");
            result.putString("output", output);
            result.putBoolean("ok", !output.contains("Error") && !output.contains("Exception"));
        } catch (Exception e) {
            result.putString("error", ShellBridge.message(e));
        }
        return result;
    }

    /**
     * Apply the matching cover wallpaper using Samsung's WallpaperManager.
     * Equivalent to: python3 cover-wallpaper.py apply
     */
    static Bundle applyCover() {
        Bundle result = new Bundle();
        try {
            // Samsung stores cover wallpaper separately. Use the content provider
            // to copy the matching sub_wallpaper_002 to the cover home screen.
            String output = shellExec(
                "am broadcast -a com.samsung.android.wallpaper.APPLY_WALLPAPER " +
                "--es wallpaper_type static " +
                "--es wallpaper_name sub_wallpaper_002 " +
                "--ei which 16 2>&1");
            result.putString("output", output);
            result.putBoolean("ok", !output.contains("Error") && !output.contains("Exception"));
        } catch (Exception e) {
            result.putString("error", ShellBridge.message(e));
        }
        return result;
    }

    /** List available Samsung interactive wallpapers on the device. */
    static String listAvailable() {
        try {
            return shellExec(
                "ls /system/media/wallpaper/ 2>/dev/null; " +
                "ls /product/media/wallpaper/ 2>/dev/null; " +
                "ls /data/wallpaper/ 2>/dev/null");
        } catch (Exception e) {
            return ShellBridge.message(e);
        }
    }

    private static String shellExec(String command) throws Exception {
        java.lang.Process process = new ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true).start();
        try {
            if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                return "";
            }
            return new String(process.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).trim();
        } finally {
            process.destroy();
        }
    }
}
