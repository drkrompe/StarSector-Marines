package com.dillon.starsectormarines.cinematic;

import org.jcodec.codecs.h264.H264Decoder;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Packet;
import org.jcodec.common.model.Picture;
import org.jcodec.containers.mp4.demuxer.AbstractMP4DemuxerTrack;
import org.jcodec.containers.mp4.demuxer.MP4Demuxer;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.List;

/**
 * Decodes one shot's H.264 video ahead of playback, on background threads.
 *
 * <p>H.264 decodes strictly in order, so one thread cannot keep up with 1080p24 in pure Java (about
 * 15 frames a second a thread on the game's runtime). The packager therefore cuts the video into
 * closed {@link #GOP}-frame groups that each start on an IDR frame, and workers decode different
 * groups at once. Frames are held as the decoder leaves them (YUV 4:2:0); a window bounds how far
 * ahead of the frame being shown the workers may run, which bounds memory.
 *
 * <p>Thread-safe; one monitor guards the slots. The render side never blocks on it.
 */
public final class ShotDecoder implements AutoCloseable {
    /** Frames per closed group. Must match {@code cinematic_export.py}. */
    public static final int GOP = 24;

    /** A decoded picture: JCodec planes (each sample stored as value - 128) and their row lengths. */
    public static final class Frame {
        public final byte[][] planes;
        public final int lumaStride;
        public final int chromaStride;
        Frame(byte[][] planes, int lumaStride, int chromaStride) {
            this.planes = planes;
            this.lumaStride = lumaStride;
            this.chromaStride = chromaStride;
        }
    }

    private final Callable<byte[]> source;
    private byte[] bytes;
    private final int frameCount;
    private final int width;
    private final int height;
    private final Object lock = new Object();
    private final Frame[] slots;
    private final ArrayDeque<Frame> pool = new ArrayDeque<>();
    private final List<Thread> threads = new ArrayList<>();
    private int nextGroup;
    private int consumed;          // frames below this are no longer wanted
    private int window;
    private volatile boolean closed;
    private volatile Throwable failure;

    /** {@code source} supplies the whole mp4; it is called once, on the first worker to need it. */
    public ShotDecoder(Callable<byte[]> source, int frameCount, int width, int height, int window) {
        this.source = source;
        this.frameCount = frameCount;
        this.width = width;
        this.height = height;
        this.window = Math.max(GOP, window);
        this.slots = new Frame[frameCount];
    }

    public ShotDecoder start(int workers) {
        for (int i = 0; i < workers; i++) {
            Thread thread = new Thread(this::work, "marines-cinematic-decode-" + i);
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            threads.add(thread);
            thread.start();
        }
        return this;
    }

    public int frameCount() { return frameCount; }
    public Throwable failure() { return failure; }

    /** Lets decoding run further ahead; a shot held back as a prefetch starts with one group. */
    public void setWindow(int frames) {
        synchronized (lock) {
            window = Math.max(GOP, frames);
            lock.notifyAll();
        }
    }

    /** The newest decoded frame in {@code (after, wanted]}, or null if none has arrived. */
    public Integer newestReady(int wanted, int after) {
        synchronized (lock) {
            for (int i = Math.min(wanted, frameCount - 1); i > after; i--) if (slots[i] != null) return i;
        }
        return null;
    }

    /** The decoded frame, or null. The caller must finish with it before {@link #consumed} passes it. */
    public Frame frame(int index) {
        synchronized (lock) {
            return index >= 0 && index < frameCount ? slots[index] : null;
        }
    }

    /** Frames below {@code index} are done with: their buffers are recycled and the window advances. */
    public void consumed(int index) {
        synchronized (lock) {
            for (int i = consumed; i < Math.min(index, frameCount); i++) {
                if (slots[i] != null) {
                    pool.push(slots[i]);
                    slots[i] = null;
                }
            }
            consumed = Math.max(consumed, index);
            lock.notifyAll();
        }
    }

    @Override public void close() {
        closed = true;
        synchronized (lock) {
            lock.notifyAll();
        }
        for (Thread t : threads) t.interrupt();
        synchronized (lock) {
            Arrays.fill(slots, null);
            bytes = null;
            pool.clear();
        }
    }

    private void work() {
        try (SeekableByteChannel channel = new MemoryChannel(bytes())) {
            MP4Demuxer demuxer = MP4Demuxer.createMP4Demuxer(channel);
            AbstractMP4DemuxerTrack track = (AbstractMP4DemuxerTrack) demuxer.getVideoTrack();
            DemuxerTrackMeta meta = track.getMeta();
            H264Decoder decoder = H264Decoder.createH264DecoderFromCodecPrivate(meta.getCodecPrivate());
            int alignedHeight = (height + 15) & ~15;
            while (!closed) {
                int group;
                synchronized (lock) {
                    if (nextGroup * GOP >= frameCount) return;
                    group = nextGroup++;
                }
                track.gotoFrame(group * GOP);
                for (int k = 0; k < GOP && group * GOP + k < frameCount && !closed; k++) {
                    int index = group * GOP + k;
                    Packet packet = track.nextFrame();
                    if (packet == null) throw new IOException("video ends at frame " + index + " of " + frameCount);
                    byte[][] target = acquire(width, alignedHeight);
                    Picture picture = decoder.decodeFrame(packet.getData(), target);
                    publish(index, new Frame(picture.getData(), picture.getWidth(), picture.getWidth() / 2));
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            if (!closed) {
                failure = t;
                synchronized (lock) {
                    lock.notifyAll();
                }
            }
        }
    }

    private byte[] bytes() throws Exception {
        synchronized (this) {
            if (bytes == null) bytes = source.call();
            return bytes;
        }
    }

    /** Waits until this index is inside the window, then files it; a frame already behind is recycled. */
    private void publish(int index, Frame frame) throws InterruptedException {
        synchronized (lock) {
            while (!closed && index >= consumed + window) lock.wait();
            if (closed || index < consumed) pool.push(frame);
            else slots[index] = frame;
        }
    }

    private byte[][] acquire(int w, int alignedHeight) {
        synchronized (lock) {
            Frame recycled = pool.poll();
            if (recycled != null) return recycled.planes;
        }
        return Picture.create(w, alignedHeight, ColorSpace.YUV420J).getData();
    }
}
