package dev.longshot.pixel;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.StatusBarManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;

public final class MainActivity extends Activity {
    private static final int ENABLE_SERVICE = 1;
    private static final int APP_INFO = 2;
    private Button start;
    private TextView status;
    private TextView instructions;
    private final BroadcastReceiver connectionChanges = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { render(); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        CaptureShortcutActivity.publish(this);
        getWindow().setDecorFitsSystemWindows(false);
        setContentView(R.layout.activity_main);
        findViewById(R.id.screen).setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        findViewById(R.id.illustration).setClipToOutline(true);
        findViewById(R.id.access_panel).setClipToOutline(true);
        start = findViewById(R.id.start);
        status = findViewById(R.id.status);
        instructions = findViewById(R.id.instructions);
        start.setOnClickListener(v -> {
            CaptureService service = CaptureService.connected;
            if (service == null) openAccessibility();
            else { service.arm(); finish(); }
        });
        findViewById(R.id.restriction_help).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.restricted_title)
                .setMessage(R.string.restricted_help)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.open_app_info, (dialog, which) -> openSettings(
                        new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())),
                        APP_INFO, R.string.app_info_unavailable))
                .show());
        findViewById(R.id.quick_tap_row).setOnClickListener(v -> openQuickTap());
        findViewById(R.id.add_tile).setOnClickListener(v -> addTile());
    }
    @Override protected void onResume() {
        super.onResume();
        registerReceiver(connectionChanges, new IntentFilter(CaptureService.CONNECTION_CHANGED), RECEIVER_NOT_EXPORTED);
        render();
    }
    @Override protected void onPause() {
        unregisterReceiver(connectionChanges);
        super.onPause();
    }
    private void openAccessibility() {
        // AOSP Settings scrolls to and highlights the matching service. Other settings apps may ignore the key.
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .putExtra(":settings:fragment_args_key", new ComponentName(this, CaptureService.class).flattenToString());
        openSettings(intent, ENABLE_SERVICE, R.string.accessibility_unavailable);
    }

    private void openQuickTap() {
        if (openSettings(QuickTap.settingsIntent(), -1, R.string.quick_tap_unavailable)) {
            Toast.makeText(this, R.string.quick_tap_steps, Toast.LENGTH_LONG).show();
        }
    }

    private boolean openSettings(Intent intent, int request, int fallback) {
        try {
            startActivityForResult(intent, request);
            return true;
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            new AlertDialog.Builder(this).setMessage(fallback).setPositiveButton(android.R.string.ok, null).show();
            return false;
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        boolean enabled = CaptureService.connected != null || configured();
        if (request == APP_INFO && !enabled) {
            openAccessibility();
        } else if ((request == ENABLE_SERVICE || request == APP_INFO) && enabled &&
                QuickTap.available(this) && QuickTap.status(this) != QuickTap.Status.CAPTURE) {
            openQuickTap();
        }
    }

    private boolean configured() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName ours = new ComponentName(this, CaptureService.class);
        for (String service : enabled.split(":")) {
            if (ours.equals(ComponentName.unflattenFromString(service))) return true;
        }
        return false;
    }
    private void render() {
        boolean ready = CaptureService.connected != null;
        boolean waiting = !ready && configured();
        status.setText(ready ? R.string.ready : waiting ? R.string.connecting : R.string.setup_needed);
        status.setBackgroundResource(ready ? R.drawable.status_ready : R.drawable.status_pending);
        status.setTextColor(getColor(ready ? R.color.ready_ink : R.color.muted));
        boolean quickTap = QuickTap.available(this);
        QuickTap.Status gesture = quickTap ? QuickTap.status(this) : QuickTap.Status.OTHER;
        int setupHelp = quickTap && gesture != QuickTap.Status.CAPTURE ?
                R.string.setup_with_quick_tap : R.string.setup_help;
        instructions.setText(ready ? R.string.capture_help : waiting ? R.string.connect_help : setupHelp);
        ((TextView) findViewById(R.id.heading)).setText(ready || waiting ? R.string.capture_heading : R.string.setup_heading);
        findViewById(R.id.restriction_help).setVisibility(!ready && !waiting ? View.VISIBLE : View.GONE);
        findViewById(R.id.quick_tap_row).setVisibility(quickTap ? View.VISIBLE : View.GONE);
        findViewById(R.id.quick_tap_divider).setVisibility(quickTap ? View.VISIBLE : View.GONE);
        if (quickTap) {
            int description = switch (gesture) {
                case CAPTURE -> R.string.quick_tap_ready;
                case OPENS_APP -> R.string.quick_tap_opens_app;
                case OTHER -> R.string.quick_tap_steps;
            };
            ((TextView) findViewById(R.id.quick_tap_detail)).setText(description);
        }
        start.setText(ready ? R.string.open_controls : R.string.open_settings);
    }
    private void addTile() {
        getSystemService(StatusBarManager.class).requestAddTileService(
                new ComponentName(this, CaptureTile.class), "Longshot", Icon.createWithResource(this, R.drawable.ic_capture),
                getMainExecutor(), result -> {
                    if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                        result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED)
                        Toast.makeText(this, "Longshot is in Quick Settings", Toast.LENGTH_SHORT).show();
                });
    }
}
