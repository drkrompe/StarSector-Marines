package com.dillon.starsectormarines.render;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;

import java.nio.FloatBuffer;
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
 * Renders a procedural cube into an offscreen framebuffer and blits it as a textured
 * quad over the panel's rectangle. Isolates 3D GL state from Starsector's UI render pass
 * via attrib/matrix push-pop plus explicit bind-restore for state not covered by glPushAttrib.
 */
public class BridgeRenderer {

    /** Diagnostic: draw 4 colored quadrants into the FBO instead of the cube. */
    private static final boolean DEBUG_QUADRANTS = false;

    /** Diagnostic: skip the FBO entirely; draw the 4 quadrants directly at the panel rect. */
    private static final boolean DEBUG_DIRECT_QUADRANTS = false;

    private static final Logger LOG = Global.getLogger(BridgeRenderer.class);

    private boolean diagLogged;
    private boolean fboPixelLogged;

    private static final String VS_SOURCE =
            "#version 120\n" +
            "attribute vec3 aPos;\n" +
            "attribute vec3 aColor;\n" +
            "varying vec3 vColor;\n" +
            "uniform mat4 uMvp;\n" +
            "void main() {\n" +
            "    gl_Position = uMvp * vec4(aPos, 1.0);\n" +
            "    vColor = aColor;\n" +
            "}\n";

    private static final String FS_SOURCE =
            "#version 120\n" +
            "varying vec3 vColor;\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(vColor, 1.0);\n" +
            "}\n";

    private boolean initialized;
    private boolean broken;

    private int program;
    private int uMvpLoc;
    private int vbo;
    private int ibo;
    private int indexCount;

    private int fbo;
    private int fboColor;
    private int fboDepth;
    private int fboWidth;
    private int fboHeight;

    private float rotation;
    private final FloatBuffer mvpBuf = BufferUtils.createFloatBuffer(16);

    public void render(float panelX, float panelY, float panelW, float panelH,
                       float dt, float alphaMult) {
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

        rotation += dt;

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
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        glMatrixMode(GL_PROJECTION); glPushMatrix();
        glMatrixMode(GL_MODELVIEW);  glPushMatrix();
        glMatrixMode(GL_TEXTURE);    glPushMatrix();

        int prevProgram  = glGetInteger(GL_CURRENT_PROGRAM);
        int prevArrayBuf = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        int prevElemBuf  = glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING);
        int prevFbo      = glGetInteger(GL_FRAMEBUFFER_BINDING);
        int prevTex      = glGetInteger(GL_TEXTURE_BINDING_2D);

        IntBuffer vpBuf = BufferUtils.createIntBuffer(16);
        glGetInteger(GL_VIEWPORT, vpBuf);
        int vpX = vpBuf.get(0), vpY = vpBuf.get(1), vpW = vpBuf.get(2), vpH = vpBuf.get(3);

        try {
            if (DEBUG_QUADRANTS) {
                renderQuadrantsToFbo();
            } else {
                renderCubeToFbo();
            }
            glViewport(vpX, vpY, vpW, vpH);
            blitFboToPanel(panelX, panelY, panelW, panelH, alphaMult);
        } catch (RuntimeException e) {
            LOG.error("Bridge render failed; disabling further attempts", e);
            broken = true;
        } finally {
            glBindTexture(GL_TEXTURE_2D, prevTex);
            glBindFramebuffer(GL_FRAMEBUFFER, prevFbo);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, prevElemBuf);
            glBindBuffer(GL_ARRAY_BUFFER, prevArrayBuf);
            glUseProgram(prevProgram);
            glMatrixMode(GL_TEXTURE);    glPopMatrix();
            glMatrixMode(GL_MODELVIEW);  glPopMatrix();
            glMatrixMode(GL_PROJECTION); glPopMatrix();
            glPopAttrib();
        }
    }

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
            // SW red
            glColor3f(1f, 0f, 0f);
            glVertex2f(x,        y);
            glVertex2f(x + hx,   y);
            glVertex2f(x + hx,   y + hy);
            glVertex2f(x,        y + hy);
            // SE green
            glColor3f(0f, 1f, 0f);
            glVertex2f(x + hx,   y);
            glVertex2f(x + w,    y);
            glVertex2f(x + w,    y + hy);
            glVertex2f(x + hx,   y + hy);
            // NW blue
            glColor3f(0f, 0f, 1f);
            glVertex2f(x,        y + hy);
            glVertex2f(x + hx,   y + hy);
            glVertex2f(x + hx,   y + h);
            glVertex2f(x,        y + h);
            // NE yellow
            glColor3f(1f, 1f, 0f);
            glVertex2f(x + hx,   y + hy);
            glVertex2f(x + w,    y + hy);
            glVertex2f(x + w,    y + h);
            glVertex2f(x + hx,   y + h);
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

        // Starsector keeps GL_COLOR_WRITEMASK with alpha=false for its UI compositing.
        // That mask leaks into our FBO bind, so without re-enabling alpha writes the
        // FBO texture's alpha channel stays 0 and SRC_ALPHA blending in the blit kills it.
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

        // Inner push so our matrix overrides don't leak into the blit step
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, fboWidth, 0, fboHeight, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        float hx = fboWidth * 0.5f;
        float hy = fboHeight * 0.5f;

        glBegin(GL_QUADS);
        // SW red
        glColor3f(1f, 0f, 0f);
        glVertex2f(0f, 0f); glVertex2f(hx, 0f); glVertex2f(hx, hy); glVertex2f(0f, hy);
        // SE green
        glColor3f(0f, 1f, 0f);
        glVertex2f(hx, 0f); glVertex2f(fboWidth, 0f); glVertex2f(fboWidth, hy); glVertex2f(hx, hy);
        // NW blue
        glColor3f(0f, 0f, 1f);
        glVertex2f(0f, hy); glVertex2f(hx, hy); glVertex2f(hx, fboHeight); glVertex2f(0f, fboHeight);
        // NE yellow
        glColor3f(1f, 1f, 0f);
        glVertex2f(hx, hy); glVertex2f(fboWidth, hy); glVertex2f(fboWidth, fboHeight); glVertex2f(hx, fboHeight);
        glEnd();
        checkGL("after quadrant glEnd");

        if (!fboPixelLogged) {
            fboPixelLogged = true;
            java.nio.ByteBuffer pixel = org.lwjgl.BufferUtils.createByteBuffer(4);
            // Read center of NE (yellow) quadrant
            glReadPixels((int)(hx + hx * 0.5f), (int)(hy + hy * 0.5f), 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            checkGL("glReadPixels (NE/yellow)");
            int r = pixel.get(0) & 0xff;
            int g = pixel.get(1) & 0xff;
            int b = pixel.get(2) & 0xff;
            int a = pixel.get(3) & 0xff;
            LOG.info("FBO pixel @ NE quadrant: rgba=(" + r + ", " + g + ", " + b + ", " + a + ")  [expect 255,255,0,255]");
        }

        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private void renderCubeToFbo() {
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

        float aspect = (float) fboWidth / (float) fboHeight;
        float[] proj  = perspective(60f, aspect, 0.1f, 50f);
        float[] view  = translation(0f, 0f, -4f);
        float[] model = mul(rotationY(rotation), rotationX(rotation * 0.6f));
        float[] mvp   = mul(proj, mul(view, model));

        glUseProgram(program);
        mvpBuf.clear();
        mvpBuf.put(mvp).flip();
        glUniformMatrix4(uMvpLoc, false, mvpBuf);

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 24, 0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 24, 12);

        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);

        glDisableVertexAttribArray(0);
        glDisableVertexAttribArray(1);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
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

    // ---- Init ----

    private void initIfNeeded() {
        if (initialized) return;
        try {
            LOG.info("GL_VERSION: " + glGetString(GL_VERSION));
            LOG.info("GL_RENDERER: " + glGetString(GL_RENDERER));
            LOG.info("GL_SHADING_LANGUAGE_VERSION: " + glGetString(GL_SHADING_LANGUAGE_VERSION));
            program = buildProgram();
            uMvpLoc = glGetUniformLocation(program, "uMvp");
            buildCube();
            initialized = true;
        } catch (RuntimeException e) {
            LOG.error("BridgeRenderer init failed", e);
            broken = true;
        }
    }

    private static void checkGL(String label) {
        int err = glGetError();
        if (err != GL_NO_ERROR) {
            LOG.error("GL error at " + label + ": 0x" + Integer.toHexString(err));
        }
    }

    private static int buildProgram() {
        int vs = compileShader(GL_VERTEX_SHADER, VS_SOURCE);
        int fs = compileShader(GL_FRAGMENT_SHADER, FS_SOURCE);
        int prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glBindAttribLocation(prog, 0, "aPos");
        glBindAttribLocation(prog, 1, "aColor");
        glLinkProgram(prog);
        if (glGetProgrami(prog, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(prog, 4096);
            throw new RuntimeException("Shader link failed: " + log);
        }
        glDeleteShader(vs);
        glDeleteShader(fs);
        return prog;
    }

    private static int compileShader(int type, String src) {
        int s = glCreateShader(type);
        glShaderSource(s, src);
        glCompileShader(s);
        if (glGetShaderi(s, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(s, 4096);
            throw new RuntimeException("Shader compile failed: " + log);
        }
        return s;
    }

    private void buildCube() {
        // 24 vertices (4 per face) × (3 pos + 3 color) floats
        float[] verts = cubeVertices();
        int[]   idx   = cubeIndices();
        indexCount = idx.length;

        FloatBuffer vbuf = BufferUtils.createFloatBuffer(verts.length);
        vbuf.put(verts).flip();
        IntBuffer ibuf = BufferUtils.createIntBuffer(idx.length);
        ibuf.put(idx).flip();

        vbo = glGenBuffers();
        ibo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vbuf, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, ibuf, GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
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
        java.nio.ByteBuffer empty = org.lwjgl.BufferUtils.createByteBuffer(w * h * 4);
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

    // ---- Geometry ----

    private static float[] cubeVertices() {
        float[][] faces = {
                // +Z front (red)
                {-1, -1,  1, 1, 0.3f, 0.3f,  1, -1,  1, 1, 0.3f, 0.3f,  1,  1,  1, 1, 0.3f, 0.3f, -1,  1,  1, 1, 0.3f, 0.3f},
                // -Z back (green)
                { 1, -1, -1, 0.3f, 1, 0.3f, -1, -1, -1, 0.3f, 1, 0.3f, -1,  1, -1, 0.3f, 1, 0.3f,  1,  1, -1, 0.3f, 1, 0.3f},
                // +X right (blue)
                { 1, -1,  1, 0.4f, 0.5f, 1,    1, -1, -1, 0.4f, 0.5f, 1,    1,  1, -1, 0.4f, 0.5f, 1,    1,  1,  1, 0.4f, 0.5f, 1},
                // -X left (yellow)
                {-1, -1, -1, 1, 0.9f, 0.3f, -1, -1,  1, 1, 0.9f, 0.3f, -1,  1,  1, 1, 0.9f, 0.3f, -1,  1, -1, 1, 0.9f, 0.3f},
                // +Y top (magenta)
                {-1,  1,  1, 1, 0.4f, 0.9f,  1,  1,  1, 1, 0.4f, 0.9f,  1,  1, -1, 1, 0.4f, 0.9f, -1,  1, -1, 1, 0.4f, 0.9f},
                // -Y bottom (cyan)
                {-1, -1, -1, 0.4f, 0.95f, 0.95f, 1, -1, -1, 0.4f, 0.95f, 0.95f, 1, -1,  1, 0.4f, 0.95f, 0.95f, -1, -1,  1, 0.4f, 0.95f, 0.95f},
        };
        float[] flat = new float[6 * 24];
        int o = 0;
        for (float[] f : faces) {
            System.arraycopy(f, 0, flat, o, 24);
            o += 24;
        }
        return flat;
    }

    private static int[] cubeIndices() {
        int[] idx = new int[36];
        for (int f = 0; f < 6; f++) {
            int b = f * 4;
            int o = f * 6;
            idx[o]     = b;
            idx[o + 1] = b + 1;
            idx[o + 2] = b + 2;
            idx[o + 3] = b;
            idx[o + 4] = b + 2;
            idx[o + 5] = b + 3;
        }
        return idx;
    }

    // ---- Math (column-major float[16]) ----

    private static float[] identity() {
        float[] m = new float[16];
        m[0] = m[5] = m[10] = m[15] = 1f;
        return m;
    }

    private static float[] perspective(float fovYDeg, float aspect, float near, float far) {
        float f = (float) (1.0 / Math.tan(Math.toRadians(fovYDeg) * 0.5));
        float[] m = new float[16];
        m[0]  = f / aspect;
        m[5]  = f;
        m[10] = (far + near) / (near - far);
        m[11] = -1f;
        m[14] = (2f * far * near) / (near - far);
        return m;
    }

    private static float[] translation(float x, float y, float z) {
        float[] m = identity();
        m[12] = x;
        m[13] = y;
        m[14] = z;
        return m;
    }

    private static float[] rotationY(float r) {
        float c = (float) Math.cos(r);
        float s = (float) Math.sin(r);
        float[] m = identity();
        m[0] = c;  m[2]  = -s;
        m[8] = s;  m[10] =  c;
        return m;
    }

    private static float[] rotationX(float r) {
        float c = (float) Math.cos(r);
        float s = (float) Math.sin(r);
        float[] m = identity();
        m[5] =  c;  m[6]  = s;
        m[9] = -s;  m[10] = c;
        return m;
    }

    private static float[] mul(float[] a, float[] b) {
        float[] c = new float[16];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0f;
                for (int k = 0; k < 4; k++) {
                    sum += a[k * 4 + row] * b[col * 4 + k];
                }
                c[col * 4 + row] = sum;
            }
        }
        return c;
    }

    private static float scaleX() {
        return (float) Display.getWidth() / Math.max(1f, Global.getSettings().getScreenWidth());
    }

    private static float scaleY() {
        return (float) Display.getHeight() / Math.max(1f, Global.getSettings().getScreenHeight());
    }
}
