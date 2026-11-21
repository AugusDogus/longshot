package dev.longshot.pixel;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

final class SavedScreenshot {
    enum Action { VIEW, EDIT, SHARE }

    static Intent intent(Uri uri, Action action) {
        Intent intent = switch (action) {
            case VIEW -> new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/png");
            case EDIT -> new Intent(Intent.ACTION_EDIT).setDataAndType(uri, "image/png")
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            case SHARE -> new Intent(Intent.ACTION_SEND).setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri);
        };
        intent.setClipData(ClipData.newRawUri("Screenshot", uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (action == Action.SHARE) intent = Intent.createChooser(intent, null);
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    static void open(Context context, Uri uri, Action action) {
        Intent intent = intent(uri, action);
        if (action == Action.EDIT) {
            try {
                context.startActivity(new Intent(intent).setPackage("com.google.android.markup"));
                return;
            } catch (ActivityNotFoundException unavailable) {
                // Non-Pixel devices can handle ACTION_EDIT with their installed image editor.
            }
        }
        context.startActivity(intent);
    }
}
