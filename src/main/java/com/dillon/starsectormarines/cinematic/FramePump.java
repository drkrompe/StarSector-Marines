package com.dillon.starsectormarines.cinematic;

import com.dillon.starsectormarines.cinematic.ShotDecoder.Frame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Hands the render thread finished RGB pictures without making it wait. A converter thread watches
 * the frame the clock wants, takes the newest decoded frame at or before it, converts it into one of
 * a few direct buffers and posts it. The render thread takes whatever is posted, uploads it and
 * gives the buffer back. If decoding falls behind, the picture holds and the clock is not touched.
 */
final class FramePump implements AutoCloseable {
    private static final int SLOTS = 3;
    private static final int FREE = 0, FILLING = 1, READY = 2, UPLOADING = 3;

    private final int width;
    private final int height;
    private final ByteBuffer[] buffers = new ByteBuffer[SLOTS];
    private final int[] state = new int[SLOTS];
    private final int[] index = new int[SLOTS];
    private final Object lock = new Object();
    private final Thread thread;
    private ShotDecoder decoder;
    private int wanted;
    private int lastConverted = -1;
    private volatile boolean closed;

    FramePump(int width, int height) {
        this.width = width;
        this.height = height;
        for (int i = 0; i < SLOTS; i++)
            buffers[i] = ByteBuffer.allocateDirect(width * height * 3).order(ByteOrder.nativeOrder());
        thread = new Thread(this::run, "marines-cinematic-convert");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.start();
    }

    /** Points the pump at a shot's decoder and rewinds; the previous decoder is not closed here. */
    void attach(ShotDecoder next) {
        synchronized (lock) {
            decoder = next;
            wanted = 0;
            lastConverted = -1;
            for (int i = 0; i < SLOTS; i++) if (state[i] == READY) state[i] = FREE;
            lock.notifyAll();
        }
    }

    void request(int frame) {
        synchronized (lock) {
            if (frame != wanted) {
                wanted = frame;
                lock.notifyAll();
            }
        }
    }

    /** A finished picture the render thread has not seen, or -1. Pair with {@link #done}. */
    int take() {
        synchronized (lock) {
            int best = -1;
            for (int i = 0; i < SLOTS; i++)
                if (state[i] == READY && (best < 0 || index[i] > index[best])) best = i;
            for (int i = 0; i < SLOTS; i++) if (state[i] == READY && i != best) state[i] = FREE;
            if (best >= 0) state[best] = UPLOADING;
            lock.notifyAll();
            return best;
        }
    }

    ByteBuffer buffer(int slot) {
        ByteBuffer b = buffers[slot];
        b.clear();
        return b;
    }

    int frameOf(int slot) { return index[slot]; }

    void done(int slot) {
        synchronized (lock) {
            state[slot] = FREE;
            lock.notifyAll();
        }
    }

    boolean failed() {
        synchronized (lock) {
            return decoder != null && decoder.failure() != null;
        }
    }

    @Override public void close() {
        closed = true;
        synchronized (lock) {
            lock.notifyAll();
        }
        thread.interrupt();
    }

    private void run() {
        try {
            while (!closed) {
                ShotDecoder source;
                int pick = -1, slot = -1;
                Frame frame = null;
                synchronized (lock) {
                    source = decoder;
                    if (source != null) {
                        for (int i = 0; i < SLOTS && slot < 0; i++) if (state[i] == FREE) slot = i;
                        Integer ready = slot < 0 ? null : source.newestReady(wanted, lastConverted);
                        if (ready != null) {
                            pick = ready;
                            frame = source.frame(pick);
                            state[slot] = FILLING;
                        }
                    }
                    if (frame == null) {
                        lock.wait(8);
                        continue;
                    }
                }
                ByteBuffer out = buffer(slot);
                YuvToRgb.convert(frame.planes[0], frame.lumaStride, frame.planes[1], frame.planes[2],
                        frame.chromaStride, width, height, out);
                out.flip();
                synchronized (lock) {
                    if (decoder == source) {
                        state[slot] = READY;
                        index[slot] = pick;
                        lastConverted = pick;
                        source.consumed(pick);
                    } else {
                        state[slot] = FREE;
                    }
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
