package com.dillon.starsectormarines.render;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;

import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT24;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Owns the offscreen framebuffer, the camera, and the root scene node; walks the scene
 * each frame into the FBO, then blits the FBO over the panel rectangle.
 *
 * Isolates 3D GL state from Starsector's UI render pass via {@code glPushAttrib} + matrix
 * push-pop. Texture binding and viewport are restored by {@code glPopAttrib}; the bindings
 * outside the attrib stack (shader program, array/element buffers) restore to the
 * fixed-function defaults the UI pass runs under, and the inherited draw-FBO + viewport are
 * sampled once and cached ({@link #uiFboBinding}). No per-frame {@code glGet*} — that
 * readback stalls async-renderer bridge mods; see the async-renderer-bridge-glget-stall note.
 *
 * See [[gl-state-gotchas]] in project memory for the specific landmines this isolates against.
 */
public class BridgeRenderer {

    /** Diagnostic: draw 4 colored quadrants into the FBO instead of the scene. */
    private static final boolean DEBUG_QUADRANTS = false;

    /** Diagnostic: skip the FBO entirely; draw the 4 quadrants directly at the panel rect. */
    private static final boolean DEBUG_DIRECT_QUADRANTS = false;

    private static final Logger LOG = Global.getLogger(BridgeRenderer.class);

    private boolean diagLogged;
    private boolean fboPixelLogged;

    private boolean initialized;
    private boolean broken;

    private int fbo;
    private int fboColor;
    private int fboDepth;
    private int fboWidth;
    private int fboHeight;

    /**
     * Inherited UI draw-FBO + viewport, cached instead of read back each frame:
     * a {@code glGet*} forces a synchronous round-trip that stalls async-renderer
     * bridge mods (e.g. genir's). The viewport is in framebuffer px, so we
     * re-sample whenever the drawable size changes (window resize / fullscreen
     * toggle), keyed off {@link Display#getWidth()}/{@link Display#getHeight()} —
     * cached LWJGL values, NOT a GL readback. {@code uiSampledFbW < 0} = unsampled.
     */
    private int uiSampledFbW = -1;
    private int uiSampledFbH = -1;
    private int uiFboBinding = -1;
    private int uiVpX, uiVpY, uiVpW, uiVpH;

    private SceneNode sceneRoot;
    private Camera    camera;

    /**
     * Hand the renderer an externally-built scene + camera. The renderer doesn't
     * own scene state; the caller ticks nodes per-frame and the renderer walks
     * whatever's there at draw time. Pass {@code null} to clear.
     */
    public void setScene(SceneNode sceneRoot, Camera camera) {
        this.sceneRoot = sceneRoot;
        this.camera = camera;
    }

    public void render(float panelX, float panelY, float panelW, float panelH,
                       float alphaMult) {
        if (broken) return;

        if (DEBUG_DIRECT_QUADRANTS) {
            renderDirectQuadrants(panelX, panelY, panelW, panelH);
            if (!diagLogged) {
                diagLogged = true;
                LOG.info(String.format(
                        "BridgeRenderer direct-quadrant render: panel xy=(%.1f, %.1f) wh=(%.1f, %.1f), alpha=%.2f",
                        panelX, panelY, panelW, panelH, alphaMult));
            }
            return;
        }

        initIfNeeded();
        if (broken) return;

        int pxW = Math.max(1, Math.round(panelW * scaleX()));
        int pxH = Math.max(1, Math.round(panelH * scaleY()));
        ensureFbo(pxW, pxH);
        if (broken) return;

        if (!diagLogged) {
            diagLogged = true;
            LOG.info(String.format(
                    "BridgeRenderer first render: panel xy=(%.1f, %.1f) wh=(%.1f, %.1f), fbo=%dx%d, scale=(%.2f, %.2f), display=%dx%d, settings=%.0fx%.0f, alpha=%.2f",
                    panelX, panelY, panelW, panelH,
                    pxW, pxH,
                    scaleX(), scaleY(),
                    Display.getWidth(), Display.getHeight(),
                    Global.getSettings().getScreenWidth(), Global.getSettings().getScreenHeight(),
                    alphaMult));
        }

        // ---- Save Starsector UI GL state ----
        // Sample the inherited draw-FBO + viewport once per drawable size (re-
        // sampled on resize); we restore program/buffer bindings to fixed-function
        // defaults and let glPopAttrib restore texture + viewport. No per-frame
        // glGet*, which would stall async-renderer bridge mods (e.g. genir's).
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        glMatrixMode(GL_PROJECTION); glPushMatrix();
        glMatrixMode(GL_MODELVIEW);  glPushMatrix();
        glMatrixMode(GL_TEXTURE);    glPushMatrix();

        int fbW = Display.getWidth(), fbH = Display.getHeight();
        if (fbW != uiSampledFbW || fbH != uiSampledFbH) {
            uiFboBinding = glGetInteger(GL_FRAMEBUFFER_BINDING);
            IntBuffer vpBuf = BufferUtils.createIntBuffer(16);
            glGetInteger(GL_VIEWPORT, vpBuf);
            uiVpX = vpBuf.get(0); uiVpY = vpBuf.get(1); uiVpW = vpBuf.get(2); uiVpH = vpBuf.get(3);
            uiSampledFbW = fbW; uiSampledFbH = fbH;
        }

        try {
            if (DEBUG_QUADRANTS) {
                renderQuadrantsToFbo();
            } else {
                renderSceneToFbo();
            }
            // renderSceneToFbo rebinds the UI FBO and leaves its own viewport —
            // restore the UI viewport before blitting the panel quad.
            glViewport(uiVpX, uiVpY, uiVpW, uiVpH);
            blitFboToPanel(panelX, panelY, panelW, panelH, alphaMult);
        } catch (RuntimeException e) {
            LOG.error("Bridge render failed; disabling further attempts", e);
            broken = true;
        } finally {
            glBindFramebuffer(GL_FRAMEBUFFER, uiFboBinding);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glUseProgram(0);
            glMatrixMode(GL_TEXTURE);    glPopMatrix();
            glMatrixMode(GL_MODELVIEW);  glPopMatrix();
            glMatrixMode(GL_PROJECTION); glPopMatrix();
            glPopAttrib();
        }
    }

    private void renderSceneToFbo() {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glViewport(0, 0, fboWidth, fboHeight);

        glColorMask(true, true, true, true);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_STENCIL_TEST);

        glClearColor(0.04f, 0.06f, 0.10f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        glDisable(GL_TEXTURE_2D);

        if (sceneRoot != null && camera != null) {
            camera.aspect = (float) fboWidth / (float) fboHeight;
            float[] viewProj = camera.getViewProjection();
            renderNode(sceneRoot, viewProj, Mat4.identity());
        }

        // Back to the inherited UI draw-FBO (sampled in render()), not a
        // hardcoded 0 — the UI may be rendering into an offscreen target.
        glBindFramebuffer(GL_FRAMEBUFFER, uiFboBinding);
    }

    private static void renderNode(SceneNode node, float[] viewProj, float[] parentWorld) {
        float[] world = Mat4.mul(parentWorld, node.getLocalMatrix());
        if (node.drawable != null) {
            node.drawable.draw(viewProj, world);
        }
        for (SceneNode child : node.getChildren()) {
            renderNode(child, viewProj, world);
        }
    }

    private void blitFboToPanel(float x, float y, float w, float h, float alphaMult) {
        glUseProgram(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, fboColor);
        glColor4f(1f, 1f, 1f, alphaMult);

        glBegin(GL_QUADS);
        glTexCoord2f(0f, 0f); glVertex2f(x,       y);
        glTexCoord2f(1f, 0f); glVertex2f(x + w,   y);
        glTexCoord2f(1f, 1f); glVertex2f(x + w,   y + h);
        glTexCoord2f(0f, 1f); glVertex2f(x,       y + h);
        glEnd();
    }

    // ---- Init / scene build ----

    private void initIfNeeded() {
        if (initialized) return;
        try {
            LOG.info("GL_VERSION: " + glGetString(GL_VERSION));
            LOG.info("GL_RENDERER: " + glGetString(GL_RENDERER));
            LOG.info("GL_SHADING_LANGUAGE_VERSION: " + glGetString(GL_SHADING_LANGUAGE_VERSION));
            initialized = true;
        } catch (RuntimeException e) {
            LOG.error("BridgeRenderer init failed", e);
            broken = true;
        }
    }

    private void ensureFbo(int w, int h) {
        if (fbo != 0 && w == fboWidth && h == fboHeight) return;
        if (fbo != 0) {
            glDeleteFramebuffers(fbo);
            glDeleteTextures(fboColor);
            glDeleteRenderbuffers(fboDepth);
        }
        fboWidth = w;
        fboHeight = h;
        fbo      = glGenFramebuffers();
        fboColor = glGenTextures();
        fboDepth = glGenRenderbuffers();

        glBindTexture(GL_TEXTURE_2D, fboColor);
        // Use a real (zeroed) buffer rather than null — some drivers/LWJGL paths refuse null.
        java.nio.ByteBuffer empty = BufferUtils.createByteBuffer(w * h * 4);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, empty);
        checkGL("glTexImage2D fboColor");
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);

        glBindRenderbuffer(GL_RENDERBUFFER, fboDepth);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, w, h);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);

        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        checkGL("glBindFramebuffer (init)");
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, fboColor, 0);
        checkGL("glFramebufferTexture2D");
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, fboDepth);
        checkGL("glFramebufferRenderbuffer");
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            LOG.error("FBO incomplete: 0x" + Integer.toHexString(status));
            broken = true;
        } else {
            LOG.info("FBO " + fbo + " complete at " + w + "x" + h + " (colorTex=" + fboColor + ", depthRB=" + fboDepth + ")");
        }
    }

    private static void checkGL(String label) {
        int err = glGetError();
        if (err != GL_NO_ERROR) {
            LOG.error("GL error at " + label + ": 0x" + Integer.toHexString(err));
        }
    }

    private static float scaleX() {
        return (float) Display.getWidth() / Math.max(1f, Global.getSettings().getScreenWidth());
    }

    private static float scaleY() {
        return (float) Display.getHeight() / Math.max(1f, Global.getSettings().getScreenHeight());
    }

    // ==== Diagnostics (gated by DEBUG flags above) ===============================

    private void renderDirectQuadrants(float x, float y, float w, float h) {
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        int prevProgram  = glGetInteger(GL_CURRENT_PROGRAM);
        int prevArrayBuf = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        int prevElemBuf  = glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING);
        int prevTex      = glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            glUseProgram(0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_TEXTURE_2D);
            glDisable(GL_BLEND);
            glDisable(GL_LIGHTING);
            glDisable(GL_CULL_FACE);

            float hx = w * 0.5f;
            float hy = h * 0.5f;

            glBegin(GL_QUADS);
            glColor3f(1f, 0f, 0f);
            glVertex2f(x, y); glVertex2f(x + hx, y); glVertex2f(x + hx, y + hy); glVertex2f(x, y + hy);
            glColor3f(0f, 1f, 0f);
            glVertex2f(x + hx, y); glVertex2f(x + w, y); glVertex2f(x + w, y + hy); glVertex2f(x + hx, y + hy);
            glColor3f(0f, 0f, 1f);
            glVertex2f(x, y + hy); glVertex2f(x + hx, y + hy); glVertex2f(x + hx, y + h); glVertex2f(x, y + h);
            glColor3f(1f, 1f, 0f);
            glVertex2f(x + hx, y + hy); glVertex2f(x + w, y + hy); glVertex2f(x + w, y + h); glVertex2f(x + hx, y + h);
            glEnd();
        } finally {
            glBindTexture(GL_TEXTURE_2D, prevTex);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, prevElemBuf);
            glBindBuffer(GL_ARRAY_BUFFER, prevArrayBuf);
            glUseProgram(prevProgram);
            glPopAttrib();
        }
    }

    private void renderQuadrantsToFbo() {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glViewport(0, 0, fboWidth, fboHeight);

        glColorMask(true, true, true, true);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_STENCIL_TEST);

        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_LIGHTING);
        glUseProgram(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);

        glMatrixMode(GL_PROJECTION); glPushMatrix();
        glLoadIdentity();
        glOrtho(0, fboWidth, 0, fboHeight, -1, 1);
        glMatrixMode(GL_MODELVIEW);  glPushMatrix();
        glLoadIdentity();

        float hx = fboWidth * 0.5f;
        float hy = fboHeight * 0.5f;

        glBegin(GL_QUADS);
        glColor3f(1f, 0f, 0f);
        glVertex2f(0f, 0f); glVertex2f(hx, 0f); glVertex2f(hx, hy); glVertex2f(0f, hy);
        glColor3f(0f, 1f, 0f);
        glVertex2f(hx, 0f); glVertex2f(fboWidth, 0f); glVertex2f(fboWidth, hy); glVertex2f(hx, hy);
        glColor3f(0f, 0f, 1f);
        glVertex2f(0f, hy); glVertex2f(hx, hy); glVertex2f(hx, fboHeight); glVertex2f(0f, fboHeight);
        glColor3f(1f, 1f, 0f);
        glVertex2f(hx, hy); glVertex2f(fboWidth, hy); glVertex2f(fboWidth, fboHeight); glVertex2f(hx, fboHeight);
        glEnd();
        checkGL("after quadrant glEnd");

        if (!fboPixelLogged) {
            fboPixelLogged = true;
            java.nio.ByteBuffer pixel = BufferUtils.createByteBuffer(4);
            glReadPixels((int)(hx + hx * 0.5f), (int)(hy + hy * 0.5f), 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            checkGL("glReadPixels (NE/yellow)");
            int r = pixel.get(0) & 0xff;
            int g = pixel.get(1) & 0xff;
            int b = pixel.get(2) & 0xff;
            int a = pixel.get(3) & 0xff;
            LOG.info("FBO pixel @ NE quadrant: rgba=(" + r + ", " + g + ", " + b + ", " + a + ")  [expect 255,255,0,255]");
        }

        glMatrixMode(GL_MODELVIEW);  glPopMatrix();
        glMatrixMode(GL_PROJECTION); glPopMatrix();

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }
}
