package dev.longshot.pixel;

import android.graphics.Bitmap;
import android.graphics.Rect;
import java.io.File;
import java.io.IOException;

final class Frames implements AutoCloseable {
    enum Result {
        ADDED(null),
        STILL("The page stopped moving. Tap Save to finish."),
        UNMEASURED("The app didn’t expose a reliable scroll distance. Save the captured portion."),
        GAP("The page moved beyond the captured area. Save the captured portion."),
        RESIZED("The captured window changed size. Save the captured portion.");
        final String message;
        Result(String message) { this.message = message; }
    }
    CaptureFile output;
    private int previousHeight;
    private Rect previousArea;

    Result add(Bitmap frame, Rect window, Rect area, Integer distance, File directory) throws IOException {
        int height = frame.getHeight();
        if (window.height() != height || window.width() != frame.getWidth()) {
            // The initial screenshot can still be saved. A scaled window cannot use screen-pixel offsets.
            if (output != null) return Result.RESIZED;
            area = null;
        }
        int seam = 0, keep = 0;
        if (output != null) {
            if (frame.getWidth() != output.width || height != previousHeight) return Result.RESIZED;
            if (distance == null || distance < 0 || area == null || previousArea == null) return Result.UNMEASURED;
            if (distance == 0) return Result.STILL;
            if (distance >= height) return Result.GAP;
            if (area.left != previousArea.left || area.right != previousArea.right) return Result.RESIZED;
            // A row at y in the new frame was at y + distance in the previous one.
            int top = Math.max(area.top, previousArea.top - distance);
            int bottom = Math.min(area.bottom, previousArea.bottom - distance);
            if (top >= bottom) return Result.GAP;
            seam = top + (bottom - top) / 2 - window.top;
            keep = output.height - height + seam + distance;
        }
        if (output == null) output = new CaptureFile(directory, frame.getWidth());
        output.append(frame, seam, keep);
        previousHeight = height;
        previousArea = area;
        return Result.ADDED;
    }

    boolean hasScrollArea() { return previousArea != null; }

    @Override public void close() { if (output != null) output.close(); }
}
