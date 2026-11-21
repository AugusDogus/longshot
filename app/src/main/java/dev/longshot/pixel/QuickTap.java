package dev.longshot.pixel;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/** Pixel Settings owns the gesture and asks the user to select a launcher shortcut. */
final class QuickTap {
    enum Status { CAPTURE, OPENS_APP, OTHER }

    static boolean available(Context context) {
        return context.getPackageManager().hasSystemFeature("com.google.android.feature.QUICK_TAP");
    }

    static Intent settingsIntent() {
        return new Intent("com.google.android.settings.gestures.QUICK_TAP_SETTINGS")
                .setPackage("com.android.settings");
    }

    static Status status(Context context) {
        ContentResolver resolver = context.getContentResolver();
        ComponentName launcher = new ComponentName(context, MainActivity.class);
        try {
            if (Settings.Secure.getInt(resolver, "columbus_enabled", 0) != 1 ||
                    !"launch".equals(Settings.Secure.getString(resolver, "columbus_action"))) return Status.OTHER;
            String app = Settings.Secure.getString(resolver, "columbus_launch_app");
            if (app == null || !launcher.equals(ComponentName.unflattenFromString(app))) return Status.OTHER;
            String shortcut = Settings.Secure.getString(resolver, "columbus_launch_app_shortcut");
            return CaptureShortcutActivity.ID.equals(shortcut) ? Status.CAPTURE : Status.OPENS_APP;
        } catch (SecurityException unreadable) {
            return Status.OTHER;
        }
    }
}
