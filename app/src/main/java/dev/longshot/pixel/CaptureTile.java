package dev.longshot.pixel;

import android.app.PendingIntent;
import android.content.Intent;
import android.service.quicksettings.TileService;

public final class CaptureTile extends TileService {
    @Override public void onClick() {
        unlockAndRun(() -> {
            CaptureService service = CaptureService.connected;
            if (service == null) {
                Intent setup = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, setup, PendingIntent.FLAG_IMMUTABLE));
            } else {
                service.arm();
            }
        });
    }
}
