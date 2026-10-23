package com.dillon.starsectormarines.tools.snapshot;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.SinglePixelPackedSampleModel;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Writes an opaque snapshot still as a PNG straight from its raster.
 *
 * <p>ImageIO's PNG writer read every row through {@code Raster.getPixels}, one int per sample, and that copy was
 * half of a still's cost. For an RGB image it filters nothing: each row is filter byte 0 and the samples, the
 * whole deflated at level 4 into 32 KB IDAT chunks. This writes that same file from the frame's own bytes, so a
 * still is byte-identical to the one ImageIO wrote. Any other layout still goes through ImageIO.
 */
public final class PngStillWriter {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
    /** ImageIO's IDAT chunk length and default deflate level. */
    private static final int CHUNK = 32768;
    private static final int LEVEL = 4;

    private PngStillWriter() { }

    public static void write(BufferedImage image, Path output) throws IOException {
        byte[] rows = rgbRows(image);
        if (rows == null) {
            if (!ImageIO.write(image, "PNG", output.toFile())) throw new IOException("no PNG writer available for " + output);
            return;
        }
        int width = image.getWidth(), height = image.getHeight();
        Deflater deflater = new Deflater(LEVEL);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream(rows.length / 4 + 64);
        try {
            deflater.setInput(rows);
            deflater.finish();
            byte[] buffer = new byte[1 << 16];
            while (!deflater.finished()) compressed.write(buffer, 0, deflater.deflate(buffer));
        } finally {
            deflater.end();
        }
        byte[] data = compressed.toByteArray();
        Files.deleteIfExists(output);
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(output), 1 << 16))) {
            out.write(SIGNATURE);
            byte[] header = new byte[13];
            header[0] = (byte) (width >>> 24);
            header[1] = (byte) (width >>> 16);
            header[2] = (byte) (width >>> 8);
            header[3] = (byte) width;
            header[4] = (byte) (height >>> 24);
            header[5] = (byte) (height >>> 16);
            header[6] = (byte) (height >>> 8);
            header[7] = (byte) height;
            header[8] = 8; // bit depth
            header[9] = 2; // truecolour; compression, filter and interlace methods stay 0
            chunk(out, "IHDR", header, 0, header.length);
            for (int at = 0; at < data.length; at += CHUNK) chunk(out, "IDAT", data, at, Math.min(CHUNK, data.length - at));
            chunk(out, "IEND", data, 0, 0);
        }
    }

    private static void chunk(DataOutputStream out, String type, byte[] data, int offset, int length) throws IOException {
        byte[] name = {(byte) type.charAt(0), (byte) type.charAt(1), (byte) type.charAt(2), (byte) type.charAt(3)};
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data, offset, length);
        out.writeInt(length);
        out.write(name);
        out.write(data, offset, length);
        out.writeInt((int) crc.getValue());
    }

    /** The image as unfiltered PNG rows (a 0 then R, G, B per pixel), or null for a layout this does not read. */
    private static byte[] rgbRows(BufferedImage image) {
        int width = image.getWidth(), height = image.getHeight();
        Raster raster = image.getRaster();
        if (raster.getParent() != null || raster.getMinX() != 0 || raster.getMinY() != 0) return null;
        int stride = 1 + 3 * width;
        if (image.getType() == BufferedImage.TYPE_3BYTE_BGR
                && raster.getSampleModel() instanceof ComponentSampleModel model
                && raster.getDataBuffer() instanceof DataBufferByte bytes && bytes.getNumBanks() == 1
                && model.getPixelStride() == 3 && model.getScanlineStride() == 3 * width && bytes.getOffset() == 0) {
            int[] offsets = model.getBandOffsets();
            if (offsets.length != 3 || offsets[0] != 2 || offsets[1] != 1 || offsets[2] != 0) return null;
            byte[] data = bytes.getData();
            byte[] rows = new byte[stride * height];
            for (int y = 0; y < height; y++) {
                int from = y * 3 * width, to = y * stride + 1;
                for (int x = 0; x < width; x++, from += 3, to += 3) {
                    rows[to] = data[from + 2];
                    rows[to + 1] = data[from + 1];
                    rows[to + 2] = data[from];
                }
            }
            return rows;
        }
        if (image.getType() == BufferedImage.TYPE_INT_RGB
                && raster.getSampleModel() instanceof SinglePixelPackedSampleModel model
                && raster.getDataBuffer() instanceof DataBufferInt ints && ints.getNumBanks() == 1
                && model.getScanlineStride() == width && ints.getOffset() == 0) {
            int[] data = ints.getData();
            byte[] rows = new byte[stride * height];
            for (int y = 0; y < height; y++) {
                int from = y * width, to = y * stride + 1;
                for (int x = 0; x < width; x++, from++, to += 3) {
                    int c = data[from];
                    rows[to] = (byte) (c >> 16);
                    rows[to + 1] = (byte) (c >> 8);
                    rows[to + 2] = (byte) c;
                }
            }
            return rows;
        }
        return null;
    }
}
