package dev.longshot.pixel;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SetupFlowTest {
    private final Context app = RuntimeEnvironment.getApplication();

    @Test public void androidConsentComesBeforeQuickTapAndCancellationStaysInLongshot() {
        shadowOf(app.getPackageManager()).setSystemFeature("com.google.android.feature.QUICK_TAP", true);
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        activity.findViewById(R.id.start).performClick();
        var access = shadowOf(activity).getNextStartedActivityForResult();
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, access.intent.getAction());
        assertEquals(new ComponentName(app, CaptureService.class).flattenToString(),
                access.intent.getStringExtra(":settings:fragment_args_key"));
        activity.onActivityResult(access.requestCode, 0, null);
        assertNull(shadowOf(activity).getNextStartedActivityForResult());
        Settings.Secure.putString(app.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                new ComponentName(app, CaptureService.class).flattenToString());
        activity.onActivityResult(access.requestCode, 0, null);
        var next = shadowOf(activity).getNextStartedActivityForResult().intent;
        assertEquals("com.google.android.settings.gestures.QUICK_TAP_SETTINGS", next.getAction());
        assertEquals("com.android.settings", next.getPackage());
    }

    @Test public void distinguishesTheLauncherFromTheCaptureShortcutAndSkipsCompletedSetup() {
        shadowOf(app.getPackageManager()).setSystemFeature("com.google.android.feature.QUICK_TAP", true);
        var resolver = app.getContentResolver();
        var launcher = new ComponentName(app, MainActivity.class).flattenToString();
        Settings.Secure.putInt(resolver, "columbus_enabled", 1);
        Settings.Secure.putString(resolver, "columbus_action", "launch");
        Settings.Secure.putString(resolver, "columbus_launch_app", launcher);
        Settings.Secure.putString(resolver, "columbus_launch_app_shortcut", launcher);
        assertEquals(QuickTap.Status.OPENS_APP, QuickTap.status(app));
        Settings.Secure.putString(resolver, "columbus_launch_app_shortcut", CaptureShortcutActivity.ID);
        assertEquals(QuickTap.Status.CAPTURE, QuickTap.status(app));
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        activity.findViewById(R.id.start).performClick();
        var access = shadowOf(activity).getNextStartedActivityForResult();
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                new ComponentName(app, CaptureService.class).flattenToString());
        activity.onActivityResult(access.requestCode, 0, null);
        assertNull(shadowOf(activity).getNextStartedActivityForResult());
    }

    @Test public void restrictedSettingHelpOpensThisAppsDetailsThenReturnsToAccessibility() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertEquals(View.GONE, activity.findViewById(R.id.quick_tap_row).getVisibility());
        activity.findViewById(R.id.restriction_help).performClick();
        var dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        var info = shadowOf(activity).getNextStartedActivityForResult();
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, info.intent.getAction());
        assertEquals("package:dev.longshot.pixel", info.intent.getDataString());
        activity.onActivityResult(info.requestCode, 0, null);
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, shadowOf(activity).getNextStartedActivityForResult().intent.getAction());
    }
}
