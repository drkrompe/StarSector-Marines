package com.dillon.starsectormarines.tools.snapshot;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayOutputStream;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.RandomAccess;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * An append-only list of opaque frames that keeps each one deflated and inflates it only when read.
 *
 * <p>A recorded battle is hundreds of 1920x1080 frames, six megabytes apiece uncompressed, and the review
 * frames are mostly flat ground, so they deflate by an order of magnitude. Holding them this way is what
 * lets a long scene wait for its encoder without needing a heap the size of the recording.
 * {@link SnapshotArtifact} keeps an instance of this class as it is rather than copying it out.
 */
public final class CompressedFrameList extends AbstractList<BufferedImage> implements RandomAccess {

    private record Packed(int width, int height, byte[] bytes, int rawLength) { }

    private final List<Packed> packed = new ArrayList<>();

    /** Appends a frame, converting it to opaque 3-byte BGR first when it is not already. */
    @Override
    public boolean add(BufferedImage frame) {
        BufferedImage opaque = frame.getType() == BufferedImage.TYPE_3BYTE_BGR ? frame : opaque(frame);
        byte[] raw = ((DataBufferByte) opaque.getRaster().getDataBuffer()).getData();
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 8 + 64);
        byte[] chunk = new byte[1 << 16];
        while (!deflater.finished()) out.write(chunk, 0, deflater.deflate(chunk));
        deflater.end();
        packed.add(new Packed(opaque.getWidth(), opaque.getHeight(), out.toByteArray(), raw.length));
        modCount++;
        return true;
    }

    @Override
    public BufferedImage get(int index) {
        Packed p = packed.get(index);
        byte[] raw = new byte[p.rawLength()];
        Inflater inflater = new Inflater();
        inflater.setInput(p.bytes());
        try {
            int filled = 0;
            while (filled < raw.length && !inflater.finished()) filled += inflater.inflate(raw, filled, raw.length - filled);
            if (filled != raw.length) throw new IllegalStateException("truncated frame " + index);
        } catch (DataFormatException failure) {
            throw new IllegalStateException("corrupt frame " + index, failure);
        } finally {
            inflater.end();
        }
        BufferedImage frame = new BufferedImage(p.width(), p.height(), BufferedImage.TYPE_3BYTE_BGR);
        System.arraycopy(raw, 0, ((DataBufferByte) frame.getRaster().getDataBuffer()).getData(), 0, raw.length);
        return frame;
    }

    @Override
    public int size() {
        return packed.size();
    }

    private static BufferedImage opaque(BufferedImage frame) {
        BufferedImage opaque = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        var graphics = opaque.createGraphics();
        graphics.drawImage(frame, 0, 0, null);
        graphics.dispose();
        return opaque;
    }
}
