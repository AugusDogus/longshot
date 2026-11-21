package dev.longshot.pixel;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.BitmapFactory;
import java.io.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FramesTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private static final int WIDTH = 480, HEIGHT = 800, TOP = 64, FOOT = 30;

    @Test public void exportsEveryRowOnceWithPinnedControlsAndAnUnevenFinalStep() throws Exception {
        int[] offsets = {0, 275, 543, 816, 843};
        int finalOffset = offsets[offsets.length-1];
        Bitmap expected = Bitmap.createBitmap(WIDTH, HEIGHT + finalOffset, Bitmap.Config.ARGB_8888);
        expected.eraseColor(Color.WHITE);
        Canvas canvas = new Canvas(expected);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(15);
        paint.setColor(Color.DKGRAY);
        java.util.Random random = new java.util.Random(831);
        for (int y = TOP + 22; y < expected.getHeight() - FOOT; y += 24)
            canvas.drawText("Entry " + y + "    " + Long.toUnsignedString(random.nextLong(), 36) +
                    "    " + Long.toUnsignedString(random.nextLong(), 36), 20, y, paint);
        paint.setColor(Color.rgb(32, 61, 42)); canvas.drawRect(0, 0, WIDTH, TOP, paint);
        paint.setColor(Color.GRAY); canvas.drawRect(0, expected.getHeight()-FOOT, WIDTH, expected.getHeight(), paint);

        Rect window = new Rect(0, 0, WIDTH, HEIGHT);
        Rect viewport = new Rect(0, TOP, WIDTH, HEIGHT-FOOT);
        int previous = 0;
        try (Frames frames = new Frames()) {
            for (int offset : offsets) {
                Bitmap view = viewport(expected, offset);
                try { assertEquals("Scroll offset " + offset, Frames.Result.ADDED, frames.add(view, window, viewport, offset - previous, temp.getRoot())); }
                finally { view.recycle(); }
                previous = offset;
            }
            Bitmap end = viewport(expected, finalOffset);
            try { assertEquals(Frames.Result.STILL, frames.add(end, window, viewport, 0, temp.getRoot())); }
            finally { end.recycle(); }
            Bitmap unrelated = viewport(expected, 0);
            try {
                assertEquals(Frames.Result.UNMEASURED, frames.add(unrelated, window, viewport, null, temp.getRoot()));
                assertEquals(Frames.Result.GAP, frames.add(unrelated, window, viewport, HEIGHT, temp.getRoot()));
            } finally { unrelated.recycle(); }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            frames.output.writePng(output);
            byte[] png = output.toByteArray();
            Bitmap actual = BitmapFactory.decodeByteArray(png, 0, png.length);
            assertEquals(expected.getHeight(), actual.getHeight());
            assertEquals(WIDTH, actual.getWidth());
            int[] want = new int[WIDTH], got = new int[WIDTH];
            for (int y = 0; y < expected.getHeight(); y++) {
                expected.getPixels(want, 0, WIDTH, 0, y, WIDTH, 1);
                actual.getPixels(got, 0, WIDTH, 0, y, WIDTH, 1);
                assertArrayEquals("Decoded PNG row " + y, want, got);
            }
        }
        assertEquals(0, temp.getRoot().listFiles().length);
    }

    private static Bitmap viewport(Bitmap expected, int offset) {
        int[] pixels = new int[WIDTH * HEIGHT];
        expected.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, TOP);
        expected.getPixels(pixels, TOP*WIDTH, WIDTH, 0, TOP+offset, WIDTH, HEIGHT-TOP-FOOT);
        expected.getPixels(pixels, (HEIGHT-FOOT)*WIDTH, WIDTH, 0, expected.getHeight()-FOOT, WIDTH, FOOT);
        return Bitmap.createBitmap(pixels, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
    }
}
