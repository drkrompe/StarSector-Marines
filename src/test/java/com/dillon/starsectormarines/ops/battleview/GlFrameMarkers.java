package com.dillon.starsectormarines.ops.battleview;

import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.KHRDebug;

/**
 * Names each frame {@link RenderBudgetEvidence} draws as a {@code KHR_debug} group, so a GPU profiler can tell
 * them apart.
 *
 * <p>The pbuffer never presents, so a GPU profiler has no swap to cut frames at. A debug group is the frame marker
 * a GL application can give it without an NVTX binding, and with no tool attached it costs a driver call. Nsight
 * Systems 2026.4 records each push and pop on the OpenGL API row with its label, and builds no KHR range rows from
 * them; a frame there runs from one {@code glPushDebugGroup} to the next {@code glPopDebugGroup}, with the
 * {@code glFinish} that ends every frame inside it. Where the context has no {@code KHR_debug} the markers are
 * skipped.
 */
final class GlFrameMarkers {

    private static Boolean available;

    private GlFrameMarkers() { }

    static void push(String name) {
        if (available()) KHRDebug.glPushDebugGroup(KHRDebug.GL_DEBUG_SOURCE_APPLICATION, 0, name);
    }

    static void pop() {
        if (available()) KHRDebug.glPopDebugGroup();
    }

    private static boolean available() {
        if (available == null) {
            boolean has;
            try {
                has = GLContext.getCapabilities().GL_KHR_debug;
            } catch (RuntimeException | LinkageError e) {
                has = false;
            }
            available = has;
            System.out.println("[render-evidence] KHR_debug frame markers: " + (has ? "on" : "unavailable"));
        }
        return available;
    }
}
