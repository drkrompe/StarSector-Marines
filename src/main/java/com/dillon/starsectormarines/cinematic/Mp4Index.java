package com.dillon.starsectormarines.cinematic;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The video track of an mp4, read straight from its bytes: where each sample is, which are
 * sync frames, and the H.264 parameter sets.
 *
 * <p>Starsector's script loader rejects reflection, and JCodec's own container code builds every box
 * by reflection, so JCodec is used only for the H.264 decoder and this class does the demuxing. It
 * reads what a plain x264 {@code +faststart} file carries: {@code stsd/avcC}, {@code stsz},
 * {@code stsc}, {@code stco} or {@code co64}, and {@code stss}.
 */
final class Mp4Index {
    private final byte[] data;
    private int[] sampleOffset = new int[0];
    private int[] sampleSize = new int[0];
    private boolean[] sync = new boolean[0];
    private final List<ByteBuffer> sps = new ArrayList<>();
    private final List<ByteBuffer> pps = new ArrayList<>();
    private int nalLengthSize = 4;
    private boolean sawVideo;

    private Mp4Index(byte[] data) { this.data = data; }

    static Mp4Index parse(byte[] data) throws IOException {
        Mp4Index index = new Mp4Index(data);
        index.boxes(0, data.length, "");
        if (!index.sawVideo || index.sampleSize.length == 0) throw new IOException("no H.264 video track in the mp4");
        return index;
    }

    int frames() { return sampleSize.length; }
    boolean isSync(int frame) { return sync[frame]; }
    List<ByteBuffer> sps() { return sps; }
    List<ByteBuffer> pps() { return pps; }

    /** One access unit in Annex B form (start-code delimited NAL units), as JCodec's decoder reads it. */
    ByteBuffer annexB(int frame) throws IOException {
        int at = sampleOffset[frame], end = at + sampleSize[frame];
        ByteBuffer out = ByteBuffer.allocate(sampleSize[frame] + 512);
        if (sync[frame]) {
            // Parameter sets travel in the avcC, not the samples; the decoder takes them in-band.
            for (ByteBuffer set : sps) startCode(out).put(set.duplicate());
            for (ByteBuffer set : pps) startCode(out).put(set.duplicate());
        }
        while (at + nalLengthSize <= end) {
            int length = 0;
            for (int i = 0; i < nalLengthSize; i++) length = (length << 8) | (data[at + i] & 0xFF);
            at += nalLengthSize;
            if (length < 0 || at + length > end) throw new IOException("corrupt sample " + frame);
            if (out.remaining() < length + 4) {
                ByteBuffer bigger = ByteBuffer.allocate(out.capacity() * 2 + length);
                out.flip();
                bigger.put(out);
                out = bigger;
            }
            startCode(out).put(data, at, length);
            at += length;
        }
        out.flip();
        return out;
    }

    private static ByteBuffer startCode(ByteBuffer out) {
        return out.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 1);
    }

    // ---------------------------------------------------------------- boxes

    private int u32(int at) {
        return ((data[at] & 0xFF) << 24) | ((data[at + 1] & 0xFF) << 16) | ((data[at + 2] & 0xFF) << 8) | (data[at + 3] & 0xFF);
    }

    private int u16(int at) { return ((data[at] & 0xFF) << 8) | (data[at + 1] & 0xFF); }

    private long u64(int at) { return ((long) u32(at) << 32) | (u32(at + 4) & 0xFFFFFFFFL); }

    private String type(int at) {
        return new String(data, at, 4, StandardCharsets.ISO_8859_1);
    }

    /** Walks sibling boxes in [from, to); {@code path} is the container chain above them. */
    private void boxes(int from, int to, String path) throws IOException {
        int at = from;
        while (at + 8 <= to) {
            long size = u32(at) & 0xFFFFFFFFL;
            int header = 8;
            if (size == 1) {
                size = u64(at + 8);
                header = 16;
            } else if (size == 0) {
                size = to - at;
            }
            if (size < header || at + size > to) throw new IOException("corrupt box at " + at);
            String name = type(at + 4);
            int body = at + header, end = (int) (at + size);
            switch (name) {
                case "moov", "mdia", "minf", "stbl" -> boxes(body, end, path + "/" + name);
                case "trak" -> {
                    videoTrack = false;
                    syncNumbers = new int[0];
                    syncSeen = false;
                    boxes(body, end, path + "/" + name);
                }
                case "hdlr" -> { if (path.endsWith("mdia")) videoTrack = "vide".equals(type(body + 8)); }
                case "stsd" -> { if (videoTrack) sampleDescription(body); }
                case "stsz" -> { if (videoTrack) sizes(body); }
                case "stsc" -> { if (videoTrack) chunkRuns(body); }
                case "stco" -> { if (videoTrack) chunkOffsets(body, false); }
                case "co64" -> { if (videoTrack) chunkOffsets(body, true); }
                case "stss" -> { if (videoTrack) syncSamples(body); }
                default -> { }
            }
            at = end;
        }
        if (path.endsWith("stbl") && videoTrack) assemble();
    }

    private boolean videoTrack;
    private int[] runFirstChunk = new int[0], runSamples = new int[0];
    private long[] chunkOffset = new long[0];

    private void sampleDescription(int body) throws IOException {
        // version/flags, entry count, then the first entry: size, type, 78 bytes of visual sample entry.
        int entry = body + 8;
        int entryEnd = entry + u32(entry);
        int child = entry + 8 + 78;
        while (child + 8 <= entryEnd) {
            int size = u32(child);
            if (size < 8) throw new IOException("corrupt sample entry");
            if ("avcC".equals(type(child + 4))) {
                avcC(child + 8);
                sawVideo = true;
            }
            child += size;
        }
    }

    private void avcC(int at) {
        nalLengthSize = (data[at + 4] & 3) + 1;
        int count = data[at + 5] & 31;
        at += 6;
        sps.clear();
        pps.clear();
        for (int i = 0; i < count; i++) {
            int length = u16(at);
            sps.add(ByteBuffer.wrap(data, at + 2, length).slice());
            at += 2 + length;
        }
        count = data[at++] & 0xFF;
        for (int i = 0; i < count; i++) {
            int length = u16(at);
            pps.add(ByteBuffer.wrap(data, at + 2, length).slice());
            at += 2 + length;
        }
    }

    private void sizes(int body) {
        int fixed = u32(body + 4), count = u32(body + 8);
        sampleSize = new int[count];
        for (int i = 0; i < count; i++) sampleSize[i] = fixed != 0 ? fixed : u32(body + 12 + 4 * i);
    }

    private void chunkRuns(int body) {
        int count = u32(body + 4);
        runFirstChunk = new int[count];
        runSamples = new int[count];
        for (int i = 0; i < count; i++) {
            runFirstChunk[i] = u32(body + 8 + 12 * i);
            runSamples[i] = u32(body + 12 + 12 * i);
        }
    }

    private void chunkOffsets(int body, boolean wide) {
        int count = u32(body + 4);
        chunkOffset = new long[count];
        for (int i = 0; i < count; i++)
            chunkOffset[i] = wide ? u64(body + 8 + 8 * i) : u32(body + 8 + 4 * i) & 0xFFFFFFFFL;
    }

    private void syncSamples(int body) {
        int count = u32(body + 4);
        syncNumbers = new int[count];
        for (int i = 0; i < count; i++) syncNumbers[i] = u32(body + 8 + 4 * i) - 1;
        syncSeen = true;
    }

    private boolean syncSeen;
    private int[] syncNumbers = new int[0];

    private void assemble() {
        int total = sampleSize.length;
        sampleOffset = new int[total];
        int sample = 0;
        for (int chunk = 0; chunk < chunkOffset.length && sample < total; chunk++) {
            int run = runFirstChunk.length - 1;
            while (run > 0 && runFirstChunk[run] > chunk + 1) run--;
            long at = chunkOffset[chunk];
            for (int k = 0; k < runSamples[run] && sample < total; k++) {
                sampleOffset[sample] = (int) at;
                at += sampleSize[sample++];
            }
        }
        sync = new boolean[total];
        if (!syncSeen) Arrays.fill(sync, true);     // no stss: every sample is a sync sample
        for (int n : syncNumbers) if (n >= 0 && n < total) sync[n] = true;
    }
}
