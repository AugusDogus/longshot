package dev.longshot.pixel;

import android.content.Intent;
import android.net.Uri;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SavedScreenshotTest {
    private final Uri image = Uri.parse("content://media/external/images/media/42");

    @Test public void sharesThePublishedPngWithTemporaryReadAccess() {
        Intent chooser = SavedScreenshot.intent(image, SavedScreenshot.Action.SHARE);
        assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());
        assertTrue((chooser.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        Intent send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
        assertNotNull(send);
        assertEquals(Intent.ACTION_SEND, send.getAction());
        assertEquals("image/png", send.getType());
        assertEquals(image, send.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class));
        assertEquals(image, send.getClipData().getItemAt(0).getUri());
        assertTrue((send.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertEquals(0, send.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }

    @Test public void opensOrEditsTheSamePublishedImage() {
        Intent view = SavedScreenshot.intent(image, SavedScreenshot.Action.VIEW);
        Intent edit = SavedScreenshot.intent(image, SavedScreenshot.Action.EDIT);
        assertEquals(Intent.ACTION_VIEW, view.getAction());
        assertEquals(Intent.ACTION_EDIT, edit.getAction());
        for (Intent intent : new Intent[]{view, edit}) {
            assertEquals(image, intent.getData());
            assertEquals("image/png", intent.getType());
            assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        }
        assertEquals(0, view.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        assertTrue((edit.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0);
    }
}
