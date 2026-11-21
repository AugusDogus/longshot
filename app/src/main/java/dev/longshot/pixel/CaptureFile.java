package dev.longshot.pixel;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/** A lossless RGB spool. Replacing the overlap only commits after the new tail is on disk. */
final class CaptureFile implements AutoCloseable {
    private record Strip(File file, int rows) {}
    private final ArrayList<Strip> strips = new ArrayList<>();
    private final File directory;
    final int width;
    int height;
    private final int rowBytes;

    CaptureFile(File directory, int width) {
        this.directory = directory;
        this.width = width;
        rowBytes = 1 + 3 * width;
    }

    void append(Bitmap image, int seam, int keep) throws IOException {
        int nextHeight = keep + image.getHeight() - seam;
        if ((long) nextHeight * rowBytes > 192L * 1024 * 1024)
            throw new IOException("Capture reached the 192 MB working-size limit. Save this image and start another.");
        File file = File.createTempFile("rows-", ".rgb", directory);
        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(file), 65536)) {
            int[] pixels = new int[width];
            byte[] row = new byte[rowBytes];
            for (int y = seam; y < image.getHeight(); y++) {
                image.getPixels(pixels, 0, width, 0, y, width, 1);
                int i = 1;
                for (int rgb : pixels) {
                    row[i++] = (byte)(rgb >> 16);
                    row[i++] = (byte)(rgb >> 8);
                    row[i++] = (byte)rgb;
                }
                output.write(row);
            }
        } catch (IOException | RuntimeException e) {
            file.delete();
            throw e;
        }
        while (height > keep) {
            Strip tail = strips.remove(strips.size() - 1);
            int start = height - tail.rows;
            if (keep > start) strips.add(new Strip(tail.file, keep - start));
            else tail.file.delete();
            height = Math.max(keep, start);
        }
        strips.add(new Strip(file, image.getHeight() - seam));
        height = nextHeight;
    }

    Uri save(ContentResolver resolver) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "Longshot-" + UUID.randomUUID().toString().substring(0, 8) + ".png");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Longshot");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("Photos storage is unavailable.");
        boolean published = false;
        try {
            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null) throw new IOException("Photos storage could not open the image.");
                writePng(out);
            }
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            if (resolver.update(uri, values, null, null) != 1) throw new IOException("Photos storage could not publish the image.");
            published = true;
            return uri;
        } finally {
            if (!published) resolver.delete(uri, null, null);
        }
    }

    void writePng(OutputStream output) throws IOException {
        DataOutputStream png = new DataOutputStream(new BufferedOutputStream(output));
        png.writeLong(0x89504e470d0a1a0aL);
        byte[] header = ByteBuffer.allocate(13).putInt(width).putInt(height).put((byte)8).put((byte)2).array();
        chunk(png, "IHDR", header, 0, header.length);
        // PNG allows one zlib stream split across arbitrarily many IDAT chunks.
        OutputStream chunks = new OutputStream() {
            @Override public void write(int b) throws IOException { write(new byte[]{(byte)b}); }
            @Override public void write(byte[] b, int off, int len) throws IOException { chunk(png, "IDAT", b, off, len); }
        };
        Deflater encoder = new Deflater(Deflater.BEST_SPEED);
        try (DeflaterOutputStream compressed = new DeflaterOutputStream(chunks, encoder, 65536)) {
            byte[] buffer = new byte[65536];
            for (Strip strip : strips) {
                try (InputStream in = Files.newInputStream(strip.file.toPath())) {
                    int remaining = strip.rows * rowBytes;
                    while (remaining > 0) {
                        int count = in.read(buffer, 0, Math.min(buffer.length, remaining));
                        if (count < 0) throw new EOFException("Longshot temporary frame is incomplete.");
                        compressed.write(buffer, 0, count);
                        remaining -= count;
                    }
                }
            }
        } finally { encoder.end(); }
        chunk(png, "IEND", new byte[0], 0, 0);
        png.flush();
    }

    private static void chunk(DataOutputStream out, String type, byte[] b, int off, int len) throws IOException {
        byte[] tag = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(tag);
        crc.update(b, off, len);
        out.writeInt(len);
        out.write(tag);
        out.write(b, off, len);
        out.writeInt((int)crc.getValue());
    }

    @Override public void close() {
        for (Strip strip : strips) strip.file.delete();
        strips.clear();
    }
}
