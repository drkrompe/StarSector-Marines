package com.dillon.starsectormarines.cinematic;

import org.jcodec.common.io.SeekableByteChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.NonWritableChannelException;

/**
 * A read-only seekable channel over a byte array. Starsector's script loader rejects file channels,
 * so the video is read once through the settings API and each decode worker gets its own cursor
 * over the shared bytes.
 */
final class MemoryChannel implements SeekableByteChannel {
    private final byte[] bytes;
    private int position;
    private boolean open = true;

    MemoryChannel(byte[] bytes) { this.bytes = bytes; }

    @Override public int read(ByteBuffer dst) throws IOException {
        if (!open) throw new IOException("closed");
        if (position >= bytes.length) return -1;
        int n = Math.min(dst.remaining(), bytes.length - position);
        dst.put(bytes, position, n);
        position += n;
        return n;
    }

    @Override public int write(ByteBuffer src) { throw new NonWritableChannelException(); }
    @Override public long position() { return position; }
    @Override public SeekableByteChannel setPosition(long newPosition) {
        position = (int) Math.max(0, Math.min(bytes.length, newPosition));
        return this;
    }
    @Override public long size() { return bytes.length; }
    @Override public SeekableByteChannel truncate(long size) { throw new NonWritableChannelException(); }
    @Override public boolean isOpen() { return open; }
    @Override public void close() { open = false; }
}
