package com.dillon.starsectormarines.tools.snapshot;

import java.awt.image.BufferedImage;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.SinglePixelPackedSampleModel;

/**
 * A frame's pixels as packed {@code 0xAARRGGBB} — what {@link BufferedImage#getRGB(int, int, int, int, int[], int, int)}
 * returns, read straight off the raster for the layouts snapshot frames actually come in.
 *
 * <p>{@code getRGB} asks the colour model for every channel of every pixel. On a 1920x1080 frame that was most of
 * a GIF encoder's time, and the answer for a plain 3-byte BGR or packed-int raster is just the bytes rearranged.
 * Any other layout goes through {@code getRGB} as before.
 */
public final class FramePixels {

    private FramePixels() { }

    public static int[] argb(BufferedImage image) {
        int width = image.getWidth(), height = image.getHeight();
        Raster raster = image.getRaster();
        if (raster.getParent() == null && raster.getMinX() == 0 && raster.getMinY() == 0) {
            if (image.getType() == BufferedImage.TYPE_3BYTE_BGR
                    && raster.getSampleModel() instanceof ComponentSampleModel model
                    && raster.getDataBuffer() instanceof DataBufferByte bytes && bytes.getNumBanks() == 1
                    && model.getPixelStride() == 3 && model.getScanlineStride() == 3 * width
                    && bytes.getOffset() == 0) {
                int[] offsets = model.getBandOffsets();
                if (offsets.length == 3 && offsets[0] == 2 && offsets[1] == 1 && offsets[2] == 0) {
                    byte[] data = bytes.getData();
                    int[] out = new int[width * height];
                    for (int i = 0, at = 0; i < out.length; i++, at += 3) {
                        out[i] = 0xff000000 | (data[at + 2] & 0xff) << 16 | (data[at + 1] & 0xff) << 8 | (data[at] & 0xff);
                    }
                    return out;
                }
            }
            if ((image.getType() == BufferedImage.TYPE_INT_ARGB || image.getType() == BufferedImage.TYPE_INT_RGB)
                    && raster.getSampleModel() instanceof SinglePixelPackedSampleModel model
                    && raster.getDataBuffer() instanceof DataBufferInt ints && ints.getNumBanks() == 1
                    && model.getScanlineStride() == width && ints.getOffset() == 0) {
                int[] data = ints.getData();
                int[] out = new int[width * height];
                if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
                    System.arraycopy(data, 0, out, 0, out.length);
                } else {
                    for (int i = 0; i < out.length; i++) out[i] = 0xff000000 | data[i];
                }
                return out;
            }
        }
        return image.getRGB(0, 0, width, height, null, 0, width);
    }
}
