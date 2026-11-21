package dev.longshot.pixel;

import android.content.ComponentName;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ConnectionScreenTest {
    @Test public void enabledServiceReconnectingUpdatesTheOpenScreen() {
        var app = RuntimeEnvironment.getApplication();
        var component = new ComponentName(app, CaptureService.class);
        Settings.Secure.putString(app.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                "other.app/.OtherService:" + component.flattenToShortString());
        var controller = Robolectric.buildActivity(MainActivity.class).setup();
        var service = Robolectric.buildService(CaptureService.class).create();
        try {
            View screen = controller.get().findViewById(android.R.id.content);
            assertTrue(contains(screen, "Accessibility is enabled"));
            service.get().onServiceConnected();
            shadowOf(Looper.getMainLooper()).idle();
            assertTrue(contains(screen, "Show capture controls"));
        } finally {
            controller.pause().stop().destroy();
            service.destroy();
        }
    }

    private boolean contains(View view, String text) {
        if (view instanceof TextView label) return label.getText().toString().contains(text);
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) if (contains(group.getChildAt(i), text)) return true;
        }
        return false;
    }
}
