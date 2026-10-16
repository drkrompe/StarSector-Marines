package com.dillon.starsectormarines.tools.snapshot;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.RandomAccess;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
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
 *
 * <p><b>Compression happens off the caller's thread</b> on {@link SnapshotWorkers#background()}: flattening and
 * deflating a frame was a fifth of a scene recording's wall clock, all of it spent while the simulation waited.
 * Reads wait for their own frame, so a frame always comes back as it was added and in the order it was added. A
 * frame handed to {@link #add} must therefore not be changed afterwards. At most a few frames per worker are in
 * flight at once, so a renderer that outruns the compressors waits rather than filling the heap.
 */
public final class CompressedFrameList extends AbstractList<BufferedImage> implements RandomAccess {

    /**
     * A deflated frame, held either in {@code bytes} or, once the heap is half full, at {@code offset} in the
     * list's spill file. A long recording of a detailed map does not fit otherwise: the broadside scene's 1,800
     * frames deflate to several gigabytes, and held on the heap they ran it out of memory before its GIFs were
     * written.
     */
    private record Packed(int width, int height, byte[] bytes, long offset, int length, int rawLength) { }

    /** Heap share above which a newly compressed frame goes to disk rather than staying in memory. */
    private static final double SPILL_ABOVE = 0.5;

    private static final Cleaner CLEANER = Cleaner.create();

    /** True to send every frame to disk: the test seam for a path a test cannot otherwise reach. */
    private final boolean alwaysSpill;
    private FileChannel spill;

    public CompressedFrameList() {
        this(false);
    }

    CompressedFrameList(boolean alwaysSpill) {
        this.alwaysSpill = alwaysSpill;
    }

    /** Uncompressed frames allowed to wait for a compressor, across every list. */
    private static final Semaphore IN_FLIGHT = new Semaphore(2 * SnapshotWorkers.threads());

    private final List<Future<Packed>> packed = new ArrayList<>();

    /** Appends a frame, converting it to opaque 3-byte BGR first when it is not already. */
    @Override
    public boolean add(BufferedImage frame) {
        if (frame == null) throw new NullPointerException("frame");
        if (SnapshotWorkers.threads() <= 1) {
            packed.add(CompletableFuture.completedFuture(pack(frame)));
        } else {
            IN_FLIGHT.acquireUninterruptibly();
            try {
                packed.add(SnapshotWorkers.background().submit(() -> {
                    try {
                        return pack(frame);
                    } finally {
                        IN_FLIGHT.release();
                    }
                }));
            } catch (RuntimeException rejected) {
                IN_FLIGHT.release();
                throw rejected;
            }
        }
        modCount++;
        return true;
    }

    private Packed pack(BufferedImage frame) {
        BufferedImage opaque = frame.getType() == BufferedImage.TYPE_3BYTE_BGR ? frame : opaque(frame);
        byte[] raw = ((DataBufferByte) opaque.getRaster().getDataBuffer()).getData();
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 8 + 64);
        byte[] chunk = new byte[1 << 16];
        while (!deflater.finished()) out.write(chunk, 0, deflater.deflate(chunk));
        deflater.end();
        byte[] bytes = out.toByteArray();
        Runtime runtime = Runtime.getRuntime();
        if (alwaysSpill || runtime.totalMemory() - runtime.freeMemory() > SPILL_ABOVE * runtime.maxMemory()) {
            return new Packed(opaque.getWidth(), opaque.getHeight(), null, spill(bytes), bytes.length, raw.length);
        }
        return new Packed(opaque.getWidth(), opaque.getHeight(), bytes, -1, bytes.length, raw.length);
    }

    @Override
    public BufferedImage get(int index) {
        Packed p = packed(index);
        byte[] raw = new byte[p.rawLength()];
        Inflater inflater = new Inflater();
        inflater.setInput(p.bytes() != null ? p.bytes() : unspill(p));
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

    /** Appends {@code bytes} to this list's spill file, opening it on first use, and returns where they start. */
    private synchronized long spill(byte[] bytes) {
        try {
            if (spill == null) {
                Path file = Files.createTempFile("snapshot-frames-", ".bin");
                file.toFile().deleteOnExit();
                FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE,
                        StandardOpenOption.DELETE_ON_CLOSE);
                spill = channel;
                // The file goes when the list does, not only when the process does.
                CLEANER.register(this, () -> {
                    try {
                        channel.close();
                    } catch (IOException ignored) {
                        // Deleted at exit regardless.
                    }
                });
            }
            long offset = spill.size();
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            long at = offset;
            while (buffer.hasRemaining()) at += spill.write(buffer, at);
            return offset;
        } catch (IOException failure) {
            throw new UncheckedIOException("could not spill a frame to disk", failure);
        }
    }

    private synchronized byte[] unspill(Packed p) {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(p.length());
            long at = p.offset();
            while (buffer.hasRemaining()) {
                int read = spill.read(buffer, at);
                if (read < 0) throw new IOException("spill file ended early");
                at += read;
            }
            return buffer.array();
        } catch (IOException failure) {
            throw new UncheckedIOException("could not read a spilled frame", failure);
        }
    }

    private Packed packed(int index) {
        try {
            return packed.get(index).get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted waiting for frame " + index, interrupted);
        } catch (ExecutionException failure) {
            throw new IllegalStateException("could not compress frame " + index, failure.getCause());
        }
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
