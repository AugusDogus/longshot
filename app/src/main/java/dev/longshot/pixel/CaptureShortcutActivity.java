package dev.longshot.pixel;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import java.util.Collections;
import android.widget.Toast;

/** An app shortcut needs an activity target. NoDisplay leaves the underlying app window visible. */
public final class CaptureShortcutActivity extends Activity {
    static final String ID = "capture_page";

    static void publish(Context context) {
        ShortcutManager shortcuts = context.getSystemService(ShortcutManager.class);
        for (ShortcutInfo shortcut : shortcuts.getDynamicShortcuts()) {
            if (ID.equals(shortcut.getId())) return;
        }
        // Manifest shortcuts add TASK_ON_HOME, which puts the launcher underneath the capture.
        Intent capture = new Intent(context, CaptureShortcutActivity.class)
                .setAction(Intent.ACTION_VIEW).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ShortcutInfo shortcut = new ShortcutInfo.Builder(context, ID)
                .setActivity(new ComponentName(context, MainActivity.class))
                .setShortLabel(context.getString(R.string.capture_page))
                .setIcon(Icon.createWithResource(context, R.drawable.ic_capture))
                .setIntent(capture)
                .build();
        shortcuts.addDynamicShortcuts(Collections.singletonList(shortcut));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        finish();
        if (state != null) return;
        CaptureService service = CaptureService.connected;
        if (service == null) {
            Toast.makeText(this, "Open Longshot and check its accessibility connection.", Toast.LENGTH_LONG).show();
            return;
        }
        service.captureFromShortcut();
    }
}
