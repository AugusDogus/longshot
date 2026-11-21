package dev.longshot.pixel;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.*;
import android.net.Uri;
import android.util.Size;
import android.graphics.drawable.Drawable;
import android.hardware.HardwareBuffer;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

public final class CaptureService extends AccessibilityService {
    static CaptureService connected;
    static final String CONNECTION_CHANGED = "dev.longshot.pixel.CONNECTION_CHANGED";

    private void notifyConnectionChanged() {
        sendBroadcast(new android.content.Intent(CONNECTION_CHANGED).setPackage(getPackageName()));
    }
    private enum Stage { OFF, ARMED, SCROLLING, READING, READY, FINISHED, SAVING, SAVED }
    private Stage stage = Stage.OFF;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout bar;
    private WindowManager windows;
    private Target target;
    private Frames frames;
    private Viewport previous;
    private ScrollMotion motion;
    private static final int QUIET_MS = 100;
    private long lastScroll;
    private File directory;
    private int generation, count;
    private boolean continuous;

    @Override protected void onServiceConnected() {
        windows = getSystemService(WindowManager.class);
        directory = getNoBackupFilesDir();
        if (stage == Stage.OFF) worker.execute(() -> {
            File[] stale = directory.listFiles();
            if (stale != null) for (File file : stale) if (file.getName().startsWith("rows-")) file.delete();
        });
        CaptureShortcutActivity.publish(this);
        connected = this;
        notifyConnectionChanged();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if ((stage != Stage.READY && stage != Stage.SCROLLING && stage != Stage.READING) || target == null || motion == null || e.getEventType() != AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                e.getWindowId() != target.id() || target.node() == null || !target.node().equals(e.getSource())) return;
        motion.record(e.getScrollDeltaX(), e.getScrollDeltaY(), e.getScrollY(), e.getMaxScrollY());
        lastScroll = SystemClock.uptimeMillis();
    }
    @Override public void onInterrupt() { continuous = false; }
    void arm() {
        if (stage == Stage.SAVED) dismiss();
        if (stage != Stage.OFF) return;
        frames = new Frames();
        count = 0;
        bar = new LinearLayout(this);
        bar.setBackgroundResource(R.drawable.toolbar_shell);
        bar.setElevation(dp(6));
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), dp(4), dp(4), dp(4));
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(-2, -2,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.BOTTOM | Gravity.END;
        p.x = dp(8); p.y = dp(24);
        windows.addView(bar, p);
        show(Stage.ARMED);
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private void message(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private Button control(String label, int icon, boolean primary, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(15);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setLetterSpacing(0);
        int ink = primary ? 0xff26213e : 0xfff6f2ff;
        button.setTextColor(ink);
        button.setBackgroundTintList(null);
        button.setBackgroundResource(primary ? R.drawable.toolbar_primary : R.drawable.toolbar_secondary);
        button.setStateListAnimator(null);
        button.setMinWidth(dp(48));
        button.setMinimumWidth(dp(48));
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(12), dp(4), dp(12), dp(4));
        if (icon != 0) {
            Drawable symbol = getDrawable(icon).mutate();
            symbol.setTint(ink);
            symbol.setBounds(0, 0, dp(18), dp(18));
            button.setCompoundDrawablesRelative(symbol, null, null, null);
            button.setCompoundDrawablePadding(dp(6));
        }
        button.setOnClickListener(v -> action.run());
        bar.addView(button);
        return button;
    }

    private void dismissControl(String label) {
        ImageButton close = new ImageButton(this);
        close.setImageResource(R.drawable.ic_close);
        close.setColorFilter(0xffd1cddd);
        close.setBackgroundResource(R.drawable.toolbar_secondary);
        close.setContentDescription(label);
        close.setTooltipText(label);
        close.setOnClickListener(v -> dismiss());
        bar.addView(close, dp(48), dp(48));
    }

    private void progress(String label) {
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(15);
        text.setTextColor(0xfff6f2ff);
        text.setPadding(dp(14), 0, dp(14), 0);
        text.setGravity(Gravity.CENTER);
        text.setMinHeight(dp(48));
        text.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        bar.addView(text);
    }
    private void show(Stage s) {
        stage = s;
        bar.removeAllViews();
        switch (s) {
            case ARMED -> { control("Capture", R.drawable.ic_capture, true, this::first); dismissControl("Close"); }
            case READY -> {
                Button more = control("More", R.drawable.ic_more, true, this::step);
                more.setOnLongClickListener(v -> { continuous = true; step(); return true; });
                control("Save", R.drawable.ic_save, false, this::save); dismissControl("Discard capture");
            }
            case FINISHED -> { control("Save", R.drawable.ic_save, true, this::save); dismissControl("Discard capture"); }
            case READING, SCROLLING -> {
                progress(s == Stage.SCROLLING ? "Scrolling…" : "Capturing…");
                if (continuous) control("Stop", 0, false, () -> { continuous = false; show(stage); });
            }
            case SAVING -> progress("Saving…");
            case OFF, SAVED -> {}
        }
    }
    void captureFromShortcut() {
        if (stage != Stage.OFF && stage != Stage.ARMED && stage != Stage.SAVED) return;
        arm();
        awaitPage(generation, SystemClock.uptimeMillis() + 1500);
    }

    private void awaitPage(int token, long deadline) {
        if (token != generation || stage != Stage.ARMED) return;
        if (getSystemService(android.app.KeyguardManager.class).isKeyguardLocked()) {
            dismiss();
            message("Unlock your phone to capture a page.");
            return;
        }
        AccessibilityWindowInfo page = Target.foreground(this);
        if (page != null) {
            first(page);
        } else if (SystemClock.uptimeMillis() < deadline) {
            ui.postDelayed(() -> awaitPage(token, deadline), 80);
        } else {
            message("Open the page you want, then tap Capture.");
        }
    }

    private void first() {
        AccessibilityWindowInfo w = Target.foreground(this);
        if (w == null) { message("Open the app or page you want to capture first."); return; }
        first(w);
    }
    private void first(AccessibilityWindowInfo w) {
        target = Target.select(w);
        motion = new ScrollMotion();
        previous = null;
        lastScroll = 0;
        if (target.window().isEmpty()) { message("This app has no visible window."); return; }
        show(Stage.READING);
        shoot(generation, 0, 0);
    }
    private AccessibilityWindowInfo matchingWindow() {
        AccessibilityWindowInfo w = Target.foreground(this);
        if (w == null || w.getId() != target.id()) return null;
        Rect rect = new Rect();
        w.getBoundsInScreen(rect);
        return rect.equals(target.window()) ? w : null;
    }
    private void step() {
        if (stage != Stage.READY) return;
        if (matchingWindow() == null) { finishStep("The app window changed. Save the captured portion."); return; }
        show(Stage.SCROLLING);
        if (previous == null) { finishStep("This app didn’t expose a scroll area. Save the current screenshot."); return; }
        Rect r = previous.bounds();
        Path path = new Path();
        path.moveTo(r.exactCenterX(), r.top + r.height() * 0.71f);
        path.lineTo(r.exactCenterX(), r.top + r.height() * 0.33f);
        // Hold the endpoint briefly to release with zero velocity and leave a reliable overlap.
        GestureDescription.StrokeDescription swipe = new GestureDescription.StrokeDescription(path, 0, 320, true);
        int token = generation;
        GestureResultCallback failed = new GestureResultCallback() {
            @Override public void onCancelled(GestureDescription gesture) {
                if (token == generation) finishStep("The swipe was interrupted. Save the captured portion.");
            }
        };
        boolean accepted = dispatchGesture(new GestureDescription.Builder().addStroke(swipe).build(), new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gesture) {
                if (token != generation) return;
                show(Stage.READING);
                Path hold = new Path(); hold.moveTo(r.exactCenterX(), r.top + r.height() * 0.33f);
                GestureDescription release = new GestureDescription.Builder().addStroke(swipe.continueStroke(hold, 0, 120, false)).build();
                if (!dispatchGesture(release, new GestureResultCallback() {
                    @Override public void onCompleted(GestureDescription g) { ui.postDelayed(() -> shoot(token, 0, 0), 32); }
                    @Override public void onCancelled(GestureDescription g) { failed.onCancelled(g); }
                }, ui)) failed.onCancelled(release);
            }
            @Override public void onCancelled(GestureDescription gesture) { failed.onCancelled(gesture); }
        }, ui);
        if (!accepted) failed.onCancelled(null);
    }
    private void shoot(int token, int retry, int settle) {
        if (token != generation) return;
        AccessibilityWindowInfo window = matchingWindow();
        if (window == null) { finishStep("The app window changed during capture."); return; }
        if (SystemClock.uptimeMillis() - lastScroll < QUIET_MS) {
            retryAfterMotion(token, retry, settle);
            return;
        }
        if (count == 0 && settle > 0 && target.node() == null) target = Target.select(window);
        int revision = motion.revision;
        Viewport current = Viewport.read(target);
        if (count == 0 && current == null && settle < 3) {
            // Quick Tap can restore the window before its scrollable accessibility nodes are ready.
            target = new Target(target.id(), target.window(), null);
            ui.postDelayed(() -> shoot(token, retry, settle + 1), 180);
            return;
        }
        boolean canScroll = current != null && !motion.atReportedBottom() &&
                Target.verticalScroll(target.node()) == Target.VerticalScroll.MORE;
        takeScreenshotOfWindow(target.id(), getMainExecutor(), new TakeScreenshotCallback() {
            @Override public void onFailure(int reason) {
                if (token != generation) return;
                if (reason == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT && retry < 3) {
                    ui.postDelayed(() -> shoot(token, retry + 1, settle), 450);
                } else finishStep(reason == ERROR_TAKE_SCREENSHOT_SECURE_WINDOW ? "This app protects its window from screenshots."
                        : "Android couldn’t capture this window (error " + reason + ").");
            }
            @Override public void onSuccess(ScreenshotResult shot) {
                HardwareBuffer buffer = shot.getHardwareBuffer();
                if (token != generation) { buffer.close(); return; }
                if (motion.revision != revision) {
                    buffer.close();
                    retryAfterMotion(token, retry, settle);
                    return;
                }
                if (matchingWindow() == null) {
                    buffer.close();
                    finishStep("The app window changed during capture.");
                    return;
                }
                Viewport.Evidence fromChildren = previous == null || current == null ? Viewport.Missing.INSTANCE : previous.childDistance(current);
                boolean movedOrigin = previous != null && current != null && previous.bounds().top != current.bounds().top;
                Integer distance = motion.distance(fromChildren, movedOrigin);
                motion.checkpoint();
                Frames session = frames;
                Target area = target;
                worker.execute(() -> {
                    Frames.Result result;
                    try (buffer) {
                        Bitmap gpu = Bitmap.wrapHardwareBuffer(buffer, shot.getColorSpace());
                        if (gpu == null) throw new IOException("Android returned an unreadable screenshot.");
                        Bitmap image;
                        try { image = gpu.copy(Bitmap.Config.ARGB_8888, false); }
                        finally { gpu.recycle(); }
                        if (image == null) throw new IOException("There is not enough memory for this frame.");
                        try { result = session.add(image, area.window(), current == null ? null : current.bounds(), distance, directory); } finally { image.recycle(); }
                    } catch (IOException | RuntimeException e) {
                        ui.post(() -> { if (token == generation) finishStep("Frame capture failed. " + e.getMessage()); });
                        return;
                    }
                    boolean offerMore = canScroll && session.hasScrollArea();
                    ui.post(() -> {
                        if (token != generation) return;
                        if (result != Frames.Result.ADDED) { finishStep(result.message); return; }
                        previous = current;
                        count++;
                        if (!offerMore) continuous = false;
                        show(offerMore ? Stage.READY : Stage.FINISHED);
                        if (continuous) step();
                    });
                });
            }
        });
    }
    private void retryAfterMotion(int token, int retry, int settle) {
        if (settle >= 8) { finishStep("The page kept moving during capture. Try again once it settles."); return; }
        long remaining = QUIET_MS - (SystemClock.uptimeMillis() - lastScroll);
        ui.postDelayed(() -> shoot(token, retry, settle + 1), Math.max(16, remaining));
    }
    private void finishStep(String why) {
        continuous = false;
        if (count == 0 && frames != null) {
            worker.execute(frames::close);
            frames = new Frames();
        }
        message(why);
        show(count == 0 ? Stage.ARMED : Stage.FINISHED);
    }
    private void save() {
        Stage prior = stage;
        show(Stage.SAVING);
        int token = generation;
        Frames session = frames;
        worker.execute(() -> {
            try {
                Uri uri = session.output.save(getContentResolver());
                session.close();
                ui.post(() -> { if (token == generation) showSaved(uri); });
            } catch (IOException | RuntimeException e) {
                ui.post(() -> { if (token == generation) { show(prior); message("Save failed. " + e.getMessage() + " Tap Save to retry."); } });
            }
        });
    }
    private void showSaved(Uri uri) {
        show(Stage.SAVED);
        frames = null;
        target = null;
        previous = null;
        motion = null;
        int token = generation;
        View card = LayoutInflater.from(this).inflate(R.layout.saved_screenshot, bar, false);
        bar.addView(card);
        ImageButton thumbnail = card.findViewById(R.id.preview);
        thumbnail.setOnClickListener(v -> openSaved(uri, SavedScreenshot.Action.VIEW));
        card.findViewById(R.id.edit).setOnClickListener(v -> openSaved(uri, SavedScreenshot.Action.EDIT));
        card.findViewById(R.id.share).setOnClickListener(v -> openSaved(uri, SavedScreenshot.Action.SHARE));
        card.findViewById(R.id.close).setOnClickListener(v -> dismiss());
        card.announceForAccessibility(getString(R.string.screenshot_saved));
        AccessibilityManager access = getSystemService(AccessibilityManager.class);
        int timeout = access.getRecommendedTimeoutMillis(12000, AccessibilityManager.FLAG_CONTENT_CONTROLS |
                AccessibilityManager.FLAG_CONTENT_ICONS | AccessibilityManager.FLAG_CONTENT_TEXT);
        ui.postDelayed(() -> { if (token == generation && stage == Stage.SAVED) dismiss(); }, timeout);
        worker.execute(() -> {
            try {
                Bitmap preview = getContentResolver().loadThumbnail(uri, new Size(dp(64), dp(80)), null);
                ui.post(() -> { if (token == generation && stage == Stage.SAVED) thumbnail.setImageBitmap(preview); });
            } catch (IOException | RuntimeException e) {
                // The saved image and all card actions are available even if the provider lacks a thumbnail.
                android.util.Log.w("Longshot", "Saved screenshot thumbnail unavailable", e);
            }
        });
    }

    private void openSaved(Uri uri, SavedScreenshot.Action action) {
        try {
            SavedScreenshot.open(this, uri, action);
            dismiss();
        } catch (android.content.ActivityNotFoundException | SecurityException e) {
            message("Android couldn’t open that action. Your screenshot is saved in Pictures/Longshot.");
        }
    }

    private void dismiss() {
        generation++;
        continuous = false;
        stage = Stage.OFF;
        if (bar != null) windows.removeView(bar);
        bar = null;
        if (frames != null) worker.execute(frames::close);
        frames = null;
        target = null;
        previous = null;
        motion = null;
    }
    @Override public void onDestroy() {
        if (connected == this) {
            connected = null;
            notifyConnectionChanged();
        }
        dismiss();
        super.onDestroy();
    }
}
