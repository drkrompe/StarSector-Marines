package com.dillon.starsectormarines.render2d;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

/**
 * One vertex attribute's client-side array: owns the cached direct
 * {@link FloatBuffer} that {@link #upload} copies a {@code float[]} into, and
 * hands back a buffer ready to bind with {@code gl*Pointer}. The batchers
 * ({@link QuadBatch}, {@link SolidQuadBatch}, {@link LineBatch}) hold one per
 * attribute.
 *
 * <p><strong>The contract every {@code gl*Pointer} call in this package must
 * honor: one tightly-packed buffer per attribute, {@code stride == 0}, bound at
 * {@code position() == 0}, with {@code capacity()} equal to the float count
 * actually in use.</strong> Interleaving several attributes into one buffer and
 * binding them at different offsets with a non-zero stride draws identically on
 * vanilla LWJGL, but is <em>not</em> portable across async-renderer bridge mods:
 * genir's Fast Rendering (source: {@code github.com/Halke1986/starsector-render})
 * replaces {@code org.lwjgl.opengl.GL11} with a deferred command pipeline that
 * must snapshot client arrays before a draw can be queued.
 *
 * <p>Two independent constraints come out of that, and violating either one is
 * fatal rather than merely slow:
 *
 * <ul>
 *   <li><strong>Packing.</strong> Its {@code ClientAttribTracker} asserts
 *       {@code stride == 0} and {@code position() == 0} on every pointer call
 *       (and {@code size == 2} for texcoords). {@code Debug.asert} throws an
 *       unconditional {@code AssertionError} — not gated by {@code -ea} or any
 *       config flag — so an interleaved layout hard-crashes on the first flush.
 *   <li><strong>Capacity.</strong> Its {@code BufferPool} buckets and allocates
 *       by the handed-in buffer's {@code capacity()} ({@code poolIdx(capacity)},
 *       then {@code new FloatBufferSnapshot(1 << idx)}) while copying only
 *       {@code limit()}. A buffer sized for a batch's peak therefore costs a
 *       full peak-sized <em>direct</em> allocation on every draw, however few
 *       quads it holds — and the pool keeps them live until each queued draw
 *       executes. {@code urbanBatch} alone (16384-quad capacity → 2^18 color
 *       floats) burns 1 MB per flush that way, which is how the 4 GB direct
 *       buffer limit gets exhausted mid-battle.
 * </ul>
 *
 * <p>So {@link #upload} returns a view whose capacity is exactly the live float
 * count, slicing the cache when it is larger. The cache itself still grows
 * monotonically — resizing direct memory per flush would be far worse than the
 * small slice object.
 *
 * <p>Buffer reuse across flushes is safe under that bridge: it snapshots on the
 * calling thread while building the command, before enqueueing, so refilling the
 * cache for the next flush cannot corrupt a pending draw.
 *
 * <p>NOT thread-safe — same as its owning batcher.
 */
final class ClientArray {

    private FloatBuffer buf;

    /**
     * Copy the first {@code floats} elements of {@code src} into the cached
     * direct buffer and return it ready to bind: position 0, and capacity equal
     * to {@code floats} so the bridge's pool allocates no more than the data
     * actually drawn.
     */
    FloatBuffer upload(float[] src, int floats) {
        if (buf == null || buf.capacity() < floats) {
            buf = BufferUtils.createFloatBuffer(src.length);
        }
        buf.clear();
        buf.put(src, 0, floats);
        buf.flip();
        return buf.capacity() == floats ? buf : buf.slice();
    }
}
