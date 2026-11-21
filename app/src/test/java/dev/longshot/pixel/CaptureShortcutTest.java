package dev.longshot.pixel;

import android.content.Intent;
import android.content.pm.ShortcutManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CaptureShortcutTest {
    @Test public void quickTapReturnsToPreviousPageAndKeepsLauncherEntrySeparate() {
        var context = RuntimeEnvironment.getApplication();
        CaptureShortcutActivity.publish(context);
        CaptureShortcutActivity.publish(context);
        var shortcuts = context.getSystemService(ShortcutManager.class).getDynamicShortcuts();
        assertEquals(1, shortcuts.size());
        var shortcut = shortcuts.get(0);
        assertEquals(MainActivity.class.getName(), shortcut.getActivity().getClassName());
        Intent target = shortcut.getIntent();
        assertEquals(CaptureShortcutActivity.class.getName(), target.getComponent().getClassName());
        assertEquals(0, target.getFlags() & (Intent.FLAG_ACTIVITY_TASK_ON_HOME | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
}
